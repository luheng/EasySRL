package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import org.omg.PortableInterceptor.ACTIVE;

import java.util.*;
import java.util.concurrent.SynchronousQueue;
import java.util.stream.Collectors;

/**
 * Group questions by sentences and predicates.
 * Created by luheng on 2/1/16.
 */
public class ActiveLearning2 {
    public final List<List<InputReader.InputWord>> sentences;
    public final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;
    // Reranker needs to be initialized after we parse all the sentences .. maybe not?
    RerankerExponentiated reranker;

    // All queries: sentence id, predicate id, query list.
    private Table<Integer, Integer, List<GroupedQuery>> queryPool;
    private Map<Integer, Double> sentenceScores;
    private PriorityQueue<Integer> sentenceQueue;
    // private PriorityQueue<Tuple<Integer, Integer>> predicateQueue;
    private Map<Integer, List<Parse>> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    // History: sent_id.pred_id.category.arg_num
    private Set<Integer> recentlyUpdatedSentences;
    private Map<Integer, Set<String>> askedDependencies;

    private final Comparator<Integer> sentenceComparator = new Comparator<Integer>() {
        public int compare(Integer s1, Integer s2) {
            return Double.compare(-sentenceScores.get(s1), -sentenceScores.get(s2));
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
    double minAnswerEntropy = 1e-2;

    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    public ActiveLearning2(int nBest) {
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
        initialize();
    }

    public ActiveLearning2(ActiveLearning2 other) {
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
        //this.sentenceScores =
        this.sentenceQueue = new PriorityQueue<>(sentenceComparator);
        // sen
        // queryQueue.addAll(queryPool);
        System.out.println("Total number of queries:\t" + sentenceQueue.size());

        recentlyUpdatedSentences = new HashSet<>();
        askedDependencies = new HashMap<>();
    }

    private void initialize() {
        initializeParses();
        initializeQueries();
        // History.
        recentlyUpdatedSentences = new HashSet<>();
        askedDependencies = new HashMap<>();
    }

    private void initializeParses() {
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

    private void initializeQueries() {
        reranker = new RerankerExponentiated(allParses, rerankerStepSize);
        queryPool = HashBasedTable.create();
        sentenceQueue = new PriorityQueue<>(sentenceComparator);
        sentenceScores = new HashMap<>();

        for (int sentIdx : allParses.keySet()) {
            List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
            List<Parse> parses = allParses.get(sentIdx);
            List<GroupedQuery> queries = QueryGenerator.generateQueries(sentIdx, words, parses, questionGenerator,
                    generatePseudoQuestions, groupSameLabelDependencies);
            queries.forEach(query -> {
                query.computeProbabilities(reranker.expScores.get(query.sentenceId));
                if (query.answerEntropy > minAnswerEntropy) {
                    // query.setQueryId(queryPool.size());
                    int predIdx = query.predicateIndex;
                    if (!queryPool.contains(sentIdx, predIdx)) {
                        queryPool.put(sentIdx, predIdx, new ArrayList<>());
                    }
                    queryPool.get(sentIdx, predIdx).add(query);
                }
            });
        }
        int totalNumQueries = 0;
        for (int sentIdx : queryPool.rowKeySet()) {
            double sentScore = .0;
            int numQueries= 0;
            for (int predIdx : queryPool.row(sentIdx).keySet()) {
                for (GroupedQuery query : queryPool.row(sentIdx).get(predIdx)) {
                    sentScore += query.answerEntropy;
                    numQueries ++;
                }
            }
            sentenceScores.put(sentIdx, sentScore / numQueries);
            totalNumQueries += numQueries;
        }
        sentenceQueue.addAll(queryPool.rowKeySet());
        System.out.println("Total number of queries:\t" + totalNumQueries);
    }

    public void refreshQueryList() {
        // do nothing.
    }

    public List<String> getSentenceById(int sentenceId) {
        return sentences.get(sentenceId).stream().map(w -> w.word).collect(Collectors.toList());
    }

    public Map<Integer, List<GroupedQuery>> getQueryBySentenceId(int sentId) {
        return queryPool.row(sentId);
    }

    public List<GroupedQuery> getQueryById(int sentId, int predId) {
        return queryPool.get(sentId, predId);
    }

    public double getSentenceScore(int sentIdx) {
        return sentenceScores.get(sentIdx);
    }

    public int getNextSentenceInQueue() {
        return sentenceQueue.poll();
    }

    public int getNumberOfRemainingSentences() {
        return sentenceQueue.size();
    }

    public void respondToQuery(GroupedQuery query, Response response) {
        reranker.rerank(query, response);
        // Register processed query history.
        int sentId = query.sentenceId;
        if (!askedDependencies.containsKey(sentId)) {
            askedDependencies.put(sentId, new HashSet<>());
        }
        askedDependencies.get(sentId).add(query.predicateIndex + "." + query.category + "." + query.argumentNumber);
        recentlyUpdatedSentences.add(sentId);
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

    public Results getOneBestF1() {
        Results oneBestF1 = new Results();
        allParses.keySet().forEach(sid -> oneBestF1.add(allResults.get(sid).get(0)));
        return oneBestF1;
    }

    public Results getOneBestF1(int sid) {
        return allResults.get(sid).get(0);
    }

    public Results getOracleF1() {
        Results oracleF1 = new Results();
        allParses.keySet().forEach(sid -> oracleF1.add(allResults.get(sid).get(oracleParseIds.get(sid))));
        return oracleF1;
    }

    public Results getOracleF1(int sid) {
        return allResults.get(sid).get(oracleParseIds.get(sid));
    }
}
