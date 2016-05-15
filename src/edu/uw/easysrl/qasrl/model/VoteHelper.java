package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Partitive;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Moves votes around according to heuristics.
 * Created by luheng on 5/12/16.
 */
public class VoteHelper {
    public static boolean fixAppostiveRestrictive = false;
    public static boolean fixCoordinatedAppostive = false;
    public static boolean fixPartitiveNP = false;
    public static boolean fixNonPartitiveNP = false;
    public static boolean fixPronoun = true;

    public static ImmutableList<ImmutableList<Integer>> adjustVotes(final ImmutableList<String> sentence,
                                                                    final ScoredQuery<QAStructureSurfaceForm> query,
                                                                    final ImmutableList<ImmutableList<Integer>> annotation) {
        final int numAnnotators = annotation.size();
        final int numOptions = query.getOptions().size();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();

        int[][] adjustedVotes = new int[numAnnotators][numOptions];
        int[] votes = new int[numOptions];
        Arrays.fill(votes, 0);
        for (int i = 0; i < numAnnotators; i++) {
            for (int j = 0; j < numOptions; j++) {
                adjustedVotes[i][j] = annotation.get(i).contains(j) ? 1 : 0;
                votes[j] += adjustedVotes[i][j];
            }
        }

        for (int opId1 : IntStream.range(0, numQAs).toArray()) {
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final String op1 = query.getOptions().get(opId1).toLowerCase();
                final String op2 = query.getOptions().get(opId2).toLowerCase();
                final int argId1 = query.getQAPairSurfaceForms().get(opId1).getAnswerStructures().get(0).argumentIndices.get(0);
                final int argId2 = query.getQAPairSurfaceForms().get(opId2).getAnswerStructures().get(0).argumentIndices.get(0);

                int commaBetweenArgs = 0, commaBetweenPredArg1 = 0, commaBetweenArg2Pred = 0;
                boolean clauseTokenBetweenArg2Pred = false;
                int otherVotes = (int) IntStream.range(0, numQAs)
                        .filter(i -> i != opId1 && i != opId2)
                        .mapToLong(i -> votes[i])
                        .sum();
                for (int i = argId1 + 1; i < argId2; i++) {
                    if (sentence.get(i).equals(",")) {
                        commaBetweenArgs++;
                    }
                }
                for (int i = predicateId + 1; i < argId1; i++) {
                    if (sentence.get(i).equals(",")) {
                        commaBetweenPredArg1++;
                    }
                }
                for (int i = argId2 + 1; i < predicateId; i++) {
                    if (sentence.get(i).equals(",")) {
                        commaBetweenArg2Pred++;
                    }
                    if (ImmutableSet.of("that", "which", "who").contains(sentence.get(i).toLowerCase())) {
                        clauseTokenBetweenArg2Pred = true;
                    }
                }
                boolean arg2HasYearsOld = sentence.get(argId2).equalsIgnoreCase("years") && argId2 < sentence.size()
                        && sentence.get(argId2 + 1).equalsIgnoreCase("old");
                boolean arg2StartsWithDet = Stream.of("a ", "an ", "the ").anyMatch(op2::startsWith);

                if (fixAppostiveRestrictive) {
                    // Appositive-restrictive:
                    // [op1], [(a/the) op2] ...who/that/which [pred]  v(op1) -> v(op2)
                    if (argId1 < argId2 && argId2 < predicateId && commaBetweenArgs == 1 && commaBetweenArg2Pred == 0 &&
                            arg2StartsWithDet && votes[opId1] > 0 && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId1] = 0;
                        }
                        continue;
                    }
                }

                if (fixCoordinatedAppostive) {
                    // Other appositives:
                    // [op1], [op2], [pred] or [pred] [op1], [op2]: v(op1) -> v(op1, op2)
                    if (argId1 < argId2 && argId2 < predicateId && commaBetweenArgs == 1 && commaBetweenArg2Pred == 1
                            && arg2StartsWithDet && !arg2HasYearsOld && votes[opId1] > 0 && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                        }
                    }
                    if (predicateId < argId1 && argId1 < argId2 && commaBetweenArgs == 1 && !arg2HasYearsOld
                            && arg2StartsWithDet && votes[opId1] > 0 && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                        }
                    }
                }

                // op1 = partitive of op2: v(op1) -> v(op2)
                boolean isPartitive = false;
                for (String tok : Partitive.tokens) {
                    if (op1.equals(tok + " of " + op2)) {
                        if (fixPartitiveNP && votes[opId1] > 0 && votes[opId2] > 0) {
                            for (int i = 0; i < numAnnotators; i++) {
                                adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                                adjustedVotes[i][opId1] = 0;
                            }
                        }
                        isPartitive = true;
                        break;
                    }
                }

                if (fixNonPartitiveNP) {
                    // op1 = X of op2: v(op2) -> v(op1)
                    if (op1.contains(" of " + op2) && !isPartitive && votes[opId1] > 0 && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId1] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId2] = 0;
                        }
                        continue;
                    }
                }

                if (fixPronoun) {
                    // op2[pron] [pred] / [pred] op2[pron]: v(op1) -> v(op2)
                    if (PronounList.englishPronounSet.contains(op2) && votes[opId1] > 0 && votes[opId2] > 0) {
                            //(argId2 == predicateId - 1 || argId2 == predicateId + 1)) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId1] = 0;
                        }
                        continue;
                    }
                }
            }
        }
        return IntStream.range(0, numAnnotators)
                .mapToObj(i -> IntStream.range(0, numOptions)
                        .filter(j -> adjustedVotes[i][j] > 0)
                        .boxed()
                        .collect(GuavaCollectors.toImmutableList()))
                .collect(GuavaCollectors.toImmutableList());
    }
}
