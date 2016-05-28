package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.semantics.lexicon.CopulaLexicon;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A cleaner version of the heurstic fixer.
 * Created by luheng on 5/26/16.
 */
public class Fixer {


    public static ImmutableList<Integer> pronounFixer(final ImmutableList<String> sentence,
                                                      final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<ImmutableList<Integer>> responses) {
        // TODO: cut by votes and prior. Other features: X said [pron] ...
        final int minPronounVotes = 0;

        final Multiset<ImmutableList<Integer>> responseSet = HashMultiset.create(responses);
        final ImmutableList<Integer> agreedOptions = responses.stream()
                .sorted((op1, op2) -> Integer.compare(-responseSet.count(op1), -responseSet.count(op2)))
                .findFirst().get();

        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        int minDist = sentence.size();
        int bestPronounOpId = -1;
        for (int opId : IntStream.range(0, numQAs).toArray()) {
            final String op = query.getOptions().get(opId).toLowerCase();
            final int argId = query.getQAPairSurfaceForms().get(opId).getAnswerStructures().get(0).argumentIndices.get(0);
            final int dist = Math.abs(predicateId - argId);
            final int votes = (int) responses.stream().filter(r -> r.contains(opId)).count();
            if (PronounList.nonPossessivePronouns.contains(op) && !sentenceStr.contains(op + " \'s") && dist < minDist) {
                if (dist == 1 || votes >= minPronounVotes) {
                    bestPronounOpId = opId;
                    minDist = dist;
                }
            }
        }
        final int minPredicatePronounDist = minDist;
        if (agreedOptions.size() == 1 && agreedOptions.get(0) < numQAs) {
            if (query.getQAPairSurfaceForms().get(agreedOptions.get(0)).getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .allMatch(argId -> Math.abs(argId - predicateId) > minPredicatePronounDist)) {
                return ImmutableList.of(bestPronounOpId);
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> appositiveFixer(final ImmutableList<String> sentence,
                                                   //      final NBestList nBestList,
                                                         final ScoredQuery<QAStructureSurfaceForm> query,
                                                         final ImmutableList<ImmutableList<Integer>> responses) {
        // TODO: other appositive (A,B) features: A has capitalized words, B starts with a/an/the/both/each/one/two/another
        // TODO: B followed by , or .
        final int minAppositiveVotes = 0;

        final Multiset<ImmutableList<Integer>> responseSet = HashMultiset.create(responses);
        final ImmutableList<Integer> agreedOptions = responses.stream()
                .sorted((op1, op2) -> Integer.compare(-responseSet.count(op1), -responseSet.count(op2)))
                .findFirst().get();

        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        // final List<Category> onebestCategories = nBestList.getParse(0).categories;
        // TODO: look for determiners NP[nb]/N
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
                final int votes = (int) responses.stream().filter(r -> r.contains(opId2)).count();
                if ((sentenceStr.contains(op1 + " , " + op2) || sentenceStr.contains(op1 + ". , " + op2))
                       //|| sentenceStr.contains(op2 + " , " + op1 + " , ") || sentenceStr.contains(op2 + ". , " + op1 + " , "))
                        && !query.getQAPairSurfaceForms().get(opId1).getAnswer().equals(op1)
                        && (op2.startsWith("a ") || op2.startsWith("the ") || op2.startsWith("an ") || op2.startsWith("both "))
                        && votes >= minAppositiveVotes) {
                   // System.err.println(sentenceStr + "\n" + op1 + "\n" + op2);
                    // FIXME: do not return.
                    return Stream.concat(agreedOptions.stream(), Stream.of(opId2))
                            .sorted().collect(GuavaCollectors.toImmutableList());
                }
            }
        }
        return ImmutableList.of();
    }

    /**
     * A = X'B, A = X and B, A = XB/BX (?), A = B pp X, A = X of B
     * Have to give up signals such as X of Y ..
     * @param sentence
     * @param query
     * @param responses
     * @return
     */
    public static ImmutableList<Integer> subspanFixer(final ImmutableList<String> sentence,
                                                      final ScoredQuery<QAStructureSurfaceForm> query,
                                                      final ImmutableList<ImmutableList<Integer>> responses) {
        final int minSubspanVotes = 0;
        final Multiset<ImmutableList<Integer>> responseSet = HashMultiset.create(responses);
        final ImmutableList<Integer> agreedOptions = responses.stream()
                .sorted((op1, op2) -> Integer.compare(-responseSet.count(op1), -responseSet.count(op2)))
                .findFirst().get();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        if (agreedOptions.size() == 1 && agreedOptions.get(0) < numQAs) {
            int opId1 = agreedOptions.get(0);
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final String op2 = query.getQAPairSurfaceForms().get(opId2).getAnswer().toLowerCase();
                final int votes = (int) responses.stream().filter(r -> r.contains(opId2)).count();
                if (votes < minSubspanVotes) {
                    continue;
                }
                if (op1.endsWith("\'s " + op2) && query.getOptionScores().get(opId1) < 0.1) {
                    return ImmutableList.of(opId2);
                }
                for (String pp : Prepositions.prepositionWords) {
                    if (!pp.equals("of") && op1.startsWith(op2 + " " + pp + " ")) {
                        return ImmutableList.of(opId2);
                    }
                }
                // TODO: "a group of workers exposed ..."
                // "a lot of the stocks that ..."
                /*
                if (op1.endsWith(" of " + op2) && sentenceStr.contains(op2 + " that ")) {
                    return ImmutableList.of(opId2);
                }
                */
                // TODO: not sure about this.
                /*
                if (op1.contains(" and " + op2)) {
                    return ImmutableList.of(opId1, opId2);
                }
                */
            }
        }
        return ImmutableList.of();
    }

    public static ImmutableList<Integer> restrictiveClauseFixer(final ImmutableList<String> sentence,
                                                                final ScoredQuery<QAStructureSurfaceForm> query,
                                                                final ImmutableList<ImmutableList<Integer>> responses) {
        final int minRestrictiveClauseVote = 0;
        final Multiset<ImmutableList<Integer>> responseSet = HashMultiset.create(responses);
        final ImmutableList<Integer> agreedOptions = responses.stream()
                .sorted((op1, op2) -> Integer.compare(-responseSet.count(op1), -responseSet.count(op2)))
                .findFirst().get();
        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        final String sentenceStr = sentence.stream().collect(Collectors.joining(" ")).toLowerCase();
        if (agreedOptions.size() == 1 && agreedOptions.get(0) < numQAs) {
            int opId1 = agreedOptions.get(0);
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final int argId1 = qa1.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                    .max(Integer::compare).get();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                if (opId1 == opId2) {
                    continue;
                }
                final int votes = (int) responses.stream().filter(r -> r.contains(opId2)).count();
                if (votes < minRestrictiveClauseVote) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final String op2 = qa2.getAnswer().toLowerCase();
                final int argId2 = qa2.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                        .max(Integer::compare).get();
                boolean copulaInBetween = IntStream.range(argId1, argId2).mapToObj(sentence::get)
                        .anyMatch(VerbHelper::isCopulaVerb);
                boolean commaInBetween = IntStream.range(argId1, argId2).mapToObj(sentence::get)
                        .anyMatch(","::equals);
                boolean trailingWhoThat = sentenceStr.contains(op2 + " who ") || sentenceStr.contains(op2 + " that ");
                if ((copulaInBetween || commaInBetween) && trailingWhoThat && predicateId > argId2) {
                    return ImmutableList.of(opId2);
                }
            }
        }
        return ImmutableList.of();
    }
}
