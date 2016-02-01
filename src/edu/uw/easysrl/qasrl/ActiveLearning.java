package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Separating ResponseSimulator and
 * Created by luheng on 2/1/16.
 */
public class ActiveLearning {

    final List<List<InputReader.InputWord>> sentences;
    final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    // Reranker needs to be initialized after we parse all the sentences .. maybe not?
    RerankerExponentiated reranker;

    private PriorityQueue<GroupedQuery> queryList;
    private Map<Integer, List<Parse>> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    private final Comparator<GroupedQuery> queryComparator = new Comparator<GroupedQuery>() {
        public int compare(GroupedQuery q1, GroupedQuery q2) {
            return Double.compare(-q1.answerEntropy, -q2.answerEntropy);
        }
    };

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
    String preparsedFile = "parses.50best.out";
    int nBest = 50;

    // After a batch of queries, update query entropy and reorder them based on updated probabilities of parses.
    final static int reorderQueriesEvery = -1   ;

    final static String modelFolder = "/Users/luheng/Workspace/EasySRL/model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));



    public static void main(String[] args) {
        ActiveLearning learner = new ActiveLearning();
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator());
        Map<Integer, Results> budgetCurve = new HashMap<>();

        int queryCounter = 0;
        while (learner.getNumberOfRemainingQueries() > 0) {
            GroupedQuery query = learner.getNextQuery();
            Response response = responseSimulator.answerQuestion(query);
            learner.responseToQuery(query, response);
            if (queryCounter % 200 == 0) {
                budgetCurve.put(queryCounter, learner.getRerankedF1());
            }
            if (queryCounter > 0 && reorderQueriesEvery > 0 && queryCounter % reorderQueriesEvery == 0) {
                learner.refreshQueryList();
            }
            queryCounter ++;
        }

        System.out.println("[1-best]:\t" + learner.getOneBestF1());
        System.out.println("[reranked]:\t" + learner.getRerankedF1());
        System.out.println("[oracle]:\t" + learner.getOracleF1());

        budgetCurve.keySet().stream().sorted().forEach(i -> System.out.print("\t" + i));
        System.out.println();
        budgetCurve.keySet().stream().sorted().forEach(i -> System.out.print("\t" +
                String.format("%.3f", budgetCurve.get(i).getF1() * 100.0)));
        System.out.println();
    }

    public ActiveLearning() {
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        questionGenerator = new QuestionGenerator();
        parser = preparsedFile.isEmpty() ?
                new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest) :
                new BaseCcgParser.MockParser(preparsedFile, nBest);
        initialize();
    }

    public ActiveLearning(List<List<InputReader.InputWord>> sentences, List<Parse> goldParses,
                          BaseCcgParser parser, QuestionGenerator questionGenerator, int nBest) {
        this.sentences = sentences;
        this.goldParses = goldParses;
        DataLoader.readDevPool(sentences, goldParses);
        this.questionGenerator = questionGenerator;
        this.parser = parser;
        this.nBest = nBest;
        initialize();
    }

    private void initialize() {
        /****************** Base n-best Parser ***************/
        allParses = new HashMap<>();
        allResults = new HashMap<>();
        oracleParseIds = new HashMap<>();

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

        /****************** Initialize Query List ******************/
        reranker = new RerankerExponentiated(allParses, rerankerStepSize);
        queryList = new PriorityQueue<>(queryComparator);
        for (int sentIdx : allParses.keySet()) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            List<GroupedQuery> queries = QueryGenerator.generateQueries(sentIdx, words, parses, questionGenerator,
                    generatePseudoQuestions, groupSameLabelDependencies);
            queries.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
            queryList.addAll(queries);
        }
        System.out.println("Total number of queries:\t" + queryList.size());;
    }

    public List<String> getSentence(int sentenceId) {
        return sentences.get(sentenceId).stream().map(w -> w.word).collect(Collectors.toList());
    }

    public GroupedQuery getNextQuery() {
        return queryList.poll();
    }

    public void responseToQuery(GroupedQuery query, Response response) {
        reranker.rerank(query, response);
    }

    public void refreshQueryList() {
        Collection<GroupedQuery> queryBuffer = new ArrayList<>(queryList);
        queryBuffer.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
        queryList.clear();
        queryList.addAll(queryBuffer);
        System.out.println("Remaining number of queries:\t" + queryList.size());
    }

    public int getNumberOfRemainingQueries() {
        return queryList.size();
    }

    public Results getRerankedF1() {
        Results currentResult = new Results();
        allParses.keySet().forEach(sid ->
                currentResult.add(allResults.get(sid).get(reranker.getRerankedBest(sid))));
        return currentResult;
    }

    public Results getOneBestF1() {
        Results oneBestF1 = new Results();
        allParses.keySet().forEach(sid -> oneBestF1.add(allResults.get(sid).get(0)));
        return oneBestF1;
    }

    public Results getOracleF1() {
        Results oracleF1 = new Results();
        allParses.keySet().forEach(sid -> oracleF1.add(allResults.get(sid).get(oracleParseIds.get(sid))));
        return oracleF1;
    }
}
