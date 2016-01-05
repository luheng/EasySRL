package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
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
public class ActiveLearningSandbox {
    // Training pool.
    static List<List<InputWord>> sentences;
    static List<Set<ResolvedDependency>> goldParses;
    static List<BaseCcgParser> parsers;
    static QuestionGenerator questionGenerator;

    private static void initialize(String[] args) {
        // TODO: use ActiveLearning.CommandLineArguments
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            return;
        }
        // Initialize corpora.
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        ActiveLearningDataHelper.readDevPool(sentences, goldParses);

        // Initialize parsers.
        parsers = new ArrayList<>();
        parsers.add(new BaseCcgParser.EasyCCGParser(commandLineOptions.getModel(), 1 /* nBest */));
        // TODO: add Bharat parser.

        // Initialize question generator.
        questionGenerator = new QuestionGenerator();
    }

    private static void run() {
        // TODO: shuffle input
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

                // Evaluate
                Results result = DependencyEvaluation.evaluate(dependencies, goldDependencies);
                System.out.println(result);

                // Generate possible questions over predicted dependencies.
                for (ResolvedDependency targetDependency : dependencies) {
                    if (questionGenerator.filterPredicate(words.get(targetDependency.getHead()),
                            targetDependency.getCategory())) {
                        continue;
                    }
                    // Need question scorer here.
                    List<String> question =
                            questionGenerator.generateQuestion(targetDependency, words, categories, dependencies);
                    System.out.println(targetDependency.toString(words) + "\t\t"
                            + StringUtils.join(question) + "?\t"
                            + DependencyEvaluation.matchesAnyGoldDependency(targetDependency, goldDependencies));
                }
                System.out.println();
            }

        }
    }

    public static void main(String[] args) {
        initialize(args);
        run();
    }
}
