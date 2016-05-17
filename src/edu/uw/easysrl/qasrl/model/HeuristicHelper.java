package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Partitive;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Moves votes around according to heuristics.
 * Created by luheng on 5/12/16.
 */
public class HeuristicHelper {
    public static boolean fixAppositiveRestrictive = true;
    public static boolean fixCoordinatedAppositive = true;
    public static boolean fixPartitiveNP = false;
    public static boolean fixNonPartitiveNP = true;
    public static boolean fixPronoun = true;

    public static ImmutableList<ImmutableList<Integer>> adjustVotes(final ImmutableList<String> sentence,
                                                                    final ScoredQuery<QAStructureSurfaceForm> query,
                                                                    final ImmutableList<ImmutableList<Integer>> annotation) {
        if (query.getQueryType() != QueryType.Forward) {
            return annotation;
        }
        final int numAnnotators = annotation.size();
        final int numOptions = query.getOptions().size();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();

        int[][] adjustedVotes = new int[numAnnotators][numOptions];
        int[] votes = new int[numOptions];
        Arrays.fill(votes, 0);
        for (int i = 0; i < numAnnotators; i++) {
            for (int j = 0; j < numOptions; j++) {
                adjustedVotes[i][j] = annotation.get(i).contains(j) ? 1 : 0;
                votes[j] += adjustedVotes[i][j];
            }
        }

        // Look for best pronoun.
        int minPronounPredicateDist = sentence.size();
        int bestPronounOpId = -1;
        for (int opId : IntStream.range(0, numQAs).toArray()) {
            final String op = query.getOptions().get(opId).toLowerCase();
            final int argId = query.getQAPairSurfaceForms().get(opId).getAnswerStructures().get(0).argumentIndices.get(0);
            final int dist = Math.abs(predicateId - argId);
            if (PronounList.englishPronounSet.contains(op) && votes[opId] > 0 && dist < minPronounPredicateDist) {
                bestPronounOpId = opId;
                minPronounPredicateDist = dist;
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

                if (fixAppositiveRestrictive) {
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

                if (fixCoordinatedAppositive) {
                    // Other appositives:
                    // [op1], [op2], [pred] or [pred] [op1], [op2]: v(op1) -> v(op1, op2)
                    if (sentenceStr.contains(op1 + " , " + op2) && arg2StartsWithDet && votes[opId1] > 0 &&
                            votes[opId2] > 0) {
                        //System.err.println(sentenceStr + "\n" + op1 + "\n" + op2);
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                        }
                    }
                    /*
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
                    */
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
                    // TODO: consider predicate type
                    // op1 = X of op2: v(op2) -> v(op1)
                    if (op1.contains(" of " + op2) && !isPartitive && commaBetweenArg2Pred > 0 && votes[opId1] > 0
                            && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId1] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId2] = 0;
                        }
                        continue;
                    }
                    // op1 = op2 and X: v(op1, op2) -> max v(op1, op2)
                    if (op1.contains(op2 + " and ") && votes[opId1] > 0 && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId1] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                        }
                        continue;
                    }
                    // op1 = op2 pp X, pp != of: v(op1) -> v(op2)
                    for (String pp : Prepositions.prepositionWords) {
                        if (!pp.equals("of") && op1.contains(op2 + " " + pp + " ") && votes[opId1] > 0
                                && votes[opId2] > 0) {
                            for (int i = 0; i < numAnnotators; i++) {
                                adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                                adjustedVotes[i][opId1] = 0;
                            }
                            break;
                        }
                    }
                }

                if (fixPronoun) {
                    final int dist = Math.abs(argId1 - predicateId);
                    if (opId2 == bestPronounOpId && dist > minPronounPredicateDist) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId1] = 0;
                        }
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