package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Group query by sentences.
 * Created by luheng on 2/9/16.
 */
public class SimulatedExperimentBySentence {
    static ActiveLearningBySentence learner;
    static final int nBest = 50;
    static final int maxNumQueries = 20000;

    public static void main(String[] args) {
        learner = new ActiveLearningBySentence(nBest);
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator(),
                true /* Allow label match */);
        Map<Integer, Results> rerankCurve = new HashMap<>(),
                              oracleCurve = new HashMap<>(),
                              oneBestCurve = new HashMap<>();

        int queryCounter = 0;
        int sentenceCounter = 0;
        List<Integer> sentenceIds = new ArrayList<>();

        while (learner.getNumRemainingSentences() > 0) {
            int sentenceId = learner.getCurrentSentenceId();
            sentenceIds.add(sentenceId);

            // Print debugging info.
            if (sentenceCounter < 200) {
                System.out.println("SID=" + sentenceId + "\t" + learner.getSentenceScore(sentenceId));
                System.out.println(learner.getSentenceById(sentenceId).stream().collect(Collectors.joining(" ")));
                //System.out.println();
                //learner.printQueriesBySentenceId(sentenceId);
                //System.out.println();
            }

            Optional<GroupedQuery> optQuery;
            while ((optQuery = learner.getNextQuery()).isPresent()) {
                GroupedQuery query = optQuery.get();
                Response response = responseSimulator.answerQuestion(query);
                queryCounter ++;
                // Print debugging info.
                if (sentenceCounter < 200) {
                    System.out.println("query confidence:\t" + query.questionConfidence);
                    System.out.println("attachment uncertainty:\t" + query.attachmentUncertainty);
                    query.print(query.getSentence(), response);
                }
                learner.respondToQuery(query, response);
                learner.refereshQueryQueue();
                if (sentenceCounter < 200) {
                    learner.printQueriesBySentenceId(sentenceId);
                    System.out.println();
                }
            }
            sentenceCounter ++;
            if (sentenceCounter % 5 == 0) {
                rerankCurve.put(sentenceCounter, learner.getRerankedF1(sentenceIds));
                oracleCurve.put(sentenceCounter, learner.getOracleF1(sentenceIds));
                oneBestCurve.put(sentenceCounter, learner.getOneBestF1(sentenceIds));
            }

            // Print debugging info.
            if (sentenceCounter < 200) {
                System.out.println("[1-best]\n" + learner.getOneBestF1(sentenceId));
                System.out.println("[rerank]\n" + learner.getRerankedF1(sentenceId));
                System.out.println("[oracle]\n" + learner.getOracleF1(sentenceId) + "\n");
            }
            if (queryCounter >= maxNumQueries) {
                break;
            }
            learner.switchToNextSentence();
        }

        System.out.println("[1-best]:\t" + learner.getOneBestF1());
        System.out.println("[reranked]:\t" + learner.getRerankedF1());
        System.out.println("[oracle]:\t" + learner.getOracleF1());
        List<Integer> axis = rerankCurve.keySet().stream().sorted().collect(Collectors.toList());
        axis.forEach(i -> System.out.print("\t" + i));
        System.out.println();
        axis.forEach(i -> System.out.print("\t" + String.format("%.3f", oneBestCurve.get(i).getF1() * 100.0)));
        System.out.println();
        axis.forEach(i -> System.out.print("\t" + String.format("%.3f", rerankCurve.get(i).getF1() * 100.0)));
        System.out.println();
        axis.forEach(i -> System.out.print("\t" + String.format("%.3f", oracleCurve.get(i).getF1() * 100.0)));
        System.out.println();
    }
}
