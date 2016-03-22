package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This is currently just a stand-in... doesn't actually do any reranking.
 * Created by julianmichael on 3/2/16.
 */
@Deprecated
public class ActiveLearningMultiResponse {
    public final List<List<InputReader.InputWord>> sentences;
    public final List<List<String>> sentencesWords;
    public final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    // Reranker needs to be initialized after we parse all the sentences .. maybe not?
    Reranker reranker;

    // All queries for each sentence id.
    private Map<Integer, List<MultiQuery>> queryPool;
    private Queue<MultiQuery> queryQueue;
    private Queue<Integer> sentenceQueue;
    private Map<Integer, Optional<Double>> sentenceScores;

    public Map<Integer, List<Parse>> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    // private static final double minAnswerEntropy = 1e-3;

    String preparsedFile;
    int nBest;
    double rerankerStepSize = 1.0;

    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    public ActiveLearningMultiResponse(int nBest) {
        System.err.println("Initializing active learner ... nbest=" + nBest);
        this.nBest = nBest;
        // The file contains the pre-parsed n-best list (of CCGBank dev). Leave file name is empty, if we wish to parse
        // sentences in the experiment.
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        sentencesWords = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        for(List<InputReader.InputWord> sentence : sentences) {
            sentencesWords.add(sentence
                               .stream()
                               .map(iw -> iw.word)
                               .collect(Collectors.toList()));
        }
        questionGenerator = new QuestionGenerator();
        if (nBest <= 10) {
            preparsedFile = "parses.10best.out";
        } else if (nBest <= 50) {
            preparsedFile = "parses.50best.out";
        } else if (nBest <= 100) {
            preparsedFile = "parses.100best.out";
        } else if (nBest <= 1000) {
            preparsedFile = "parses.1000best.out";
        } else {
            preparsedFile = "";
        }
        // TODO: check existence of parse files.
        parser = preparsedFile.isEmpty() ?
                new BaseCcgParser.AStarParser(modelFolder, rootCategories, nBest) :
                new BaseCcgParser.MockParser(preparsedFile, nBest);
        // System.err.println("Parses loaded.");
        this.sentenceQueue = new LinkedList<>();
        initializeParses();
        initializeQueryPool();
        // initializeQueryQueue();
    }

    public ActiveLearningMultiResponse(ActiveLearningMultiResponse other) {
        this.sentences = other.sentences;
        this.sentencesWords = other.sentencesWords;
        this.goldParses = other.goldParses;
        this.questionGenerator = other.questionGenerator;
        this.parser = other.parser;
        this.nBest = other.nBest;
        this.allParses = other.allParses;
        this.allResults = other.allResults;
        this.oracleParseIds = other.oracleParseIds;
        this.reranker = new RerankerExponentiated(allParses, rerankerStepSize);
        //this.reranker = new RerankerDependencyFactored(allParses);
        this.queryPool = other.queryPool;
        this.sentenceScores = other.sentenceScores;
        this.sentenceQueue = new LinkedList<>();
        sentenceQueue.addAll(other.getAllSentenceIds());
        // initializeQueryPool();
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

    public void initializeQueryPool() {
        System.err.println("Initializing query pool ... ");
        queryPool = new HashMap<>();
        sentenceScores = new HashMap<>();
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            List<String> words = sentencesWords.get(sentIdx);
            List<Parse> parses = allParses.get(sentIdx);
            if(parses == null) {
                continue;
            }
            QueryGeneratorBothWays queryGenerator = new QueryGeneratorBothWays(sentIdx, words, parses);
            List<MultiQuery> queries = queryGenerator.getAllMaximalQueries();
            for(MultiQuery query : queries) {
                if (!queryPool.containsKey(sentIdx)) {
                    queryPool.put(sentIdx, new ArrayList<>());
                }
                final List<MultiQuery> pool = queryPool.get(sentIdx);
                // TODO do I need to do something like this?
                // query.setQueryId(pool.size());
                pool.add(query);
            }
        }
        int totalNumQueries = queryPool.values()
            .stream()
            .mapToInt(l -> l.size())
            .sum();
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
        queryQueue = new LinkedList<>();
        queryQueue.addAll(queryPool.get(sentenceId));
    }

    public int getCurrentSentenceId() {
        return sentenceQueue.isEmpty() ? -1 : sentenceQueue.peek();
    }

    public Optional<MultiQuery> getNextQuery() {
        return queryQueue.isEmpty() ? Optional.empty() : Optional.of(queryQueue.poll());
    }

    public MultiQuery getNextNonEmptyQuery() {
        Optional<MultiQuery> queryOpt;
        while (!(queryOpt = getNextQuery()).isPresent()) {
            if (!switchToNextSentence()) {
                return null;
            }
        }
        return queryOpt.get();
    }

    public void printQueriesBySentenceId(int sentIdx) {
        for (MultiQuery query : queryPool.get(sentIdx)) {
            System.out.println(String.format("%.3f\t%.3f\t%s", query.prompt));
        }
    }

    public List<MultiQuery> getQueriesBySentenceId(int sentIdx) {
        return queryPool.get(sentIdx);
    }

    public List<String> getSentenceById(int sentenceId) {
        return sentencesWords.get(sentenceId);
    }

    public Optional<Double> getSentenceScore(int sentIdx) {
        return sentenceScores.get(sentIdx);
    }

    public int getNumSentences() {
        return sentencesWords.size();
    }

    public Set<Integer> getAllSentenceIds() {
        Set<Integer> ids = new HashSet<>();
        for(int i = 0; i < sentencesWords.size(); i++) {
            ids.add(i);
        }
        return ids;
    }

    public int getNumRemainingSentences() {
        return sentenceQueue.size();
    }

    public void respondToQuery(MultiQuery query, Set<String> answers) {
        // TODO rerank.
        // reranker.rerank(query, response);
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
