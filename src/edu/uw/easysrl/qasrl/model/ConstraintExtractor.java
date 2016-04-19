package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO: Need to refactor.
 * Created by luheng on 4/1/16.
 */
public class ConstraintExtractor {

    /*
    public static Set<Constraint> extractConstraints(ScoredQuery<QAStructureSurfaceForm> query,
                                                     Collection<Integer> chosenOptions,
                                                     boolean doNotPenalizePronouns) {
        final int numQAOptions = query.getQAPairSurfaceForms().size();

        // 0: listed. 1: chosen.
        Table<Integer, Integer, Integer> attachments = HashBasedTable.create();
        for (int i = 0; i < numQAOptions; i++) {
            final boolean chosen = chosenOptions.contains(i);
            AttachmentHelper.getAttachments(query.getQAPairSurfaceForms().get(i), doNotPenalizePronouns).forEach(a -> {
                if (!attachments.contains(a[0], a[1])) {
                    attachments.put(a[0], a[1], 0);
                }
                //if (!attachments.contains(a[1], a[0])) {
                //    attachments.put(a[1], a[0], 0);
                //}
                if (chosen) {
                    attachments.put(a[0], a[1], 1);
                //    attachments.put(a[1], a[0], 1);
                }
            });
        }
        return attachments.cellSet().stream()
                // listed but not chosen.
                .filter(c -> c.getValue() == 0)
                .map(c -> new Constraint.AttachmentConstraint(c.getRowKey(), c.getColumnKey(), false, 1.0))
                .collect(Collectors.toSet());
    }*/

    public static Set<Constraint> extractPositiveConstraints(ScoredQuery<QAStructureSurfaceForm> query,
                                                             Collection<Integer> chosenOptions) {
        Set<Constraint> constraints = new HashSet<>();
        if (query.getQueryType() == QueryType.Forward) {
            chosenOptions.stream()
                    .filter(op -> op < query.getQAPairSurfaceForms().size())
                    .map(query.getQAPairSurfaceForms()::get)
                    .forEach(qa -> qa.getQuestionStructures().stream()
                            .forEach(qstr -> {
                                final int headId = qstr.targetPrepositionIndex >= 0 ?
                                        qstr.targetPrepositionIndex : qstr.predicateIndex;
                                qa.getAnswerStructures().stream()
                                        .flatMap(astr -> astr.argumentIndices.stream())
                                        .distinct()
                                        .forEach(argId -> constraints.add(
                                                new Constraint.AttachmentConstraint(headId, argId, true, 1.0)));
                                if (qstr.targetPrepositionIndex >= 0) {
                                    constraints.add(new Constraint.AttachmentConstraint(qstr.predicateIndex,
                                            qstr.targetPrepositionIndex, true, 1.0));
                                }
                            })
                    );
        }
        // if (query.getQueryType() == QueryType.Clefted) {
        // }
        return constraints;
    }

    public static Set<Constraint> extractConstraints(ScoredQuery<QAStructureSurfaceForm> query,
                                                     Collection<Integer> chosenOptions,
                                                     boolean doNotPenalizePronouns) {
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        if (query.getQueryType() == QueryType.Jeopardy || query.getQueryType() == QueryType.Clefted) {
            // Option contains "none of the above".
            if (query.getQueryType() == QueryType.Clefted &&
                    chosenOptions.contains(query.getBadQuestionOptionId().getAsInt())) {
                final int argHead = query.getQAPairSurfaceForms().get(0).getArgumentIndices().get(0);
                return query.getQAPairSurfaceForms().stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream())
                        .distinct()
                        .map(q -> q.targetPrepositionIndex >= 0 ?
                            new Constraint.AttachmentConstraint(q.targetPrepositionIndex, argHead, false, 1.0) :
                            new Constraint.AttachmentConstraint(q.predicateIndex, argHead, false, 1.0))
                        .collect(Collectors.toSet());
            }
            // 0: listed. 1: chosen.
            Table<Integer, Integer, Integer> attachments = HashBasedTable.create();
            for (int i = 0; i < numQAOptions; i++) {
                final boolean chosen = chosenOptions.contains(i);
                final List<int[]> extractedAttachments = query.getQueryType() == QueryType.Jeopardy ?
                        AttachmentHelper.getPPAttachments(query.getQAPairSurfaceForms().get(i)) :
                        AttachmentHelper.getAnswerPPAttachments(query.getQAPairSurfaceForms().get(i));
                extractedAttachments.forEach(a -> {
                    if (!attachments.contains(a[0], a[1])) {
                        attachments.put(a[0], a[1], 0);
                    }
                    if (!attachments.contains(a[1], a[0])) {
                        attachments.put(a[1], a[0], 0);
                    }
                    if (chosen) {
                        attachments.put(a[0], a[1], 1);
                        attachments.put(a[1], a[0], 1);
                    }
                });
            }
            return attachments.cellSet().stream()
                    // listed but not chosen.
                    .filter(c -> c.getValue() == 0)
                    .map(c -> new Constraint.AttachmentConstraint(c.getRowKey(), c.getColumnKey(), false, 1.0))
                    .collect(Collectors.toSet());
        }

        final Set<Constraint> constraintList = new HashSet<>();
        boolean questionIsNA = false;
        Set<Integer> chosenArgIds = new HashSet<>(), listedArgIds = new HashSet<>();
        Map<Integer, String> argIdToSpan = new HashMap<>();
        for (int i = 0; i < query.getOptions().size(); i++) {
            final String option = query.getOptions().get(i);
            if (chosenOptions.contains(i) && option.equals(QueryGeneratorUtils.kBadQuestionOptionString)) {
                questionIsNA = true;
            }
            if (i < numQAOptions) {
                final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(i);
                final ImmutableList<Integer> argIds = qa.getArgumentIndices();
                if (chosenOptions.contains(i)) {
                    chosenArgIds.addAll(argIds);
                }
                listedArgIds.addAll(argIds);
                if (doNotPenalizePronouns) {
                    final String[] answerSpans = option.split(QAPairAggregatorUtils.answerDelimiter);
                    for (int j = 0; j < argIds.size(); j++) {
                        argIdToSpan.put(argIds.get(j), answerSpans[j]);
                    }
                }
            }
        }
        final int predId = query.getQAPairSurfaceForms().get(0).getPredicateIndex();
        final Category category = query.getQAPairSurfaceForms().get(0).getCategory();
        if (questionIsNA) {
            constraintList.add(new Constraint.SupertagConstraint(predId, category, false, 1.0));
        }
        // TODO: take into account co-ordinations.
        else {
            for (int argId : listedArgIds) {
                if (!chosenArgIds.contains(argId)) {
                    if (doNotPenalizePronouns) {
                        String argSpan = argIdToSpan.get(argId).toLowerCase();
                        if (PronounList.englishPronounSet.contains(argSpan)) {
                            continue;
                        }
                    }
                    constraintList.add(new Constraint.AttachmentConstraint(predId, argId, false, 1.0));
                }
            }
        }
        return constraintList;
    }
}
