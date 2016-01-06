package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Active Learning experiments.
 * Created by luheng on 1/5/16.
 */
public class ActiveLearningPrototype {
    // Training pool.
    static List<List<InputWord>> sentences;
    static List<List<Category>> goldCategories;
    static List<Set<ResolvedDependency>> goldParses;
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
        goldCategories = new ArrayList<>();
        goldParses = new ArrayList<>();
        ActiveLearningDataHelper.readDevPool(sentences, goldCategories, goldParses);

        // Initialize parsers.
        parsers = new ArrayList<>();
        parsers.add(new BaseCcgParser.EasyCCGParser(commandLineOptions.getModel(), 1 /* nBest */));
        // TODO: add Bharat parser.

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

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            // Print sentence info.
            List<InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());
            System.out.println("\n" + StringUtils.join(words));

            // Parse using all the base parsers.
            List<List<Category>> tagged = new ArrayList<>();
            List<Set<ResolvedDependency>> parsed = new ArrayList<>();
            for (BaseCcgParser parser : parsers) {
                List<Category> categories = new ArrayList<>();
                Set<ResolvedDependency> dependencies = new HashSet<>();
                // TODO: change the interface.
                parser.parse(sentences.get(sentIdx), categories, dependencies);
                tagged.add(categories);
                parsed.add(dependencies);

                Set<ResolvedDependency> goldDependencies = goldParses.get(sentIdx);
                Set<ResolvedDependency> fixedDependencies = new HashSet<>();

                // Generate possible questions over predicted dependencies.
                for (ResolvedDependency targetDependency : dependencies) {
                    if (questionGenerator.filterPredicate(words.get(targetDependency.getHead()),
                            targetDependency.getCategory())) {
                        fixedDependencies.add(targetDependency);
                        continue;
                    }
                    // Need question scorer here.
                    List<String> question =
                            questionGenerator.generateQuestion(targetDependency, words, categories, dependencies);
                    if (question == null || question.size() == 0) {
                        fixedDependencies.add(targetDependency);
                        continue;
                    }

                    int expectedAnswer = targetDependency.getArgumentIndex();
                    List<Integer> simulatedAnswer = responseSimulator.answerQuestion(question, words, targetDependency,
                            goldCategories.get(sentIdx), goldDependencies);
                    String simulatedAnswerStr = simulatedAnswer.get(0) == -1 ? "N/A" :
                            StringUtils.join(simulatedAnswer.stream().map(idx -> words.get(idx))
                                    .collect(Collectors.toList()));

                    // Fix dependency according to answer response. Ideally we can do self-training.
                    // TODO: debug on N/A cases
                    boolean fixed = false;
                    if (simulatedAnswer.get(0) >= 0 && !simulatedAnswer.contains(targetDependency.getArgument())) {
                        for (int answerHead : simulatedAnswer) {
                            fixedDependencies.add(new ResolvedDependency(
                                    targetDependency.getHead(),
                                    targetDependency.getCategory(),
                                    targetDependency.getArgNumber(),
                                    answerHead,
                                    targetDependency.getSemanticRole(),
                                    targetDependency.getPreposition()));
                        }
                        fixed = true;
                    } else {
                        fixedDependencies.add(targetDependency);
                    }

                    // Debugging information;
                    boolean matched = DependencyEvaluation.matchesAnyGoldDependency(targetDependency, goldDependencies);
                    if ((matched && fixed) || (!matched && !fixed)) {
                        System.out.println(targetDependency.toString(words) + "\t"
                                + targetDependency.getCategory() + "\t"
                                + StringUtils.join(question) + "?\t"
                                + words.get(expectedAnswer) + "\t"
                                + simulatedAnswerStr + "\t"
                                + matched + "\t" + fixed);
                    }
                }
                System.out.println();

                before.add(DependencyEvaluation.evaluate(dependencies, goldDependencies));
                after.add(DependencyEvaluation.evaluate(fixedDependencies, goldDependencies));
                numQuestionsAsked ++;
            }
        }

        System.out.println(before);
        System.out.println("After fixing dependencies.");
        System.out.println(after);
        System.out.println("Number of questions asked:\t" + numQuestionsAsked);
    }

    public static void main(String[] args) {
        initialize(args);
        run();
    }
}
