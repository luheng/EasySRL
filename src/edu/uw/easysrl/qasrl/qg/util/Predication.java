package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.Parse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Convenience class for predicate--argument structures.
 *
 * Created by julianmichael on 3/18/16.
 */
public abstract class Predication {

    public static enum Type {
        VERB, NOUN;

        public static Optional<Type> getTypeForArgCategory(Category category) {
            if(Category.valueOf("S\\NP").matches(category)) {
                return Optional.of(VERB);
            } else if(Category.NP.matches(category)) {
                return Optional.of(NOUN);
            } else {
                return Optional.empty();
            }
        }
    }

    /* public API */

    public final String getPredicate() {
        return predicate;
    }

    public final Category getPredicateCategory() {
        return predicateCategory;
    }

    public ImmutableMap<Integer, ImmutableList<Argument>> getArgs() {
        if(args == null) {
            args = argSupplier.get();
        }
        return args;
    }

    // public final Gap elide() {
    //     return new Gap(predicateCategory);
    // }

    /* abstract methods */

    public abstract ImmutableList<String> getPhrase();
    // consider
    // public abstract ImmutableList<String> getPhrase(Category desiredCategory);

    /* protected methods and fields */

    protected Predication(String predicate,
                          Category predicateCategory,
                          Supplier<ImmutableMap<Integer, ImmutableList<Argument>>> argSupplier) {
        this.predicate = predicate;
        this.predicateCategory = predicateCategory;
        this.argSupplier = argSupplier;
        this.args = null;
    }

    protected Predication(String predicate,
                          Category predicateCategory,
                          ImmutableMap<Integer, ImmutableList<Argument>> args) {
        this.predicate = predicate;
        this.predicateCategory = predicateCategory;
        this.argSupplier = null;
        this.args = args;
    }

    /* private fields */

    private final String predicate;
    private final Category predicateCategory;
    private final Supplier<ImmutableMap<Integer, ImmutableList<Argument>>> argSupplier;
    // final for all intents and purposes, but has to be set after all arguments are created
    private ImmutableMap<Integer, ImmutableList<Argument>> args;
}
