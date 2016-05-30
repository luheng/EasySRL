package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/30/16.
 */
public class ReparsingHelper {
    public static int[] getNewOptionDist (final ImmutableList<String> sentence,
                                          final ScoredQuery<QAStructureSurfaceForm> query,
                                          final ImmutableList<ImmutableList<Integer>> matchedResponses,
                                          final ReparsingConfig config) {
        final int[] optionDist = new int[query.getOptions().size()];
        int[] newOptionDist = new int[optionDist.length];
        Arrays.fill(optionDist, 0);
        Arrays.fill(newOptionDist, 0);
        matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));

        final Multiset<Integer> votes = HashMultiset.create(matchedResponses.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList()));
        final ImmutableList<Integer> agreedOptions = votes.entrySet().stream()
                .filter(e -> e.getCount() >= config.positiveConstraintMinAgreement)
                .map(e -> e.getElement()).distinct().sorted()
                .collect(GuavaCollectors.toImmutableList());


        final ImmutableList<Integer> pronounFix = FixerNew.pronounFixer(query, agreedOptions, optionDist);
        final ImmutableList<Integer> subspanFix = FixerNew.subspanFixer(query, agreedOptions, optionDist);
        final ImmutableList<Integer> appositiveFix = FixerNew.appositiveFixer(sentence, query, agreedOptions, optionDist);
        final ImmutableList<Integer> relativeFix = FixerNew.relativeFixer(sentence, query, agreedOptions, optionDist);
        final ImmutableList<Integer> conjunctionFix = FixerNew.conjunctionFixer(sentence, query, agreedOptions, optionDist);
        String fixType = "None";
        List<Integer> fixedResopnse = null;
        if (config.fixPronouns && !pronounFix.isEmpty()) {
            fixedResopnse = pronounFix;
            fixType = "pronoun";
        } else if (config.fixSubspans && !subspanFix.isEmpty()) {
            fixedResopnse = subspanFix;
            fixType = "subspan";
        } else if (config.fixRelatives && !relativeFix.isEmpty()) {
            fixedResopnse = relativeFix;
            fixType = "relative";
        } else if (config.fixAppositves && !appositiveFix.isEmpty()) {
            fixedResopnse = appositiveFix;
            fixType = "appositive";
        } else if (config.fixConjunctions && !conjunctionFix.isEmpty()) {
            fixedResopnse = conjunctionFix;
            fixType = "conjunction";
        }
        if (fixedResopnse != null) {
            fixedResopnse.stream().forEach(op -> newOptionDist[op] += config.positiveConstraintMinAgreement);
        } else {
            for (ImmutableList<Integer> response : matchedResponses) {
                response.stream().forEach(op -> newOptionDist[op] ++);
            }
        }
        return newOptionDist;
    }

    public static ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                          final int[] optionDist,
                                                          final ReparsingConfig config) {
        final Set<Constraint> constraints = new HashSet<>();
        final int numQA = query.getQAPairSurfaceForms().size();

        if (IntStream.range(0, numQA).map(i -> optionDist[i]).sum() <= config.negativeConstraintMaxAgreement) {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .forEach(qstr -> constraints.add(new Constraint
                            .SupertagConstraint(qstr.predicateIndex, qstr.category, false, config.supertagPenalty)));
            return ImmutableSet.copyOf(constraints);
        }

        final ImmutableList<Integer> numVotes = IntStream.range(0, numQA)
                .mapToObj(i -> optionDist[i]).collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> optionOrder = IntStream.range(0, numQA).boxed()
                .sorted((i, j) -> Integer.compare(-numVotes.get(i), -numVotes.get(j)))
                .collect(GuavaCollectors.toImmutableList());

        Set<Integer> skipOps = new HashSet<>();
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : query.getPredicateId().getAsInt();

        for (int opId1 : optionOrder) {
            if (skipOps.contains(opId1)) {
                continue;
            }
            final int votes = numVotes.get(opId1);
            final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .filter(argId -> argId != headId)
                    .distinct().sorted()
                    .collect(GuavaCollectors.toImmutableList());
            final String opStr = qa.getAnswer().toLowerCase();

            // Handle subspan/superspan.
            if (config.useSubspanDisjunctives) {
                boolean hasDisjunctiveConstraints = false;
                for (int opId2 : optionOrder) {
                    if (opId2 != opId1) {
                        final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                        final String opStr2 = qa2.getAnswer().toLowerCase();
                        final int votes2 = numVotes.get(opId2);
                        if (votes + votes2 >= config.positiveConstraintMinAgreement && votes > 0 && votes2 > 0) {
                            // TODO: + or
                            if (opStr.startsWith(opStr2 + " and ") || opStr.endsWith(" and " + opStr2)
                                    || opStr2.endsWith(" and " + opStr) || opStr2.startsWith(opStr + " and ")
                                    || opStr.endsWith(" of " + opStr2) || opStr2.endsWith(" of " + opStr)) {
                                final ImmutableList<Integer> concatArgs = Stream
                                        .concat(argIds.stream(), qa2.getArgumentIndices().stream())
                                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                                constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, concatArgs, true,
                                        config.positiveConstraintPenalty));
                                hasDisjunctiveConstraints = true;
                                skipOps.add(opId2);
                            }
                        }
                    }
                }
                if (hasDisjunctiveConstraints) {
                    continue;
                }
            }
            if (votes >= config.positiveConstraintMinAgreement) {
                if (argIds.size() == 1) {
                    constraints.add(new Constraint.AttachmentConstraint(headId, argIds.get(0), true, config.positiveConstraintPenalty));
                } else {
                    constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, argIds, true, config.positiveConstraintPenalty));
                }
            } else if (votes <= config.negativeConstraintMaxAgreement && !skipOps.contains(opId1)) {
                argIds.forEach(argId ->
                        constraints.add(new Constraint.AttachmentConstraint(headId, argId, false, config.negativeConstraintPenalty)));
            }
        }
        return ImmutableSet.copyOf(constraints);
    }
}
