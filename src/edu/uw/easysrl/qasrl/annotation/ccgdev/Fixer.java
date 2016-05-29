package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.semantics.lexicon.CopulaLexicon;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;
import scala.Int;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A cleaner version of the heurstic fixer.
 * Created by luheng on 5/26/16.
 */
public class Fixer {


    private static ImmutableList<Integer> getAgreedOptions(final ImmutableList<ImmutableList<Integer>> responses) {
        final Multiset<ImmutableList<Integer>> responseSet = HashMultiset.create(responses);
        return responses.stream()
                .sorted((op1, op2) -> Integer.compare(-responseSet.count(op1), -responseSet.count(op2)))
                .findFirst().get();
    }

    public static ImmutableList<Integer> pronounFixer(final ImmutableList<String> sentence,
                                                      final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<ImmutableList<Integer>> responses) {
        // TODO: cut by votes and prior. Other features: X said [pron] ...
        ImmutableList<Integer> agreedOptions = getAgreedOptions(responses);
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        int minDist = sentence.size();
        int bestPronounOpId = -1, bestArgId = -1;

        for (int opId : IntStream.range(0, numQAs).toArray()) {
            final String op = query.getOptions().get(opId).toLowerCase();
            final int argId = query.getQAPairSurfaceForms().get(opId).getAnswerStructures().get(0).argumentIndices.get(0);
            //final int dist = Math.abs(predicateId - argId);
            final int votes = (int) responses.stream().filter(r -> r.contains(opId)).count();
            if (PronounList.nonPossessivePronouns.contains(op) && votes > 0) {
                return ImmutableList.of(bestPronounOpId);
            }
        }

        /*
        for (int opId : IntStream.range(0, numQAs).toArray()) {
            final String op = query.getOptions().get(opId).toLowerCase();
            final int argId = query.getQAPairSurfaceForms().get(opId).getAnswerStructures().get(0).argumentIndices.get(0);
            final int dist = Math.abs(predicateId - argId);
            if (PronounList.nonPossessivePronouns.contains(op) && !sentenceStr.contains(op + " \'s")
                    && argId < predicateId && dist < minDist) {
                bestPronounOpId = opId;
                bestArgId = argId;
                minDist = dist;
            }
        }
        if (bestPronounOpId >= 0) {
            final int bestPronounArgId = bestArgId;
            if (agreedOptions.size() == 1 && agreedOptions.get(0) < numQAs) {
                if (query.getQAPairSurfaceForms().get(agreedOptions.get(0)).getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream()).allMatch(argId -> argId < bestPronounArgId)) {
                    return ImmutableList.of(bestPronounOpId);
                }
            }
        }
        */
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> appositiveFixer(final ImmutableList<String> sentence,
                                                         final ScoredQuery<QAStructureSurfaceForm> query,
                                                         final ImmutableList<ImmutableList<Integer>> responses) {
        // TODO: other appositive (A,B) features: A has capitalized words, B starts with a/an/the/both/each/one/two/another
        // TODO: B followed by , or .
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final ImmutableList<Integer> agreedOptions = getAgreedOptions(responses);
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        // TODO: look for determiners NP[nb]/N
        for (int opId1 : agreedOptions) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final String op1 = qa1.getAnswer().toLowerCase();
            final int argId1 = qa1.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int argId2 = qa2.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final int votes2 = (int) responses.stream().filter(r -> r.contains(opId2)).count();
                final boolean commaInBetween = IntStream.range(argId1, argId2).mapToObj(sentence::get)
                        .anyMatch(","::equals);
                // Weak appositive.
                if (commaInBetween && !op2.contains(op1) && votes2 > 0) {
                    return Stream.concat(agreedOptions.stream(), Stream.of(opId2))
                            .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                }
                // Strong appositive.
                if ((sentenceStr.contains(op1 + ", " + op2) || sentenceStr.contains(op1 + "., " + op2))
                        && !query.getQAPairSurfaceForms().get(opId1).getAnswer().equals(op1)
                        && Determiners.determinerList.stream().anyMatch(d -> op2.startsWith(d + " "))) {
                    //System.err.println(sentenceStr + "\n" + op1 + "\n" + op2);
                    return Stream.concat(agreedOptions.stream(), Stream.of(opId2))
                            .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                }
            }
        }
        return ImmutableList.of();
    }

    /**
     * A = X'B, A = X and B, A = XB/BX (?), A = B pp X, A = X of B
     * Have to give up signals such as X of Y ..
     * @return
     */
    public static ImmutableList<Integer> subspanFixer(final ImmutableList<String> sentence,
                                                      final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<ImmutableList<Integer>> responses) {
        final ImmutableList<Integer> agreedOptions = getAgreedOptions(responses);
        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 : agreedOptions) {
            if (opId1 >= numQAs) {
                continue;
            }
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final String op2 = query.getQAPairSurfaceForms().get(opId2).getAnswer().toLowerCase();
                if (op1.endsWith("\'s " + op2) & query.getOptionScores().get(opId1) < 0.1) {
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
                                                       final ImmutableList<ImmutableList<Integer>> responses) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final ImmutableList<Integer> agreedOptions = getAgreedOptions(responses);
        //final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        final String sentenceStr = TextGenerationHelper.renderString(sentence).toLowerCase();
        for (int opId1 : agreedOptions) {
            if (opId1 >= numQAs) {
                continue;
            }
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final int argId1 = qa1.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int minArgId2 = qa2.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                        .min(Integer::compare).get();
                final int maxArgId2 = qa2.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                        .max(Integer::compare).get();
                final boolean copulaInBetween = IntStream.range(argId1, minArgId2).mapToObj(sentence::get)
                        .anyMatch(VerbHelper::isCopulaVerb);
                final boolean commaBetweenArg1Arg2 = IntStream.range(argId1, minArgId2).mapToObj(sentence::get)
                        .anyMatch(","::equals);
                final boolean commaBetweenArg2Pred = IntStream.range(maxArgId2, predicateId).mapToObj(sentence::get)
                        .anyMatch(","::equals);
                final boolean trailingWhoThat = sentenceStr.contains(op2 + " who ") || sentenceStr.contains(op2 + " that ")
                                                || sentenceStr.contains(op2 + ". who ") || sentenceStr.contains(op2 + ". that ");
                final boolean trailingPss = sentenceStr.contains(op2 + " " + sentence.get(predicateId))
                        && query.getQAPairSurfaceForms().stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream())
                        .anyMatch(q -> q.category.isFunctionInto(Category.valueOf("S[pss]")));
                if ((copulaInBetween || commaBetweenArg1Arg2) && (trailingWhoThat || trailingPss)
                        && !commaBetweenArg2Pred) {
                    return Stream.concat(agreedOptions.stream().filter(i -> i != opId1), Stream.of(opId2))
                            .distinct().sorted()
                            .collect(GuavaCollectors.toImmutableList());
                }
            }
        }
        return ImmutableList.of();
    }
}
