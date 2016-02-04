package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * New sandbox :)
 * Created by luheng on 2/2/16.
 */
public class SimulatedExperiment {
    static ActiveLearning learner;
    static final int nBest = 50;
    static final int reorderQueriesEvery = 100;
    static final boolean verbose = true;

    public static void main(String[] args) {
        learner = new ActiveLearning(nBest);
        ResponseSimulator responseSimulator = new ResponseSimulatorGold(learner.goldParses, new QuestionGenerator());
        Map<Integer, Results> budgetCurve = new HashMap<>();

        int queryCounter = 0;
        while (learner.getNumberOfRemainingQueries() > 0) {
            GroupedQuery query = learner.getNextQueryInQueue();
            if (query.getAnswerEntropy() < 1e-3) {
                continue;
            }
            Response response = responseSimulator.answerQuestion(query);
            learner.respondToQuery(query, response);
            if (queryCounter % 200 == 0) {
                budgetCurve.put(queryCounter, learner.getRerankedF1());
            }
            if (verbose) {
                System.out.println(query.getSentence().stream().collect(Collectors.joining(" ")));
                query.print(query.getSentence(), response);
                System.out.println(response.debugInfo + "\n");
            }
            if (queryCounter > 0 && reorderQueriesEvery > 0 && queryCounter % reorderQueriesEvery == 0) {
                learner.refreshQueryList();
            }
            queryCounter ++;
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
