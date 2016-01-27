package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.util.*;
import java.util.stream.Collectors;


/**
 * New - Active Learning experiments (n-best reranking).
 * Created by luheng on 1/5/16.
 */
public class ActiveLearningReranker {
    List<List<InputWord>> sentences;
    List<Parse> goldParses;
    BaseCcgParser parser;
    QuestionGenerator questionGenerator;
    ResponseSimulator responseSimulator;

    int nBest;
    Map<String, Double> aggregatedResults;

    // Pre-compute expected vote scores for parses using the p(a|q). But this makes the learning curve much worse.
    final boolean usePriorRank = false;
    // Print debugging info or not.
    final boolean verbose = true;
    // Plot learning curve (F1 vs. number of queries).
    final boolean plotCurve = true;
    // Maximum number of queries per sentence.
    final int maxNumQueriesPerSentence = 100;

    public static void main(String[] args) {
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        List<List<InputWord>> sentences = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);

        String modelFolder = commandLineOptions.getModel();
        List<Category> rootCategories = commandLineOptions.getRootCategories();
        QuestionGenerator questionGenerator = new QuestionGenerator();
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(questionGenerator);

        /************** manual parameter tuning ... ***********/
        //final int[] nBestList = new int[] { 3, 5, 10, 20, 50, 100, 250, 500, 1000 };
        final int[] nBestList = new int[] { 50 };


