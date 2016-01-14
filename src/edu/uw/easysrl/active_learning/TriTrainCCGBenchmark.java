package edu.uw.easysrl.active_learning;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.evaluation.Results;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single parser baseline on dev set.
 *
 *      Tagging accuracy = 0.9447142825167312
 *       Precision = 87.42
 *       Recall    = 86.62
 *       F1        = 87.02
 *
 * Created by luheng on 1/13/16.
 */
public class TriTrainCCGBenchmark {
    static List<List<InputReader.InputWord>> sentences;
    static List<Parse> goldParses;
    static BaseCcgParser parser;

    private static void initialize(String[] args, int nBest) {
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            return;
        }
        // Initialize corpora.
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        parser = new BaseCcgParser.EasyCCGParser(commandLineOptions.getModel(), nBest);
    }

    private static void run(boolean verbose) {
        // TODO: evaluate diversity of n-best parses
        // TODO: timer
        // TODO: tagging-parsing accuracy correlation
        // TODO: can we automatically detect sentences with low tagging accuracy?
        Results parsingAcc = new Results();
        Accuracy taggingAcc = new Accuracy();
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            List<InputReader.InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());
            Parse gold = goldParses.get(sentIdx);
            Parse predict;
            try {
                predict = parser.parse(sentences.get(sentIdx));
                if (predict == null) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            Set<ResolvedDependency> dependencies = predict.dependencies;
            Results sentenceF1 = CcgEvaluation.evaluate(dependencies, gold.dependencies);
            Accuracy sentenceAcc = CcgEvaluation.evaluateTags(predict.categories, gold.categories);
            parsingAcc.add(sentenceF1);
            taggingAcc.add(sentenceAcc);
            // Verbose output.
            if (verbose) {
                System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words));
                System.out.println(dependencies.size());
                for (ResolvedDependency dep : dependencies) {
                    System.out.println(
                            String.format("%s\t%s.%d\t%s\t", words.get(dep.getHead()),
                                    dep.getCategory(), dep.getArgNumber(),
                                    dep.getCategory().getArgument(dep.getArgNumber())));
                }
                System.out.println(sentenceAcc + "\n" + sentenceF1);
            }
        }
        System.out.println("\n" + taggingAcc + "\n" + parsingAcc);
    }

    public static void runNBestOracle(boolean verbose) {
        Results parsingAcc = new Results();
        Accuracy taggingAcc = new Accuracy();
        int numParsed = 0;
        int avgNBest = 0;
        int avgBestK = 0;

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            List<InputReader.InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());
            Parse gold = goldParses.get(sentIdx);
            List<Parse> parses;
            try {
                parses = parser.parseNBest(sentence);
            } catch (Exception e) {
                continue;
            }

            Results bestF1 = new Results();
            int bestK = -1;
            for (int k = 0; k < parses.size(); k++) {
                Results f1 = CcgEvaluation.evaluate(parses.get(k).dependencies, gold.dependencies);
                if (f1.getF1() > bestF1.getF1()) {
                    bestF1 = f1;
                    bestK = k;
                }
            }
            avgBestK += bestK;
            numParsed ++;
            avgNBest += parses.size();
            parsingAcc.add(bestF1);
            Accuracy bestAcc = CcgEvaluation.evaluateTags(parses.get(bestK).categories, gold.categories);
            taggingAcc.add(bestAcc);
            // Verbose output best-K info.
            if (verbose) {
                System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words));
                Set<ResolvedDependency> dependencies = parses.get(bestK).dependencies;
                System.out.println(dependencies.size());
                for (ResolvedDependency dep : dependencies) {
                    System.out.println(String.format("%s\t%s.%d\t%s\t", words.get(dep.getHead()), dep.getCategory(),
                            dep.getArgNumber(), dep.getCategory().getArgument(dep.getArgNumber())));
                }
                System.out.println(bestK + "\n" + bestF1 + "\n" + bestAcc);
            }
        }
        System.out.println("\n" + taggingAcc + "\n" + parsingAcc);
        System.out.println("Avg. n-best:\t" + 1.0 * avgNBest / numParsed +
                           "\t Avg. best-k:\t" + 1.0 * avgBestK / numParsed);
    }

    public static void run1BestExperiment(String[] args) {
        System.out.println("Running 1-Best Benchmark.");
        initialize(args, 1 /* nBest */);
        run(true /* verbose */);
    }

    public static void runNBestOracleExperiment(String[] args, int nBest) {
        System.out.println(String.format("Running %d-Best Benchmark.", nBest));
        initialize(args, nBest);
        runNBestOracle(true /* verbose */);
    }

    public static void main(String[] args) {
        //run1BestExperiment(args);
        runNBestOracleExperiment(args, 100);
    }
}
