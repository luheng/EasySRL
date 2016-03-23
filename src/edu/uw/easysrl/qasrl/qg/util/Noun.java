package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class Noun extends Predication {

    /* useful grammar enums */

    public static enum Case {
        NOMINATIVE, ACCUSATIVE
    }

    public static enum Number {
        SINGULAR, PLURAL
    }

    public static enum Gender {
        FEMALE, MALE, ANIMATE, INANIMATE;

        public boolean isAnimate() {
            return this != INANIMATE;
        }
    }

    public static enum Person {
        FIRST, SECOND, THIRD
    }

    public static enum Definiteness {
        DEFINITE, INDEFINITE, FOCAL
    }

    /* public API */

    // overrides

    @Override
    public QuestionData getQuestionData() {
        ImmutableList<String> wh = this.getFocalPronoun()
            .withCase(Case.NOMINATIVE)
            .getCompletePhrase();
        Predication placeholder = this.elide();
        Predication answer = this;
        return new QuestionData(wh, placeholder, answer);
    }

    @Override
    public ImmutableList<String> getCompletePhrase() {
        assert false;
        return null;
    }

    // getters

    public Optional<Case> getCase() {
        return caseMarking;
    }

    public Optional<Number> getNumber() {
        return number;
    }

    public Optional<Gender> getGender() {
        return gender;
    }

    public Person getPerson() {
        return person;
    }

    public Definiteness getDefiniteness() {
        return definiteness;
    }

    // convenience methods

    public final Boolean isAnimate() {
        return gender.map(Gender::isAnimate).orElse(false);
    }

    public final boolean isFocal() {
        return definiteness == Definiteness.FOCAL;
    }

    public final Pronoun getPronoun() {
        return new Pronoun(caseMarking, number, gender, person, definiteness);
    }

    public final Pronoun getIndefinitePronoun() {
        return this.getPronoun()
            .withNumber(Number.SINGULAR)
            .withPerson(Person.THIRD)
            .withDefiniteness(Definiteness.INDEFINITE);
    }

    public final Pronoun getFocalPronoun() {
        return this.getPronoun()
            .withNumber(Number.SINGULAR)
            .withPerson(Person.THIRD)
            .withDefiniteness(Definiteness.FOCAL);
    }

    // transformers -- subclasses should override

    public Noun withCase(Case caseMarking) {
        return this.withCase(Optional.of(caseMarking));
    }

    public Noun withCase(Optional<Case> caseMarking) {
        return new Noun(getPredicate(), getPredicateCategory(), getArgPreds(),
                        caseMarking, number, gender, person, definiteness);
    }

    public Noun withNumber(Number number) {
        return this.withNumber(Optional.of(number));
    }

    public Noun withNumber(Optional<Number> number) {
        return new Noun(getPredicate(), getPredicateCategory(), getArgPreds(),
                        caseMarking, number, gender, person, definiteness);
    }

    public Noun withGender(Gender gender) {
        return this.withGender(Optional.of(gender));
    }

    public Noun withGender(Optional<Gender> gender) {
        return new Noun(getPredicate(), getPredicateCategory(), getArgPreds(),
                        caseMarking, number, gender, person, definiteness);
    }

    public Noun withPerson(Person person) {
        return new Noun(getPredicate(), getPredicateCategory(), getArgPreds(),
                        caseMarking, number, gender, person, definiteness);
    }

    public Noun withDefiniteness(Definiteness definiteness) {
        return new Noun(getPredicate(), getPredicateCategory(), getArgPreds(),
                        caseMarking, number, gender, person, definiteness);
    }

    /* protected methods and fields */

    protected Noun(String predicate,
                   Category predicateCategory,
                   ImmutableMap<Integer, Predication> argPreds,
                   Optional<Case> caseMarking,
                   Optional<Number> number,
                   Optional<Gender> gender,
                   Person person,
                   Definiteness definiteness) {
        super(predicate, predicateCategory, argPreds);
        this.caseMarking = caseMarking;
        this.number = number;
        this.gender = gender;
        this.person = person;
        this.definiteness = definiteness;
    }

    /* private fields */

    private final Optional<Case> caseMarking;
    private final Optional<Number> number;
    private final Optional<Gender> gender;
    private final Person person;
    private final Definiteness definiteness;
}
