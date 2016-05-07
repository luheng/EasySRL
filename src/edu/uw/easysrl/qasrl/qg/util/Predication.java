package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

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
        VERB, NOUN
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
            assert false; // this shouldn't happen anyway. if it does we have a convenient fallback.
            resolveArguments();
        }
        return args;
    }

    // public final Gap elide() {
    //     return new Gap(predicateCategory);
    // }

    /* abstract methods */

    /**
     * Gets the phrase representation of the predication, together with its "internal arguments",
     * i.e., for verbs, not including the subject, for modifiers, not including the thing being modified.
     * Indeed, perhaps we can think of all of these things as "subjects" yes?
     * (For a clause, I have in mind that this would be the whole clause.)
     * It seems that working directly with linguistically-motivated text generation is affecting my linguistic sensibilities...
     * Aha, and for nouns, the place of "subject" is with the determiner. Just look at "Elwood's flouting of the law"!
     *   internal is N/N'/NP as opposed to DP.
     *   internal is VP/I' as opposed to IP. hmm..
     */
    // public abstract ImmutableList<String> getInternalPhrase();

    /**
     * This phrase will include all of the arguments, including external ones,
     * and will include determiners and such for nouns. maybe?
     * actually... consider passing in a desired category?
     */
    public abstract ImmutableList<String> getPhrase();

    // consider
    // public abstract ImmutableList<String> getPhrase(Category desiredCategory);

    public abstract QuestionData getQuestionData();

    /* protected methods and fields */

    protected void resolveArguments() {
        this.args = argSuppliers.entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().get()));
    }

    protected Predication(String predicate,
                          Category predicateCategory,
                          ImmutableMap<Integer, Supplier<ImmutableList<Argument>>> argSuppliers) {
        this.predicate = predicate;
        this.predicateCategory = predicateCategory;
        this.args = null;
    }

    /* private fields */

    private final String predicate;
    private final Category predicateCategory;
    // final for all intents and purposes, but has to be set after all arguments are created
    private ImmutableMap<Integer, ImmutableList<Argument>> args;
}
