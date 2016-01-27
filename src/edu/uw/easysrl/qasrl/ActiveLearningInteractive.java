package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Interactive active Learning experiments (n-best reranking).
 */
public class ActiveLearningInteractive {
    List<List<InputWord>> sentences;
    List<Parse> goldParses;
    BaseCcgParser parser;
    QuestionGenerator questionGenerator;
    ResponseSimulator responseSimulator;
    int nBest;
    Map<String, Double> aggregatedResults;

    double minAnswerEntropy = 0.0;
    double maxAnswerMargin = 0.9;
    boolean shuffleSentences = false;
    int maxNumSentences = -1;
    int maxNumQueries = 50;
    int randomSeed = 0;

    public static void main(String[] args) {
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            return;
        }
        List<List<InputWord>  > sentences = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);

        String modelFolder = commandLineOptions.getModel();
        List<Category> rootCategories = commandLineOptions.getRootCategories();
        QuestionGenerator questionGenerator = new QuestionGenerator();
        ResponseSimulator responseSimulator = new ResponseSimulatorMultipleChoice();

        /************** manual parameter tuning ... ***********/
        //final int[] nBestList = new int[] { 3, 5, 10, 20, 50, 100, 250, 500, 1000 };
        final int[] nBestList = new int[] { 50 };
        boolean verbose = true;

        List<Map<String, Double>> allResults = new ArrayList<>();
        for (int nBest : nBestList) {
            BaseCcgParser parser = new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest);

            ActiveLearningInteractive learner = new ActiveLearningInteractive(sentences, goldParses, parser,
                    questionGenerator, responseSimulator, nBest);
            learner.run(verbose);
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
                System.out.print(String.format("\t%.3f",allResults.get(i).get(rk)));
            }
        });
        System.out.println();
    }

    public ActiveLearningInteractive(List<List<InputWord>> sentences, List<Parse> goldParses, BaseCcgParser parser,
                                     QuestionGenerator questionGenerator, ResponseSimulator responseSimulator,
                                     int nBest) {
        System.out.println(String.format("\n========== ReRanker Active Learning with %d-Best List ==========", nBest));
        this.sentences = sentences;
        this.goldParses = goldParses;
        this.parser = parser;
        this.questionGenerator = questionGenerator;
        this.responseSimulator = responseSimulator;
        this.nBest = nBest;
    }


    public void run(boolean verbose) {
        aggregatedResults = new HashMap<>();

        Results oneBest = new Results();
        Results reRanked = new Results();
        Results oracle = new Results();
        Accuracy oneBestAcc = new Accuracy();
        Accuracy reRankedAcc = new Accuracy();
        Accuracy oracleAcc = new Accuracy();

        int numSentencesParsed = 0;
        int avgBestK = 0, avgOracleK = 0;

        // For debugging.
        ResponseSimulatorGold goldHuman = new ResponseSimulatorGold(questionGenerator);

        List<Integer> sentenceOrder = IntStream.range(0, sentences.size()).boxed().collect(Collectors.toList());
        if (shuffleSentences) {
            Collections.shuffle(sentenceOrder, new Random(randomSeed));
        }

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
        List<GroupedQuery> queryList = allQueries.stream()
                .sorted((q1, q2) -> Double.compare(-q1.answerEntropy, -q2.answerEntropy))
                .collect(Collectors.toList());

        /******************* Response simulator ************/
        Reranker reranker = new Reranker(allParses, allQueries);
        List<GroupedQuery> asked = new ArrayList<>();
        List<Integer> responses = new ArrayList<>();
        for (int i = 0; i < queryList.size(); i++) {
            GroupedQuery query = queryList.get(i);
            int sentIdx = query.sentenceId;
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            int response = responseSimulator.answerQuestion(query, words, goldParses.get(sentIdx));
            asked.add(query);
            responses.add(response);
            reranker.rerank(query, response);

            /*************** Print Debugging Info *************/
            if (verbose) {
                System.out.print("\n===============");
                int goldResponse = goldHuman.answerQuestion(query, words, goldParses.get(sentIdx));
                DebugPrinter.printQueryInfo(words, query, response, goldResponse);
                // print gold
                /*
                Set<Integer> predicates = queryList.stream().map(q -> q.predicateIndex).collect(Collectors.toSet());
                System.out.println("[gold]");
                goldParse.dependencies.forEach(d -> {
                    //if (predicates.contains(d.getHead())) {
                    System.out.println(d.getCategory() + "." + d.getArgument() + "\t" + d.toString(words));
                    //}
                });
                */
                System.out.println("===============\n");
            }

            if (asked.size() >= maxNumQueries) {
                break;
            }
        }

        /**************** Rerank ***********************/
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
        }

        System.out.println("\n1-best:\navg-k = 1.0\n" + oneBestAcc + "\n" + oneBest);
        System.out.println("re-ranked:\navg-k = " + 1.0 * avgBestK / numSentencesParsed + "\n" + reRankedAcc + "\n" + reRanked);
        System.out.println("oracle:\navg-k = " + 1.0 * avgOracleK / numSentencesParsed + "\n"+ oracleAcc + "\n" + oracle);
        System.out.println("Number of queries = " + reranker.numQueries);
        System.out.println("Number of effective queries = " + reranker.numEffectiveQueries);
        System.out.println("Effective ratio = " + 1.0 * reranker.numEffectiveQueries / reranker.numQueries);
        double baselineF1 = oneBest.getF1();
        double rerankF1 = reRanked.getF1();
        double avgGain = (rerankF1 - baselineF1) / reranker.numQueries;
        System.out.println("Avg. F1 gain = " + avgGain);

        aggregatedResults.put("1-best-acc", oneBestAcc.getAccuracy() * 100);
        aggregatedResults.put("1-best-f1", oneBest.getF1() * 100);
        aggregatedResults.put("rerank-acc", reRankedAcc.getAccuracy() * 100);
        aggregatedResults.put("rerank-f1", reRanked.getF1() * 100);
        aggregatedResults.put("oracle-acc", oracleAcc.getAccuracy() * 100);
        aggregatedResults.put("oracle-f1", oracle.getF1() * 100);
        aggregatedResults.put("num-queries", (double) reranker.numQueries);
        aggregatedResults.put("num-eff-queries", (double) reranker.numEffectiveQueries);
        aggregatedResults.put("num-queries", (double) reranker.numQueries);
        aggregatedResults.put("eff-ratio", 100.0 * reranker.numEffectiveQueries / reranker.numQueries);
        aggregatedResults.put("avg-gain", avgGain);
    }
}