        List<Map<String, Double>> allResults = new ArrayList<>();
        for (int nBest : nBestList) {
            BaseCcgParser parser = new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest);
            ActiveLearningReranker learner = new ActiveLearningReranker(sentences, goldParses, parser,
                                                                        questionGenerator, responseSimulator, nBest);
            learner.run();
            allResults.add(learner.aggregatedResults);
        }

        /*********** output results **********/
        List<String> resultKeys = new ArrayList<>(allResults.get(0).keySet());
        Collections.sort(resultKeys);
        System.out.print("\nnbest");
        for (int i = 0; i < nBestList.length; i++) {
            System.out.print("\t" + nBestList[i]);
        }
        resultKeys.forEach(rk -> {
            System.out.print("\n" + rk);
            for (int i = 0; i < nBestList.length; i++) {
                System.out.print(String.format("\t%.6f",allResults.get(i).get(rk)));
            }
        });
        System.out.println();
    }

    public ActiveLearningReranker(List<List<InputWord>> sentences, List<Parse> goldParses, BaseCcgParser parser,
                                  QuestionGenerator questionGenerator, ResponseSimulator responseSimulator, int nBest) {
        System.out.println(String.format("\n========== ReRanker Active Learning with %d-Best List ==========", nBest));
        this.sentences = sentences;
        this.goldParses = goldParses;
        this.parser = parser;
        this.questionGenerator = questionGenerator;
        this.responseSimulator = responseSimulator;
        this.nBest = nBest;
    }

    public void run() {
        /****************** Base n-best Parser ***************/
        Map<Integer, List<Parse>> allParses = new HashMap<>();
        Map<Integer, List<Results>> allResults = new HashMap<>();
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            // TODO: get parse scores.
            List<Parse> parses = parser.parseNBest(sentences.get(sentIdx));
            if (parses != null) {
                allParses.put(sentIdx, parses);
                allResults.put(sentIdx, CcgEvaluation.evaluate(parses, goldParses.get(sentIdx).dependencies));
                if (allParses.size() % 100 == 0) {
                    System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
                }
            }
        }
        /****************** Generate Queries ******************/
        List<GroupedQuery> allQueries = new ArrayList<>();
        for (int sentIdx : allParses.keySet()) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            allQueries.addAll(QueryGenerator.generateQueries(sentIdx, words, parses, questionGenerator));
        }
        System.out.println("Total number of queries:\t" + allQueries.size());
        // TODO: random order
        List<GroupedQuery> queryList = allQueries.stream()
                //.filter(q -> q.answerMargin < 0.99)
                //.sorted((q1, q2) -> Double.compare(q1.answerMargin, q2.answerMargin))
                .sorted((q1, q2) -> Double.compare(-q1.answerEntropy, -q2.answerEntropy))
                //.unordered()
                .limit(maxNumQueriesPerSentence * sentences.size())
                .collect(Collectors.toList());

        System.out.println("Pruned number of queries:\t" + queryList.size());

        /******************* Response simulator ************/
        List<Integer> responseList = queryList.stream().map(q -> {
            int sentId = q.sentenceId;
            List<String> words = sentences.get(sentId).stream().map(w -> w.word).collect(Collectors.toList());
            return responseSimulator.answerQuestion(q, words, goldParses.get(sentId));
        }).collect(Collectors.toList());

        /***************** Reranking ****************/
        Reranker reranker = usePriorRank ? new Reranker(allParses, allQueries) : new Reranker(allParses, null);

        Map<Integer, Results> budgetCurve = new HashMap<>();
        for (int i = 0; i < queryList.size(); i++) {
            reranker.rerank(queryList.get(i), responseList.get(i));
            if (plotCurve && i % 200 == 0) {
                Results currentResult = new Results();
                allParses.keySet().forEach(sentIdx -> {
                    int bestK = reranker.getRerankedBest(sentIdx);
                    currentResult.add(allResults.get(sentIdx).get(bestK));
                });
                budgetCurve.put(i, currentResult);
            }
        }

        /*************** Evaluation ********************/
        aggregatedResults = new HashMap<>();
        Results oneBest = new Results();
        Results reRanked = new Results();
        Results oracle = new Results();
        Accuracy oneBestAcc = new Accuracy();
        Accuracy reRankedAcc = new Accuracy();
        Accuracy oracleAcc = new Accuracy();
        int avgBestK = 0, avgOracleK = 0;

        int numMultiHeadQueries = 0;
        int numMultiHeadQueriesGold = 0;

        for (int sentIdx : allParses.keySet()) {
            List<Parse> parses = allParses.get(sentIdx);
            List<Results> results = allResults.get(sentIdx);
            Parse goldParse = goldParses.get(sentIdx);
            int bestK = reranker.getRerankedBest(sentIdx);
            int oracleK = 0;
            for (int k = 1; k < parses.size(); k++) {
                if (results.get(k).getF1() > results.get(oracleK).getF1()) {
                    oracleK = k;
                }
            }
            avgBestK += bestK;
            avgOracleK += oracleK;
            oneBest.add(results.get(0));
            reRanked.add(results.get(bestK));
            oracle.add(results.get(oracleK));
            oneBestAcc.add(CcgEvaluation.evaluateTags(parses.get(0).categories, goldParse.categories));
            reRankedAcc.add(CcgEvaluation.evaluateTags(parses.get(bestK).categories, goldParse.categories));
            oracleAcc.add(CcgEvaluation.evaluateTags(parses.get(oracleK).categories, goldParse.categories));
            if (verbose) {
                for (int i = 0; i < queryList.size(); i++) {
                    GroupedQuery query = queryList.get(i);
                    int response = responseList.get(i);
                    if (query.sentenceId != sentIdx || response == -1) {
                        continue;
                    }
                    if (query.answerOptions.get(response).argumentIds.size() > 1) {
                        numMultiHeadQueriesGold ++;
                    }
                    if (query.answerOptions.stream().anyMatch(ao -> ao.argumentIds.size() > 1)) {
                        numMultiHeadQueries ++;
                    }
                    if (query.answerOptions.size() > 0 && query.answerEntropy > 0.5) {
                        List<String> words = sentences.get(sentIdx).stream().map(w -> w.word)
                                .collect(Collectors.toList());
                        DebugPrinter.printQueryInfo(words, query, response);
                    }
                }
            }
        }
        System.out.println(numMultiHeadQueriesGold + "\t" + queryList.size() + "\t" +
                100.0 * numMultiHeadQueriesGold / queryList.size());
        System.out.println(numMultiHeadQueries + "\t" + queryList.size() + "\t" +
                100.0 * numMultiHeadQueries / queryList.size());

        // Effect query: a query whose response boosts the score of a non-top parse but not the top one.
        int numSentencesParsed = allParses.size();
        int numQueries = reranker.numQueries;
        int numEffectiveQueries = reranker.numEffectiveQueries;
        System.out.println("\n1-best:\navg-k = 1.0\n" + oneBestAcc + "\n" + oneBest);
        System.out.println("re-ranked:\navg-k = " + 1.0 * avgBestK / numSentencesParsed + "\n" + reRankedAcc + "\n" +
                reRanked);
        System.out.println("oracle:\navg-k = " + 1.0 * avgOracleK / numSentencesParsed + "\n"+ oracleAcc + "\n" +
                oracle);
        System.out.println("Number of queries = " + numQueries);
        System.out.println("Number of effective queries = " + numEffectiveQueries);
        System.out.println("Effective ratio = " + 1.0 * numEffectiveQueries / numQueries);
        double baselineF1 = oneBest.getF1();
        double rerankF1 = reRanked.getF1();
        double avgGain = (rerankF1 - baselineF1) / numQueries * 100;
        System.out.println("Avg. F1 gain = " + avgGain);

        aggregatedResults.put("1-best-acc", oneBestAcc.getAccuracy() * 100);
        aggregatedResults.put("1-best-f1", oneBest.getF1() * 100);
        aggregatedResults.put("rerank-acc", reRankedAcc.getAccuracy() * 100);
        aggregatedResults.put("rerank-f1", reRanked.getF1() * 100);
        aggregatedResults.put("oracle-acc", oracleAcc.getAccuracy() * 100);
        aggregatedResults.put("oracle-f1", oracle.getF1() * 100);
        aggregatedResults.put("num-queries", (double) numQueries);
        aggregatedResults.put("num-eff-queries", (double) numEffectiveQueries);
        aggregatedResults.put("num-queries", (double) numQueries);
        aggregatedResults.put("eff-ratio", 100.0 * numEffectiveQueries / numQueries);
        aggregatedResults.put("avg-gain", avgGain);

        if (plotCurve) {
            budgetCurve.keySet().stream().sorted().forEach(i -> System.out.print("\t" + i));
            System.out.println();
            budgetCurve.keySet().stream().sorted().forEach(i -> System.out.print("\t" +
                    String.format("%.3f", budgetCurve.get(i).getF1() * 100.0)));
            System.out.println();
        }
    }
}
