package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.corpora.GreedyAnswerAligner;
import edu.uw.easysrl.qasrl.pomdp.ObservationModel;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 2/29/16.
 */
public class CrowdsourcingErrorAnalysis {
    private static final int nBest = 100;
    private static final int maxNumOptionsPerQuestion = 6;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }
    private static final String annotationFilePath = "./Crowdflower_data/f878213.csv";

    static Accuracy answerAcc = new Accuracy();
    static int numUnmatchedQuestions = 0, numMatchedQuestions = 0;

    public static void main(String[] args) {
        Map<Integer, List<AlignedAnnotation>> annotations = loadData(annotationFilePath);
        assert annotations != null;

        printOneStepAnalysis(annotations);
    }

    private static Map<Integer, List<AlignedAnnotation>> loadData(String fileName) {
        Map<Integer, List<AlignedAnnotation>> sentenceToAnnotations;
        List<AlignedAnnotation> annotationList;
        try {
            annotationList = CrowdFlowerDataReader.readAggregatedAnnotationFromFile(fileName);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        sentenceToAnnotations = new HashMap<>();
        annotationList.forEach(annotation -> {
            int sentId = annotation.sentenceId;
            if (!sentenceToAnnotations.containsKey(sentId)) {
                sentenceToAnnotations.put(sentId, new ArrayList<>());
            }
            sentenceToAnnotations.get(sentId).add(annotation);
        });
        return sentenceToAnnotations;
    }

    private static void printOneStepAnalysis(Map<Integer, List<AlignedAnnotation>> annotations) {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        // Learn a observation model.
        POMDP learner = new POMDP(nBest, 1000, 0.0);
        ResponseSimulator goldSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator());
        for (int sentenceId : sentenceIds) {
            learner.initializeForSentence(sentenceId, annotations.get(sentenceId));
            final List<GroupedQuery> queries = learner.getQueryPool();
            for (GroupedQuery query : queries) {
                int[] optionDist = getUserResponses(query, annotations.get(sentenceId));
                if (optionDist == null) {
                    continue;
                }
                int goldOption  = goldSimulator.answerQuestion(query).chosenOptions.get(0);
                Results baselineF1 = learner.getOneBestF1(sentenceId);
                Results oracleF1 = learner.getOracleF1(sentenceId);
                Results[] rerankedF1 = new Results[optionDist.length];
                for (int j = 0; j < optionDist.length; j++) {
                    learner.resetBeliefModel();
                    query.computeProbabilities(learner.beliefModel.belief);
                    learner.receiveObservationForQuery(query, new Response(j));
                    rerankedF1[j] = learner.getRerankedF1(sentenceId);
                }
                // Print.
                String sentenceStr = query.getSentence().stream().collect(Collectors.joining(" "));
                int predId = query.getPredicateIndex();
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t"
                        + query.getQuestionKey() + "\n" + query.getQuestion() + "\n";
                result += String.format("Baseline:\t%.3f%%\tOracle:%.3f%%\n", 100.0 * baselineF1.getF1(),
                        100.0 * oracleF1.getF1());
                for (int j = 0; j < optionDist.length; j++) {
                    String match = "";
                    for (int k = 0; k < optionDist[j]; k++) {
                        match += "*";
                    }
                    if (j == goldOption) {
                        match += "G";
                    }
                    String improvement = " ";
                    if (rerankedF1[j].getF1() < baselineF1.getF1() - 1e-8) {
                        improvement = "-";
                    } else if (rerankedF1[j].getF1() > baselineF1.getF1() + 1e-8) {
                        improvement = "+";
                    }
                    GroupedQuery.AnswerOption option = query.getAnswerOptions().get(j);
                    result += String.format("%-8s\t%d\t%.3f\t%-40s\t%.3f%%\t%s\n", match, j,
                            option.getProbability(), option.getAnswer(), 100.0 * rerankedF1[j].getF1(),
                            improvement);
                }
                System.out.println(result);
            }
        }
    }

    private static void printSequentialAnalysis(Map<Integer, List<AlignedAnnotation>> annotations) {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));

        // Learn a observation model.
        POMDP learner = new POMDP(nBest, 1000, 0.0);
        ResponseSimulator goldSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator());
        for (int sentenceId : sentenceIds) {
            learner.initializeForSentence(sentenceId, annotations.get(sentenceId));
            final List<GroupedQuery> queries = learner.getQueryPool();
            for (GroupedQuery query : queries) {
                int[] optionDist = getUserResponses(query, annotations.get(sentenceId));
                if (optionDist == null) {
                    continue;
                }
                int goldOption  = goldSimulator.answerQuestion(query).chosenOptions.get(0);
                Results baselineF1 = learner.getOneBestF1(sentenceId);
                Results oracleF1 = learner.getOracleF1(sentenceId);
                Results[] rerankedF1 = new Results[optionDist.length];

                // Get best option.
                int bestOption = 0;
                for (int j = 1; j < optionDist.length; j++) {
                    if (optionDist[j] > optionDist[bestOption]) {
                        bestOption = j;
                    }
                }
                // Print.
                String sentenceStr = query.getSentence().stream().collect(Collectors.joining(" "));
                int predId = query.getPredicateIndex();
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t"
                        + query.getQuestionKey() + "\n" + query.getQuestion() + "\n";
                for (int j = 0; j < optionDist.length; j++) {
                    String match = "";
                    for (int k = 0; k < optionDist[j]; k++) {
                        match += "*";
                    }
                    if (j == goldOption) {
                        match += "G";
                    }
                    String improvement = "";
                    if (rerankedF1[j].getF1() < baselineF1.getF1() - 1e-8) {
                        improvement = "-";
                    } else if (rerankedF1[j].getF1() > baselineF1.getF1() + 1e-8) {
                        improvement = "+";
                    }
                    GroupedQuery.AnswerOption option = query.getAnswerOptions().get(j);
                    result += String.format("%-8s\t%d\t%.3f\t%-40s\t%.3f%%\t%s\n", match, j,
                            option.getProbability(), option.getAnswer(), 100.0 * rerankedF1[j].getF1(),
                            improvement);
                }
                result += String.format("Baseline:\t%.3f%%\tOracle:%.3f%%", 100.0 * baselineF1.getF1(),
                        100.0 * oracleF1.getF1());
                System.out.println(result);
            }
        }
    }

    private static int[] getUserResponses(GroupedQuery query, List<AlignedAnnotation> annotations) {
        String qkey =query.getPredicateIndex() + "\t" + query.getQuestion();
        int numOptions = query.getAnswerOptions().size();
        int[] optionDist = new int[numOptions];
        Arrays.fill(optionDist, 0);
        boolean matchedAnnotation = false;
        for (AlignedAnnotation annotation : annotations) {
            String qkey2 = annotation.predicateId + "\t" + annotation.question;
            if (qkey.equals(qkey2)) {
                for (int i = 0; i < numOptions; i++) {
                    for (int j = 0; j < annotation.answerStrings.size(); j++) {
                        if (query.getAnswerOptions().get(i).getAnswer().equals(annotation.answerStrings.get(j))) {
                            optionDist[i] += annotation.answerDist[j];
                            break;
                        }
                    }
                }
                matchedAnnotation = true;
                break;
            }
        }
        return matchedAnnotation ? optionDist : null;
    }
}
