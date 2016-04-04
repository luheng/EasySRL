package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luheng on 4/3/16.
 */
public class ConstraintExtractorUtils {
    /**
     * 1. Verb argument: ((S\NP)/PP)/NP -> return < qa.predicateIndex, qa.otherDependencies[qa.targetArgnum] >
     * 2. Verb adjunct:  ((S\NP)\(S\NP))/NP -> return < qa.predicateIndex, qa.otherDependencies[2] >, assert cat[2]=S\NP
     * 3. Noun adjunct:  (NP\NP)/NP -> return < qa.predicateIndex, qa.otherDependencies[1] >, assert cat[1]=NP
     * @param qa
     * @return
     */
    static List<int[]> getPPAttachments(QAStructureSurfaceForm qa) {
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
            // System.out.println(qa.getQuestion() + "\t" + qa.getAnswer() + "\t" + isPPArg + "\t" + ppId + "\t"
            //                    + DebugPrinter.getShortListString(qa.getAnswerStructures().get(0).argumentIndices));
            if (ppId >= 0) {
                qa.getAnswerStructures().stream()
                        .flatMap(astr -> astr.argumentIndices.stream())
                        .distinct()
                        .forEach(argId -> attachments.add(new int[]{ ppId, argId }));
            }
        }
        return attachments;
    }
}
