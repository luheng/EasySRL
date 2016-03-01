package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by luheng on 2/27/16.
 */
public class ObservationModel {
    final double fixedErrorRate = 0.0;

    double[][][] observation;
    double[][] counts;

    public ObservationModel() {
    }

    /**
     * Training an observation model from annotations.
     */
    public ObservationModel(List<GroupedQuery> queries, List<Parse> goldParses, ResponseSimulator userModel,
                            ResponseSimulator goldModel) {
        // (0, 0, 0) adjunct questions, gold is NA and answer is "question not valid".
        // (0, 0, 1) adjunct questions, gold is NA and answer is "answer not listed".
        // (0, 0, 2) adjunct questions, gold is NA and answer is max. dependency overlap.
        // (0, 0, 3) adjunct questions, gold is NA and answer is something else.
        // (1, 1, 0-2) core questions, gold is not NA.
        observation = new double[2][2][5];
        counts = new double[2][2];

        for (GroupedQuery query : queries) {
            Response userResponse = userModel.answerQuestion(query);
            if (userResponse.chosenOptions.size() == 0) {
                continue;
            }
            int user = userResponse.chosenOptions.get(0);
            int gold = goldModel.answerQuestion(query).chosenOptions.get(0);
            Parse goldParse = goldParses.get(query.getSentenceId());
            boolean isAdjunct = query.getCategory().equals(Category.valueOf("((S\\NP)\\(S\\NP))/NP"))
                    || query.getCategory().equals(Category.valueOf("(NP\\NP)/NP"));
            boolean goldIsNA = GroupedQuery.BadQuestionOption.class.isInstance(query.getAnswerOptions().get(gold));
            GroupedQuery.AnswerOption userOption = query.getAnswerOptions().get(user);
            Set<Integer> maxOverlapOptions = new HashSet<>();
            int maxOverlap = -1;
            for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                GroupedQuery.AnswerOption option = query.getAnswerOptions().get(i);
                if (!option.isNAOption()) {
                    int depOverlap = computeDependencyOverlap(query, option, goldParse);
                    if (depOverlap > maxOverlap) {
                        maxOverlapOptions = new HashSet<>();
                        maxOverlap = depOverlap;
                    }
                    if (depOverlap == maxOverlap) {
                        maxOverlapOptions.add(i);
                    }
                }
            }
            int questionType = isAdjunct ? 0 : 1;
            int goldType = goldIsNA ? 0 : 1;
            int userResponseType;
            if (GroupedQuery.BadQuestionOption.class.isInstance(userOption)) {
                userResponseType = 0;
            } else if (GroupedQuery.NoAnswerOption.class.isInstance(userOption)) {
                userResponseType = 1;
            } else if (user == gold) {
                userResponseType = 2;
            } else if (maxOverlap > 0 && maxOverlapOptions.contains(user)){
                userResponseType = 3;
            } else {
                userResponseType = 4;
            }
            observation[questionType][goldType][userResponseType] += userResponse.trust;
            counts[questionType][goldType] ++;
        }
        // Normalize.
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                normalizeCounts(observation[i][j]);
            }
        }
        // Print.
        System.out.println("Adjunct, gold NA:\t"    + counts[0][0] + "\t" + observation[0][0][0] + "\t" + observation[0][0][1] + "\t" + observation[0][0][2] + "\t" + observation[0][0][3]);
        System.out.println("Adjunct, gold valid:\t" + counts[0][1] + "\t" + observation[0][1][0] + "\t" + observation[0][1][1] + "\t" + observation[0][1][2] + "\t" + observation[0][1][3]);
        System.out.println("Core, gold NA:\t"       + counts[1][0] + "\t" + observation[1][0][0] + "\t" + observation[1][0][1] + "\t" + observation[1][0][2] + "\t" + observation[1][0][3]);
        System.out.println("Core, gold valid:\t"    + counts[1][1] + "\t" + observation[1][1][0] + "\t" + observation[1][1][1] + "\t" + observation[1][1][2] + "\t" + observation[1][1][3]);
    }

    public ObservationModel(ObservationModel baseModel) {
        observation = new double[2][2][5];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 5; k++) {
                    observation[i][j][k] = baseModel.observation[i][j][k];
                }
            }
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
        GroupedQuery.AnswerOption userOption = query.getAnswerOptions().get(user);
        if (observation == null) {
            boolean userCorrect = query.getAnswerOptions().get(user).getParseIds().contains(parseId);
            int K = query.getAnswerOptions().size() - 1;
            return userCorrect ? 1.0 - fixedErrorRate : fixedErrorRate / K;
        } else {
            boolean isAdjunct = query.getCategory().equals(Category.valueOf("((S\\NP)\\(S\\NP))/NP"))
                    || query.getCategory().equals(Category.valueOf("(NP\\NP)/NP"));
            boolean parseIsNA = false;
            int maxOverlap = -1;
            Set<Integer> maxOverlapOptions = new HashSet<>();
            for (int i = 0; i < query.getAnswerOptions().size(); i++) {
                GroupedQuery.AnswerOption option = query.getAnswerOptions().get(i);
                if (GroupedQuery.BadQuestionOption.class.isInstance(option) && option.getParseIds().contains(parseId)) {
                    parseIsNA = true;
                } else if (!option.isNAOption()){
                    int depOverlap = computeDependencyOverlap(query, option, parse);
                    if (depOverlap > 0) {
                        if (depOverlap > maxOverlap) {
                            maxOverlapOptions = new HashSet<>();
                            maxOverlap = depOverlap;
                        }
                        if (depOverlap == maxOverlap) {
                            maxOverlapOptions.add(i);
                        }
                    }
                }
            }
            int questionType = isAdjunct ? 0 : 1;
            int parseType = parseIsNA ? 0 : 1;
            boolean perfectMatchHasMaxOverlap = false;
            for (int i : maxOverlapOptions) {
                if (query.getAnswerOptions().get(i).getParseIds().contains(parseId)) {
                    perfectMatchHasMaxOverlap = true;
                    break;
                }
            }
            if (GroupedQuery.BadQuestionOption.class.isInstance(userOption)) {
                return observation[questionType][parseType][0];
            } else if (GroupedQuery.NoAnswerOption.class.isInstance(userOption)) {
                return observation[questionType][parseType][1];
            } else if (userOption.getParseIds().contains(parseId)) {
                return observation[questionType][parseType][2];
            } else if (maxOverlapOptions.contains(user)) {
                int K = maxOverlapOptions.size() - (perfectMatchHasMaxOverlap ? 1 : 0);
                // System.out.println("Num. max overlap:\t" + K);
                return observation[questionType][parseType][3] / K;
            } else {
                int K = query.getAnswerOptions().size() - maxOverlapOptions.size() - (perfectMatchHasMaxOverlap ? 0 : 1) - 2;
                // FIXME: why?
                if (K <= 0) {
                    K = 1;
                }
                /* if (numOtherOptions > 2) {
                    System.out.println(query.getDebuggingInfo(response));
                }
                */
                //System.out.println("Num other:\t" + K);
                return observation[questionType][parseType][4] / K;
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
        if (option.isNAOption()) {
            return 0;
        }
        return (int) parse.dependencies.stream().filter(dep -> dep.getHead() == query.getPredicateIndex()
                && option.getArgumentIds().contains(dep.getArgument())).count();
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
}
