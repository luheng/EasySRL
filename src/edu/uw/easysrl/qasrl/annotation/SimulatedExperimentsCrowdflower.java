package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 1. Read AlignedAnnotations.
 * 2. Run simulated experiment - unsorted - read queries by sentence ID and answer those queries.
 * 3. Get averaged results.
 * Created by luheng on 2/24/16.
 */
public class SimulatedExperimentsCrowdflower {

    static final int nBest = 100;
    private static final double minQuestionConfidence = 0.1;
    private static final double minAnswerEntropy = 0.1;

    private static boolean skipBinaryQueries = true;

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
            annotations = CrowdFlowerDataReader.readAggregatedAnnotationFromFile(annotationFilePath,
                    false /* check box */);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        List<Integer> sentenceIds = annotations.stream()
                .map(annot -> annot.sentenceId)
                .distinct().sorted()
                .collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        ActiveLearningBySentence learner = new ActiveLearningBySentence(nBest);
        learner.initializeQueryPool(sentenceIds);
        ActiveLearningBySentence goldLearner = new ActiveLearningBySentence(learner);

        assert annotations != null;
        ResponseSimulator responseSimulator = new ResponseSimulatorRecorded(annotations);
        ResponseSimulator responseSimulatorGold = new ResponseSimulatorGold(learner.goldParses,
                false /* allow label match */);

        Results rerank = new Results(),
                oracle = new Results(),
                goldRerank = new Results(),
                onebest = new Results();

        // Process other questions.
        for (int sentenceId : sentenceIds) {
            //final List<String> sentence = learner.getSentenceById(sentenceId);
            List<GroupedQuery> queries = learner.getQueriesBySentenceId(sentenceId).stream()
                    .filter(query -> query.answerEntropy > minAnswerEntropy
                            && query.questionConfidence > minQuestionConfidence
                            && (!skipBinaryQueries || query.attachmentUncertainty > 1e-6))
                    .collect(Collectors.toList());

            for (GroupedQuery query : queries) {
                // TODO: put trust info in debugging string.
                Response userResponse = responseSimulator.answerQuestion(query);
                Response goldResponse = responseSimulatorGold.answerQuestion(query);

                boolean matchesGold = userResponse.chosenOptions.size() > 0 &&
                        (userResponse.chosenOptions.get(0).intValue() == goldResponse.chosenOptions.get(0).intValue());
                if (userResponse.chosenOptions.size() == 0) {
                    numUnmatchedQuestions ++;
                } else {
                    numMatchedQuestions ++;
                    answerAcc.add(matchesGold);

                    //if (matchesGold) {
                    learner.respondToQuery(query, userResponse);
                    //  }
                    if (!matchesGold) {
                        System.out.println(query.getDebuggingInfo(userResponse, goldResponse) + "\n");
                    }
                }
                goldLearner.respondToQuery(query, goldResponse);
            }

            rerank.add(learner.getRerankedF1(sentenceId));
            oracle.add(learner.getOracleF1(sentenceId));
            onebest.add(learner.getOneBestF1(sentenceId));
            goldRerank.add(goldLearner.getRerankedF1(sentenceId));
        }

        System.out.println("onebest:\t" + onebest);
        System.out.println("rerank:\t" + rerank);
        System.out.println("gold rerank:\t" + goldRerank);
        System.out.println("oracle:\t" + oracle);

        System.out.println("answer accuracy:\t" + answerAcc);
        System.out.println("number of unmatched:\t" + numUnmatchedQuestions);
        System.out.println("number of matched:\t" + numMatchedQuestions);
    }
}

