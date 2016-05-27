package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

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
        final int minPronounVotes = 1;

        final Multiset<ImmutableList<Integer>> responseSet = HashMultiset.create(responses);
        final ImmutableList<Integer> agreedOptions = responses.stream()
                .sorted((op1, op2) -> Integer.compare(-responseSet.count(op1), -responseSet.count(op2)))
                .findFirst().get();

        final int numQAs = query.getQAPairSurfaceForms().size();
        final int predicateId = query.getPredicateId().getAsInt();
        int minDist = sentence.size();
        int bestPronounOpId = -1;
        for (int opId : IntStream.range(0, numQAs).toArray()) {
            final String op = query.getOptions().get(opId).toLowerCase();
            final int argId = query.getQAPairSurfaceForms().get(opId).getAnswerStructures().get(0).argumentIndices.get(0);
            final int dist = Math.abs(predicateId - argId);
            final int votes = (int) responses.stream().filter(r -> r.contains(opId)).count();
            if (PronounList.englishPronounSet.contains(op) && dist < minDist) {
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
                      // || sentenceStr.contains(op2 + " , " + op1) || sentenceStr.contains(op2 + ". , " + op1))
                        && !query.getQAPairSurfaceForms().get(opId1).getAnswer().equals(op1)
                        && (op2.startsWith("a ") || op2.startsWith("the "))
                        && votes >= minAppositiveVotes) {
                   // System.err.println(sentenceStr + "\n" + op1 + "\n" + op2);
                    return Stream.concat(agreedOptions.stream(), Stream.of(opId2))
                            .sorted().collect(GuavaCollectors.toImmutableList());
                }
            }
        }
        return ImmutableList.of();
    }
}
