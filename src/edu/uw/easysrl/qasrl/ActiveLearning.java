package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Separating ResponseSimulator and the other modules. Useful for Web Interface logic.
 * Created by luheng on 2/1/16.
 */
public class ActiveLearning {
    public final List<List<InputReader.InputWord>> sentences;
    public final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    // Reranker needs to be initialized after we parse all the sentences .. maybe not?
    RerankerExponentiated reranker;

    // All queries.
    private List<GroupedQuery> queryPool;
    private PriorityQueue<GroupedQuery> queryQueue;
    private Map<Integer, List<Parse>> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    private final Comparator<GroupedQuery> queryComparator = new Comparator<GroupedQuery>() {
        public int compare(GroupedQuery q1, GroupedQuery q2) {
            return Double.compare(-q1.answerEntropy, -q2.answerEntropy);
        }
    };

    // Incorporate -NOQ- queries (dependencies we can't generate questions for) in reranking to see potential
    // improvements.
    boolean generatePseudoQuestions = false;
    boolean groupSameLabelDependencies = true;
    // The change inflicted on distribution of parses after each query update.

    // The file contains the pre-parsed n-best list (of CCGBank dev). Leave file name is empty, if we wish to parse
    // sentences in the experiment.
    String preparsedFile = "";
    int nBest;
    double rerankerStepSize = 1.0;

    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    public ActiveLearning(int nBest) {
        this.nBest = nBest;
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        questionGenerator = new QuestionGenerator();
        if (nBest <= 10) {
            preparsedFile = "parses.10best.out";
        } else if (nBest <= 50) {
            preparsedFile = "parses.50best.out";
        } else if (nBest <= 100) {
            preparsedFile = "parses.100best.out";
        } else if (nBest <= 1000) {
            preparsedFile = "parses.1000best.out";
        }
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
        queryPool = new ArrayList<>();
        queryQueue = new PriorityQueue<>(queryComparator);
        for (int sentIdx : allParses.keySet()) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            List<GroupedQuery> queries = QueryGenerator.generateQueries(sentIdx, words, parses, questionGenerator,
                                                                        generatePseudoQuestions,
                                                                        groupSameLabelDependencies);
            queries.forEach(query -> {
                query.computeProbabilities(reranker.expScores.get(query.sentenceId));
                query.setQueryId(queryPool.size());
                queryPool.add(query);
            });
        }
        queryQueue.addAll(queryPool);
        System.out.println("Total number of queries:\t" + queryQueue.size());;
    }

    public List<String> getSentenceById(int sentenceId) {
        return sentences.get(sentenceId).stream().map(w -> w.word).collect(Collectors.toList());
    }

    public GroupedQuery getQueryById(int queryId) {
        if (queryId >= 0 && queryId < queryPool.size()) {
            return queryPool.get(queryId);
        }
        return null;
    }

    public GroupedQuery getNextQueryInQueue() {
        return queryQueue.poll();
    }

    public void respondToQuery(GroupedQuery query, Response response) {
        reranker.rerank(query, response);
    }

    public void refreshQueryList() {
        Collection<GroupedQuery> queryBuffer = new ArrayList<>(queryQueue);
        queryBuffer.forEach(query -> query.computeProbabilities(reranker.expScores.get(query.sentenceId)));
        queryQueue.clear();
        queryQueue.addAll(queryBuffer);
        System.out.println("Remaining number of queries:\t" + queryQueue.size());
    }

    public int getNumberOfRemainingQueries() {
        return queryQueue.size();
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
