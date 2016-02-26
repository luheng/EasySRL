package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import javax.swing.text.html.Option;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Group questions by sentences and predicates.
 * Created by luheng on 2/1/16.
 */
public class ActiveLearningBySentence {
    public final List<List<InputReader.InputWord>> sentences;
    public final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    // Reranker needs to be initialized after we parse all the sentences .. maybe not?
    RerankerExponentiated reranker;

    // All queries for each sentence id.
    private Map<Integer, List<GroupedQuery>> queryPool;
    private PriorityQueue<GroupedQuery> queryQueue;
    private PriorityQueue<Integer> sentenceQueue;
    private Map<Integer, Double> sentenceScores;

    private Map<Integer, List<Parse>> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    private static final double minAnswerEntropy = 1e-3;

    // Default order of sentneces: by average question entropy.
    private final Comparator<Integer> sentenceComparator = new Comparator<Integer>() {
        public int compare(final Integer s1, final Integer s2) {
            return Double.compare(-sentenceScores.get(s1), -sentenceScores.get(s2));
        }
    };

    private final Comparator<GroupedQuery> queryComparator = new Comparator<GroupedQuery>() {
        public int compare(final GroupedQuery q1, final GroupedQuery q2) {
            return Double.compare(-q1.answerEntropy, -q2.answerEntropy);
        }
    };

