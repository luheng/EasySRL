package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableMap;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * CCG structure encoded in question:
 *      category, argnum, question dependency (dependency of other core args used in the question).
 * CCG structure encoded in answer:
 *      argument id list, other dependencies used to generate the answer span.
 * Created by luheng on 3/19/16.
 */
public class QuestionStructure {
    public final int predicateIndex;
    public final Category category;
    public final int targetArgNum;
    public final ImmutableMap<Integer, Integer> otherDependencies;

    public QuestionStructure(int predId, Category category, int argNum, Collection<ResolvedDependency> otherDeps) {
        this.predicateIndex = predId;
        this.category = category;
        this.targetArgNum = argNum;
        this.otherDependencies = ImmutableMap.copyOf(otherDeps.stream()
                .filter(dep -> dep.getHead() == predId && dep.getArgNumber() != argNum)
                .collect(Collectors.toMap(ResolvedDependency::getArgNumber, ResolvedDependency::getArgument)));
    }
}
