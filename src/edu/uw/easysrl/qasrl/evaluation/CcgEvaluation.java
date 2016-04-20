package edu.uw.easysrl.qasrl.evaluation;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
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

    public static Results evaluateUnlabeled(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold) {
        int numMatches = (int) gold.stream()
                .filter(goldDep -> predicted.stream().anyMatch(predDep -> unlabeledMatch(goldDep, predDep)))
                .count();
        return new Results(predicted.size(), numMatches, gold.size());
    }

    // FIXME: might cause problem when both X->Y and Y->X exist.
    public static Results evaluateUndirected(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold) {
        int numMatches = (int) gold.stream()
                .filter(goldDep -> predicted.stream().anyMatch(predDep -> undirectedMatch(goldDep, predDep)))
                .count();
        return new Results(predicted.size(), numMatches, gold.size());
    }

    public static boolean unlabeledMatch(final ResolvedDependency dep1, final ResolvedDependency dep2) {
        return dep1.getHead() == dep2.getHead() && dep1.getArgument() == dep2.getArgument();
    }

    public static boolean undirectedMatch(final ResolvedDependency dep1, final ResolvedDependency dep2) {
        return (dep1.getHead() == dep2.getHead() && dep1.getArgument() == dep2.getArgument()) ||
                (dep1.getHead() == dep2.getArgument() && dep1.getArgument() == dep2.getHead());
    }

    /**
     * Get the dependencies present in one but not the other.
     * Interpretable as either the "false positives" that hurt precision of `one`
     * or the "false negatives" that hurt the recall of `two`.
     */
    public static Set<ResolvedDependency> difference(final Set<ResolvedDependency> one, final Set<ResolvedDependency> two) {
        Set<String> twoSet = two.stream().map(CcgEvaluation::dependencyToString).collect(Collectors.toSet());
        Set<ResolvedDependency> extrasInOne =
            one.stream().filter(dep -> !twoSet.contains(dependencyToString(dep))).collect(Collectors.toSet());
        return extrasInOne;
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
