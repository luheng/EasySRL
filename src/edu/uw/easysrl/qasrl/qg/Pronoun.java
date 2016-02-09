package edu.uw.easysrl.qasrl.qg;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * Convenience class for dealing with pronouns,
 * when we want to enforce nominative/accusative agreement,
 * perhaps among other things as well in the future.
 *
 * Created by julianmichael on 2/6/16.
 */
final class Pronoun {
    public static enum Case {
        NOMINATIVE, ACCUSATIVE
    }
    public static enum Number {
        SINGULAR, PLURAL
    }
    public static enum Gender {
        FEMALE, MALE, NEUTER
    }
    public static enum Person {
        FIRST, SECOND, THIRD
    }

    public static final Map<String, Pronoun> pronounsByString = new HashMap<>();
    public static final Map<String, Pronoun> pronounsByLowerCaseString = new HashMap<>();
    static {
        pronounsByString.put("I", new Pronoun(Optional.of(Case.NOMINATIVE),
                                              Optional.of(Number.SINGULAR),
                                              Optional.empty(),
                                              Person.FIRST));
        pronounsByString.put("me", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                              Optional.of(Number.SINGULAR),
                                              Optional.empty(),
                                              Person.FIRST));
        pronounsByString.put("we", new Pronoun(Optional.of(Case.NOMINATIVE),
                                               Optional.of(Number.PLURAL),
                                               Optional.empty(),
                                               Person.FIRST));
        pronounsByString.put("us", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                               Optional.of(Number.PLURAL),
                                               Optional.empty(),
                                               Person.FIRST));
        pronounsByString.put("you", new Pronoun(Optional.empty(),
                                                Optional.empty(),
                                                Optional.empty(),
                                                Person.SECOND));
        pronounsByString.put("he", new Pronoun(Optional.of(Case.NOMINATIVE),
                                               Optional.of(Number.SINGULAR),
                                               Optional.of(Gender.MALE),
                                               Person.THIRD));
        pronounsByString.put("she", new Pronoun(Optional.of(Case.NOMINATIVE),
                                                Optional.of(Number.SINGULAR),
                                                Optional.of(Gender.FEMALE),
                                                Person.THIRD));
        pronounsByString.put("him", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                                Optional.of(Number.SINGULAR),
                                                Optional.of(Gender.MALE),
                                                Person.THIRD));
        pronounsByString.put("her", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                                Optional.of(Number.SINGULAR),
                                                Optional.of(Gender.FEMALE),
                                                Person.THIRD));
        pronounsByString.put("it", new Pronoun(Optional.empty(),
                                               Optional.of(Number.SINGULAR),
                                               Optional.of(Gender.NEUTER),
                                               Person.THIRD));
        // yes, there's also a singular "they" with non-neuter gender and singular number,
        // but I'm not including that for now (I don't have or need a facility to do disjunctions on gender).
        pronounsByString.put("they", new Pronoun(Optional.of(Case.NOMINATIVE),
                                                 Optional.of(Number.PLURAL),
                                                 Optional.empty(),
                                                 Person.THIRD));
        pronounsByString.put("them", new Pronoun(Optional.of(Case.ACCUSATIVE),
                                                 Optional.of(Number.PLURAL),
                                                 Optional.empty(),
                                                 Person.THIRD));
        for(String str : pronounsByString.keySet()) {
            pronounsByLowerCaseString.put(str.toLowerCase(), pronounsByString.get(str));
        }
    }


    public static Optional<Pronoun> fromString(String str) {
        return Optional.ofNullable(pronounsByLowerCaseString.get(str.toLowerCase()));
    }

    public final Optional<Case> caseMarking;
    public final Optional<Number> number;
    public final Optional<Gender> gender;
    public final Person person;

    private Pronoun(Optional<Case> caseMarking, Optional<Number> number, Optional<Gender> gender, Person person) {
        this.caseMarking = caseMarking;
        this.number = number;
        this.gender = gender;
        this.person = person;
    }

    /**
     * Returns whether this pronoun meets all the requirements of the specified one
     * (i.e., this one matches and is at least as specific, i.e., may have populated fields
     * where the given one is empty).
     */
    public boolean matches(Pronoun pron) {
        boolean caseMatch = pron.caseMarking.map(x -> this.caseMarking.map(y -> x == y).orElse(false)).orElse(true);
        boolean numMatch = pron.number.map(x -> this.number.map(y -> x == y).orElse(false)).orElse(true);
        boolean genMatch = pron.gender.map(x -> this.gender.map(y -> x == y).orElse(false)).orElse(true);
        boolean persMatch = pron.person == this.person;
        return caseMatch && numMatch && genMatch && persMatch;
    }

    public Pronoun withCase(Case caseMarking) {
        return this.withCase(Optional.ofNullable(caseMarking));
    }
    public Pronoun withCase(Optional<Case> caseMarking) {
        return new Pronoun(caseMarking, this.number, this.gender, this.person);
    }

    public boolean isAnimate() {
        return gender.map(gen -> gen != Gender.NEUTER).orElse(true);
    }

    /**
     * Any pronoun should be guaranteed to find at least one string realization given our little lexicon above.
     * ideally it will be completely specified, but if not we'll just choose the first that comes.
     */
    public String toString() {
        return pronounsByString.keySet()
            .stream()
            .filter(str -> this.matches(pronounsByString.get(str)))
            .findFirst()
            .get();
    }

}
