package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.stanford.nlp.util.Pair;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.corpora.PronounList;
import edu.uw.easysrl.qasrl.experiments.DebugPrinter;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/1/16.
 */
public class EvidenceExtractor {

    /**
     * 1. Verb argument: ((S\NP)/PP)/NP -> return < qa.predicateIndex, qa.otherDependencies[qa.targetArgnum] >
     * 2. Verb adjunct:  ((S\NP)\(S\NP))/NP -> return < qa.predicateIndex, qa.otherDependencies[2] >, assert cat[2]=S\NP
     * 3. Noun adjunct:  (NP\NP)/NP -> return < qa.predicateIndex, qa.otherDependencies[1] >, assert cat[1]=NP
     * @param qa
     * @return
     */
    private static List<int[]> getPPAttachments(QAStructureSurfaceForm qa) {
        final List<int[]> attachments = new ArrayList<>();
        for (QuestionStructure qs : qa.getQuestionStructures()) {
            final Category category = qs.category;
            if (category.getArgument(qs.targetArgNum).equals(Category.PP)) {
                System.out.println("Verb-arg:\t" + category + "\t" + qs.targetArgNum
                        + qa.getAnswerStructures().stream().map(a -> DebugPrinter.getShortListString(a.argumentIndices))
                            .collect(Collectors.joining("\t")));
                //attachments.add(new int[] { predicateIndex, });
                continue;
            }
            int attachmentArgNum = -1;
            if (category.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"))) {
                //System.out.println("Verb-adj:\t" + category + "\t" + 2);
                attachmentArgNum = 2;
            } else if (category == Category.valueOf("(NP\\NP)/NP")) {
                //System.out.println("Noun-adj:\t" + category + "\t" + 1);
                attachmentArgNum = 1;
            }
            if (qs.otherDependencies.containsKey(attachmentArgNum)) {
                qs.otherDependencies.get(attachmentArgNum)
                        .forEach(argId -> attachments.add(new int[]{ qa.getPredicateIndex(), argId }));
            }
        }
        return attachments;
    }

    public static Set<Evidence> getEvidenceFromQuery(ScoredQuery<QAStructureSurfaceForm> query,
                                                     Collection<Integer> chosenOptions,
                                                     boolean doNotPenalizePronouns) {
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        if (query.isJeopardyStyle()) {
            // 0: listed. 1: chosen.
            Table<Integer, Integer, Integer> attachments = HashBasedTable.create();
            for (int i = 0; i < numQAOptions; i++) {
                final boolean chosen = chosenOptions.contains(i);
                getPPAttachments(query.getQAPairSurfaceForms().get(i)).forEach(a -> {
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
                    .map(c -> new Evidence.AttachmentEvidence(c.getRowKey(), c.getColumnKey(), false, 1.0))
                    .collect(Collectors.toSet());
        }

        final Set<Evidence> evidenceList = new HashSet<>();
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
