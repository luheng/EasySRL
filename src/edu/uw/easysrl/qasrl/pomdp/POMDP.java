package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.pomdp.BeliefModel;
import edu.uw.easysrl.qasrl.pomdp.ObservationModel;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * POMDP prototype.
 * Created by luheng on 2/27/16.
 */
public class POMDP {
    public final List<List<InputReader.InputWord>> sentences;
    public final List<Parse> goldParses;
    final BaseCcgParser parser;
    final QuestionGenerator questionGenerator;

    // The action space.
    private List<GroupedQuery> queryPool;

    private BeliefModel beliefModel;
    private ObservationModel observationModel;
    private History history;
    private Policy policy;

    private Map<Integer, List<Parse>> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    String preparsedFile = "";
    int nBest;
    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    public POMDP(int nBest) {
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
    }

    public POMDP(POMDP other) {
        this.sentences = other.sentences;
        this.goldParses = other.goldParses;
        this.questionGenerator = other.questionGenerator;
        this.parser = other.parser;
        this.nBest = other.nBest;
        this.allParses = other.allParses;
        this.allResults = other.allResults;
        this.oracleParseIds = other.oracleParseIds;
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
            List<Results> results = CcgEvaluation.evaluateNBest(parses, goldParses.get(sentIdx).dependencies);
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

    public void initializeForSentence(int sentIdx) {
        queryPool = new ArrayList<>();
        beliefModel = new BeliefModel(allParses.get(sentIdx));
        observationModel = new ObservationModel();
        history = new History();
        policy = new Policy(queryPool, history, beliefModel);

        List<String> words = sentences.get(sentIdx).stream().map(w -> w.word).collect(Collectors.toList());
        List<Parse> parses = allParses.get(sentIdx);
        List<GroupedQuery> queries = QueryGeneratorNew2.generateQueries(sentIdx, words, parses, questionGenerator);
        queries.stream().forEach(query -> {
            query.setQueryId(queryPool.size());
            queryPool.add(query);
        });
        System.err.println("Total number of queries:\t" + queryPool.size());
    }

    public Optional<GroupedQuery> generateAction() {
        Optional<GroupedQuery> action = policy.getAction();
        if (action.isPresent()) {
            history.addAction(action.get());
        }
        return action;
    }

    public void receiveObservation(Response response) {
        beliefModel.update(observationModel, history.getLastAction(), response);
        history.addObservation(response);
    }

    public List<String> getSentenceById(int sentenceId) {
        return sentences.get(sentenceId).stream().map(w -> w.word).collect(Collectors.toList());
    }

    public Results getRerankedF1(int sid) {
        return allResults.get(sid).get(beliefModel.getBestState());
    }

    public Results getOneBestF1(int sid) {
        return allResults.get(sid).get(0);
    }

    public Results getOracleF1(int sid) {
        return allResults.get(sid).get(oracleParseIds.get(sid));
    }
}
