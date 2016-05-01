package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

/**
 * Created by luheng on 4/22/16.
 */
public class DependencyInstanceHelper {

    static boolean containsDependency(final ImmutableList<String> sentence, final QAStructureSurfaceForm qa,
                                      int headId, int argId) {
        for (QuestionStructure qstr : qa.getQuestionStructures()) {
            // Verb-PP argument (undirected).
            /*
            if ((qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) ||
                (qstr.predicateIndex == argId && qstr.targetPrepositionIndex == headId)) {
                return true;
            }
            */
            // Core/PP-NP dependencies.
            if ((qstr.predicateIndex == headId || qstr.targetPrepositionIndex == headId) &&
                    qa.getAnswerStructures().stream().anyMatch(astr -> astr.argumentIndices.contains(argId))) {
                // Hack to get around answer span error.
                if (qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) {
                    return false;
                }
                return true;
            }
            // NP adjunct dependencies.
            if (qa.getAnswerStructures().stream()
                    .flatMap(astr -> astr.adjunctDependencies.stream())
                    .anyMatch(d -> d.getHead() == headId && d.getArgument() == argId)) {
                return true;
            }
        }
        return false;
    }

    static String getDependencyContainsType(ScoredQuery<QAStructureSurfaceForm> query, int headId, int argId) {
        for (QAStructureSurfaceForm qa : query.getQAPairSurfaceForms()) {
            for (QuestionStructure qstr : qa.getQuestionStructures()) {
                // Verb-PP argument (undirected).
                /*
                if ((qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) ||
                        (qstr.predicateIndex == argId && qstr.targetPrepositionIndex == headId)) {
                    return "pp_arg_in_question";
                }*/
                // Core/PP-NP dependencies.
                if ((qstr.predicateIndex == headId || qstr.targetPrepositionIndex == headId) &&
                        qa.getAnswerStructures().stream().anyMatch(astr -> astr.argumentIndices.contains(argId))) {
                    return "in_qa";
                }
                // NP adjunct dependencies.
                if (qa.getAnswerStructures().stream()
                        .flatMap(astr -> astr.adjunctDependencies.stream())
                        .anyMatch(d -> d.getHead() == headId && d.getArgument() == argId)) {
                    return "in_answer";
                }
            }
        }
        return "";
    }

}
