package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.Deque;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static edu.uw.easysrl.util.GuavaCollectors.*;

public final class Verb extends Predication {

    /* useful grammar enums */

    // not really a tense... but the thing that we need in order to reproduce the verb form.
    public static enum Tense {
        NONE, BARE, TO, MODAL, PAST, PRESENT, FUTURE
    }

    // not really the voice... but these are exclusive of each other in the right way.
    public static enum Voice {
        ACTIVE, PASSIVE, ADJECTIVE
    }

    /* factory methods */

    public static Verb getFromParse(Integer headIndex, PredicateCache preds, Parse parse) {
        final SyntaxTreeNode tree = parse.syntaxTree;
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);
        // stem the verb
        final String predicate = VerbHelper
            .getStem(TextGenerationHelper
            .renderString(TextGenerationHelper
            .getNodeWords(headLeaf, Optional.empty(), Optional.empty())));
        final Category predicateCategory = parse.categories.get(headIndex);

        final ImmutableMap<Integer, ImmutableList<Predication>> argPreds = null; // TODO XXX

        final Tense tense;
        final Voice voice;
        final boolean isPerfect;
        final boolean isProgressive;
        final boolean isNegated;

        return new Verb(predicate, predicateCategory, argPreds,
                        tense, modal, voice,
                        isPerfect, isProgressive, isNegated);
    }

    // overrides

    @Override
    public ImmutableList<String> getCompletePhrase() {
        assert false;
        return null;
    }

    public ImmutableList<String>[] getQAPairForArgument(int argNum) {
        QuestionData argData = getArgPreds().get(argNum).getQuestionData();
        Verb questionPredication = this.withArg(argNum, argData.getPlaceholder());
        ImmutableList<String> question = new ImmutableList.Builder<String>()
            .addAll(argData.getWhWords())
            .addAll(questionPredication.getQuestionWords())
            .build();
        ImmutableList<String>[] qa = new ImmutableList[2];
        qa[0] = question;
        qa[1] = argData.getAnswer().getCompletePhrase();
        return qa;
    }

    public Noun getSubject() {
        return (Noun) getArgPreds().get(1);
    }

    public ImmutableList<String> getQuestionWords() {
        ImmutableList<String> leftInternalArgs = ImmutableList.of();
        ImmutableList<String> rightInternalArgs = ImmutableList.of();
        Category done = Category.valueOf("(S\\NP)");
        Category curCat = getPredicateCategory();
        while(!done.matches(curCat)) { // we're not including the subject
            Predication curArg = getArgPreds().get(curCat.getNumberOfArguments());
            Slash slash = curCat.getSlash();
            switch(slash) {
            case BWD: leftInternalArgs = new ImmutableList.Builder<String>()
                    .addAll(curArg.getCompletePhrase())
                    .addAll(leftInternalArgs)
                    .build();
                break;
            case FWD: rightInternalArgs = new ImmutableList.Builder<String>()
                    .addAll(rightInternalArgs)
                    .addAll(curArg.getCompletePhrase())
                    .build();
                break;
            default: assert false;
            }
            curCat = curCat.getLeft();
        }

        ImmutableList<String> subjWords = getSubject().getCompletePhrase();
        ImmutableList<String> flippedAux;
        ImmutableList<String> verbWords;
        if(subjWords.size() == 0) {
            verbWords = getVerbWithoutSplit();
            flippedAux = ImmutableList.of();
        } else {
            ImmutableList<String> wordsForFlip = getVerbWithSplit();
            flippedAux = wordsForFlip.subList(0, 1); // hypothetically we could include another in case of contraction
            verbWords = wordsForFlip.subList(1, wordsForFlip.size());
        }

        return new ImmutableList.Builder<String>()
            .addAll(flippedAux)
            .addAll(subjWords)
            .addAll(leftInternalArgs)
            .addAll(verbWords)
            .addAll(rightInternalArgs)
            .build();
    }

    // in order to ask a question about this,
    // I have to be able to split it into:
    // 1) wh-word (string), 2) placeholder (string), 3) answer (string).
    // TENSELESS
    // Some like it hot. -> How do some like it?
    //   1) how, 2) "", 3) hot
    // I like eating ice cream. -> What do I like doing?
    //   1) what, 2) doing, 3) eating ice cream
    // I want to have finished it by tomorrow. -> What do I want to have done?
    //   1) what, 2) to have done, 3) finished it
    // I want to be running daily. -> What do I want to do? / What do I want to be doing?
    //   1) what, 2) to do / to be doing, 3) be running daily / running daily
    // I want him thrown in jail. -> What do I want him to do?
    //   1) what, 2) to do, 3) be thrown in jail
    // TENSED
    // I have been hoisted by my own petard. -> What have I done?
    //   1) what, 2) ? (have done), 3) been hoisted
    // I had flown to San Francisco twice before. -> What had I done?
    //   1) what, 2) ? (had done), 3) flown to San Franscisco
    // I am covered in shame. -> What am I? N/A. <- tensed S[adj] invalid.
    @Override
    public QuestionData getQuestionData() {
        assert false;
        return null; // TODO
    }

    /* protected methods */

    protected Verb(String predicate,
                   Category predicateCategory,
                   ImmutableMap<Integer, ImmutableList<Predication>> argPreds,
                   Tense tense,
                   Optional<String> modal,
                   Voice voice,
                   boolean isPerfect,
                   boolean isProgressive,
                   boolean isNegated) {
        super(predicate, predicateCategory, argPreds);
        this.tense = tense;
        this.modal = modal;
        this.voice = voice;
        this.isPerfect = isPerfect;
        this.isProgressive = isProgressive;
        this.isNegated = isNegated;
        validate();
    }

    /* private fields and methods */

    private final Tense tense;
    private final Optional<String> modal; // populated iff tense == MODAL
    private final Voice voice;
    private final boolean isPerfect;
    private final boolean isProgressive;
    private final boolean isNegated;

    private Verb withArg(int argNum, Predication newArg) {
        ImmutableMap<Integer, Predication> newArgPreds = getArgPreds()
            .entrySet()
            .stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getKey().equals(argNum) ? newArg : e.getValue()));
        return new Verb(getPredicate(), getPredicateCategory(), newArgPreds,
                        tense, modal, voice, isPerfect, isProgressive, isNegated);
    }

    private void validate() {
        boolean isVerb = getPredicateCategory().isFunctionInto(Category.valueOf("S\\NP")) &&
            !getPredicateCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"));
        if(!isVerb) {
            throw new IllegalArgumentException("verb predication must be over a verb");
        }
        if((tense == Tense.MODAL) != modal.isPresent()) {
            throw new IllegalArgumentException("modal word must be present iff verb has a modal tense type");
        }
        if(tense == Tense.NONE) {
            int noTenseConditions = 0;
            if(isPerfect) noTenseConditions++;
            if(isProgressive) noTenseConditions++;
            if(voice == Voice.ADJECTIVE) noTenseConditions++;
            if(noTenseConditions != 1) {
                throw new IllegalArgumentException("exactly 1 no-tense condition must be satisfied when there is no tense");
            }
        }
        if(!(getArgPreds().get(1) instanceof Noun)) {
            throw new IllegalArgumentException("subject of verb predication must be a Noun");
        }
    }

    private ImmutableList<String> getVerbWithSplit() {
        Deque<String> verbStack = getVerbWordStack();
        if(verbStack.size() == 1) {
            assert !isNegated : "aux flip should not cause changes when negated";
            splitVerb(verbStack);
        }
        return ImmutableList.copyOf(verbStack);
    }

    private ImmutableList<String> getVerbWithoutSplit() {
        return ImmutableList.copyOf(getVerbWordStack());
    }

    private Deque<String> getVerbWordStack() {
        Deque<String> verbStack = new LinkedList<>();
        // special case for NO tense rather than BARE tense: we want participles not to have their auxes
        // (but passives should).
        if(tense == Tense.NONE) {
            if(isPerfect) {
                verbStack.addFirst(VerbHelper.getPastParticiple(getPredicate()));
            } else if(isProgressive) {
                verbStack.addFirst(VerbHelper.getPresentParticiple(getPredicate()));
            }
            return verbStack;
        }
        verbStack.addFirst(getPredicate()); // this should be the stem form of the verb.

        // both adjective and progressive: being proud of myself ... who would be proud?
        // both passive   and progressive: being used by someone ... who would be used?
        // I don't want to ask "who would be being proud" etc.; so, we forget progressive in these cases.
        if(voice == Voice.ADJECTIVE || voice == Voice.PASSIVE) {
            if(voice == Voice.PASSIVE) {
                verbStack.addFirst(VerbHelper.getPastParticiple(verbStack.removeFirst()));
            }
            verbStack.addFirst("be");
        } else if(isProgressive) {
            String verbStem = verbStack.removeFirst();
            String verbProg = VerbHelper.getPresentParticiple(verbStem);
            verbStack.addFirst(verbProg);
            verbStack.addFirst("be");
        }

        if(isPerfect) {
            String verbStem = verbStack.removeFirst();
            String verbParticiple = VerbHelper.getPastParticiple(verbStem);
            verbStack.addFirst(verbParticiple);
            verbStack.addFirst("have");
        }

        switch(tense) {
        case BARE: break;
        case TO: verbStack.add("to");
        case MODAL: verbStack.add(modal.get()); break;
        case PAST: verbStack.addFirst(VerbHelper.getPastTense(verbStack.removeFirst())); break;
        case PRESENT: verbStack.addFirst(VerbHelper.getPresentTense(verbStack.removeFirst(), getSubject())); break;
        case FUTURE: verbStack.addFirst("will"); break;
        }

        if(isNegated) {
            if(verbStack.size() == 1) {
                splitVerb(verbStack);
            }
            String top = verbStack.removeFirst();
            verbStack.addFirst("not"); // let's not bother with contractions
            verbStack.addFirst(top);
        }

        return verbStack;
    }

    private void splitVerb(Deque<String> verbStack) {
        assert verbStack.size() == 1 &&
            voice == Voice.ACTIVE &&
            !isProgressive && !isPerfect &&
            (tense == Tense.PAST || tense == Tense.PRESENT)
            : "verb should only be split in very specific circumstances";
        verbStack.addFirst(VerbHelper.getStem(verbStack.removeFirst()));
        switch(tense) {
        case PAST: verbStack.addFirst(VerbHelper.getPastTense("do")); break;
        case PRESENT: verbStack.addFirst(VerbHelper.getPresentTense("do", getSubject())); break;
        default: assert false;
        }
        assert verbStack.size() > 1; // always should have at least two words at the end
    }
}
