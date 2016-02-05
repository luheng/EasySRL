package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class for error analysis.
 * Created by luheng on 1/31/16.
 */
public class AnalysisHelper {

    public static Results evaluateQuestionCoveredDependencies(final Parse pred, final Parse gold,
                                                              final List<String> words,
                                                              final QuestionGenerator questionGenerator) {
        Set<ResolvedDependency> predDeps = pred.dependencies.stream()
                .filter(dep -> isCoveredByQuestion(dep, words, pred, questionGenerator))
                .collect(Collectors.toSet());
        Set<ResolvedDependency> goldDeps = gold.dependencies.stream()
                .filter(dep -> isCoveredByQuestion(dep, words, gold, questionGenerator))
                .collect(Collectors.toSet());
        return CcgEvaluation.evaluate(predDeps, goldDeps);
    }

    public static boolean isCoveredByQuestion(final ResolvedDependency dependency,
                                              final List<String> words,
                                              final Parse parse,
                                              final QuestionGenerator questionGenerator) {

        List<String> question = questionGenerator.generateQuestion(dependency, words, parse).questionWords;
        return question != null && question.size() > 0;
    }

}