    // The file contains the pre-parsed n-best list (of CCGBank dev). Leave file name is empty, if we wish to parse
    // sentences in the experiment.
    String preparsedFile = "";
    int nBest;
    double rerankerStepSize = 1.0;

    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    public ActiveLearningBySentence(int nBest) {
        System.err.println("Initializing active learner ... nbest=" + nBest);
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
        // TODO: check existence of parse files.
        parser = preparsedFile.isEmpty() ?
                new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest) :
                new BaseCcgParser.MockParser(preparsedFile, nBest);
        System.err.println("Parse initialized.");
        initializeParses();
        // initializeQueryPool();
        // initializeQueryQueue();
    }

    public ActiveLearningBySentence(ActiveLearningBySentence other) {
        this.sentences = other.sentences;
        this.goldParses = other.goldParses;
        this.questionGenerator = other.questionGenerator;
        this.parser = other.parser;
        this.nBest = other.nBest;
        this.allParses = other.allParses;
        this.allResults = other.allResults;
        this.oracleParseIds = other.oracleParseIds;
        this.reranker = new RerankerExponentiated(allParses, rerankerStepSize);
        this.queryPool = other.queryPool;
        this.sentenceScores = other.sentenceScores;
        // this.sentenceQueue = new PriorityQueue<>(sentenceComparator);
        // sentenceQueue.addAll(sentenceScores.keySet());
        // initializeQueryQueue();
    }

    private void initializeParses() {
        System.err.println("Initializing parses ... ");
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
            if (allParses.size() % 500 == 0) {
                System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
            }
        }
    }

    public void initializeQueryPool(List<Integer> sentenceIds) {
        System.err.println("Initializing query pool ... ");
        reranker = new RerankerExponentiated(allParses, rerankerStepSize);
        queryPool = new HashMap<>();
        sentenceScores = new HashMap<>();
        //sentenceQueue = new PriorityQueue<>(sentenceComparator);
        for (int sentIdx : sentenceIds) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            List<GroupedQuery> queries = QueryGeneratorNew2.generateQueries(sentIdx, words, parses, questionGenerator);
            queries.stream().forEach(query -> {
                query.computeProbabilities(reranker.expScores.get(query.sentenceId));
                if (query.answerEntropy > minAnswerEntropy) {
                    if (!queryPool.containsKey(sentIdx)) {
                        queryPool.put(sentIdx, new ArrayList<>());
                    }
                    final List<GroupedQuery> pool = queryPool.get(sentIdx);
                    query.setQueryId(pool.size());
                    pool.add(query);
                }
            });
        }
        int totalNumQueries = 0;
        for (int sentIdx : queryPool.keySet()) {
            //updateQueryScoresBySentenceId(sentIdx);
            int numQueriesInSentence = 0;
            double sentScore = .0;
            for (GroupedQuery query : queryPool.get(sentIdx)) {
                sentScore += query.attachmentUncertainty;
                numQueriesInSentence ++;
            }
            sentenceScores.put(sentIdx, sentScore / Math.sqrt(numQueriesInSentence));
            totalNumQueries += numQueriesInSentence;
        }
        //sentenceQueue.addAll(sentenceScores.keySet());
        System.out.println("Total number of queries:\t" + totalNumQueries);
    }

    /**
     * Move on to next sentence in queue.
     * @return true if there is sentence left in the queue.
     */
    public boolean switchToNextSentence() {
        if (!sentenceQueue.isEmpty()) {
            sentenceQueue.poll();
        }
        if (!sentenceQueue.isEmpty()) {
            initializeQueryQueue();
            return true;
        }
        return false;
    }

    /**
     * Initialize query queue for the current sentence.
     */
    private void initializeQueryQueue() {
        System.out.println("Initializing query queue ... ");
        int sentenceId = sentenceQueue.peek();
        queryQueue = new PriorityQueue<>(queryComparator);
        queryQueue.addAll(queryPool.get(sentenceId));
    }

    public int getCurrentSentenceId() {
        return sentenceQueue.isEmpty() ? -1 : sentenceQueue.peek();
    }

    public Optional<GroupedQuery> getNextQuery() {
        return queryQueue.isEmpty() ? Optional.empty() : Optional.of(queryQueue.poll());
    }

    public GroupedQuery getNextNonEmptyQuery() {
        Optional<GroupedQuery> queryOpt;
        while (!(queryOpt = getNextQuery()).isPresent()) {
            if (!switchToNextSentence()) {
                return null;
            }
        }
        return queryOpt.get();
    }

    public void refereshQueryQueue() {
        Set<GroupedQuery> buffer = new HashSet<>(queryQueue);
        buffer.forEach(query -> query.computeProbabilities(reranker.getParseScores(query.sentenceId)));
        queryQueue = new PriorityQueue<>(queryComparator);
        queryQueue.addAll(buffer);
    }

    public void printQueriesBySentenceId(int sentIdx) {
        for (GroupedQuery query : queryPool.get(sentIdx)) {
            System.out.println(String.format("%.3f\t%.3f\t%s", query.questionConfidence, query.attachmentUncertainty,
                    query.question));
        }
    }

    public List<GroupedQuery> getQueriesBySentenceId(int sentIdx) {
        return queryPool.get(sentIdx);
    }

    public List<String> getSentenceById(int sentenceId) {
        return sentences.get(sentenceId).stream().map(w -> w.word).collect(Collectors.toList());
    }

    public GroupedQuery getQueryById(int sentenceId, int queryId) {
        return queryPool.get(sentenceId).get(queryId);
    }

    public double getSentenceScore(int sentIdx) {
        return sentenceScores.get(sentIdx);
    }

    public int getNumSentences() {
        return sentenceScores.size();
    }

    public Set<Integer> getAllSentenceIds() {
        return sentenceScores.keySet();
    }

    public int getNumRemainingSentences() {
        return sentenceQueue.size();
    }

    public void respondToQuery(GroupedQuery query, Response response) {
        reranker.rerank(query, response);
    }

    public Results getRerankedF1() {
        Results currentResult = new Results();
        allParses.keySet().forEach(sid ->
                currentResult.add(allResults.get(sid).get(reranker.getRerankedBest(sid))));
        return currentResult;
    }

    public Results getRerankedF1(int sid) {
        return allResults.get(sid).get(reranker.getRerankedBest(sid));
    }

    public Results getRerankedF1(Collection<Integer> sentenceIds) {
        Results results = new Results();
        sentenceIds.forEach(sid -> results.add(allResults.get(sid).get(reranker.getRerankedBest(sid))));
        return results;
    }

    public Results getOneBestF1() {
        Results oneBestF1 = new Results();
        allParses.keySet().forEach(sid -> oneBestF1.add(allResults.get(sid).get(0)));
        return oneBestF1;
    }

    public Results getOneBestF1(Collection<Integer> sentenceIds) {
        Results results = new Results();
        sentenceIds.forEach(sid -> results.add(allResults.get(sid).get(0)));
        return results;
    }

    public Results getOneBestF1(int sid) {
        return allResults.get(sid).get(0);
    }

    public Results getOracleF1() {
        Results oracleF1 = new Results();
        allParses.keySet().forEach(sid -> oracleF1.add(allResults.get(sid).get(oracleParseIds.get(sid))));
        return oracleF1;
    }

    public Results getOracleF1(Collection<Integer> sentenceIds) {
        Results results = new Results();
        sentenceIds.forEach(sid -> results.add(allResults.get(sid).get(oracleParseIds.get(sid))));
        return results;
    }

    public Results getOracleF1(int sid) {
        return allResults.get(sid).get(oracleParseIds.get(sid));
    }
}
