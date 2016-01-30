package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Evaluating with labeled CCG dependency.
 * Created by luheng on 1/5/16.
 */
public class CcgEvaluation {

    public static Results evaluate(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold) {
        Set<String> goldSet = gold.stream().map(CcgEvaluation::dependencyToString).collect(Collectors.toSet());
        int numMatches = (int) predicted.stream().filter(dep -> goldSet.contains(dependencyToString(dep))).count();
        return new Results(predicted.size(), numMatches, gold.size());
    }

    public static List<Results> evaluateNBest(final List<Parse> parses, final Set<ResolvedDependency> gold) {
        Set<String> goldSet = gold.stream().map(CcgEvaluation::dependencyToString).collect(Collectors.toSet());
        return parses.stream().map(parse -> {
            int numMatches = (int) parse.dependencies.stream()
                    .filter(dep -> goldSet.contains(dependencyToString(dep))).count();
            return new Results(parse.dependencies.size(), numMatches, gold.size());
        }).collect(Collectors.toList());
    }

    public static Accuracy evaluateTags(final List<Category> predicted, final List<Category> gold) {
        Accuracy accuracy = new Accuracy();
        IntStream.range(0, predicted.size()).forEach(i -> accuracy.add(predicted.get(i) == gold.get(i)));
        return accuracy;
    }

    public static String dependencyToString(final ResolvedDependency dep) {
        return dep.getHead() + "_" + dep.getArgument() + "_" + dep.getCategory() + "." + dep.getArgNumber();
    }
}
