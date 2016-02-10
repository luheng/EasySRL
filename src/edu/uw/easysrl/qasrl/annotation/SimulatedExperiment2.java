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
public class SimulatedExperiment2 {
    static ActiveLearning2 learner;
    static final int nBest = 50;
    static final int maxNumQueries = 20000;

    public static void main(String[] args) {
        learner = new ActiveLearning2(nBest);
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator(),
                true /* Allow label match */);
        Map<Integer, Results> budgetCurve = new HashMap<>();

        int queryCounter = 0;
        int sentenceCounter = 0;
        // TODO: print accuracy before and after for each sentence.
        // TODO: print sentence ID
        while (learner.getNumberOfRemainingSentences() > 0) {
            int sentenceId = learner.getNextSentenceInQueue();
            Map<Integer, List<GroupedQuery>>  queries = learner.getQueryBySentenceId(sentenceId);
            List<Integer> predicates = queries.keySet().stream().sorted().collect(Collectors.toList());

            // Print debugging info.
            if (sentenceCounter < 200) {
                System.out.println("SID=" + sentenceId + "\t" + learner.getSentenceScore(sentenceId));
                System.out.println(learner.getSentenceById(sentenceId).stream().collect(Collectors.joining(" ")));
                System.out.println();
                learner.printQueriesBySentenceId(sentenceId);
                System.out.println();
            }

            for (int predId : predicates) {
                for (GroupedQuery query : queries.get(predId)) {
                    Response response = responseSimulator.answerQuestion(query);
                    learner.respondToQuery(query, response);
                    learner.updateQueryScoresBySentenceId(sentenceId);

                    // TODO: update query scores.
                    queryCounter ++;
                    if (queryCounter % 200 == 0) {
                        budgetCurve.put(queryCounter, learner.getRerankedF1());
                    }
                    // Print debugging info.
                    if (sentenceCounter < 200) {
                        System.out.println("query confidence:\t" + query.questionConfidence);
                        System.out.println("attachment uncertainty:\t" + query.attachmentUncertainty);
                        query.print(query.getSentence(), response);

                        learner.printQueriesBySentenceId(sentenceId);
                        System.out.println();
                    }
                }
            }

            // Print debugging info.
            if (sentenceCounter < 200) {
                System.out.println("[1-best]\n" + learner.getOneBestF1(sentenceId));
                System.out.println("[rerank]\n" + learner.getRerankedF1(sentenceId));
                System.out.println("[oracle]\n" + learner.getOracleF1(sentenceId) + "\n");
            }
            sentenceCounter ++;
            if (queryCounter >= maxNumQueries) {
                break;
            }
        }

        System.out.println("[1-best]:\t" + learner.getOneBestF1());
        System.out.println("[reranked]:\t" + learner.getRerankedF1());
        System.out.println("[oracle]:\t" + learner.getOracleF1());
        budgetCurve.keySet().stream().sorted().forEach(i -> System.out.print("\t" + i));
        System.out.println();
        budgetCurve.keySet().stream().sorted().forEach(i -> System.out.print("\t" +
                String.format("%.3f", budgetCurve.get(i).getF1() * 100.0)));
        System.out.println();
    }
}
