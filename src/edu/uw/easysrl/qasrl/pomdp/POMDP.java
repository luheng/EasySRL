package edu.uw.easysrl.qasrl.pomdp;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

/**
 * POMDP prototype.
 * Created by luheng on 2/27/16.
 */
public class POMDP {
    public final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences;
    public final ImmutableList<ImmutableList<String>> sentences;
    public final ImmutableList<Parse> goldParses;
    final BaseCcgParser parser;

    // The action space.
    private List<ScoredQuery<QAStructureSurfaceForm>> queryPool;

    public BeliefModel beliefModel;
    private ObservationModel observationModel, baseObservationModel;
    public History history;
    private Policy policy;
    private RewardFunction rewardFunction;
    private int timeStep;

    // The state space.
    public Map<Integer, NBestList> allParses;
    private Map<Integer, List<Results>> allResults;
    private Map<Integer, Integer> oracleParseIds;

    String preparsedFile = "";
    final static String modelFolder = "./model_tritrain_big/";
    final static List<Category> rootCategories =  Arrays.asList(
            Category.valueOf("S[dcl]"), Category.valueOf("S[wq]"), Category.valueOf("S[q]"),
            Category.valueOf("S[b]\\NP"), Category.valueOf("NP"));

    // Other parameters.
    int nBest;
    int horizon;
    double moneyPenalty;

    public POMDP(int nBest, int horizon, double moneyPenalty) {
        System.err.println("Initializing POMDP ... nbest=" + nBest);
        this.nBest = nBest;
        this.horizon = horizon;
        this.moneyPenalty = moneyPenalty;

        ParseData parseData = ParseData.loadFromDevPool().get();
        sentences = parseData.getSentences();
        inputSentences = parseData.getSentenceInputWords();
        goldParses = parseData.getGoldParses();

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
        System.err.println("Parse initialized.");
        initializeParses();
    }

    public POMDP(POMDP other) {
        this.inputSentences = other.inputSentences;
        this.sentences = other.sentences;
        this.goldParses = other.goldParses;
        this.parser = other.parser;
        this.nBest = other.nBest;
        this.horizon = other.horizon;
        this.moneyPenalty = other.moneyPenalty;
        this.allParses = other.allParses;
        this.allResults = other.allResults;
        this.oracleParseIds = other.oracleParseIds;
    }

    public void setBaseObservationModel(ObservationModel baseObservationModel) {
        this.baseObservationModel = baseObservationModel;
    }

    private void initializeParses() {
        allParses = new HashMap<>();
        allResults = new HashMap<>();
        oracleParseIds = new HashMap<>();
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx ++) {
            List<Parse> parses = parser.parseNBest(sentIdx, inputSentences.get(sentIdx));
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
            allParses.put(sentIdx, new NBestList(ImmutableList.copyOf(parses)));
            allResults.put(sentIdx, results);
            oracleParseIds.put(sentIdx, oracleK);
            if (allParses.size() % 500 == 0) {
                System.out.println("Parsed:\t" + allParses.size() + " sentences ...");
            }
        }
    }

    public void initializeForSentence(int sentIdx) {
        queryPool = new ArrayList<>();
        beliefModel = new BeliefModel(allParses.get(sentIdx).getParses());
        observationModel = baseObservationModel == null ? new ObservationModel() :
                                                          new ObservationModel(baseObservationModel);
        history = new History();
        //policy = new Policy(queryPool, history, beliefModel, horizon);
        rewardFunction = new RewardFunction(allParses.get(sentIdx).getParses(), allResults.get(sentIdx), moneyPenalty);
        timeStep = 0;

        List<ScoredQuery<QAStructureSurfaceForm>> queries =
                QueryGenerators.checkboxQueryAggregator().generate(
                        QAPairAggregators.aggregateForMultipleChoiceQA().aggregate(
                                QuestionGenerator.generateAllQAPairs(sentIdx,
                                                                     sentences.get(sentIdx),
                                                                     allParses.get(sentIdx))));

        queries.stream().forEach(query -> {
            query.computeScores(allParses.get(sentIdx));
            query.setQueryId(queryPool.size());
            queryPool.add(query);
        });
        System.out.println("Total number of queries:\t" + queryPool.size());
    }

    /**
     * Restrict the action space to be annotated queries.
     * @param sentIdx
     * @param annotations
     */
    public void initializeForSentence(int sentIdx, List<AlignedAnnotation> annotations) {
        queryPool = new ArrayList<>();
        beliefModel = new BeliefModel(allParses.get(sentIdx).getParses());
        observationModel = baseObservationModel == null ? new ObservationModel() :
                                                          new ObservationModel(baseObservationModel);
        history = new History();
        //policy = new Policy(queryPool, history, beliefModel, horizon);
        rewardFunction = new RewardFunction(allParses.get(sentIdx).getParses(), allResults.get(sentIdx), moneyPenalty);
        timeStep = 0;

        List<ScoredQuery<QAStructureSurfaceForm>> queries =
                QueryGenerators.checkboxQueryAggregator().generate(
                        QAPairAggregators.aggregateForMultipleChoiceQA().aggregate(
                                QuestionGenerator.generateAllQAPairs(sentIdx,
                                        sentences.get(sentIdx),
                                        allParses.get(sentIdx))));

        queries.stream().forEach(query -> {
            query.computeScores(allParses.get(sentIdx));
            if (annotations.stream().anyMatch(annotation -> annotation.sentenceId == sentIdx
                    && annotation.question.equals(query.getPrompt()))) {
                query.setQueryId(queryPool.size());
                queryPool.add(query);
            }
        });
        System.out.println("Total number of queries:\t" + queryPool.size());
    }

    public Optional<ScoredQuery> generateAction() {
        Optional<ScoredQuery> action = policy.getAction();
        if (action.isPresent()) {
            history.addAction(action.get());
        }
        timeStep ++;
        double reward = rewardFunction.getReward(action, beliefModel, history);
        System.out.println("Receiving reward:\t" + reward + " at time:\t" + timeStep + "\tbelief entropy:\t" +
                beliefModel.getEntropy() + "\tmargin:\t" + beliefModel.getMargin());
        return action;
    }

    public void resetBeliefModel() {
        beliefModel.resetToPrior();
    }

    public List<ScoredQuery<QAStructureSurfaceForm>> getQueryPool() {
        return queryPool;
    }

    public void receiveObservation(Response response) {
        beliefModel.update(observationModel, history.getLastAction(), response);
        history.addObservation(response);
    }

    public void receiveObservationForQuery(ScoredQuery query, Response response) {
        beliefModel.update(observationModel, query, response);
    }

    public List<String> getSentenceById(int sentenceId) {
        return sentences.get(sentenceId);
    }

    public Results getRerankedF1(int sid) {
        return allResults.get(sid).get(beliefModel.getBestState());
    }

    public int getRerankParseId(int sid) {
        return beliefModel.getBestState();
    }

    public Results getOneBestF1(int sid) {
        return allResults.get(sid).get(0);
    }

    public Results getOracleF1(int sid) {
        return allResults.get(sid).get(oracleParseIds.get(sid));
    }

    public int getOracleParseId(int sid) {
        return oracleParseIds.get(sid);
    }
}
