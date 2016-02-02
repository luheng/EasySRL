package edu.uw.easysrl.qasrl;

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
     Acc = 0.9486358854994292
     Precision = 88.15
     Recall    = 87.09
     F1        = 87.61
     Averaged parsing time (in sec):	0.0
     Averaged evaluation time (in sec):	0.0
 *
 * Created by luheng on 1/13/16.
 */

/*
10-best (chart size = 10000):
Acc = 0.9582149271375634
Precision = 91.60
Recall    = 90.73
F1        = 91.16
Avg. n-best:	9.779303062302006
Avg. best-k:	1.4714889123548047
Averaged parsing time (in sec):	0.0026399155227032735
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

/*
50-best
0.9627737560707413
Precision = 92.86
Recall    = 92.20
F1        = 92.53
Avg. n-best:	47.50795334040297	 Avg. best-k:	6.695121951219512
Averaged parsing time (in sec):	0.002651113467656416
Averaged evaluation time (in sec):	0.0
*/

/*
100-best
0.9662051270197175
Precision = 93.68
Recall    = 93.02
F1        = 93.35
Avg. n-best:	93.07725093233884	 Avg. best-k:	12.103889184869473
Averaged parsing time (in sec):	0.005327650506126798
Averaged evaluation time (in sec):	0.0

Increased chart size:
0.9649484074579762
Precision = 93.38
Recall    = 92.71
F1        = 93.04
Avg. n-best:	93.53357817418677	 Avg. best-k:	12.396117523609654
Averaged parsing time (in sec):	0.022035676810073453
Averaged evaluation time (in sec):	0.0

After merge:
Acc = 0.9611836050874254
Precision = 92.73
Recall    = 91.77
F1        = 92.25
Avg. n-best:	92.03627504060638
Avg. best-k:	13.10774228478614
Averaged parsing time (in sec):	0.17217108825121819
Averaged evaluation time (in sec):	0.0
*/

/*
250-best
0.9709006707884749
Precision = 94.74
Recall    = 94.10
F1        = 94.42
Avg. n-best:	220.6863488624052	 Avg. best-k:	24.675514626218852
Averaged parsing time (in sec):	0.004875406283856988
Averaged evaluation time (in sec):	0.0
*/

/*
500-best
0.9728454778381743
Precision = 95.11
Recall    = 94.48
F1        = 94.79
Avg. n-best:	389.30982094411286	 Avg. best-k:	39.003255561584375
Averaged parsing time (in sec):	0.005968529571351058
Averaged evaluation time (in sec):	0.0

1000-best (chartsize=20000)
0.9737314719475109
Precision = 95.33
Recall    = 94.67
F1        = 95.00
Avg. n-best:	552.0689093868692	 Avg. best-k:	54.53879544221378
Averaged parsing time (in sec):	0.010851871947911014
Averaged evaluation time (in sec):	0.0

1000-best (chart size=100000)
0.9756712178136168
Precision = 95.65
Recall    = 94.97
F1        = 95.31
Avg. n-best:	867.3950159066808
Avg. best-k:	95.09066808059384
Averaged parsing time (in sec):	0.5397667020148462
Averaged evaluation time (in sec):	0.0


2500-best
0.9738990924546826
Precision = 95.37
Recall    = 94.70
F1        = 95.03
Avg. n-best:	610.953336950624	 Avg. best-k:	57.16115029842648
Averaged parsing time (in sec):	0.011937059142702116
Averaged evaluation time (in sec):	0.0

5000-best
0.9739230382414215
Precision = 95.37
Recall    = 94.70
F1        = 95.03
Avg. n-best:	614.7015735214325	 Avg. best-k:	58.33532284319045
Averaged parsing time (in sec):	0.009766684753119913
Averaged evaluation time (in sec):	0.0

5000-best (chart-size=200000)
0.979585326953748
Precision = 96.50
Recall    = 95.79
F1        = 96.14
Avg. n-best:	3796.759915388683
Avg. best-k:	315.4717080909572
Averaged parsing time (in sec):	2.8529878371232154
Averaged evaluation time (in sec):	5.288207297726071E-4
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
            System.out.println("Exception occurred while parsing command line arguments.");
            return;
        }
        // Initialize corpora.
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        parser = new BaseCcgParser.AStarParser(commandLineOptions.getModel(), commandLineOptions.getRootCategories(),
                nBest);
        //parser = new BaseCcgParser.PipelineCCGParser(commandLineOptions.getModel(), nBest);
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
            Parse predict = parser.parse(sentences.get(sentIdx));
            if (predict == null) {
                continue;
            }
            numParsed ++;
            avgParsingTime += TicToc.toc();
            TicToc.tic();
            Results sentenceF1 = CcgEvaluation.evaluate(predict.dependencies, gold.dependencies);
            Accuracy sentenceAcc = CcgEvaluation.evaluateTags(predict.categories, gold.categories);
            parsingAcc.add(sentenceF1);
            taggingAcc.add(sentenceAcc);
            avgEvalTime += TicToc.toc();
            // Verbose output.
            if (verbose) {
                System.out.println(String.format("\n[S%d]:\t", sentIdx) + StringUtils.join(words));
                System.out.println(String.format("Words: %d\tPredicted deps: %d\t Gold deps: %d",
                                                 words.size(), predict.dependencies.size(), gold.dependencies.size()));
                Set<ResolvedDependency> precisionMistakes = CcgEvaluation.difference(predict.dependencies, gold.dependencies);
                Set<ResolvedDependency> recallMistakes = CcgEvaluation.difference(gold.dependencies, predict.dependencies);
                System.out.println("False positive dependencies:");
                for (ResolvedDependency dep : precisionMistakes) {
                    System.out.println(String.format("\t%d:%s\t%s.%d\t%d:%s", dep.getHead(), words.get(dep.getHead()),
                                                     dep.getCategory(), dep.getArgNumber(),
                                                     dep.getArgument(), words.get(dep.getArgument())));
                }
                System.out.println("False negative (missed) dependencies:");
                for (ResolvedDependency dep : recallMistakes) {
                    System.out.println(String.format("\t%d:%s\t%s.%d\t%d:%s", dep.getHead(), words.get(dep.getHead()),
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
            List<Parse> parses = parser.parseNBest(sentence);
            if (parses == null) {
                continue;
            }
            avgParsingTime += TicToc.toc();
            TicToc.tic();
            List<Results> results = CcgEvaluation.evaluateNBest(parses, gold.dependencies);
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
        System.out.println("Avg. n-best:\t" + 1.0 * avgNBest / numParsed);
        System.out.println("Avg. best-k:\t" + 1.0 * avgBestK / numParsed);
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
        run1BestExperiment(args);
        // runNBestOracleExperiment(args, 100);
    }
}
