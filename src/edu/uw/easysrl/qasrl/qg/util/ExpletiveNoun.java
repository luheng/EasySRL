package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiFunction;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ExpletiveNoun extends Noun {

    /* public API */

    public static final String PRED = "'e'";

    public static final ExpletiveNoun there = new ExpletiveNoun(PRED,
                                                                Category.valueOf("NP[thr]"),
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Person.THIRD,
                                                                Definiteness.DEFINITE,
                                                                "there");
    public static final ExpletiveNoun it = new ExpletiveNoun(PRED,
                                                             Category.valueOf("NP[expl]"),
                                                             Optional.empty(),
                                                             Optional.of(Number.SINGULAR),
                                                             Optional.empty(),
                                                             Person.THIRD,
                                                             Definiteness.DEFINITE,
                                                             "it");

    // overrides

    @Override
    public ExpletiveNoun transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return this;
    }

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        assert desiredCategory.matches(getPredicateCategory()) && getPredicateCategory().matches(desiredCategory)
            : "must want an expletive NP if getting phrase of expletive NP";
        return ImmutableList.of(word);
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return ImmutableSet.of();
    }

    // transformers -- subclasses should override

    @Override
    public ExpletiveNoun withCase(Case caseMarking) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withCase(Optional<Case> caseMarking) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withNumber(Number number) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withNumber(Optional<Number> number) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withGender(Gender gender) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withGender(Optional<Gender> gender) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withPerson(Person person) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withDefiniteness(Definiteness definiteness) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    /* private methods and fields */

    private ExpletiveNoun(String predicate,
                          Category predicateCategory,
                          Optional<Case> caseMarking,
                          Optional<Number> number,
                          Optional<Gender> gender,
                          Person person,
                          Definiteness definiteness,
                          String word) {
        super(predicate, predicateCategory, ImmutableMap.of(), caseMarking, number, gender, person, definiteness);
        this.word = word;
    }

    private final String word;
}
