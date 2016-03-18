package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * New - Active Learning experiments (n-best reranking).
 * Created by luheng on 1/5/16.
 */
public class MTurkDataWriter {
    final List<List<InputWord>> sentences;
    final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    final ResponseSimulator responseSimulator;

    Map<String, Double> aggregatedResults;

    // Print debugging info or not.
    final boolean verbose = true;
    // Plot learning curve (F1 vs. number of queries).
    final boolean plotCurve = true;
    // Incorporate -NOQ- queries (dependencies we can't generate questions for) in reranking to see potential
    // improvements.
    boolean generatePseudoQuestions = false;
    boolean groupSameLabelDependencies = true;
    // The change inflicted on distribution of parses after each query update.
    double rerankerStepSize = 1.0;
    // The file contains the pre-parsed n-best list (of CCGBank dev). Leave file name is empty, if we wish to parse
    // sentences in the experiment.
    final static String preparsedFile = "parses.50best.out";

    final static int nBest = 50;

    // After a batch of queries, update query entropy and reorder them based on updated probabilities of parses.
    final static int reorderQueriesEvery = 100;
    // Maximum number of answer options per query.
    final static int maxAnswerOptionsPerQuery = 4;
    // Maximum number of queries
    final static int maxNumQueries = 1000;

    private static String[] csvHeader = {"query_id", "sent_id", "sentence", "pred_id", "pred_head",
                                         "question", "answer1", "answer2", "answer3", "answer4"};

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
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(goldParses);

        BaseCcgParser parser = preparsedFile.isEmpty() ?
                        new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest) :
                        new BaseCcgParser.MockParser(preparsedFile, nBest);
        MTurkDataWriter learner = new MTurkDataWriter(sentences, goldParses, parser, questionGenerator, responseSimulator);
        try {
            learner.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MTurkDataWriter(List<List<InputWord>> sentences, List<Parse> goldParses, BaseCcgParser parser,
                           QuestionGenerator questionGenerator, ResponseSimulator responseSimulator) {
        System.out.println(String.format("\n========== ReRanker Active Learning with %d-Best List ==========", nBest));
        this.sentences = sentences;
        this.goldParses = goldParses;
        this.parser = parser;
        this.questionGenerator = questionGenerator;
        this.responseSimulator = responseSimulator;
    }

    public void run() throws IOException {
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
                    generatePseudoQuestions);
            queries.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
            queryList.addAll(queries);
        }
        System.out.println("Total number of queries:\t" + queryList.size());;

        TIntIntHashMap numQueriesPerSentence = new TIntIntHashMap();
        allParses.keySet().forEach(sid -> numQueriesPerSentence.put(sid, 0));
        Map<Integer, Results> budgetCurve = new HashMap<>();

        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter("test.csv")), CSVFormat.EXCEL
                    .withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) csvHeader);

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
            if (queryCounter >= maxNumQueries) {
                break;
            }
            if (queryCounter > 0 && queryCounter % reorderQueriesEvery == 0) {
                Collection<GroupedQuery> queryBuffer = new ArrayList<>(queryList);
                queryBuffer.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
                queryList.clear();
                queryList.addAll(queryBuffer);
            }

            queryCounter ++;
            GroupedQuery query = queryList.poll();
            int sentId = query.sentenceId;
            List<String> words = sentences.get(sentId).stream().map(w -> w.word).collect(Collectors.toList());
            Response response = responseSimulator.answerQuestion(query);

            double entropy = reranker.computeParsesEntropy(sentId);
            reranker.rerank(query, response);
            numQueriesPerSentence.adjustValue(sentId, 1);
            int bestK = reranker.getRerankedBest(sentId);
            int oracleK = oracleParseIds.get(sentId);
            System.out.println(numQueriesPerSentence.get(sentId) + "\t" + sentId + "\t" +
                    numQueriesPerSentence.get(sentId) + "\t" + bestK + "\t" + oracleK + "\t" + entropy + "\t" +
                    reranker.computeParsesEntropy(sentId));

            // "query_id", "sent_id", "sentence", "pred_id", "pred_head","question",
            // "answer1", "answer2", "answer3", "answer4";
            List<String> csvRow = new ArrayList<>();
            csvRow.add(String.valueOf(queryCounter));
            csvRow.add(String.valueOf(sentId));
            // get highlighted sentence
            String sentenceStr = IntStream.range(0, words.size())
                    .mapToObj(i -> i == query.predicateIndex ? "<mark>" + words.get(i) + "</mark>" : words.get(i))
                    .collect(Collectors.joining(" "));
            csvRow.add(String.valueOf(sentenceStr));
            csvRow.add(String.valueOf(query.predicateIndex));
            csvRow.add(words.get(query.predicateIndex));
            csvRow.add(query.question);
            List<GroupedQuery.AnswerOption> options = query.getTopAnswerOptions(maxAnswerOptionsPerQuery);
            for (int i = 0; i < maxAnswerOptionsPerQuery; i++) {
                if (i < options.size()) {
                    csvRow.add(options.get(i).answer);
                } else {
                    csvRow.add("");
                }
            }
            csvPrinter.printRecord(csvRow);
        }

        csvPrinter.close();

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

        for (int sentIdx : allParses.keySet()) {
            List<Parse> parses = allParses.get(sentIdx);
            List<Results> results = allResults.get(sentIdx);
            Parse goldParse = goldParses.get(sentIdx);
            int bestK = reranker.getRerankedBest(sentIdx);
            int oracleK = oracleParseIds.get(sentIdx);

            boolean bestIsOracle = (bestK == oracleK);
            boolean oracleIsTop = (oracleK == 0);

            analysis.adjustOrPutValue(String.format("BestIsOracle=%b, OracleIsTop=%b", bestIsOracle, oracleIsTop), 1, 1);

            avgBestK += bestK;
            avgOracleK += oracleK;
            oneBest.add(results.get(0));
            reRanked.add(results.get(bestK));
            oracle.add(results.get(oracleK));
            oneBestAcc.add(CcgEvaluation.evaluateTags(parses.get(0).categories, goldParse.categories));
            reRankedAcc.add(CcgEvaluation.evaluateTags(parses.get(bestK).categories, goldParse.categories));
            oracleAcc.add(CcgEvaluation.evaluateTags(parses.get(oracleK).categories, goldParse.categories));
        }

        for (String s : analysis.keySet()) {
            System.out.println(s + "\t" + analysis.get(s));
        }
        System.out.println(numMultiHeadQueriesGold + "\t" + queryList.size() + "\t" +
                100.0 * numMultiHeadQueriesGold / queryList.size());
        System.out.println(numMultiHeadQueries + "\t" + queryList.size() + "\t" +
                100.0 * numMultiHeadQueries / queryList.size());
        System.out.println("Number of truly effective queries:\t" + numTrulyEffectiveQueries);

        int numQueries = queryList.size(),
            numEffectiveQueries = 0,
            numSentencesParsed = allParses.size();
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
