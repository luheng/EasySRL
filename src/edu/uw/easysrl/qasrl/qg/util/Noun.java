package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public abstract class Noun extends Predication {

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

    /* factory methods */

    public static Noun getFullNPFromHead(Optional<Integer> headIndexOpt, Parse parse) {
        if(!headIndexOpt.isPresent()) {
            return Pronoun.fromString("something").get();
        }
        final SyntaxTreeNode tree = parse.syntaxTree;
        final int headIndex = headIndexOpt.get();
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);
        final String predicate = TextGenerationHelper.renderString(TextGenerationHelper.getNodeWords(headLeaf, Optional.empty(), Optional.empty()));

        // if it's a pronoun, we're done
        final Optional<Pronoun> pronounOpt = Pronoun.fromString(predicate);
        if(pronounOpt.isPresent()) {
            return pronounOpt.get();
        }

        // otherwise, we have a lot more work to do.

        /* recover the whole noun phrase. */
        final Optional<SyntaxTreeNode> npNodeOpt = TextGenerationHelper.getLowestAncestorFunctionIntoCategory(headLeaf, Category.NP, tree);
        assert npNodeOpt.isPresent()
            : "the head of an NP can always be traced up to a function into an NP";
        final SyntaxTreeNode npNode = npNodeOpt.get();
        assert npNode.getCategory().matches(Category.valueOf("NP"))
            : "climbing up to function into NP should always yield NP, since we don't have NPs that take arguments";

        /* extract grammatical features. */
        // this is fine because none of our nouns take arguments, right?
        final ImmutableMap<Integer, Predication> argPreds = ImmutableMap.of();
        // only pronouns are case marked
        final Optional<Case> caseMarking = Optional.empty();
        // TODO we should predict this.
        final Optional<Number> number = null;
        // TODO we should predict this.
        final Optional<Gender> gender = null;
        // only pronouns can be non-third person
        final Person person = Person.THIRD;
        // TODO we should predict this.
        final Definiteness definiteness = null;

        /* include an of-phrase if necessary. */
        final ImmutableList<String> words;
        if(npNode.getEndIndex() < tree.getEndIndex() &&
           tree.getLeaves().get(npNode.getEndIndex()).getWord().equals("of")) {
            final SyntaxTreeNode ofNode = tree.getLeaves().get(npNode.getEndIndex());
            final Optional<SyntaxTreeNode> npNodeWithOfOpt = TextGenerationHelper.getLowestAncestorOfNodes(npNode, ofNode, tree);
            if(npNodeWithOfOpt.isPresent()) {
                words = ImmutableList.copyOf(TextGenerationHelper.getNodeWords(npNodeWithOfOpt.get(), Optional.empty(), Optional.empty()));
            } else {
                words = ImmutableList.copyOf(TextGenerationHelper.getNodeWords(npNode, Optional.empty(), Optional.empty()));
            }
        } else {
            words = ImmutableList.copyOf(TextGenerationHelper.getNodeWords(npNode, Optional.empty(), Optional.empty()));
        }
        return new BasicNoun(predicate, Category.NP,
                             ImmutableMap.of(),
                             caseMarking, number, gender, person, definiteness,
                             words);
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

    // getCompletePhrase is left abstract

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

    // transformers -- subclasses need to override

    public abstract Noun withCase(Case caseMarking);
    public abstract Noun withCase(Optional<Case> caseMarking);

    public abstract Noun withNumber(Number number);
    public abstract Noun withNumber(Optional<Number> number);

    public abstract Noun withGender(Gender gender);
    public abstract Noun withGender(Optional<Gender> gender);

    public abstract Noun withPerson(Person person);

    public abstract Noun withDefiniteness(Definiteness definiteness);

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
