package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.pomdp.ObservationModel;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 2/27/16.
 */
public class SimulatedExperimentPOMDP {
    static final int nBest = 100;
    static final int horizon = 1000;
    static final double moneyPenalty = 0.1;

    static final int numTrainingSentences = 30;

    private static final int maxNumOptionsPerQuestion = 6;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }

    private static final String annotationFilePath = "./Crowdflower_data/f878213.csv";

    static Accuracy answerAcc = new Accuracy();
    static int numUnmatchedQuestions = 0, numMatchedQuestions = 0;

    public static void main(String[] args) throws IOException {
        // Read annotations.
        List<AlignedAnnotation> annotations;
        try {
            annotations = CrowdFlowerDataReader.readAggregatedAnnotationFromFile(annotationFilePath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        List<Integer> sentenceIds = annotations.stream()
                .map(annot -> annot.sentenceId)
                .distinct().sorted()
                .collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        // Learn a observation model.
        POMDP qgen = new POMDP(nBest, 1000, 0.0);
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

        ObservationModel observationModel = new ObservationModel(trainingQueries, qgen.goldParses, userModel, goldModel);
        POMDP learner = new POMDP(nBest, horizon, moneyPenalty);
        learner.setBaseObservationModel(observationModel);

        assert annotations != null;
        Results rerank = new Results(),
                oracle = new Results(),
                onebest = new Results();

        // Process other questions.
        List<Integer> testSentIds = sentenceIds.subList(numTrainingSentences, sentenceIds.size());
        for (int sentenceId : testSentIds) {
            learner.initializeForSentence(sentenceId, annotations);
            Optional<GroupedQuery> action;
            while ((action = learner.generateAction()).isPresent()) {
                Response userResponse = userModel.answerQuestion(action.get());
                Response goldResponse = goldModel.answerQuestion(action.get());
                // Hack.
                /*
                if (userResponse.trust < 3
                        || action.get().getCategory().equals(Category.valueOf("((S\\NP)\\(S\\NP))/NP"))
                        || action.get().getCategory().equals(Category.valueOf("(NP\\NP)/NP"))) {
                    continue;
                }*/
                boolean matchesGold = userResponse.chosenOptions.size() > 0 &&
                        (userResponse.chosenOptions.get(0).intValue() == goldResponse.chosenOptions.get(0).intValue());
                if (userResponse.chosenOptions.size() == 0) {
                    numUnmatchedQuestions ++;
                } else {
                    numMatchedQuestions ++;
                    answerAcc.add(matchesGold);
                    learner.receiveObservation(userResponse);
                    /* if (!matchesGold) {
                        System.out.println(query.getDebuggingInfo(userResponse, goldResponse) + "\n");
                    }*/
                }
            }
            rerank.add(learner.getRerankedF1(sentenceId));
            oracle.add(learner.getOracleF1(sentenceId));
            onebest.add(learner.getOneBestF1(sentenceId));
        }

        System.out.println("onebest:\t" + onebest);
        System.out.println("rerank:\t" + rerank);
        System.out.println("oracle:\t" + oracle);

        System.out.println("answer accuracy:\t" + answerAcc);
        System.out.println("number of unmatched:\t" + numUnmatchedQuestions);
        System.out.println("number of matched:\t" + numMatchedQuestions);
    }
}
