package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.corpora.PronounList;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Extract unlabeled dependencies from QA pairs.
 * Created by luheng on 4/4/16.
 */
public class AttachmentHelper {

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
            final int predicateIndex = qs.predicateIndex;
            final Category category = qs.category;
            final ImmutableMap<Integer, ImmutableList<Integer>> qDeps = qs.otherDependencies;
            int attachmentArgNum = -1;
            boolean isPPArg = false;
            if (category.getArgument(qs.targetArgNum).equals(Category.PP)) {
                attachmentArgNum = qs.targetArgNum;
                isPPArg = true;
            }
            if (category.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"))) {
                attachmentArgNum = 2;
            }
            else if (category == Category.valueOf("(NP\\NP)/NP")) {
                attachmentArgNum = 1;
            }

            if (qDeps.containsKey(attachmentArgNum)) {
                qDeps.get(attachmentArgNum)
                        .forEach(argId -> attachments.add(new int[]{ predicateIndex, argId }));
            }
            final int ppId = isPPArg ? (qDeps.containsKey(attachmentArgNum) ? qDeps.get(attachmentArgNum).get(0) : -1)
                    : predicateIndex;
            if (ppId >= 0) {
                // Target dependency.
                qa.getAnswerStructures().stream()
                        .flatMap(astr -> astr.argumentIndices.stream())
                        .distinct()
                        .forEach(argId -> attachments.add(new int[]{ ppId, argId }));
            }
        }
        return attachments;
    }

    private static ImmutableSet<Integer> getPronounArgumentIds(final QAStructureSurfaceForm qa) {
        final String[] answerSpans = qa.getAnswer().split(QAPairAggregatorUtils.answerDelimiter);
        return qa.getAnswerStructures().stream()
                .filter(astr -> astr.argumentIndices.size() == answerSpans.length)
                .flatMap(astr -> IntStream.range(0, astr.argumentIndices.size())
                        .boxed()
                        .filter(i -> PronounList.englishPronounSet.contains(answerSpans[i].toLowerCase()))
                        .map(astr.argumentIndices::get))
                .distinct()
                .collect(GuavaCollectors.toImmutableSet());
    }

    public static List<int[]> getAttachments(final QAStructureSurfaceForm qa, boolean skipPronounAnswer) {
        final List<int[]> attachments = new ArrayList<>();
        for (QuestionStructure qs : qa.getQuestionStructures()) {
            final int predicateIndex = qs.predicateIndex;
            final Category category = qs.category;
            final ImmutableMap<Integer, ImmutableList<Integer>> qDeps = qs.otherDependencies;

            // Get PP child (for PP arg).
            int ppId = -1;
            if (category.getArgument(qs.targetArgNum).equals(Category.PP)  && qDeps.containsKey(qs.targetArgNum)) {
                ppId = qDeps.get(qs.targetArgNum).get(0);
            }

            // Add Attachment(q).
            qDeps.values().forEach(argList -> argList
                    .forEach(argId -> attachments.add(new int[] { predicateIndex, argId })));

            // Add attachment (q, a) (target dependency).
            Set<Integer> skipArgIds = new HashSet<>();
            if (skipPronounAnswer) {
                skipArgIds.addAll(getPronounArgumentIds(qa));
            }
            final int qaDepHead = ppId >= 0 ? ppId : predicateIndex;
            qa.getAnswerStructures().stream()
                    .flatMap(astr -> astr.argumentIndices.stream())
                    .distinct()
                    .filter(id -> !skipArgIds.contains(id))
                    .forEach(argId -> attachments.add(new int[]{ qaDepHead, argId }));
        }
        return attachments;
    }

}
