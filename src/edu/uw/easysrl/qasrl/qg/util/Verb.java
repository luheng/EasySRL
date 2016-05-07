package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.Deque;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import static edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;
import java.util.stream.IntStream;
import java.util.function.Function;
import java.util.function.BiFunction;

public final class Verb extends Predication {

    /* useful grammar enums */

    // not really a tense... but the thing that we need in order to reproduce the verb form.
    public static enum Tense {
        BARE_VERB, BARE_COPULA, TO, MODAL, PAST, PRESENT, FUTURE
    }

    // not really the voice... but these are exclusive of each other in the right way.
    public static enum Voice {
        ACTIVE, PASSIVE, ADJECTIVE
    }

    /* factory methods */

    public static Verb getFromParse(Integer headIndex, PredicateCache preds, Parse parse) {
        final SyntaxTreeNode tree = parse.syntaxTree;
        final ImmutableList<String> words = tree.getLeaves().stream()
            .map(leaf -> leaf.getWord())
            .collect(toImmutableList());
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);
        // stem the verb
        final String predicate = VerbHelper
            .getStem(TextGenerationHelper
            .renderString(TextGenerationHelper
            .getNodeWords(headLeaf, Optional.empty(), Optional.empty())));
        final Category predicateCategory = parse.categories.get(headIndex);
        assert predicateCategory.isFunctionInto(Category.valueOf("S\\NP"))
            : "verb predication must arise from verb category";

        final ImmutableMap<Integer, ImmutableList<Argument>> args = IntStream
            .range(1, predicateCategory.getNumberOfArguments() + 1)
            .boxed()
            .collect(toImmutableMap(argNum -> argNum, argNum -> parse.dependencies.stream()
            .filter(dep -> dep.getHead() == headIndex && dep.getArgument() != headIndex && argNum == dep.getArgNumber())
            .map(argDep -> new Argument(Optional.of(argDep), preds.getPredication(argDep.getArgument(), predicateCategory.getArgument(argDep.getArgNumber()))))
            .filter(arg -> arg.getPredication() != null) // XXX just because haven't impl for all categories yet
            .collect(toImmutableList())));

        final ImmutableList<Noun> subjects = args.get(1).stream()
            .map(arg -> (Noun) arg.getPredication())
            .collect(toImmutableList());

        Tense tense = Tense.BARE_VERB;
        Optional<String> modal = Optional.empty();
        boolean isPerfect = false;
        boolean isProgressive = false;
        boolean isNegated = false;
        // Optional<String> negationWord = Optional.empty();
        Voice voice = Voice.ACTIVE;

