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

    public static Noun getFromParse(Integer headIndex, Parse parse) {
        final SyntaxTreeNode tree = parse.syntaxTree;
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
        final ImmutableMap<Integer, ImmutableList<Argument>> args = ImmutableMap.of();

        // only pronouns are case marked
        final Optional<Case> caseMarking = Optional.empty();

        final String nounPOS = headLeaf.getPos();
        final Optional<Number> number;
        if(nounPOS.equals("NN") || nounPOS.equals("NNP")) {
            number = Optional.of(Number.SINGULAR);
        } else if(nounPOS.equals("NNS") || nounPOS.equals("NNPS")) {
            number = Optional.of(Number.PLURAL);
        } else {
            System.err.println(String.format("noun %s has mysterious POS %s", headLeaf.getWord(), nounPOS));
            number = Optional.empty();
        }

        // TODO we could try and predict this... not clear how though
        final Optional<Gender> gender = Optional.empty();

        // only pronouns can be non-third person
        final Person person = Person.THIRD;

        final Optional<Definiteness> definitenessOpt = npNode.getLeaves().stream()
            .filter(leaf -> leaf.getPos().equals("DT") || leaf.getPos().equals("WDT"))
            .findFirst()
            .flatMap(leaf -> {
                    if(leaf.getWord().equalsIgnoreCase("the")) {
                        return Optional.of(Definiteness.DEFINITE);
                    } else if(leaf.getWord().equalsIgnoreCase("a") || leaf.getWord().equalsIgnoreCase("an")) {
                        return Optional.of(Definiteness.INDEFINITE);
                    } else if(leaf.getPos().equals("WDT")) {
                        return Optional.of(Definiteness.FOCAL);
                    } else {
                        return Optional.empty();
                    }
                });
        final Definiteness definiteness;
        // heuristics: if it's proper, assume definite. otherwise if it's plural, assume indefinite.
        if(definitenessOpt.isPresent()) {
            definiteness = definitenessOpt.get();
        } else if(headLeaf.getPos().equals("NNP") || headLeaf.getPos().equals("NNPS")) {
            definiteness = Definiteness.DEFINITE;
        } else if(headLeaf.getPos().equals("NNS")) {
            definiteness = Definiteness.INDEFINITE;
        } else {
            definiteness = null;
        }

        if(definiteness == null) {
            System.err.println("couldn't establish definiteness for [" + npNode.getWord() + "]");
        }

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
        // ImmutableList<String> wh = this.getFocalPronoun()
        //     .withCase(Case.NOMINATIVE)
        //     .getPhrase();
        // Predication placeholder = new Gap(Category.NP);
        // Predication answer = this;
        // return new QuestionData(wh, placeholder, answer);
        return null;
    }

    // getPhrase is left abstract

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
                   ImmutableMap<Integer, ImmutableList<Argument>> args,
                   Optional<Case> caseMarking,
                   Optional<Number> number,
                   Optional<Gender> gender,
                   Person person,
                   Definiteness definiteness) {
        super(predicate, predicateCategory, args);
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
