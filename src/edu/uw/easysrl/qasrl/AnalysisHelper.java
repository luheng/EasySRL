package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luheng on 1/31/16.
 */
public class AnalysisHelper {

    public static Results evaluateQuestionCoveredDependencies(final Parse pred, final Parse gold,
                                                              final List<String> words,
                                                              final QuestionGenerator questionGenerator) {
        Set<ResolvedDependency> predDeps = pred.dependencies.stream()
                .filter(dep -> isCoveredByQuestion(dep, words, pred.categories, pred.dependencies, questionGenerator))
                .collect(Collectors.toSet());
        Set<ResolvedDependency> goldDeps = gold.dependencies.stream()
                .filter(dep -> isCoveredByQuestion(dep, words, gold.categories, gold.dependencies, questionGenerator))
                .collect(Collectors.toSet());
        return CcgEvaluation.evaluate(predDeps, goldDeps);
    }

    public static boolean isCoveredByQuestion(final ResolvedDependency dependency,
                                              final List<String> words,
                                              final List<Category> categories,
                                              final Set<ResolvedDependency> ccgDeps,
                                              final QuestionGenerator questionGenerator) {

        List<String> question = questionGenerator.generateQuestion(dependency, words, categories, ccgDeps);
        return question != null && question.size() > 0;
    }

}
