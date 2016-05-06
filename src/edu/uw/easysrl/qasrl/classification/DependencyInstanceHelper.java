package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

/**
 * Created by luheng on 4/22/16.
 */
public class DependencyInstanceHelper {

    static boolean containsDependency(final ImmutableList<String> sentence,
                                      final QAStructureSurfaceForm qa, int headId, int argId) {
        for (QuestionStructure qstr : qa.getQuestionStructures()) {
            // Verb-PP argument (undirected).
            /*
            if ((qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) ||
                (qstr.predicateIndex == argId && qstr.targetPrepositionIndex == headId)) {
                return true;
            }
            */
            // Core/PP-NP dependencies.
            if (qstr.predicateIndex == headId && qstr.targetPrepositionIndex < 0 &&
                    qa.getAnswerStructures().stream().anyMatch(astr -> astr.argumentIndices.contains(argId))) {
                return true;
            }
            if (qstr.targetPrepositionIndex == headId &&
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

    static DependencyInstanceType getDependencyType(ScoredQuery<QAStructureSurfaceForm> query, int headId, int argId) {
        for (QAStructureSurfaceForm qa : query.getQAPairSurfaceForms()) {
            for (QuestionStructure qstr : qa.getQuestionStructures()) {
                // Verb-PP argument (undirected).
                /*
                if (qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) {
                    return DependencyInstanceType.VerbPP;
                }
                if (qstr.predicateIndex == argId && qstr.targetPrepositionIndex == headId) {
                    return DependencyInstanceType.PPGovernorVerb;
                }*/
                // Core/PP-NP dependencies.
                if (qstr.predicateIndex == headId && qstr.targetPrepositionIndex < 0 &&
                        qa.getAnswerStructures().stream().anyMatch(astr -> astr.argumentIndices.contains(argId))) {
                    return DependencyInstanceType.VerbArgument;
                }
                if (qstr.targetPrepositionIndex == headId &&
                        qa.getAnswerStructures().stream().anyMatch(astr -> astr.argumentIndices.contains(argId))) {
                    // Hack to get around answer span error.
                    //if (qstr.predicateIndex == headId && qstr.targetPrepositionIndex == argId) {
                    //    return DependencyInstanceType.NONE;
                    //}
                    return DependencyInstanceType.VerbArgument;
                }
                // NP adjunct dependencies.
                for (AnswerStructure astr : qa.getAnswerStructures()) {
                    for (ResolvedDependency dep : astr.adjunctDependencies) {
                        if (dep.getHead() == headId && dep.getArgument() == argId) {
                            return (argId < headId) ? DependencyInstanceType.PPGovernor : DependencyInstanceType.PPObject;
                        }
                        /*if (dep.getHead() == headId && qstr.predicateIndex == argId) {
                            return DependencyInstanceType.PPGovernorVerb;
                        }
                        if (dep.getHead() == argId && qstr.predicateIndex == headId) {
                            return DependencyInstanceType.VerbPP;
                        }*/
                    }
                }
                /*
                if (qa.getAnswerStructures().stream()
                        .flatMap(astr -> astr.adjunctDependencies.stream())
                        .anyMatch(d -> )) {
                    return (argId < headId) ? DependencyInstanceType.PPGovernor : DependencyInstanceType.PPObject;
                }*/
            }
        }
        return DependencyInstanceType.NONE;
    }

}
