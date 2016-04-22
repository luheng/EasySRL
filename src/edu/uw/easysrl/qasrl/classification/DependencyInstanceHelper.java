package edu.uw.easysrl.qasrl.classification;

import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;

/**
 * Created by luheng on 4/22/16.
 */
public class DependencyInstanceHelper {
    static boolean containsDependency(QAStructureSurfaceForm qa, int headId, int argId) {
        for (QuestionStructure qstr : qa.getQuestionStructures()) {
            // Verb-PP argument.
            if (qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) {
                return true;
            }
            // Core/PP-NP dependencies.
            if ((qstr.predicateIndex == headId || qstr.targetPrepositionIndex == headId) &&
                    qa.getAnswerStructures().stream().anyMatch(astr -> astr.argumentIndices.contains(argId))) {
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

}
