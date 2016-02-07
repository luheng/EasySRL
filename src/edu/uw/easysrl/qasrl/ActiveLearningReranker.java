package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * New - Active Learning experiments (n-best reranking).
 * Created by luheng on 1/5/16.
 */
public class ActiveLearningReranker {
    final List<List<InputWord>> sentences;
    final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    final ResponseSimulator responseSimulator;

    final int nBest;
    Map<String, Double> aggregatedResults;

    // Print debugging info or not.
    final boolean verbose = true;
    // Plot learning curve (F1 vs. number of queries).
    final boolean plotCurve = true;
    // Maximum number of queries per sentence.
    final int maxNumQueriesPerSentence = 100;
    // Incorporate -NOQ- queries (dependencies we can't generate questions for) in reranking to see potential
    // improvements.
    boolean generatePseudoQuestions = false;
    boolean groupSameLabelDependencies = true;
    // The change inflicted on distribution of parses after each query update.
    double rerankerStepSize = 1.0;
    // The file contains the pre-parsed n-best list (of CCGBank dev). Leave file name is empty, if we wish to parse
    // sentences in the experiment.
    final static Optional<String> preparsedFile(int n) {
        return Optional.of("parses." + n + "best.out");
        // return Optional.empty();
    }
    // After a batch of queries, update query entropy and reorder them based on updated probabilities of parses.
    final static int reorderQueriesEvery = 100;

    // TODO: if a parse already has low probability, it should provide very little weight when computing answer entropy.
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
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(goldParses, questionGenerator);

        /************** manual parameter tuning ... ***********/
        //final int[] nBestList = new int[] { 3, 5, 10, 20, 50, 100, 250, 500, 1000 };
        final int[] nBestList = new int[] { 10 };

