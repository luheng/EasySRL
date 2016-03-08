package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.analysis.PPAttachment;
import edu.uw.easysrl.qasrl.pomdp.ObservationModel;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Experiment on collected data.
 * Created by luheng on 2/27/16.
 */
public class SimulatedExperimentPOMDP {
    static final int nBest = 100;
    static final int horizon = 1000;
    static final double moneyPenalty = 0.1;

    // Query pruning parameters.
    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters(
            1,    /* top K */
            0.1,  /* min question confidence */
            0.05, /* min answer confidence */
            0.05  /* min attachment entropy */
    );

    //static final int[] trials = new int[] {10, 20, 30, 40, 50 };
    static final int[] trials = new int[] { 0 };
    static final int numRandomRuns = 1;

    static final boolean useObservationModel = false;
    static final boolean skipPPQuestions = true;
    static final double minResponseTrust = 2.5;

    private static final int maxNumOptionsPerQuestion = 6;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }
    //private static final String annotationFilePath = "./Crowdflower_data/f878213.csv";
    private static final String annotationFilePath = "./Crowdflower_data/f882410.csv";

    // Shared data
    static POMDP baseLeaner;
    static List<AlignedAnnotation> annotations;
    static List<Integer> sentenceIds;
    static List<List<Double>> rerankF1, rerankF1Affected, baselineF1, baselineF1Affected, oracleF1;

    private static void runExperiment(int numTrainingSentences) {
        // Learn a observation model.
        POMDP qgen = new POMDP(baseLeaner);
        qgen.setQueryPruningParameters(queryPruningParameters);
        ResponseSimulator userModel = new ResponseSimulatorRecorded(annotations);
        ResponseSimulator goldModel = new ResponseSimulatorGold(qgen.goldParses, new QuestionGenerator(),
                false /* allow label match */);

        System.err.println("Training sentences:\t" + numTrainingSentences + "\ttest sentences:\t"
                + (sentenceIds.size() - numTrainingSentences));
        Collections.shuffle(sentenceIds, new Random(12345));
        List<Integer> trainingSentIds = sentenceIds.subList(0, numTrainingSentences);
        List<GroupedQuery> trainingQueries = new ArrayList<>();
        for (int sentenceId : trainingSentIds) {
            qgen.initializeForSentence(sentenceId);
            Optional<GroupedQuery> action;
            while ((action = qgen.generateAction()).isPresent()) {
                Response userResponse = userModel.answerQuestion(action.get());
                if (userResponse.chosenOptions.size() > 0) {
                    trainingQueries.add(action.get());
                }
            }
        }

        POMDP learner = new POMDP(baseLeaner);
        qgen.setQueryPruningParameters(queryPruningParameters);
        Accuracy answerAcc = new Accuracy();
        int numUnmatchedQuestions = 0,
            numMatchedQuestions = 0,
            numAffectedSentences = 0,
            numCoreQuestions = 0;

        if (useObservationModel) {
            Map<Integer, Integer> oracleIds = new HashMap<>();
            learner.allParses.keySet().forEach(sid -> oracleIds.put(sid, learner.getOracleParseId(sid)));
            ObservationModel observationModel = new ObservationModel(trainingQueries, learner.allParses, oracleIds,
                    userModel, minResponseTrust);
            learner.setBaseObservationModel(observationModel);
        }

        assert annotations != null;
        Results rerank = new Results(),
                oracle = new Results(),
                onebest = new Results(),
                onebest2 = new Results(),
                rerank2 = new Results();

        // Process other questions.
        List<Integer> testSentIds = sentenceIds.subList(numTrainingSentences, sentenceIds.size());
        for (int sentenceId : testSentIds) {
            learner.initializeForSentence(sentenceId, annotations);
            Optional<GroupedQuery> action;
            boolean usedQuery = false;
            while ((action = learner.generateAction()).isPresent()) {
                Response userResponse = userModel.answerQuestion(action.get());
                Response goldResponse = goldModel.answerQuestion(action.get());
                Category category = action.get().getCategory();
                if (skipPPQuestions && (category == PPAttachment.nounAdjunct || category == PPAttachment.verbAdjunct
                        || category == Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"))) {
                    continue;
                }
                numCoreQuestions ++;
                if (userResponse.trust < minResponseTrust) {
                    continue;
                }

                boolean matchesGold = userResponse.chosenOptions.size() > 0 &&
                        (userResponse.chosenOptions.get(0).intValue() == goldResponse.chosenOptions.get(0).intValue());
                if (userResponse.chosenOptions.size() == 0) {
                    numUnmatchedQuestions ++;
                } else {
                    numMatchedQuestions ++;
                    answerAcc.add(matchesGold);
                    learner.receiveObservation(userResponse);
                    usedQuery = true;
                    /* if (!matchesGold) {
                        System.out.println(query.getDebuggingInfo(userResponse, goldResponse) + "\n");
                    }*/
                }
            }
            rerank.add(learner.getRerankedF1(sentenceId));
            oracle.add(learner.getOracleF1(sentenceId));
            onebest.add(learner.getOneBestF1(sentenceId));
            if (usedQuery) {
                rerank2.add(learner.getRerankedF1(sentenceId));
                onebest2.add(learner.getOneBestF1(sentenceId));
                numAffectedSentences ++;
            }
        }

        System.out.println("Num. affected sentences:\t" + numAffectedSentences);
        System.out.println("Num. core questions:\t" + numCoreQuestions);
        System.out.println("onebest:\t" + onebest);
        System.out.println("onebest2:\t" + onebest2);
        System.out.println("rerank:\t" + rerank);
        System.out.println("rerank2:\t" + rerank2);
        System.out.println("oracle:\t" + oracle);

        int last = rerankF1.size() - 1;
        rerankF1.get(last).add(rerank.getF1());
        rerankF1Affected.get(last).add(rerank2.getF1());
        oracleF1.get(last).add(oracle.getF1());
        baselineF1.get(last).add(onebest.getF1());
        baselineF1Affected.get(last).add(onebest2.getF1());

        System.out.println("answer accuracy:\t" + answerAcc);
        System.out.println("number of unmatched:\t" + numUnmatchedQuestions);
        System.out.println("number of matched:\t" + numMatchedQuestions);
    }


    public static void main(String[] args) throws IOException {
        // Read annotations.
        baseLeaner = new POMDP(nBest, horizon, moneyPenalty);
        try {
            annotations = CrowdFlowerDataReader.readAggregatedAnnotationFromFile(annotationFilePath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        sentenceIds = annotations.stream()
                .map(annot -> annot.sentenceId)
                .distinct().sorted()
                .collect(Collectors.toList());

        rerankF1 = new ArrayList<>();
        rerankF1Affected = new ArrayList<>();
        oracleF1 = new ArrayList<>();
        baselineF1 = new ArrayList<>();
        baselineF1Affected = new ArrayList<>();
        for (int numTrainingSents : trials) {
            rerankF1.add(new ArrayList<>());
            rerankF1Affected.add(new ArrayList<>());
            oracleF1.add(new ArrayList<>());
            baselineF1.add(new ArrayList<>());
            baselineF1Affected.add(new ArrayList<>());
            for (int t = 0; t < numRandomRuns; t++) {
                runExperiment(numTrainingSents);
            }
        }
        for (int i = 0; i < trials.length; i++) {
            System.out.println(trials[i]);
            printResults(baselineF1.get(i));
            printResults(rerankF1.get(i));
            printResults(oracleF1.get(i));
            printResults(baselineF1Affected.get(i));
            printResults(rerankF1Affected.get(i));
        }
    }

    private static void printResults(List<Double> results) {
        double avg = results.stream().mapToDouble(r -> r).average().getAsDouble();
        double std = Math.sqrt(results.stream().mapToDouble(r -> (r - avg)).map(r2 -> r2 * r2).sum() / results.size());
        System.out.println(avg + "\t" + std);
    }
}
