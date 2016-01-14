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
 * Model loading time: 78 seconds.
 *
 * 1-best results (on luheng's mac):
 *      Tagging accuracy = 0.9447142825167312
 *       Precision = 87.42
 *       Recall    = 86.62
 *       F1        = 87.02
 *
 *   Averaged parsing time (in sec):	0.0
 *   Averaged evaluation time (in sec):	0.0
 *
 * Created by luheng on 1/13/16.
 */

/*
10-best:
 0.9534445217625536
 Precision = 90.62
 Recall    = 89.86
 F1        = 90.24
 Avg. n-best:	9.823529411764707	 Avg. best-k:	1.41859243697479
 Averaged parsing time (in sec):	0.0
 Averaged evaluation time (in sec):	0.0
*/

/*
20-best
0.9574190354016159
Precision = 91.65
Recall    = 90.86
F1        = 91.26
Avg. n-best:	19.41062039957939	 Avg. best-k:	2.854889589905363
Averaged parsing time (in sec):	0.0
Averaged evaluation time (in sec):	0.0
*/

public class TriTrainCCGBenchmark {
    static List<List<InputReader.InputWord>> sentences;
    static List<Parse> goldParses;
    static BaseCcgParser parser;

    private static void initialize(String[] args, int nBest) {
        EasySRL.CommandLineArguments commandLineOptions;
        TicToc.tic();
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
        System.out.println("Model loading time:\t" + TicToc.toc() + " seconds.");
    }

    private static void run(boolean verbose) {
        // TODO: evaluate diversity of n-best parses
        // TODO: tagging-parsing accuracy correlation
        // TODO: can we automatically detect sentences with low tagging accuracy?
        Results parsingAcc = new Results();
        Accuracy taggingAcc = new Accuracy();
        int numParsed = 0;
        long avgParsingTime = 0, avgEvalTime = 0;
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            TicToc.tic();

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
            numParsed ++;
            avgParsingTime += TicToc.toc();
            TicToc.tic();
            Set<ResolvedDependency> dependencies = predict.dependencies;
            Results sentenceF1 = CcgEvaluation.evaluate(dependencies, gold.dependencies);
            Accuracy sentenceAcc = CcgEvaluation.evaluateTags(predict.categories, gold.categories);
            parsingAcc.add(sentenceF1);
            taggingAcc.add(sentenceAcc);
            avgEvalTime += TicToc.toc();
            // Verbose output.
            if (verbose) {
                System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words));
                System.out.println(words.size() + "\t" + dependencies.size());
                for (ResolvedDependency dep : dependencies) {
                    System.out.println(
                            String.format("%d:%s\t%s.%d\t%d:%s", dep.getHead(), words.get(dep.getHead()),
                                    dep.getCategory(), dep.getArgNumber(),
                                    dep.getArgument(), words.get(dep.getArgument())));
                }
                System.out.println(sentenceAcc + "\n" + sentenceF1);
                System.out.println("Averaged parsing time (in sec):\t" + 1.0 * avgParsingTime / numParsed);
                System.out.println("Averaged evaluation time (in sec):\t" + 1.0 * avgEvalTime / numParsed);
            }

        }
        System.out.println("\n" + taggingAcc + "\n" + parsingAcc);
        System.out.println("Averaged parsing time (in sec):\t" + 1.0 * avgParsingTime / numParsed);
        System.out.println("Averaged evaluation time (in sec):\t" + 1.0 * avgEvalTime / numParsed);
    }

    public static void runNBestOracle(boolean verbose) {
        Results parsingAcc = new Results();
        Accuracy taggingAcc = new Accuracy();
        int numParsed = 0, avgNBest = 0, avgBestK = 0;
        long avgParsingTime = 0, avgEvalTime = 0;
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            TicToc.tic();
            List<InputReader.InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());
            Parse gold = goldParses.get(sentIdx);
            List<Parse> parses;
            try {
                parses = parser.parseNBest(sentence);
                if (parses == null) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            avgParsingTime += TicToc.toc();
            TicToc.tic();
            List<Results> results = CcgEvaluation.evaluate(parses, gold.dependencies);
            Results bestF1 = new Results();
            int bestK = 0;
            for (int k = 0; k < results.size(); k++) {
                if (bestF1.isEmpty() || results.get(k).getF1() > bestF1.getF1()) {
                    bestF1 = results.get(k);
                    bestK = k;
                }
            }
            avgBestK += bestK;
            numParsed ++;
            avgNBest += parses.size();
            parsingAcc.add(bestF1);
            Accuracy bestAcc = CcgEvaluation.evaluateTags(parses.get(bestK).categories, gold.categories);
            taggingAcc.add(bestAcc);
            avgEvalTime += TicToc.toc();
            // Verbose output best-K info.
            if (verbose) {
                Set<ResolvedDependency> dependencies = parses.get(bestK).dependencies;
                System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words));
                System.out.println(words.size() + "\t" + dependencies.size());
                for (ResolvedDependency dep : dependencies) {
                    System.out.println(
                            String.format("%d:%s\t%s.%d\t%d:%s", dep.getHead(), words.get(dep.getHead()),
                                    dep.getCategory(), dep.getArgNumber(),
                                    dep.getArgument(), words.get(dep.getArgument())));
                }
                System.out.println(bestK + "\n" + bestF1 + "\n" + bestAcc);
                System.out.println("Averaged parsing time (in sec):\t" + 1.0 * avgParsingTime / numParsed);
                System.out.println("Averaged evaluation time (in sec):\t" + 1.0 * avgEvalTime / numParsed);
            }
        }
        System.out.println("\n" + taggingAcc + "\n" + parsingAcc);
        System.out.println("Avg. n-best:\t" + 1.0 * avgNBest / numParsed +
                           "\t Avg. best-k:\t" + 1.0 * avgBestK / numParsed);
        System.out.println("Averaged parsing time (in sec):\t" + 1.0 * avgParsingTime / numParsed);
        System.out.println("Averaged evaluation time (in sec):\t" + 1.0 * avgEvalTime / numParsed);
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
        runNBestOracleExperiment(args, 50);
    }
}
