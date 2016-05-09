package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiFunction;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap;

import static edu.uw.easysrl.util.GuavaCollectors.*;

/**
 * Convenience class for dealing with pronouns,
 * when we want to enforce nominative/accusative agreement,
 * perhaps among other things as well in the future.
 *
 * Created by julianmichael on 2/6/16.
 */
public final class Pronoun extends Noun {

    public static final String PRED = "'pro'";

    public static Optional<Pronoun> fromString(String str) {
        return Optional.ofNullable(pronounsByLowerCaseString.get(str.toLowerCase()));
    }

    /**
     * Any pronoun should be guaranteed to find at least one string realization given our little lexicon below.
     * Ideally it will be completely specified, but if not we'll just choose the first that comes.
     */
    public String toString() {
        Optional<String> result = pronounsByStringForMatching.keySet()
            .stream()
            .filter(str -> this.matches(pronounsByStringForMatching.get(str)))
            .findFirst();
        if(result.isPresent()) {
            return result.get();
        } else {
            System.err.println(toStringDetail());
            assert false;
            return null;
        }
    }

    public String toStringDetail() {
        return String.format("case: %s\nnum: %s\ngen: %s\npers: %s\n def: %s",
                             getCase().map(x -> x.name()),
                             getNumber().map(x -> x.name()),
                             getGender().map(x -> x.name()),
                             getPerson().name(),
                             getDefiniteness().name());
    }

    /**
     * Returns whether this pronoun meets all the requirements of the specified one
     * (i.e., this one matches and is at least as specific, i.e., may have populated fields
     * where the given one is empty).
     */
    public boolean matches(Pronoun pron) {
        boolean caseMatch = pron.getCase().map(x -> this.getCase().map(y -> x == y).orElse(false)).orElse(true);
        boolean numMatch = pron.getNumber().map(x -> this.getNumber().map(y -> x == y).orElse(false)).orElse(true);
        boolean genMatch = pron.getGender()
            .map(x -> this.getGender()
            .map(y -> x == Gender.ANIMATE ? y != Gender.INANIMATE : x == y)
            .orElse(false)).orElse(true);
        boolean persMatch = pron.getPerson() == null || pron.getPerson() == this.getPerson();
        boolean defMatch = pron.getDefiniteness() == this.getDefiniteness();
        return caseMatch && numMatch && genMatch && persMatch && defMatch;
    }

    @Override
    public ImmutableList<String> getPhrase() {
        return ImmutableList.of(this.toString());
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return ImmutableSet.of();
    }

