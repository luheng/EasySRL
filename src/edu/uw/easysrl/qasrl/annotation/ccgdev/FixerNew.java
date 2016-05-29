package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
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
        final int predId = query.getPredicateId().getAsInt();
        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final int dist1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .map(argId -> Math.abs(argId - predId))
                    .min(Integer::compare).get();
            for (int opId2 = 0; opId2 < query.getQAPairSurfaceForms().size(); opId2++) {
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = query.getOptions().get(opId2).toLowerCase();
                final int dist2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .map(argId -> Math.abs(argId - predId))
                        .min(Integer::compare).get();
                if (PronounList.nonPossessivePronouns.contains(op2) && dist2 < dist1 && optionDist[opId2] > 0) {
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
        // TODO: other appositive (A,B) features: A has capitalized words, B starts with a/an/the/both/each/one/two/another
        // TODO: B followed by , or .
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        final Set<Integer> newOptions = new HashSet<>();

        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final String op1 = qa1.getAnswer().toLowerCase();
            final int argId1 = qa1.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] == 0) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int minArgId2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final boolean commaInBetween = IntStream.range(argId1, minArgId2)
                        .mapToObj(sentence::get)
                        .anyMatch(","::equals);
                if (sentenceStr.contains(op1 + ", " + op2) || sentenceStr.contains(op1 + "., " + op2)
                        || sentenceStr.contains(op2 + ", " + op1) || sentenceStr.contains(op2 + "., " + op1)) {
                      //  && !query.getQAPairSurfaceForms().get(opId1).getAnswer().equals(op1)
                      //  && Determiners.determinerList.stream().anyMatch(d -> op2.startsWith(d + " "))) {
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

    /**
     * A = X'B, A = X and B, A = XB/BX (?), A = B pp X, A = X of B
     * Have to give up signals such as X of Y ..
     * @return
     */
    public static ImmutableList<Integer> subspanFixer(final ImmutableList<String> sentence,
                                                      final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<Integer> options,
                                                      final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] == 0) {
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

    public static ImmutableList<Integer> relativeFixer(final ImmutableList<String> sentence,
                                                       final ScoredQuery<QAStructureSurfaceForm> query,
                                                       final ImmutableList<Integer> options,
                                                       final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final int argId1 = qa1.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2 || optionDist[opId2] == 0) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int minArgId2 = qa2.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final boolean copulaInBetween = IntStream.range(argId1, minArgId2).mapToObj(sentence::get)
                        .anyMatch(VerbHelper::isCopulaVerb);
                final boolean commaBetweenArg1Arg2 = IntStream.range(argId1, minArgId2).mapToObj(sentence::get)
                        .anyMatch(","::equals);
                final String predStr = sentence.get(predicateId).toLowerCase();
                final boolean trailingWhoThat = sentenceStr.contains(op2 + " who " + predStr)
                        || sentenceStr.contains(op2 + " that " + predStr)
                        || sentenceStr.contains(op2 + ". who " + predStr)
                        || sentenceStr.contains(op2 + ". that " + predStr);
                final boolean trailingPss = sentenceStr.contains(op2 + " " + predStr)
                        && query.getQAPairSurfaceForms().stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream())
                        .anyMatch(q -> q.category.isFunctionInto(Category.valueOf("S[pss]")));
                if ((copulaInBetween || commaBetweenArg1Arg2) && (trailingWhoThat || trailingPss)) {
                    return ImmutableList.of(opId2);
                }
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> conjunctionFixer(final ImmutableList<String> sentence,
                                                          final ScoredQuery<QAStructureSurfaceForm> query,
                                                          final ImmutableList<Integer> options,
                                                          final int[] optionDist) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        final Set<Integer> newOptions = new HashSet<>();
        for (int opId1 : options) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final String op1 = qa1.getAnswer().toLowerCase();
            final int argId1 = qa1.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                if (opId1 == opId2 || optionDist[opId2] == 0 || op1.contains(op2) || op2.contains(op1)) {
                    continue;
                }
                final int minArgId2 = qa2.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final String inBetween = IntStream.range(argId1, minArgId2)
                        .mapToObj(sentence::get)
                        .collect(Collectors.joining(" "));
                if (Stream.of(" and ", " or ", " as well as ").anyMatch(inBetween::contains)) {
                    newOptions.add(numQAs);
                }
            }
        }
        if (newOptions.isEmpty()) {
            return ImmutableList.of();
        }
        newOptions.addAll(options);
        return newOptions.stream().distinct().sorted().collect(GuavaCollectors.toImmutableList());
    }
}

