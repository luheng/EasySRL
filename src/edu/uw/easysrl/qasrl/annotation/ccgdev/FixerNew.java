package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Pronoun:    if exists a pronoun A and votes(pronoun) > 0: C+(A), C-(everything else)
 * Appositive: any A,B construction, where votes(A) is high-agreement, and votes(B) > 0: C+(A,B)
 * Relative:   any "A is/, B who/that" construction, where votes(A) > 0, and votes(B) > 0: C+(B), C-(everything else)
 * Subspan:    any
 * Coordinate: any "A and/or/as well as/nor B" construction, where votes(A) > 0 and votes(B) > 0, C+(A,B)
 * Created by luheng on 5/29/16.
 */

/**
 * A cleaner version of the heurstic fixer.
 * Created by luheng on 5/26/16.
 */
public class FixerNew {

    final static int minVotesToFix = 1;
    final static double minMarginToFix = 0.33;

    /**
     * Pronoun rule:
     * @param query
     * @param options
     * @param optionDist
     * @return
     */
    public static ImmutableList<Integer> pronounFixer(final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<Integer> options,
                                                      final int[] optionDist) {
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : query.getPredicateId().getAsInt();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int minOptionDist = options.stream()
                .filter(op -> op < numQAs)
                .mapToInt(op -> {
                    final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(op);
                    return qa1.getAnswerStructures().stream()
                            .flatMap(ans -> ans.argumentIndices.stream())
                            .mapToInt(argId -> Math.abs(argId - headId))
                            .min().getAsInt();
                })
                .min().orElse(-1);
        for (int opId2 = 0; opId2 < numQAs; opId2++) {
            if (optionDist[opId2] < minVotesToFix) {
                continue;
            }
            final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
            final String op2 = query.getOptions().get(opId2).toLowerCase();
            final int dist2 = Math.abs(qa2.getAnswerStructures().get(0).argumentIndices.get(0) - headId);
            if (PronounList.nonPossessivePronouns.contains(op2) && dist2 < minOptionDist) {
                return ImmutableList.of(opId2);
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> pronounFixer2(final ScoredQuery<QAStructureSurfaceForm> query,
                                                       final ImmutableList<Integer> options,
                                                       final NBestList nBestList) {
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : query.getPredicateId().getAsInt();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final OptionalInt minOptionDist = options.stream()
                .filter(op -> op < numQAs)
                .mapToInt(op -> {
                    final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(op);
                    return qa1.getAnswerStructures().stream()
                            .flatMap(ans -> ans.argumentIndices.stream())
                            .mapToInt(argId -> Math.abs(argId - headId))
                            .min().getAsInt();
                })
                .min();
        final OptionalDouble maxOptionPrior = options.stream()
                .filter(op -> op < numQAs)
                .mapToDouble(op -> ReparsingHelper.getNBestPrior(query, op, nBestList))
                .max();
        if (!maxOptionPrior.isPresent() || !minOptionDist.isPresent()) {
            return ImmutableList.of();
        }
        for (int opId2 = 0; opId2 < numQAs; opId2++) {
            if (!options.contains(opId2)) {
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = query.getOptions().get(opId2).toLowerCase();
                final int dist2 = Math.abs(qa2.getAnswerStructures().get(0).argumentIndices.get(0) - headId);
                final double prior2 = ReparsingHelper.getNBestPrior(query, opId2, nBestList);
                if (PronounList.nonPossessivePronouns.contains(op2)
                        && dist2 < minOptionDist.getAsInt()
                        && prior2 - minMarginToFix > maxOptionPrior.getAsDouble()) {
                    return ImmutableList.of(opId2);
                }
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> subspanFixer2(final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<Integer> options,
                                                      final NBestList nBestList) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final double prior1 = ReparsingHelper.getNBestPrior(query, opId1, nBestList);
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final double prior2 = ReparsingHelper.getNBestPrior(query, opId2, nBestList);
                final String op2 = query.getOptions().get(opId2).toLowerCase();
                if ((op1.contains(" " + op2) || op1.contains(op2 + " ")) && prior2 - minMarginToFix > prior1) {
                    return ImmutableList.of();
                }
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> subspanFixer(final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<Integer> options,
                                                      final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] < minVotesToFix) {
                    continue;
                }
                final String op2 = query.getQAPairSurfaceForms().get(opId2).getAnswer().toLowerCase();
                if (op1.endsWith("\'s " + op2)) {
                    return ImmutableList.of(opId2);
                }
                for (String pp : Prepositions.prepositionWords) {
                    if (!pp.equals("of") && op1.startsWith(op2 + " " + pp + " ")) {
                        return ImmutableList.of(opId2);
                    }
                }
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> relativeFixer2(final ImmutableList<String> sentence,
                                                       final ScoredQuery<QAStructureSurfaceForm> query,
                                                       final ImmutableList<Integer> options,
                                                       final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int headId = query.getPredicateId().getAsInt();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final int argId1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] < minVotesToFix) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int minArgId2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final int maxArgId2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final int copulaBetweenArgs = (int) IntStream.range(argId1, minArgId2)
                        .mapToObj(sentence::get)
                        .filter(VerbHelper::isCopulaVerb)
                        .count();
                final int commaBetweenArg1Arg2 = (int) IntStream.range(argId1, minArgId2)
                        .mapToObj(sentence::get)
                        .filter(","::equals)
                        .count();
                final boolean trailingWhoThat = (sentenceStr.contains(op2 + " who ")
                        || sentenceStr.contains(op2 + " that "))
                        && Math.abs(headId - maxArgId2) <= 3;
                if ((copulaBetweenArgs == 1 || commaBetweenArg1Arg2 == 1)
                        && Determiners.determinerList.stream().anyMatch(d -> op2.startsWith(d + " "))
                        && trailingWhoThat) {
                    return ImmutableList.of(opId2);
                }
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> appositiveFixer(final ImmutableList<String> sentence,
                                                         final ScoredQuery<QAStructureSurfaceForm> query,
                                                         final ImmutableList<Integer> options,
                                                         final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        final Set<Integer> newOptions = new HashSet<>();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final String op1 = qa1.getAnswer().toLowerCase();
            if (op1.contains(" of ")) {
                continue;
            }
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] < minVotesToFix) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                if ((sentenceStr.contains(op1 + ", " + op2) || sentenceStr.contains(op1 + "., " + op2))
                        && (sentenceStr.contains(op2 + ",") || sentenceStr.contains(op2 + ".,"))
                        && !qa1.getAnswer().equals(op1)
                        && Determiners.determinerList.stream().anyMatch(d -> op2.startsWith(d + " "))) {
                    newOptions.add(opId2);
                }
            }
        }
        if (newOptions.isEmpty()) {
            return ImmutableList.of();
        }
        newOptions.addAll(options);
        return newOptions.stream().distinct().sorted().collect(GuavaCollectors.toImmutableList());
    }

    public static ImmutableList<Integer> relativeFixer(final ImmutableList<String> sentence,
                                                       final ScoredQuery<QAStructureSurfaceForm> query,
                                                       final ImmutableList<Integer> options,
                                                       final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int headId = query.getPredicateId().getAsInt();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final int argId1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] < minVotesToFix) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int minArgId2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final int maxArgId2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final int copulaBetweenArgs = (int) IntStream.range(argId1, minArgId2)
                        .mapToObj(sentence::get)
                        .filter(VerbHelper::isCopulaVerb)
                        .count();
                final int commaBetweenArg1Arg2 = (int) IntStream.range(argId1, minArgId2)
                        .mapToObj(sentence::get)
                        .filter(","::equals)
                        .count();
                final boolean trailingWhoThat = (sentenceStr.contains(op2 + " who ")
                        || sentenceStr.contains(op2 + " that "))
                        && Math.abs(headId - maxArgId2) <= 3;
                if ((copulaBetweenArgs == 1 || commaBetweenArg1Arg2 == 1)
                        && Determiners.determinerList.stream().anyMatch(d -> op2.startsWith(d + " "))
                        && trailingWhoThat) {
                    return ImmutableList.of(opId2);
                }
            }
        }
        return ImmutableList.of();
    }
}