    @Override
    public Pronoun transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return this;
    }

    // transformers

    @Override
    public Pronoun withCase(Case caseMarking) {
        return this.withCase(Optional.of(caseMarking));
    }

    @Override
    public Pronoun withCase(Optional<Case> caseMarking) {
        return new Pronoun(caseMarking, getNumber(), getGender(), getPerson(), getDefiniteness());
    }

    @Override
    public Pronoun withNumber(Number number) {
        return this.withNumber(Optional.of(number));
    }

    @Override
    public Pronoun withNumber(Optional<Number> number) {
        return new Pronoun(getCase(), number, getGender(), getPerson(), getDefiniteness());
    }

    @Override
    public Pronoun withGender(Gender gender) {
        return this.withGender(Optional.of(gender));
    }

    @Override
    public Pronoun withGender(Optional<Gender> gender) {
        return new Pronoun(getCase(), getNumber(), gender, getPerson(), getDefiniteness());
    }

    @Override
    public Pronoun withPerson(Person person) {
        return new Pronoun(getCase(), getNumber(), getGender(), person, getDefiniteness());
    }

    @Override
    public Pronoun withDefiniteness(Definiteness definiteness) {
        return new Pronoun(getCase(), getNumber(), getGender(), getPerson(), definiteness);
    }

    /* protected methods */

    protected Pronoun(Optional<Case> caseMarking, Optional<Number> number, Optional<Gender> gender, Person person,
                      Definiteness definiteness) {
        super(PRED, Category.NP, ImmutableMap.of(), caseMarking, number, gender, person, definiteness);
    }

    /* private fields, methods, etc. */

    private static final Map<String, Pronoun> pronounsByString = new ImmutableMap.Builder<String, Pronoun>()
        .put("I", new Pronoun(Optional.of(Case.NOMINATIVE),
                              Optional.of(Number.SINGULAR),
                              Optional.of(Gender.ANIMATE),
                              Person.FIRST,
                              Definiteness.DEFINITE))
        .put("me", new Pronoun(Optional.of(Case.ACCUSATIVE),
                               Optional.of(Number.SINGULAR),
                               Optional.of(Gender.ANIMATE),
                               Person.FIRST,
                               Definiteness.DEFINITE))
        .put("we", new Pronoun(Optional.of(Case.NOMINATIVE),
                               Optional.of(Number.PLURAL),
                               Optional.of(Gender.ANIMATE),
                               Person.FIRST,
                               Definiteness.DEFINITE))
        .put("us", new Pronoun(Optional.of(Case.ACCUSATIVE),
                               Optional.of(Number.PLURAL),
                               Optional.of(Gender.ANIMATE),
                               Person.FIRST,
                               Definiteness.DEFINITE))
        .put("you", new Pronoun(Optional.empty(),
                                Optional.of(Number.PLURAL),
                                Optional.of(Gender.ANIMATE),
                                Person.SECOND,
                                Definiteness.DEFINITE))
        .put("he", new Pronoun(Optional.of(Case.NOMINATIVE),
                               Optional.of(Number.SINGULAR),
                               Optional.of(Gender.MALE),
                               Person.THIRD,
                               Definiteness.DEFINITE))
        .put("she", new Pronoun(Optional.of(Case.NOMINATIVE),
                                Optional.of(Number.SINGULAR),
                                Optional.of(Gender.FEMALE),
                                Person.THIRD,
                                Definiteness.DEFINITE))
        .put("him", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                Optional.of(Number.SINGULAR),
                                Optional.of(Gender.MALE),
                                Person.THIRD,
                                Definiteness.DEFINITE))
        .put("her", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                Optional.of(Number.SINGULAR),
                                Optional.of(Gender.FEMALE),
                                Person.THIRD,
                                Definiteness.DEFINITE))
        .put("it", new Pronoun(Optional.empty(),
                               Optional.of(Number.SINGULAR),
                               Optional.of(Gender.INANIMATE),
                               Person.THIRD,
                               Definiteness.DEFINITE))
        .put("they", new Pronoun(Optional.of(Case.NOMINATIVE),
                                 Optional.of(Number.PLURAL),
                                 Optional.empty(),
                                 Person.THIRD,
                                 Definiteness.DEFINITE))
        .put("them", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                 Optional.of(Number.PLURAL),
                                 Optional.empty(),
                                 Person.THIRD,
                                 Definiteness.DEFINITE))
        .put("something", new Pronoun(Optional.empty(),
                                      Optional.of(Number.SINGULAR),
                                      Optional.of(Gender.INANIMATE),
                                      Person.THIRD,
                                      Definiteness.INDEFINITE))
        .put("someone", new Pronoun(Optional.empty(),
                                    Optional.of(Number.SINGULAR),
                                    Optional.of(Gender.ANIMATE),
                                    Person.THIRD,
                                    Definiteness.INDEFINITE))
        .put("what", new Pronoun(Optional.empty(),
                                 Optional.empty(),
                                 Optional.of(Gender.INANIMATE),
                                 Person.THIRD,
                                 Definiteness.FOCAL))
        .put("who", new Pronoun(Optional.of(Case.NOMINATIVE),
                                Optional.empty(),
                                Optional.of(Gender.ANIMATE),
                                Person.THIRD,
                                Definiteness.FOCAL))
        .put("whom", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                Optional.empty(),
                                Optional.of(Gender.ANIMATE),
                                Person.THIRD,
                                Definiteness.FOCAL))
        .build();
    private static final ImmutableMap<String, Pronoun> pronounsByLowerCaseString = pronounsByString
        .entrySet()
        .stream()
        .collect(toImmutableMap(e -> e.getKey().toLowerCase(), ImmutableMap.Entry::getValue));

    private static final Map<String, Pronoun> pronounsByStringForMatching = new ImmutableMap.Builder<String, Pronoun>()
        .put("I", new Pronoun(Optional.of(Case.NOMINATIVE),
                              Optional.of(Number.SINGULAR),
                              Optional.of(Gender.ANIMATE),
                              Person.FIRST,
                              Definiteness.DEFINITE))
        .put("me", new Pronoun(Optional.of(Case.ACCUSATIVE),
                               Optional.of(Number.SINGULAR),
                               Optional.of(Gender.ANIMATE),
                               Person.FIRST,
                               Definiteness.DEFINITE))
        .put("we", new Pronoun(Optional.of(Case.NOMINATIVE),
                               Optional.of(Number.PLURAL),
                               Optional.of(Gender.ANIMATE),
                               Person.FIRST,
                               Definiteness.DEFINITE))
        .put("us", new Pronoun(Optional.of(Case.ACCUSATIVE),
                               Optional.of(Number.PLURAL),
                               Optional.of(Gender.ANIMATE),
                               Person.FIRST,
                               Definiteness.DEFINITE))
        // can be matched by singular things
        .put("you", new Pronoun(Optional.empty(),
                                Optional.empty(),
                                Optional.of(Gender.ANIMATE),
                                Person.SECOND,
                                Definiteness.DEFINITE))
        .put("he", new Pronoun(Optional.of(Case.NOMINATIVE),
                               Optional.of(Number.SINGULAR),
                               Optional.of(Gender.MALE),
                               Person.THIRD,
                               Definiteness.DEFINITE))
        .put("she", new Pronoun(Optional.of(Case.NOMINATIVE),
                                Optional.of(Number.SINGULAR),
                                Optional.of(Gender.FEMALE),
                                Person.THIRD,
                                Definiteness.DEFINITE))
        .put("him", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                Optional.of(Number.SINGULAR),
                                Optional.of(Gender.MALE),
                                Person.THIRD,
                                Definiteness.DEFINITE))
        .put("her", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                Optional.of(Number.SINGULAR),
                                Optional.of(Gender.FEMALE),
                                Person.THIRD,
                                Definiteness.DEFINITE))
        .put("it", new Pronoun(Optional.empty(),
                               Optional.of(Number.SINGULAR),
                               Optional.of(Gender.INANIMATE),
                               Person.THIRD,
                               Definiteness.DEFINITE))
        // to allow for a semantically animate, singular, genderless "they" and "them"
        .put("them", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                 Optional.empty(),
                                 Optional.empty(),
                                 Person.THIRD,
                                 Definiteness.DEFINITE))
        // can match against case unknown
        .put("they", new Pronoun(Optional.empty(),
                                 Optional.empty(),
                                 Optional.empty(),
                                 Person.THIRD,
                                 Definiteness.DEFINITE))
        // we also probably want plural things to match to "something" and "someone"
        .put("someone", new Pronoun(Optional.empty(),
                                    Optional.empty(),
                                    Optional.of(Gender.ANIMATE),
                                    null,
                                    Definiteness.INDEFINITE))
        // can match against animateness unknown
        .put("something", new Pronoun(Optional.empty(),
                                      Optional.empty(),
                                      Optional.empty(),
                                      Person.THIRD,
                                      Definiteness.INDEFINITE))
        .put("whom", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                 Optional.empty(),
                                 Optional.of(Gender.ANIMATE),
                                 Person.THIRD,
                                 Definiteness.FOCAL))
        // can match when don't know case or person
        .put("who", new Pronoun(Optional.empty(),
                                Optional.empty(),
                                Optional.of(Gender.ANIMATE),
                                null,
                                Definiteness.FOCAL))
        // can match against animateness unknown
        .put("what", new Pronoun(Optional.empty(),
                                 Optional.empty(),
                                 Optional.empty(),
                                 Person.THIRD,
                                 Definiteness.FOCAL))
        .build();

}
