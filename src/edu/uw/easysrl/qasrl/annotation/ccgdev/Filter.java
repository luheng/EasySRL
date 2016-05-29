package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Created by luheng on 5/27/16.
 */
public class Filter {
    public static boolean filter(final ImmutableList<String> sentence,
                                 final NBestList nBestList,
                                 final ScoredQuery<QAStructureSurfaceForm> query,
                                 final ImmutableList<ImmutableList<Integer>> responses) {
        final int numQAs = query.getQAPairSurfaceForms().size();
        // Skip (S[b]\NP)/NP.1 (... to do)
        // Ditransitives (patients)

        /*
        if (query.getQAPairSurfaceForms().stream().flatMap(qa -> qa.getQuestionStructures().stream())
                .allMatch(q ->
           //             (q.category.isFunctionInto(Category.valueOf("S[b]\\NP")) && q.targetArgNum == 1)
                      (q.category == Category.valueOf("((S[dcl]\\NP)/NP)/NP") && q.targetArgNum > 1)
                     || (q.category == Category.valueOf("((S[b]\\NP)/NP)/NP") && q.targetArgNum > 1))) {
            return true;
        }
        for (int opId1 : IntStream.range(0, numQAs).toArray()) {
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            int covering = 0;
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                final String op2 = query.getQAPairSurfaceForms().get(opId2).getAnswer().toLowerCase();
                if (opId1 != opId2 && op1.contains(op2)) {
                    ++covering;
                }
            }
            // System.out.println(op1 + "\t" + covering);
            if (covering > 2) {
                return true;
            }
        }
        */
        /*
        final int predicateId = query.getPredicateId().getAsInt();
        final String predicateString = sentence.get(predicateId);
        if (sentence.stream().filter(predicateString::equalsIgnoreCase).count() > 1) {
            return true;
        }
        // Uncertainty filter.
        if (IntStream.range(0, numQAs).boxed().filter(i -> query.getOptionScores().get(i) < 0.99).count() == 0) {
            return true;
        }
        if (computeEntropy(query, nBestList) < 0.1) {
            return true;
        }
        */
        return false;
    }

    private static double computeEntropy(ScoredQuery<QAStructureSurfaceForm> query,
                                         NBestList nBestList) {
        Map<ImmutableList<Integer>, AtomicDouble> distribution = new HashMap<>();
        final List<QAStructureSurfaceForm> qaStructures = query.getQAPairSurfaceForms();
        final Set<String> labelSet = new HashSet<>();
        qaStructures.stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .map(q -> q.category + "." + q.targetArgNum)
                .forEach(labelSet::add);
        final OptionalInt prepositionIdOpt = query.getPrepositionIndex();
        final int headId = prepositionIdOpt.isPresent() ?
                prepositionIdOpt.getAsInt() :
                query.getPredicateId().getAsInt();
        if (prepositionIdOpt.isPresent()) {
            labelSet.add("PP/NP.1");
        }
        for (Parse parse : nBestList.getParses()) {
            final ImmutableList<Integer> options = IntStream.range(0, qaStructures.size())
                    .boxed()
                    .filter(op -> {
                        final QAStructureSurfaceForm qa = qaStructures.get(op);
                        final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                                .flatMap(ans -> ans.argumentIndices.stream())
                                .sorted()
                                .collect(GuavaCollectors.toImmutableList());
                        return parse.dependencies.stream()
                                .filter(dep -> labelSet.contains(dep.getCategory() + "." + dep.getArgNumber()))
                                .anyMatch(dep -> dep.getHead() == headId && argIds.contains(dep.getArgument()));
                    })
                    .collect(GuavaCollectors.toImmutableList());
            if (!distribution.containsKey(options)) {
                distribution.put(options, new AtomicDouble(0));
            }
            distribution.get(options).addAndGet(parse.score);
        }
        double norm = distribution.entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .mapToDouble(e -> e.getValue().get()).sum();
        double entropy = distribution.entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .mapToDouble(p -> p.getValue().get() / norm)
                .map(p -> 0.0 - p * Math.log(p) / Math.log(2))
                .sum();
        //distribution.entrySet().forEach(e -> System.out.println(e.getKey() + "\t" + e.getValue()));
        //System.out.println(entropy);
        return entropy;
    }
}