        int curAuxIndex = headIndex;
        boolean done = false;
        while(!done) {
            String aux = words.get(curAuxIndex).toLowerCase();
            Category cat = parse.categories.get(curAuxIndex);
            // adverbs might be between auxiliaries. including (S\NP)/(S\NP) maybe.
            if(curAuxIndex != 0 && !VerbHelper.isAuxiliaryVerb(aux, cat) && !VerbHelper.isNegationWord(aux) &&
               cat.isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)"))) {
                continue;
            } else if(VerbHelper.isNegationWord(aux)) {
                isNegated = true;
            } else if(cat.isFunctionInto(Category.valueOf("S[adj]\\NP"))) {
                voice = Voice.ADJECTIVE;
                tense = Tense.BARE_VERB;
            } else if(cat.isFunctionInto(Category.valueOf("S[pss]\\NP"))) {
                voice = Voice.PASSIVE;
                tense = Tense.BARE_VERB;
            } else if(cat.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
                tense = Tense.BARE_VERB;
            } else if (cat.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
                tense = Tense.TO;
            } else if (cat.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
                isPerfect = true;
                tense = Tense.BARE_VERB;
            } else if (cat.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
                isProgressive = true;
                tense = Tense.BARE_VERB;
            } else if (cat.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
                if(VerbHelper.isModal(aux)) {
                    tense = Tense.MODAL;
                    modal = Optional.of(VerbHelper.getNormalizedModal(aux));
                } else if(VerbHelper.isPastTense(aux)) {
                    tense = Tense.PAST;
                } else if(VerbHelper.isPresentTense(aux, subjects.get(0))) {
                    tense = Tense.PRESENT;
                } else if(VerbHelper.isFutureTense(aux)) {
                    tense = Tense.FUTURE;
                } else {
                    System.err.println("error getting info from S[dcl] for " + aux + "(" + cat + ")");
                    done = true;
                }
            } else {
                done = true;
            }
            curAuxIndex--;
        }

        return new Verb(predicate, predicateCategory, args,
                        tense, modal, voice,
                        isPerfect, isProgressive, isNegated);
    }

    // overrides

    @Override
    public ImmutableList<String> getPhrase() {
        assert false;
        return null;
    }

    public Noun getSubject() {
        return (Noun) getArgs().get(1).get(0).getPredication(); // TODO
    }

    public ImmutableList<String> getQuestionWords() {
        assert getArgs().entrySet().stream().allMatch(e -> e.getValue().size() == 1)
            : "can only get question words for predication with exactly one arg in each slot";
        ImmutableMap<Integer, Argument> args = getArgs().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().get(0)));
        assert args.entrySet().stream()
            .filter(e -> (e.getValue().getPredication() instanceof Noun) && ((Noun) e.getValue().getPredication()).isFocal())
            .collect(counting()) <= 1
            : "can't have more than one noun argument in focus";
        // TODO: PP args, VP args, etc. and others might also hypothetically be in focus, if we had supersense questions
        Optional<Integer> focalArgNumOpt = args.keySet().stream()
            .filter(argNum -> (args.get(argNum).getPredication() instanceof Noun) && ((Noun) args.get(argNum).getPredication()).isFocal())
            .findFirst();

        ImmutableList<String> leftInternalArgs = ImmutableList.of();
        ImmutableList<String> rightInternalArgs = ImmutableList.of();
        Category done = Category.valueOf("(S\\NP)");
        Category curCat = getPredicateCategory();
        while(!done.matches(curCat)) { // we're not including the subject
            if(!focalArgNumOpt.isPresent() || focalArgNumOpt.get() != curCat.getNumberOfArguments()) {
                Predication curArg = args.get(curCat.getNumberOfArguments()).getPredication();
                Slash slash = curCat.getSlash();
                switch(slash) {
                case BWD: leftInternalArgs = new ImmutableList.Builder<String>()
                        .addAll(curArg.getPhrase())
                        .addAll(leftInternalArgs)
                        .build();
                    break;
                case FWD: rightInternalArgs = new ImmutableList.Builder<String>()
                        .addAll(rightInternalArgs)
                        .addAll(curArg.getPhrase())
                        .build();
                    break;
                default: assert false;
                }
            }
            curCat = curCat.getLeft();
        }

        Noun subject = (Noun) args.get(1).getPredication();
        ImmutableList<String> subjWords = subject.getPhrase();

        // we're going to flip the auxiliary only if the subject is not focal and the subject's string form is nonempty
        boolean flipAuxiliary = (!focalArgNumOpt.isPresent() || focalArgNumOpt.get() != 1) && subjWords.size() > 0;

        ImmutableList<String> questionPrefix;
        ImmutableList<String> verbWords;
        if(focalArgNumOpt.isPresent() && focalArgNumOpt.get() == 1) { // if we're asking about the subject
            questionPrefix = new ImmutableList.Builder<String>()
                .addAll(subjWords) // wh-subject
                .build();
            verbWords = new ImmutableList.Builder<String>()
                .addAll(getVerbWithoutSplit())
                .build();
        } else if(focalArgNumOpt.isPresent()) { // if we're asking about a non-subject
            ImmutableList<String> wordsForFlip = getVerbWithSplit();
            ImmutableList<String> flippedAux = wordsForFlip.subList(0, 1);
            questionPrefix = new ImmutableList.Builder<String>()
                .addAll(args.get(focalArgNumOpt.get()).getPredication().getPhrase())
                .addAll(flippedAux)
                .addAll(subjWords)
                .build();
            verbWords = wordsForFlip.subList(1, wordsForFlip.size());
        } else { // it's a yes/no question; no focal arg
            ImmutableList<String> wordsForFlip = getVerbWithSplit();
            ImmutableList<String> flippedAux = wordsForFlip.subList(0, 1);
            questionPrefix = new ImmutableList.Builder<String>()
                .addAll(flippedAux)
                .addAll(subjWords)
                .build();
            verbWords = wordsForFlip.subList(1, wordsForFlip.size());
        }

        return new ImmutableList.Builder<String>()
            .addAll(questionPrefix)
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

    public verb transformargs(bifunction<integer, immutablelist<argument>, immutablelist<argument>> transform) {
        immutablemap<integer, supplier<immutablelist<argument>>> newargsuppliers = getargs()
            .entryset()
            .stream()
            .collect(toimmutablemap(e -> e.getkey(), e -> (() -> transform.apply(e.getkey(), e.getvalue()))));
        return new verb(getpredicate(), getpredicatecategory(), newargsuppliers,
                        tense, modal, voice, isperfect, isprogressive, isnegated);
    }

    public Verb withModal(String modal) {
        return new Verb(getPredicate(), getPredicateCategory(), getArgs(),
                        Tense.MODAL, Optional.of(modal),
                        voice, isPerfect, isProgressive, isNegated);
    }

    /* protected methods */

    protected Verb(String predicate,
                   Category predicateCategory,
                   ImmutableMap<Integer, ImmutableList<Argument>> args,
                   Tense tense,
                   Optional<String> modal,
                   Voice voice,
                   boolean isPerfect,
                   boolean isProgressive,
                   boolean isNegated) {
        super(predicate, predicateCategory, args);
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

    private void validate() {
        boolean isVerb = getPredicateCategory().isFunctionInto(Category.valueOf("S\\NP")) &&
            !getPredicateCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"));
        if(!isVerb) {
            throw new IllegalArgumentException("verb predication must be over a verb");
        }
        if((tense == Tense.MODAL) != modal.isPresent()) {
            throw new IllegalArgumentException("modal word must be present iff verb has a modal tense type");
        }
        // if(tense == Tense.NONE) {
        //     int noTenseConditions = 0;
        //     if(isPerfect) noTenseConditions++;
        //     if(isProgressive) noTenseConditions++;
        //     if(voice == Voice.ADJECTIVE) noTenseConditions++;
        //     if(noTenseConditions != 1) {
        //         throw new IllegalArgumentException("exactly 1 no-tense condition must be satisfied when there is no tense");
        //     }
        // }
        if(getArgs().get(1).stream().anyMatch(arg -> !(arg.getPredication() instanceof Noun))) {
            throw new IllegalArgumentException("subject of verb predication must be a Noun");
        }
    }

    private ImmutableList<String> getVerbWithSplit() {
        Deque<String> verbStack = getVerbWordStack();
        assert verbStack.size() > 0;
        if(verbStack.size() == 1) {
            assert !isNegated : "aux flip should not cause changes when negated";
            splitVerb(verbStack);
        }
        assert verbStack.size() >= 2; // should be splittable
        return ImmutableList.copyOf(verbStack);
    }

    private ImmutableList<String> getVerbWithoutSplit() {
        return ImmutableList.copyOf(getVerbWordStack());
    }

    private Deque<String> getVerbWordStack() {
        Deque<String> verbStack = new LinkedList<>();
        // special case for NO tense rather than BARE tense: we want participles not to have their auxes
        // (but passives should).
        if(tense == Tense.BARE_VERB) {
            if(isPerfect || voice == Voice.PASSIVE) {
                verbStack.addFirst(VerbHelper.getPastParticiple(getPredicate()));
            } else if(isProgressive) {
                verbStack.addFirst(VerbHelper.getPresentParticiple(getPredicate()));
            } else if(voice == Voice.ADJECTIVE) {
                verbStack.addFirst(getPredicate());
            } else {
                System.err.println("unhandled case of NONE tense:");
                System.err.println(getPredicateCategory());
                System.err.println(getPredicate());
                System.err.println(voice.name());
            }
            return verbStack;
        } else {
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
            case BARE_COPULA: break;
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
    }

    private void splitVerb(Deque<String> verbStack) {
        // assert verbStack.size() == 1 &&
        //     voice == Voice.ACTIVE &&
        //     !isProgressive && !isPerfect &&
        //     (tense == Tense.PAST || tense == Tense.PRESENT)
        //     : "verb should only be split in very specific circumstances";
        verbStack.addFirst(VerbHelper.getStem(verbStack.removeFirst()));
        switch(tense) {
        case PAST: verbStack.addFirst(VerbHelper.getPastTense("do")); break;
        case PRESENT: verbStack.addFirst(VerbHelper.getPresentTense("do", getSubject())); break;
        default: assert false;
        }
        assert verbStack.size() > 1; // always should have at least two words at the end
    }
}
