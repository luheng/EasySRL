package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.corpora.PronounList;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/1/16.
 */
public class EvidenceExtractor {
    public static Set<Evidence> getEvidenceFromQuery(ScoredQuery<QAStructureSurfaceForm> query,
                                                     Collection<Integer> chosenOptions,
                                                     boolean doNotPenalizePronouns) {
        Set<Evidence> evidenceList = new HashSet<>();

        if (query.isJeopardyStyle()) {
            // TODO: change this to attachment.
            IntStream.range(0, query.getQAPairSurfaceForms().size())
                    .boxed()
                    .filter(i -> !chosenOptions.contains(i))
                    .map(query.getQAPairSurfaceForms()::get)
                    .forEach(qa -> qa.getQuestionStructures()
                            .forEach(qstr -> evidenceList.add(
                                    new Evidence.SupertagEvidence(qstr.predicateIndex, qstr.category, false, 1.0))));
            return evidenceList;
        }

        boolean questionIsNA = false;
        Set<Integer> chosenArgIds = new HashSet<>(),
                listedArgIds = new HashSet<>();
        Map<Integer, String> argIdToSpan = new HashMap<>();
        for (int i = 0; i < query.getOptions().size(); i++) {
            final String option = query.getOptions().get(i);
            if (chosenOptions.contains(i) && option.equals(QueryGeneratorUtils.kBadQuestionOptionString)) {
                questionIsNA = true;
            }
            if (i < query.getQAPairSurfaceForms().size()) {
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
            evidenceList.add(new Evidence.SupertagEvidence(predId, category, false, 1.0));
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
                    evidenceList.add(new Evidence.AttachmentEvidence(predId, argId, false, 1.0));
                }
            }
        }
        return evidenceList;
    }
}
