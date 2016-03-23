package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Convenience class for predicate--argument structures.
 *
 * Created by julianmichael on 3/18/16.
 */
public abstract class Predication {


    /* public API */

    public final String getPredicate() {
        return predicate;
    }

    public final Category getPredicateCategory() {
        return predicateCategory;
    }

    public final Gap elide() {
        return new Gap(predicateCategory);
    }

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
     * and will include determiners and such for nouns.
     * actually... consider passing in a desired category?
     */
    public abstract ImmutableList<String> getCompletePhrase();

    public abstract QuestionData getQuestionData();

    /* protected methods and fields */

    protected ImmutableMap<Integer, Predication> getArgPreds() {
        return argPreds;
    }

    protected Predication(String predicate,
                          Category predicateCategory,
                          ImmutableMap<Integer, Predication> argPreds) {
        this.predicate = predicate;
        this.predicateCategory = predicateCategory;
        this.argPreds = argPreds;
    }

    /* private fields */

    private final Category predicateCategory;
    private final String predicate;
    private final ImmutableMap<Integer, Predication> argPreds;
}
