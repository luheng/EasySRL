package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Active Learning experiments.
 * Created by luheng on 1/5/16.
 */
public class ActiveLearningPrototype {
    // Training pool.
    static List<List<InputWord>> sentences;
    static List<Parse> goldParses;
    static List<BaseCcgParser> parsers;

    // Modules :)
    static QuestionGenerator questionGenerator;
    static ResponseSimulator responseSimulator;

    private static void initialize(String[] args) {
        // TODO: use ActiveLearning.CommandLineArguments
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            return;
        }
        // Initialize corpora.
        // TODO: better data structure ...
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);

        // Initialize parsers.
        parsers = new ArrayList<>();
        parsers.add(new BaseCcgParser.EasyCCGParser(commandLineOptions.getModel(), 1 /* nBest */));
        //parsers.add(new BaseCcgParser.EasySRLParser(commandLineOptions.getModel(), 1 /* nBest */));
        //parsers.add(new BaseCcgParser.BharatParser("propbank.dev.txt.parg"));

        // Initialize the other modules.
        questionGenerator = new QuestionGenerator();
        responseSimulator = new ResponseSimulator(questionGenerator);
    }

    private static void run() {
        // TODO: shuffle input
        // TODO: self-training
        Results before = new Results();
        Results after = new Results();
        int numQuestionsAsked = 0;
        int numEffectiveQuestionsAsked = 0;
        int numMultiArgs = 0, numGoldMultiArgs = 0;

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            // Print sentence info.
            List<InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());

            StringBuffer debugOutput = new StringBuffer();
            StringBuffer extendedDebugOutput = new StringBuffer(); // contains information about every dependency.
            Set<Integer> debugPredicates = new HashSet<>();

            extendedDebugOutput.append("*** predicted ***\n");

            // Parse using all the base parsers.
            Set<ResolvedDependency> goldDependencies = goldParses.get(sentIdx).dependencies;
            List<List<Category>> tagged = new ArrayList<>();
            List<Set<ResolvedDependency>> parsed = new ArrayList<>();
            BaseCcgParser parser = parsers.get(0);
            Parse parse;
            // TODO: change the interface.
            // TODO: debug nullpointer exception.
            // probably difference between EasyCCG.makeParser and ActiveLearningHelper.makeParser or we are using the wrong model
            try {
                parse = parser.parse(sentences.get(sentIdx));
                if (parse == null) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            List<Category> categories = parse.categories;
            Set<ResolvedDependency> dependencies = parse.dependencies;

            Set<String> multiArgs = new HashSet<>();
            for (ResolvedDependency dep : dependencies) {
                String dk = dep.getHead() + "." + dep.getArgNumber();
                if (multiArgs.contains(dk)) {
                    ++ numMultiArgs;
                }
                multiArgs.add(dk);
            }

            multiArgs.clear();
            for (ResolvedDependency dep : goldDependencies) {
                String dk = dep.getHead() + "." + dep.getArgNumber();
                if (multiArgs.contains(dk)) {
                    ++ numGoldMultiArgs;
                }
                multiArgs.add(dk);
            }


            tagged.add(categories);
            parsed.add(dependencies);
            System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words));
            System.out.println(dependencies.size());
            for (ResolvedDependency dep : dependencies) {
                System.out.println(
                        String.format("%s\t%s.%d\t%s\t", words.get(dep.getHead()),
                                dep.getCategory(), dep.getArgNumber(),
                                dep.getCategory().getArgument(dep.getArgNumber())));
            }
            System.out.println("Multi args:\t" + numMultiArgs + "\t Gold multi args:\t" + numGoldMultiArgs);
            /*
            extendedDebugOutput.append("*** gold ***\n");
            for (ResolvedDependency goldDep : goldDependencies) {
                if (!CcgEvaluation.matchesAny(goldDep, newDependencies)) {
                    List<String> question = questionGenerator.generateQuestion(
                            goldDep, words, goldCategories.get(sentIdx), goldDependencies);
                    String questionStr = (question == null || question.size() == 0) ? "-noq-" :
                            StringUtils.join(question);
                    extendedDebugOutput.append(
                            String.format("%s\t%s\t%d\t%s\t%s\t%s\t%s\t", words.get(goldDep.getHead()),
                                    goldDep.getCategory(), goldDep.getArgNumber(),
                                    goldDep.getCategory().getArgument(goldDep.getArgNumber()),
                                    StringUtils.capitalize(questionStr) + "?",
                                    "---", words.get(goldDep.getArgument())));
                    if (questionStr.equals("-noq-") || categories.size() == 0) {
                        extendedDebugOutput.append("recall loss\n");
                    } else {
                        extendedDebugOutput.append("tagging error\t");
                        extendedDebugOutput.append(categories.get(goldDep.getHead()) + "\n");
                    }
                }
            }
            */
            // If there is actually precision and recall loss.
            /*
            if (extendedDebugOutput.length() > "*** predicted ***\n*** gold ***\n".length()) {
                System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words) + "\n" +
                        extendedDebugOutput);
            }
            */
            before.add(CcgEvaluation.evaluate(dependencies, goldDependencies));
            //after.add(CcgEvaluation.evaluate(newDependencies, goldDependencies));
        }
        System.out.println("\n" + before);
        //System.out.println("After fixing dependencies."); System.out.println(after);
        System.out.println("Number of questions asked:\t" + numQuestionsAsked);
        System.out.println("Number of effective questions asked:\t" + numEffectiveQuestionsAsked);
    }

    private Set<ResolvedDependency> fixDependencies() {
        //Set<String> fixedDependencies = new HashSet<>();
        Set<ResolvedDependency> newDependencies = new HashSet<>();

        // Generate possible questions over predicted dependencies.
            /*
            for (ResolvedDependency targetDependency : dependencies) {
                boolean matched = CcgEvaluation.matchesAny(targetDependency, goldDependencies);
                // Need question scorer here.
                List<String> question =
                        questionGenerator.generateQuestion(targetDependency, words, categories, dependencies);
                if (question == null || question.size() == 0) {
                    if (!matched) {
                        extendedDebugOutput.append(
                                String.format("%s\t%s\t%d\t%s\t%s\t%s\t%s\t", words.get(targetDependency.getHead()),
                                        targetDependency.getCategory(), targetDependency.getArgNumber(),
                                        targetDependency.getCategory().getArgument(targetDependency.getArgNumber()),
                                        "-noq-", words.get(targetDependency.getArgument()), "---"));
                        extendedDebugOutput.append(matched ? "matched\n" : "wrong\n");
                    }
                    continue;
                }

                String questionStr = StringUtils.join(question);
                int expectedAnswer = targetDependency.getArgumentIndex();
                List<Integer> simulatedAnswer = responseSimulator.answerQuestion(question, words, targetDependency,
                        goldCategories.get(sentIdx), goldDependencies);
                String simulatedAnswerStr = simulatedAnswer.get(0) == -1 ? "N/A" :
                        StringUtils.join(simulatedAnswer.stream().map(idx -> words.get(idx))
                                .collect(Collectors.toList()));

                // Fix dependency according to answer response. Ideally we can do self-training.
                // TODO: debug on N/A cases
                // TODO: better logic here
                boolean fixed = false;
                if (simulatedAnswer.get(0) >= 0 && (!simulatedAnswer.contains(targetDependency.getArgument()) ||
                        simulatedAnswer.size() > 1)) {
                    fixedDependencies.add(getDependencyKey(targetDependency));
                    simulatedAnswer.forEach(ans -> newDependencies.add(getFixedDependency(targetDependency, ans)));

                    List<Integer> auxChain = questionGenerator.verbHelper.getAuxiliaryChain(words, categories,
                            targetDependency.getHead());
                    if (auxChain.size() > 0) {
                        for (ResolvedDependency dep : dependencies) {
                            if (auxChain.contains(dep.getHead()) && dep.getArgument() == expectedAnswer) {
                                fixedDependencies.add(getDependencyKey(dep));
                                simulatedAnswer.forEach(ans -> newDependencies.add(getFixedDependency(dep, ans)));
                            }
                        }
                    }
                    fixed = true;
                }
                numQuestionsAsked ++;
                if (fixed) {
                    numEffectiveQuestionsAsked ++;
                }
                if (!matched || fixed) {
                    extendedDebugOutput.append(
                            String.format("%s\t%s\t%d\t%s\t%s\t%s\t%s\t", words.get(targetDependency.getHead()),
                                    targetDependency.getCategory(), targetDependency.getArgNumber(),
                                    targetDependency.getCategory().getArgument(targetDependency.getArgNumber()),
                                    StringUtils.capitalize(questionStr) + "?",
                                    words.get(expectedAnswer), simulatedAnswerStr));
                    extendedDebugOutput.append(matched ? "matched\t" : "wrong\t");
                    extendedDebugOutput.append(fixed ? "fixed\n" : "unfixed\n");
                }
                // Debugging information;
                if ((matched && fixed) || (!matched && !fixed)) {
                    debugOutput.append(targetDependency.toString(words) + "\t" +
                                       targetDependency.getCategory() + "\t");
                    debugOutput.append(StringUtils.join(question) + "?\t");
                    debugOutput.append(words.get(expectedAnswer) + "\t" + simulatedAnswerStr + "\t");
                    debugOutput.append(matched + "\t" + fixed + "\n");
                    debugPredicates.add(targetDependency.getHead());
                }
            }
            dependencies.forEach(dep -> {
                if (!fixedDependencies.contains(getDependencyKey(dep))) {
                    newDependencies.add(dep);
                }
            });
            */
        //newDependencies.addAll(dependencies);
        return newDependencies;
    }

    private static String getDependencyKey(ResolvedDependency dep) {
        return dep.getHead() + "_" + dep.getArgNumber();
    }

    private static ResolvedDependency getFixedDependency(ResolvedDependency dep, int newArgumentIndex) {
        return new ResolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(), newArgumentIndex,
                dep.getSemanticRole(), dep.getPreposition());
    }

    public static void main(String[] args) {
        initialize(args);
        run();
    }
}

/*
for (ResolvedDependency dep : goldDependencies) {
    if (debugPredicates.contains(dep.getHead())) {
        List<String> question = questionGenerator.generateQuestion(
                dep, words, goldCategories.get(sentIdx), goldDependencies);
        String questionStr = (question == null || question.size() == 0) ? "---" :
                StringUtils.join(question);
        System.out.println(dep.toString(words) + "\t" + dep.getCategory() + "\t" + questionStr);
    }
}
*/
