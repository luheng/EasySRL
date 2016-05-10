package edu.uw.easysrl.qasrl.qg.util;

import java.util.function.BiFunction;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public final class Clause extends Predication {

    // public static enum Type {
    //     DCL, EM, FOR, QEM;
    // }

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        assert getPredicateCategory().isFunctionInto(desiredCategory)
            : "desired category has to be a clause of the right kind";
        // TODO
        return null;
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        // TODO
        return ImmutableSet.of();
    }

    @Override
    public Clause transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        // TODO
        return this;
    }

    private Clause(String predicate, Category predicateCategory, ImmutableMap<Integer, ImmutableList<Argument>> args) {
        super(predicate, predicateCategory, args);
    }
}
