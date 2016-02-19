package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.syntax.grammar.Category;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Simulates user response in active learning. When presented with a question, the simulator answerOptions with its knowledge
 * of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulatorGold extends ResponseSimulator {
    private final List<Parse> goldParses;
    private QuestionGenerator questionGenerator;
    private boolean allowLabelMatch = true;

    // Evidence propagation switches
    // TODO: debug this ..
    public boolean propagateArgumentAdjunctEvidence = false;

    // TODO: simulate noise level.
    // TODO: partial reward for parses that got part of the answer heads right ..
    public ResponseSimulatorGold(List<Parse> goldParses, QuestionGenerator questionGenerator) {
        this.goldParses = goldParses;
        this.questionGenerator = questionGenerator;
    }

    public ResponseSimulatorGold(List<Parse> goldParses, QuestionGenerator questionGenerator, boolean allowLabelMatch) {
        this(goldParses, questionGenerator);
        this.allowLabelMatch = allowLabelMatch;
    }


    /**
     * If exists a gold dependency that generates the same question ...
     * @param query: question
     * @return Answer is represented a list of indices in the sentence.
     *          A single -1 in the list means ``unintelligible/unanswerable question.
     */
     public Response answerQuestion(GroupedQuery query) {
        final Parse goldParse = goldParses.get(query.sentenceId);
        final List<String> sentence = query.sentence;
        List<Integer> answerIndices = new ArrayList<>();
        for (ResolvedDependency dep : goldParse.dependencies) {
            if (dep.getHead() != query.predicateIndex) {
                continue;
            }
            if (matches(query, dep, goldParse)) {
                answerIndices.addAll(TextGenerationHelper.getArgumentIdsForDependency(sentence, goldParse, dep));
            }
        }
        Response response = new Response();
        int badQuestionOptionId = -1, noAnswerOptionId = -1;
        for (int i = 0; i < query.answerOptions.size(); i++) {
            GroupedQuery.AnswerOption option = query.answerOptions.get(i);
            if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                badQuestionOptionId = i;
                continue;
            } else if (GroupedQuery.NoAnswerOption.class.isInstance(option)) {
                noAnswerOptionId = i;
                continue;
            }
            if (answerIndices.containsAll(option.argumentIds) && option.argumentIds.containsAll(answerIndices)) {
                response.add(i);
            }
        }
        if (response.chosenOptions.size() == 0) {
            if (answerIndices.size() > 0) {
                response.add(noAnswerOptionId);
                response.debugInfo = "[gold]:\t" + answerIndices.stream().map(String::valueOf)
                        .collect(Collectors.joining(", "));
            } else {
                response.add(badQuestionOptionId);
            }
        }
        return response;
    }

    private boolean matches(final GroupedQuery query, final ResolvedDependency targetDependency,
                            final Parse goldParse) {
        final List<String> sentence = query.sentence;
        Optional<QuestionAnswerPair> goldQaPairOpt = questionGenerator.generateQuestion(targetDependency, sentence,
                goldParse);
        String goldQuestionStr = goldQaPairOpt.isPresent() ? goldQaPairOpt.get().renderQuestion() : "-NOQ-";
        boolean questionMatch = query.question.equalsIgnoreCase(goldQuestionStr);
        boolean labelMatch = (targetDependency.getCategory() == query.category &&
                targetDependency.getArgNumber() == query.argumentNumber);
        if (questionMatch || (allowLabelMatch && labelMatch)) {
            return true;
        }
        if (!goldQaPairOpt.isPresent()) {
            return false;
        }
        if (propagateArgumentAdjunctEvidence) {
            final Category verb = Category.valueOf("S\\NP");
            final Category verbAdjunct = Category.valueOf("(S\\NP)\\(S\\NP)");
            /*
            if (query.category.isFunctionInto(verbAdjunct) ||
                    targetDependency.getCategory().isFunctionInto(verbAdjunct)) {
                return false;
            }*/
            if (query.category.isFunctionInto(verb) && targetDependency.getCategory().isFunctionInto(verb)) {
                if ((query.argumentNumber == 1 && targetDependency.getArgNumber() > 1) ||
                        (query.argumentNumber > 1 && targetDependency.getArgNumber() == 1)) {
                    return false;
                }
                List<Category> args = getArgumentChain(query.category);
                List<Category> goldArgs = getArgumentChain(targetDependency.getCategory());
                String argsStr= args.stream().map(Category::toString).collect(Collectors.joining(" "));
                String goldArgsStr = goldArgs.stream().map(Category::toString).collect(Collectors.joining(" "));
                // 1. If query is has less arguments than gold and the missing arguments have number >= 3.
                if (args.size() < goldArgs.size() && args.size() >= 2 && goldArgsStr.startsWith(argsStr)) {
                    // For debugging.
                    System.out.println(sentence.stream().collect(Collectors.joining(" ")));
                    System.out.println("Gen:\t" + query.category + "\t" + query.argumentNumber + "\t" + query.question);
                    System.out.println("Gold:\t" + targetDependency.getCategory() + "\t" + targetDependency.getArgNumber() + "\t" + goldQaPairOpt.get().renderQuestion());
                    return true;
                }
                // 2. If query is has more arguments than gold and the additional arguments are adjuncts in gold.
                if (args.size() > goldArgs.size() && argsStr.startsWith(goldArgsStr)) {
                    boolean fuzzyMatch = true;
                    for (ResolvedDependency dep : query.questionDependencies) {
                        if (dep.getHead() == query.predicateIndex
                                && !isArgumentOrAdjunct(dep.getHead(), dep.getArgument(), goldParse)) {
                            fuzzyMatch = false;
                            break;
                        }
                    }
                    if (fuzzyMatch) {
                        // For debugging.
                        //System.out.println(sentence.stream().collect(Collectors.joining(" ")));
                        //System.out.println("Gen:\t" + query.argumentNumber + "\t" + query.question);
                        //System.out.println("Gold:\t" + targetDependency.getArgNumber() + "\t" + goldQaPairOpt.get().renderQuestion());
                        return true;
                    }
                }
            }   
        }
        return false;
    }

    private static List<Category> getArgumentChain(Category predicate) {
        assert predicate.getNumberOfArguments() > 1;
        List<Category> arguments = new ArrayList<>();
        arguments.add(predicate.getArgument(1));
        for (int i = predicate.getNumberOfArguments(); i > 1; i--) {
            arguments.add(predicate.getArgument(i));
        }
        return arguments;
    }

    private static boolean isArgumentOrAdjunct(int head1, int head2, Parse parse) {
        for (ResolvedDependency dep : parse.dependencies) {
            if ((dep.getHead() == head1 && dep.getArgumentIndex() == head2)
                    || (dep.getHead() == head2 && dep.getArgumentIndex() == head1)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        Category c0 = Category.valueOf("S\\NP");
        Category c1 = Category.valueOf("(S\\NP)/NP");
        Category c2 = Category.valueOf("((S\\NP)/PP)/NP");
        System.out.println(c1.isFunctionInto(c0));
        System.out.println(c2.isFunctionInto(c0));
        System.out.println(c1.getLeft() + "\t" + c1.getRight());
        System.out.println(c2.getLeft() + "\t" + c2.getRight());
        for (int i = 0; i < c1.getNumberOfArguments(); i++) {
            System.out.println(i + " " + c1.getArgument(i + 1));
        }
        for (int i = 0; i < c2.getNumberOfArguments(); i++) {
            System.out.println(i + " " + c2.getArgument(i + 1));
        }
    }
}
