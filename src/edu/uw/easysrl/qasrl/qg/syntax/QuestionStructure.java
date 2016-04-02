package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CCG structure encoded in queryPrompt:
 *      category, argnum, queryPrompt dependency (dependency of other core args used in the queryPrompt).
 * CCG structure encoded in answer:
 *      argument id list, other dependencies used to generate the answer span.
 * Created by luheng on 3/19/16.
 */
public class QuestionStructure {
    public final int predicateIndex;
    public final Category category;
    public final int targetArgNum;
    public final ImmutableMap<Integer, ImmutableList<Integer>> otherDependencies;

    public QuestionStructure(int predId, Category category, int argNum, Collection<ResolvedDependency> otherDeps) {
        this.predicateIndex = predId;
        this.category = category;
        this.targetArgNum = argNum;
        this.otherDependencies = otherDeps.stream()
                .filter(dep -> dep.getHead() == predId && dep.getArgNumber() != argNum)
                .collect(Collectors.groupingBy(ResolvedDependency::getArgNumber))
                .entrySet().stream()
                .collect(GuavaCollectors.toImmutableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(ResolvedDependency::getArgumentIndex)
                                .distinct().sorted()
                                .collect(GuavaCollectors.toImmutableList())));
    }

    /**
     * For convenience.
     * @param qaList: Q/A pairs sharing the same queryPrompt structure.
     */
    public QuestionStructure(final List<QuestionAnswerPair> qaList) {
        final QuestionAnswerPair qa = qaList.get(0);
        this.predicateIndex = qa.getPredicateIndex();
        this.category = qa.getPredicateCategory();
        this.targetArgNum = qa.getArgumentNumber();
        this.otherDependencies = qaList.get(0).getQuestionDependencies().stream()
                .filter(dep -> dep.getHead() == predicateIndex && dep.getArgNumber() != targetArgNum)
                .collect(Collectors.groupingBy(ResolvedDependency::getArgNumber))
                .entrySet().stream()
                .collect(GuavaCollectors.toImmutableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(ResolvedDependency::getArgumentIndex)
                                .distinct().sorted()
                                .collect(GuavaCollectors.toImmutableList())));
    }

    /**
     * * Return all the dependencies that's relevant to this structure.
     */
    public ImmutableSet<ResolvedDependency> filter(Collection<ResolvedDependency> dependencies) {
        return dependencies.stream()
                .filter(d -> d.getHead() == predicateIndex &&
                             d.getCategory() == category &&
                             d.getArgNumber() == targetArgNum)
                .collect(GuavaCollectors.toImmutableSet());
    }

    public String toString(final ImmutableList<String> words) {
        return String.format("%d:%s_%s.%d", predicateIndex, words.get(predicateIndex), category, targetArgNum)
                + "\t"
                + otherDependencies.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(e -> String.format("%d:%s", e.getKey(), e.getValue().stream()
                                .map(String::valueOf).collect(Collectors.joining(","))))
                        .collect(Collectors.joining("_"));
    }
}
