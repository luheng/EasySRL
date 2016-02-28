package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by luheng on 2/27/16.
 */
public class SimulatedExperimentPOMDP {
    static final int nBest = 100;
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

        POMDP learner = new POMDP(nBest);
        POMDP goldLearner = new POMDP(learner);

        assert annotations != null;
        ResponseSimulator responseSimulator = new ResponseSimulatorRecorded(annotations);
        ResponseSimulator responseSimulatorGold = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator(),
                false /* allow label match */);

        Results rerank = new Results(),
                oracle = new Results(),
                goldRerank = new Results(),
                onebest = new Results();

        // Process other questions.
        for (int sentenceId : sentenceIds) {
            learner.initializeForSentence(sentenceId, annotations);
            //goldLearner.initializeForSentence(sentenceId);
            Optional<GroupedQuery> action;
            while ((action = learner.generateAction()).isPresent()) {
                Response userResponse = responseSimulator.answerQuestion(action.get());
                Response goldResponse = responseSimulatorGold.answerQuestion(action.get());

                boolean matchesGold = userResponse.chosenOptions.size() > 0 &&
                        (userResponse.chosenOptions.get(0).intValue() == goldResponse.chosenOptions.get(0).intValue());
                if (userResponse.chosenOptions.size() == 0) {
                    numUnmatchedQuestions ++;
                } else {
                    numMatchedQuestions ++;
                    answerAcc.add(matchesGold);
                    learner.receiveObservation(userResponse);
                    /*goldLearner.updateBelief(query, goldResponse);
                    if (!matchesGold) {
                        System.out.println(query.getDebuggingInfo(userResponse, goldResponse) + "\n");
                    }*/
                }
            }

            rerank.add(learner.getRerankedF1(sentenceId));
            oracle.add(learner.getOracleF1(sentenceId));
            onebest.add(learner.getOneBestF1(sentenceId));
            //goldRerank.add(goldLearner.getRerankedF1(sentenceId));
        }

        System.out.println("onebest:\t" + onebest);
        System.out.println("rerank:\t" + rerank);
        // System.out.println("gold rerank:\t" + goldRerank);
        System.out.println("oracle:\t" + oracle);

        System.out.println("answer accuracy:\t" + answerAcc);
        System.out.println("number of unmatched:\t" + numUnmatchedQuestions);
        System.out.println("number of matched:\t" + numMatchedQuestions);
    }
}
