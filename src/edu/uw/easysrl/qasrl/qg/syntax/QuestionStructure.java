package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Collection;
import java.util.List;
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
    // public final ImmutableMap<Integer, Integer> otherDependencies;
    // For future convenience.
    public final ImmutableSet<Integer> parseIds;
    public final double score;

    public QuestionStructure(int predId, Category category, int argNum, Collection<ResolvedDependency> otherDeps,
                             ImmutableSet<Integer> parseIds, double score) {
        this.predicateIndex = predId;
        this.category = category;
        this.targetArgNum = argNum;
        // TODO: fix illegal access error
        /*
        this.otherDependencies = ImmutableMap.copyOf(otherDeps.stream()
                .filter(dep -> dep.getHead() == predId && dep.getArgNumber() != argNum)
                .collect(Collectors.toMap(ResolvedDependency::getArgNumber, ResolvedDependency::getArgument)));
                */
        this.parseIds = parseIds;
        this.score = score;
    }

    /**
     * For convenience.
     * @param qaList: Q/A pairs sharing the same question structure.
     */
    public QuestionStructure(final List<QuestionAnswerPair> qaList) {
        this.predicateIndex = qaList.get(0).getPredicateIndex();
        this.category = qaList.get(0).getPredicateCategory();
        this.targetArgNum = qaList.get(0).getArgumentNumber();
        // TODO: fix illegal access error
        /*
        this.otherDependencies = ImmutableMap.copyOf(otherDeps.stream()
                .filter(dep -> dep.getHead() == predId && dep.getArgNumber() != argNum)
                .collect(Collectors.toMap(ResolvedDependency::getArgNumber, ResolvedDependency::getArgument)));
                */
        this.parseIds = QAPairAggregatorUtils.getParseIds(qaList);
        this.score = QAPairAggregatorUtils.getScore(qaList);
    }
}
