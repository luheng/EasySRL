package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;

/**
 * Created by luheng on 2/27/16.
 */
public class ObservationModel {
    final double fixedErrorRate = 0.0;

    double[][][] counts;

    public ObservationModel() {
    }

    /**
     * Training an observation model from annotations.
     */
    public ObservationModel(List<GroupedQuery> queries, List<Parse> goldParses, ResponseSimulator userModel,
                            ResponseSimulator goldModel) {
        // (0, 0, 0) adjunct questions, gold is NA and answer is NA
        // (0, 0, 1) adjunct questions, gold is NA and answer is max. dependency overlap.
        // (0, 0, 2) adjunct questions, gold is NA and answer is something else.
        // (1, 1, 0-2) core questions, gold is not NA.
        counts = new double[2][2][3];

        for (GroupedQuery query : queries) {
            if (userModel.answerQuestion(query).chosenOptions.size() == 0) {
                continue;
            }
            int user = userModel.answerQuestion(query).chosenOptions.get(0);
            int gold = goldModel.answerQuestion(query).chosenOptions.get(0);
            Parse goldParse = goldParses.get(query.getSentenceId());
            boolean isAdjunct = query.getCategory().equals(Category.valueOf("((S\\NP)\\(S\\NP))/NP"))
                    || query.getCategory().equals(Category.valueOf("(NP\\NP)/NP"));
            boolean goldIsNA = GroupedQuery.BadQuestionOption.class.isInstance(query.getAnswerOptions().get(gold));
            boolean userIsNA = GroupedQuery.BadQuestionOption.class.isInstance(query.getAnswerOptions().get(user));
            int maxOverlapOption = -1;
            int maxOverlap = -1;
            for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                GroupedQuery.AnswerOption option = query.getAnswerOptions().get(i);
                int depOverlap = computeDependencyOverlap(query, option, goldParse);
                if (depOverlap > maxOverlap) {
                    maxOverlapOption = i;
                    maxOverlap = depOverlap;
                }
            }
            int questionType = isAdjunct ? 0 : 1;
            int goldType = goldIsNA ? 0 : 1;
            int userResponseType = userIsNA ? 0 : (user == maxOverlapOption ? 1 : 2);
            counts[questionType][goldType][userResponseType] ++;
        }

        // Normalize.
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                normalizeCounts(counts[i][j]);
            }
        }

        System.out.println("Adjunct, gold NA:\t" + counts[0][0][0] + "\t" + counts[0][0][1]);
        System.out.println("Adjunct, gold valid:\t" + counts[0][1][0] + "\t" + counts[0][1][1]);
        System.out.println("Core, gold NA:\t" + counts[1][0][0] + "\t" + counts[1][0][1]);
        System.out.println("Core, gold valid:\t" + counts[1][1][0] + "\t" + counts[1][1][1]);
    }

    public ObservationModel(ObservationModel baseModel) {
        counts = new double[2][2][3];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 3; k++) {
                    counts[i][j][k] = baseModel.counts[i][j][k];
                }
            }
        }
    }

    private static void normalizeCounts(double[] counts) {
        double sum = .0;
        for (int i  = 0; i < counts.length; i++) {
            sum += counts[i];
        }
        for (int i  = 0; i < counts.length; i++) {
            counts[i] /= sum;
        }
    }

    /**
     * Compute p(observation | action, state)
     * @param query
     * @param response
     * @param parseId
     * @param parse
     * @return
     */
    public double getObservationProbability(GroupedQuery query, Response response, int parseId, Parse parse) {
        int user = response.chosenOptions.get(0);
        boolean userCorrect = query.getAnswerOptions().get(user).getParseIds().contains(parseId);
        int K = query.getAnswerOptions().size() - 1;
        if (counts == null) {
            return userCorrect ? 1.0 - fixedErrorRate : fixedErrorRate / K;
        } else {
            boolean isAdjunct = query.getCategory().equals("((S\\NP)\\(S\\NP))/NP")
                    || query.getCategory().equals(("(NP\\NP)/NP"));
            int maxOverlapOption = -1;
            int questionNAOption = -1;
            int maxOverlap = -1;
            for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                GroupedQuery.AnswerOption option = query.getAnswerOptions().get(i);
                if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                    questionNAOption = i;
                } else {
                    int depOverlap = computeDependencyOverlap(query, option, parse);
                    if (depOverlap > maxOverlap) {
                        maxOverlapOption = i;
                        maxOverlap = depOverlap;
                    }
                }
            }
            int questionType = isAdjunct ? 0 : 1;
            int parseType = query.getAnswerOptions().get(questionNAOption).getParseIds().contains(parseId) ? 0 : 1;
            int userType = user == questionNAOption ? 0 : (user == maxOverlapOption ? 1 : 2);

            if (user == questionNAOption) {
                return counts[questionType][parseType][0];
            } else if (user == maxOverlapOption) {
                return counts[questionType][parseType][1];
            } else {
                return counts[questionType][parseType][2] / (query.getAnswerOptions().size() - 2);
            }
        }
    }

    /**
     * compute number of unlabeled dependency overlap.
     * @param query
     * @param option
     * @param parse
     * @return
     */
    private int computeDependencyOverlap(GroupedQuery query, GroupedQuery.AnswerOption option, Parse parse) {
        return (int) parse.dependencies.stream().filter(dep -> dep.getHead() == query.getPredicateIndex()
                && option.getArgumentIds().contains(dep.getArgument())).count();
    }
}
