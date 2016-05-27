package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.stream.IntStream;

/**
 * Created by luheng on 5/27/16.
 */
public class Filter {
    public static boolean filter(final ImmutableList<String> sentence,
                                 final ScoredQuery<QAStructureSurfaceForm> query,
                                 final ImmutableList<ImmutableList<Integer>> responses) {
        // Skip (S[b]\NP)/NP.1 (... to do)
        // Ditransitives (patients)
        if (query.getQAPairSurfaceForms().stream().flatMap(qa -> qa.getQuestionStructures().stream())
                .anyMatch(q ->
                        (q.category.isFunctionInto(Category.valueOf("S[b]\\NP")) && q.targetArgNum == 1)
                     || (q.category == Category.valueOf("((S[dcl]\\NP)/NP)/NP") && q.targetArgNum == 3)
                     || (q.category == Category.valueOf("((S[b]\\NP)/NP)/NP") && q.targetArgNum == 3))) {
            return true;
        }
        final int numQAs = query.getQAPairSurfaceForms().size();
        // Skip X of Y is user annotated either.
        for (int opId1 : IntStream.range(0, numQAs).toArray()) {
            final String op1 = query.getQAPairSurfaceForms().get(opId1).getAnswer().toLowerCase();
            final int votes1 = (int) responses.stream().filter(r -> r.contains(opId1)).count();
            for (int opId2 : IntStream.range(0, numQAs).toArray()) {
                final String op2 = query.getQAPairSurfaceForms().get(opId2).getAnswer().toLowerCase();
                final int votes2 = (int) responses.stream().filter(r -> r.contains(opId2)).count();
                if (op1.endsWith(" of " + op2) && (votes1 > 0 || votes2 > 0)) {
                    return true;
                }
            }
        }
        final int predicateId = query.getPredicateId().getAsInt();
        final String predicateString = sentence.get(predicateId);
        if (sentence.stream().filter(predicateString::equalsIgnoreCase).count() > 1) {
            return true;
        }
        // Uncertainty filter.
        if (IntStream.range(0, numQAs).boxed().filter(i -> query.getOptionScores().get(i) < 0.99).count() == 0) {
            return true;
        }
        return false;
    }
}