        List<Map<String, Double>> allResults = new ArrayList<>();
        for (int nBest : nBestList) {
            Optional<String> filenameOpt = preparsedFile(nBest);
            BaseCcgParser parser =
                filenameOpt.isPresent() ?
                new BaseCcgParser.MockParser(filenameOpt.get(), nBest) :
                new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest);
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
        Map<Integer, Integer> oracleParseIds = new HashMap<>();

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            List<Parse> parses = parser.parseNBest(sentIdx, sentences.get(sentIdx));
            if (parses == null) {
                continue;
            }
            // Get results for every parse in the n-best list.
            List<Results> results = CcgEvaluation.evaluateNBest(parses, goldParses.get(sentIdx).dependencies);
            // Get oracle parse id.
            int oracleK = 0;
            for (int k = 1; k < parses.size(); k++) {
                if (results.get(k).getF1() > results.get(oracleK).getF1()) {
                    oracleK = k;
                }
            }
            allParses.put(sentIdx, parses);
            allResults.put(sentIdx, results);
            oracleParseIds.put(sentIdx, oracleK);
            if (allParses.size() % 100 == 0) {
                System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
            }
        }

        /***************** Reranking ****************/
        RerankerExponentiated reranker = new RerankerExponentiated(allParses, rerankerStepSize);

        /****************** Generate Queries ******************/
        Comparator<GroupedQuery> queryComparator = new Comparator<GroupedQuery>() {
            public int compare(GroupedQuery q1, GroupedQuery q2) {
                return Double.compare(-q1.answerEntropy, -q2.answerEntropy);
            }
        };
        PriorityQueue<GroupedQuery> queryList = new PriorityQueue<>(queryComparator);
        for (int sentIdx : allParses.keySet()) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            List<GroupedQuery> queries = QueryGenerator.generateQueries(sentIdx, words, parses, questionGenerator,
                    generatePseudoQuestions, groupSameLabelDependencies);
            queries.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
            queryList.addAll(queries);
        }
        System.out.println("Total number of queries:\t" + queryList.size());
        int numQueries = queryList.size(),
            numEffectiveQueries = 0,
            numSentencesParsed = allParses.size();

        TIntIntHashMap numQueriesPerSentence = new TIntIntHashMap();
        allParses.keySet().forEach(sid -> numQueriesPerSentence.put(sid, 0));
        Map<Integer, Results> budgetCurve = new HashMap<>();

        int queryCounter = 0;
        while (!queryList.isEmpty()) {
            if (plotCurve && queryCounter % 200 == 0) {
                Results currentResult = new Results();
                allParses.keySet().forEach(sid ->
                        currentResult.add(allResults.get(sid).get(reranker.getRerankedBest(sid))));
                budgetCurve.put(queryCounter, currentResult);

                // Refresh queue
                System.out.println(queryList.size());
            }
            if (queryCounter > 0 && queryCounter % reorderQueriesEvery == 0) {
                Collection<GroupedQuery> queryBuffer = new ArrayList<>(queryList);
                queryBuffer.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
                queryList.clear();
                queryList.addAll(queryBuffer);
            }

            queryCounter ++;
            GroupedQuery query = queryList.poll();
            Response response = responseSimulator.answerQuestion(query);
            System.out.println(TextGenerationHelper.renderString(query.sentence));
            query.print(query.sentence, response);

            int sentId = query.sentenceId;
            double entropy = reranker.computeParsesEntropy(sentId);
            reranker.rerank(query, response);
            numQueriesPerSentence.adjustValue(sentId, 1);
            int bestK = reranker.getRerankedBest(sentId);
            int oracleK = oracleParseIds.get(sentId);
            System.out.println(numQueriesPerSentence.get(sentId) + "\t" + sentId + "\t" +
                    numQueriesPerSentence.get(sentId) + "\t" + bestK + "\t" + oracleK + "\t" + entropy + "\t" +
                    reranker.computeParsesEntropy(sentId));
            /*
            if (oracleK == 0 && bestK != oracleK) {
                System.out.println("\n" + words.stream().collect(Collectors.joining(" ")));
                System.out.println(numQueriesPerSentence.get(sentId) + "\t" + sentId + "\t" +
                        numQueriesPerSentence.get(sentId) + "\t" + bestK + "\t" + oracleK + "\t" + entropy + "\t" +
                        reranker.computeParsesEntropy(sentId));
                query.print(words, response);
                System.out.println("[k-best]:\t" + allResults.get(sentId).get(bestK));
                System.out.println("[oracle]:\t" + allResults.get(sentId).get(oracleK) + "\n");
                System.out.println("--on qg covered deps--");
                System.out.println("[k-best]:\t" + AnalysisHelper.evaluateQuestionCoveredDependencies(
                        allParses.get(sentId).get(bestK), goldParses.get(sentId), words, questionGenerator));
                System.out.println("[oracle]:\t" + AnalysisHelper.evaluateQuestionCoveredDependencies(
                        allParses.get(sentId).get(oracleK), goldParses.get(sentId), words, questionGenerator));
            }
            */
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
        int numTrulyEffectiveQueries = 0;

        TObjectIntHashMap<String> analysis = new TObjectIntHashMap<>();
        CountDictionary missedCategories = new CountDictionary();

        for (int sentIdx : allParses.keySet()) {
            List<Parse> parses = allParses.get(sentIdx);
            List<Results> results = allResults.get(sentIdx);
            Parse goldParse = goldParses.get(sentIdx);
            int bestK = reranker.getRerankedBest(sentIdx);
            int oracleK = oracleParseIds.get(sentIdx);
            analysis.adjustOrPutValue(String.format("BestIsOracle=%b, OracleIsTop=%b", (bestK == oracleK),
                    (oracleK == 0)), 1, 1);

            avgBestK += bestK;
            avgOracleK += oracleK;
            oneBest.add(results.get(0));
            reRanked.add(results.get(bestK));
            oracle.add(results.get(oracleK));
            oneBestAcc.add(CcgEvaluation.evaluateTags(parses.get(0).categories, goldParse.categories));
            reRankedAcc.add(CcgEvaluation.evaluateTags(parses.get(bestK).categories, goldParse.categories));
            oracleAcc.add(CcgEvaluation.evaluateTags(parses.get(oracleK).categories, goldParse.categories));
            if (verbose) {
                Parse oracleParse = parses.get(oracleK);
                Set<ResolvedDependency> oraclePrecisionMistakes =
                    CcgEvaluation.difference(oracleParse.dependencies, goldParse.dependencies);
                Set<ResolvedDependency> oracleRecallMistakes =
                    CcgEvaluation.difference(goldParse.dependencies, oracleParse.dependencies);
                // only print the extra info for ones where we could do better
                // also, only print the mistakes that were corrected by the oracle
                List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
                if(oracleK != bestK) {
                    // what if the reranker gave us something worse?
                    if(results.get(0).getF1() > results.get(bestK).getF1()) {
                        System.out.println("====== Reranker produced worse result! ======");
                    }
                    Map<String, Parse> parsesForStats = new TreeMap<String, Parse>();
                    // print false positives and negatives for:
                    parsesForStats.put("==== original best ====", parses.get(0));
                    parsesForStats.put("==== reranked best ====", parses.get(bestK));
                    parsesForStats.forEach((label, parse) -> {
                            Set<ResolvedDependency> precisionMistakes =
                                CcgEvaluation.difference(parse.dependencies, goldParse.dependencies);
                            precisionMistakes.removeAll(oraclePrecisionMistakes);
                            Set<ResolvedDependency> recallMistakes =
                                CcgEvaluation.difference(goldParse.dependencies, parse.dependencies);
                            recallMistakes.removeAll(oracleRecallMistakes);
                            System.out.println(label);
                            System.out.println("False positive dependencies:");
                            for (ResolvedDependency dep : precisionMistakes) {
                                System.out.println(String.format("\t%d:%s\t%s.%d\t%d:%s", dep.getHead(), words.get(dep.getHead()),
                                                                 dep.getCategory(), dep.getArgNumber(),
                                                                 dep.getArgument(), words.get(dep.getArgument())));
                                if(label.contains("reranked")) {
                                    missedCategories.addString(dep.getCategory().toString());
                                }
                            }
                            System.out.println("False negative (missed) dependencies:");
                            for (ResolvedDependency dep : recallMistakes) {
                                System.out.println(String.format("\t%d:%s\t%s.%d\t%d:%s", dep.getHead(), words.get(dep.getHead()),
                                                                 dep.getCategory(), dep.getArgNumber(),
                                                                 dep.getArgument(), words.get(dep.getArgument())));
                                if(label.contains("reranked")) {
                                    missedCategories.addString(dep.getCategory().toString());
                                }
                            }
                        });
                }
            }
        }
        if(verbose) {
            System.out.println("Categories of the heads of mistakes:");
            List<String> missedCats = missedCategories.getStrings()
                .stream()
                .sorted((c1, c2) -> Integer.compare(missedCategories.getCount(c1), missedCategories.getCount(c2)))
                .collect(Collectors.toList());
            for (String cat : missedCats) {
                int catCount = missedCategories.getCount(cat);
                System.out.println(cat + ": " + catCount);
            }
        }


        for (String s : analysis.keySet()) {
            System.out.println(s + "\t" + analysis.get(s));
        }
        System.out.println(numMultiHeadQueriesGold + "\t" + queryList.size() + "\t" +
                100.0 * numMultiHeadQueriesGold / queryList.size());
        System.out.println(numMultiHeadQueries + "\t" + queryList.size() + "\t" +
                100.0 * numMultiHeadQueries / queryList.size());
        System.out.println("Number of truly effective queries:\t" + numTrulyEffectiveQueries);

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
