package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/30/16.
 */
public class HeuristicHelperParserPrior {
    public static boolean fixRelative = true;
    public static boolean fixAppositive = true;
    public static boolean fixSubspan = true;
    public static boolean fixPronoun = true;

    public static ImmutableList<ImmutableList<Integer>> adjustVotes(final ImmutableList<String> sentence,
                                                                    final ScoredQuery<QAStructureSurfaceForm> query,
                                                                    final ImmutableList<ImmutableList<Integer>> annotation) {

        final int numAnnotators = annotation.size();
        final int numOptions = query.getOptions().size();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();

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
                }
                boolean arg1IsCapitalized = !query.getOptions().get(opId1).equals(op1);
                boolean arg2StartsWithDet = Stream.of("a ", "an ", "the ").anyMatch(op2::startsWith);
                final int copulaBetweenArgs = (int) IntStream.range(argId1, argId2)
                        .mapToObj(sentence::get)
                        .filter(VerbHelper::isCopulaVerb)
                        .count();
                final boolean trailingWhoThat = (sentenceStr.contains(op2 + " who ")
                        || sentenceStr.contains(op2 + " that ")) && Math.abs(predicateId - argId2) <= 3;

                if (fixRelative) {
                    // Appositive-restrictive:
                    // [op1], [(a/the) op2] ...who/that/which [pred]  v(op1) -> v(op2)
                    if ((copulaBetweenArgs == 1|| commaBetweenArgs == 1)
                            && votes[opId1] > 0 && votes[opId2] > 0
                            && arg2StartsWithDet && trailingWhoThat
                            && argId2 < predicateId
                            && commaBetweenArg2Pred == 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId1] = 0;
                        }
                        continue;
                    }
                }

                if (fixAppositive) {
                    if ((sentenceStr.contains(op1 + ", " + op2) || sentenceStr.contains(op1 + "., " + op2))
                            && votes[opId1] > 0 && votes[opId2] > 0
                            && arg1IsCapitalized && arg2StartsWithDet
                            && argId2 < predicateId
                            && commaBetweenArg2Pred > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                        }
                    }
                }

                if (fixSubspan) {
                    if (op1.endsWith("\'s " + op2) && votes[opId1] > 0 && votes[opId2] > 0) {
                        for (int i = 0; i < numAnnotators; i++) {
                            adjustedVotes[i][opId2] = Math.max(adjustedVotes[i][opId1], adjustedVotes[i][opId2]);
                            adjustedVotes[i][opId1] = 0;
                        }
                        continue;
                    }
                    for (String pp : Prepositions.prepositionWords) {
                        if (!pp.equals("of") && op1.contains(op2 + " " + pp + " ")
                                && votes[opId1] > 0 && votes[opId2] > 0) {
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
