package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class BasicNoun extends Noun {

    /* public API */

    // overrides

    @Override
    public ImmutableList<String> getCompletePhrase() {
        return words;
    }

    // transformers -- subclasses should override

    @Override
    public BasicNoun withCase(Case caseMarking) {
        return this.withCase(Optional.of(caseMarking));
    }

    @Override
    public BasicNoun withCase(Optional<Case> caseMarking) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgPreds(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words);
    }

    @Override
    public BasicNoun withNumber(Number number) {
        return this.withNumber(Optional.of(number));
    }

    @Override
    public BasicNoun withNumber(Optional<Number> number) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgPreds(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words);
    }

    @Override
    public BasicNoun withGender(Gender gender) {
        return this.withGender(Optional.of(gender));
    }

    @Override
    public BasicNoun withGender(Optional<Gender> gender) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgPreds(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words);
    }

    @Override
    public BasicNoun withPerson(Person person) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgPreds(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words);
    }

    @Override
    public BasicNoun withDefiniteness(Definiteness definiteness) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgPreds(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words);
    }

    /* protected methods and fields */

    protected BasicNoun(String predicate,
                        Category predicateCategory,
                        ImmutableMap<Integer, Predication> argPreds,
                        Optional<Case> caseMarking,
                        Optional<Number> number,
                        Optional<Gender> gender,
                        Person person,
                        Definiteness definiteness,
                        ImmutableList<String> words) {
        super(predicate, predicateCategory, argPreds, caseMarking, number, gender, person, definiteness);
        this.words = words;
    }

    /* private fields */

    private final ImmutableList<String> words;
}
