package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.TextGenerationHelper.TextWithDependencies;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.*;

/**
 * The point of a QuestionTemplate is to abstract over all of the questions
 * that could be asked about the various arguments to a predicate.
 *
 * Philosophical arguments:
 *      A question template should be defined by and only by a given subset of ccg dependencies, which are the ones
 *      fanned out from the predicate to the set of arguments (slots).
 *      We might need SyntaxTreeNode dependencies to resolve constituents in the future.
 * Created by luheng on 12/10/15.
 */
public class QuestionTemplate {

    public enum QuestionType {
        VERB (
                    Category.valueOf("S\\NP") // intransitives
                    ,Category.valueOf("(S\\NP)/NP") // transitives
                 /* ,Category.valueOf("(S\\NP)/PP")
                    ,Category.valueOf("((S\\NP)/NP)/PR")
                    ,Category.valueOf("((S\\NP)/PP)/PR")
                    // T1 said (that) T2
                    ,Category.valueOf("(S[dcl]\\NP)/S")
                    // T1, T2 said, or T1, said T2
                    ,Category.valueOf("(S[dcl]\\S[dcl])|NP")
                    // T1 agreed to do T2
                    ,Category.valueOf("(S\\NP)/(S[to]\\NP)")
                    // T1 stopped using T2
                    ,Category.valueOf("(S\\NP)/(S[ng]\\NP)")
                    // T1 made T3 T2; ditransitives
                    ,Category.valueOf("((S\\NP)/NP)/NP")
                    // T1 gave T3 to T2
                    ,Category.valueOf("((S\\NP)/PP)/NP")
                    // T1 promised T3 to do T2
                    ,Category.valueOf("((S\\NP)/(S[to]\\NP))/NP") // Category.valueOf("((S[dcl]\\NP)/(S[to]\\NP))/NP")
                 */
            ),
        ADJECTIVE_ADJUNCT(
                    Category.valueOf("((S[adj]\\NP)\\(S[adj]\\NP))/NP")
            ),
        NOUN_ADJUNCT(
                    Category.valueOf("(NP\\NP)/NP")
                    // ,Category.valueOf("N|N"),
            ),
        // right now we're assuming the second arg of a verb adjunct is always the main verb.
        VERB_ADJUNCT(
                    Category.valueOf("((S\\NP)\\(S\\NP))/NP")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]")
                 /* ,Category.valueOf("(S\\NP)\\(S\\NP)")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/S")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/(S[ng]\\NP)") // ``by'' as in ``by doing something''.
                    ,Category.valueOf("((S\NP)\(S\NP))/PP") // according (to): 
                    ,Category.valueOf("((S\NP)\(S\NP))/PP") // down (from): 
                 */
            ),
        CLAUSE_ADJUNCT(
                // Category.valueOf("S|S"),
                    ),
        RELATIVIZER(
                // Category.valueOf("(NP\\NP)/(S[dcl]\\NP)")
            ),
        INVALID();

        public final Category[] categories;
        QuestionType(Category... categories) {
            this.categories = categories;
        }

        public boolean admits(Category category) {
            for (Category c : categories) {
                if (c.matches(category)) {
                    return true;
                }
            }
            return false;
        }

        public static QuestionType getTypeFor(Category category) {
            for(QuestionType type : QuestionType.values()) {
                if(type.admits(category)) return type;
            }
            return INVALID;
        }


    }

    // Categories to skip ..
    // private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
    // private static final Category controlParticles = Category.valueOf("(S[to]\\NP)/(S[b]\\NP)");
    // private static final Category controlParticles = Category.valueOf("(S\\NP)/(S[b]\\NP)");
    // private static final Category pastParticiples = Category.valueOf("(S[dcl]\\NP)/(S[pt]\\NP)");

    private static final String[] otherFilteredCategories = new String[] {
        "(S/S)/NP",
        "(S\\NP)\\(S\\NP)",
        "S[em]/S[dcl]",
        "(S/S)/(S/S)",
        "(S[b]\\NP)/(S[pt]\\NP)",
        "S[qem]/S[dcl]",
        "(S\\S)/S[dcl]",
        "(S[adj]\\NP)/(S[to]\\NP)",
        "S/S",
        "((S\\NP)/(S\\NP))/((S\\NP)/(S\\NP))", // i.e. more
        "((S\\NP)\\(S\\NP))/((S\\NP)\\(S\\NP))",
        "((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP))",
        "((S\\NP)\\(S\\NP))/(S[b]\\NP)",
        "(((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP)))/(((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP)))",
    };


    public Parse parse;
    public List<String> words;
    public List<Category> categories;
    public SyntaxTreeNode tree;

    public int predicateIndex;
    public Category predicateCategory;

    // the category here is the one of the argument of the head's category,
    // so it's not sensitive to having multiple arguments or locations.
    public Map<Integer, Category> argCategories;
    // for each arg num, lists all of the deps ending in arguments
    public Map<Integer, List<ResolvedDependency>> allArgDeps;
    // this is for convenience and just returns the index of the first one
    public Map<Integer, Optional<Integer>> argIndices;

    public VerbHelper verbHelper;

    public QuestionType type;

    public QuestionTemplate(int predicateIndex, List<String> words, Parse parse, VerbHelper verbHelper) {
        this.categories = parse.categories;
        this.predicateIndex = predicateIndex;
        this.predicateCategory = categories.get(predicateIndex);
        this.parse = parse;
        this.tree = parse.syntaxTree;
        this.verbHelper = verbHelper;
        this.words = words;
        this.allArgDeps = new HashMap<Integer, List<ResolvedDependency>>();
        for (ResolvedDependency dep : parse.dependencies) {
            if (dep.getHead() == predicateIndex && dep.getArgument() != dep.getHead()) {
                if(!allArgDeps.containsKey(dep.getArgNumber())) {
                    allArgDeps.put(dep.getArgNumber(), new ArrayList<ResolvedDependency>());
                }
                allArgDeps.get(dep.getArgNumber()).add(dep);
            }
        }
        this.argIndices = new HashMap<Integer, Optional<Integer>>();
        allArgDeps.forEach((k, v) -> argIndices.put(k, Optional.of(v.get(0).getArgument())));
        int numArguments = predicateCategory.getNumberOfArguments();
        this.argCategories = new HashMap<Integer, Category>();
        for (int i = 1; i <= numArguments; i++) {
            if(!argIndices.containsKey(i)) argIndices.put(i, Optional.empty());
            argCategories.put(i, predicateCategory.getArgument(i));
        }

        this.type = QuestionType.getTypeFor(predicateCategory);

        /*
        // TODO: maybe we should use the identified PP? Add later.
        String ppStr = argumentCategory.isFunctionInto(Category.PP) ?
            PrepositionHelper.getPreposition(words, categories, argIdx) : "";
        */

        /* I'll burn this bridge when I get to it
        // Special case: T1, T2 said, or T2, said T1
        if (numArguments == 2 && predicateCategory.getArgument(1).equals(Category.Sdcl)) {
            ArgumentSlot[] slots = new ArgumentSlot[] { arguments[2], arguments[1] };
            return new QuestionTemplate(pred, slots, tree, words, categories, verbHelper);
        }
        */
    }

    private boolean cantAskQuestion(int targetArgNum) {
        Optional<Integer> argIndexOpt = Optional.ofNullable(argIndices.get(targetArgNum)).flatMap(x -> x);
        if(!argIndexOpt.isPresent()) {
            return false;
        }
        int argIndex = argIndexOpt.get();
        boolean cantAsk = type == QuestionType.INVALID ||
            (type == QuestionType.NOUN_ADJUNCT &&
             words.get(predicateIndex).equals("of")) || // "of" is just a doozy
            (type == QuestionType.VERB_ADJUNCT &&
             argIndices.values().stream()
             .filter(Optional::isPresent).map(Optional::get)
             .anyMatch(index -> verbHelper.isCopulaVerb(words.get(index)))) || // adverbs of copulas are wonky and not helpful
            (type == QuestionType.ADJECTIVE_ADJUNCT &&
             targetArgNum == 2) || // "full of promise" -> "something was _ of promise; what's _?" --- can't really ask it.
            categories.get(argIndex).matches(Category.valueOf("PR")) // don't ask about a particle; TODO where are all the PR arguments...?
            ;
        return cantAsk;
    }

    /**
     * Instantiate into a question about a particular argument.
     * @param targetArgNum : the argument number under the predicate
     *                       associated with what's being asked about in the question
     * @return a question asking for the targetArgNum'th argument of template's predicate
     */
    public Optional<QuestionAnswerPair> instantiateForArgument(int targetArgNum) {
        if (cantAskQuestion(targetArgNum)) {
            return Optional.empty();
        }

        final List<ResolvedDependency> questionDeps = new ArrayList<>();

        final String wh = getWhWordByArgNum(targetArgNum);
        final List<String> auxiliaries = new ArrayList<>();
        // we need the verb of the clause our predicate appears in,
        // which we will use to determine the auxiliaries we'll be using
        Optional<Integer> verbIndexOpt = Optional.empty();
        if(type == QuestionType.VERB) {
            verbIndexOpt = Optional.of(predicateIndex);
        } else if(type == QuestionType.VERB_ADJUNCT) {
            verbIndexOpt = argIndices.get(2);
        } else if(type == QuestionType.RELATIVIZER) {
            verbIndexOpt = argIndices.get(2);
        }
        // for now, this seems to be a sufficient criterion...
        final boolean shouldSplitVerb = targetArgNum != 1;

        List<String> pred = new ArrayList<>();
        // but if there is no verb, we just put "would be" in there. This works for NP adjuncts.
        if(!verbIndexOpt.isPresent()) {
            auxiliaries.add("would");
            pred.add("be");
            pred.add(words.get(predicateIndex));
        } else {
            int verbIndex = verbIndexOpt.get();
            if(verbIndex == predicateIndex) {
                if(shouldSplitVerb) {
                    auxiliaries.addAll(getAuxiliariesForPredVerb(predicateIndex));
                    pred.addAll(getBarePredVerb(predicateIndex));
                } else {
                    pred.addAll(getUnsplitPredVerb(predicateIndex));
                }
            } else {
                // do this whether we split the verb or not. whatever.
                auxiliaries.addAll(getAuxiliariesForArgVerb(verbIndex));
                // unless our pred is a relativizer,
                if(type != QuestionType.RELATIVIZER) {
                    // use the non-verb pred.
                    pred.add(words.get(predicateIndex));
                }
            }
        }

        // Add arguments on either side until done, according to CCG category.
        final List<String> left = new ArrayList<>();
        final List<String> right = new ArrayList<>();
        Category currentCategory = predicateCategory;
        for(int currentArgNum = predicateCategory.getNumberOfArguments(); currentArgNum > 0; currentArgNum--) {
            // get the surface form of the argument in question
            List<String> argWords = new ArrayList<>();
            // TODO: restructure/simplify this, we have lots of things only working because of guarantees established earlier in the code...
            if(currentArgNum == targetArgNum) { // if we're asking about the target, we have to put in placeholder words
                // we won't ask about the target if it's unrealized, so this is safe
                if(verbIndexOpt.isPresent() && argIndices.get(currentArgNum).get() == verbIndexOpt.get()) {
                    // this can only happen when we're asking about the verb,
                    // which means we're not asking about the subject,
                    // which means the verb must be split.
                    // in any such situation the result should be "do" anyway.
                    argWords.addAll(getTargetMainVerbPlaceholder(currentArgNum));
                } else {
                    argWords.addAll(getTargetArgumentPlaceholder(currentArgNum));
                }
            } else {
                // this is complicated... consider simplifying.
                // first we add the dependency to the list of deps we've touched
                final Optional<ResolvedDependency> firstArgDepOpt = Optional.ofNullable(allArgDeps.get(currentArgNum)).map(deps -> deps.get(0));
                firstArgDepOpt.ifPresent(dep -> questionDeps.add(dep));
                // then we locate the argument in the sentence
                final Optional<Integer> argIndexOpt = firstArgDepOpt.map(ResolvedDependency::getArgument);
                final Category argCategory = argCategories.get(currentArgNum);
                // then we generate the text for that argument, again logging the dependencies touched.
                if(!argIndexOpt.isPresent()) {
                    TextWithDependencies argWithDeps = TextGenerationHelper.getRepresentativePhrase(argIndexOpt, argCategory, parse);
                    questionDeps.addAll(argWithDeps.dependencies);
                    argWords = argWithDeps.tokens;
                } else {
                    int argIndex = argIndexOpt.get();
                    if(!verbIndexOpt.isPresent() || (verbIndexOpt.isPresent() && argIndex != verbIndexOpt.get())) {
                        // replace the word with a pronoun of the proper case, if necessary
                        Optional<Pronoun> pronounOpt = Pronoun.fromString(words.get(argIndex));
                        Optional<String> fixedPronounString;
                        if(currentArgNum == 1) { // heuristic for whether we want nominative case.
                            fixedPronounString = pronounOpt.map(pron -> pron.withCase(Pronoun.Case.NOMINATIVE).toString());
                        } else {
                            fixedPronounString = pronounOpt.map(pron -> pron.withCase(Pronoun.Case.ACCUSATIVE).toString());
                        }
                        TextWithDependencies argWithDeps = TextGenerationHelper.getRepresentativePhrase(argIndexOpt, argCategory, parse, fixedPronounString);
                        questionDeps.addAll(argWithDeps.dependencies);
                        argWords = argWithDeps.tokens;
                    } else if(verbIndexOpt.isPresent() && argIndex == verbIndexOpt.get()) {
                        String verbArgReplacement = TextGenerationHelper.renderString(getNonTargetBareArgumentVerb(verbIndexOpt.get()));
                        TextWithDependencies argWithDeps = TextGenerationHelper.getRepresentativePhrase(Optional.of(argIndex), argCategory, parse, verbArgReplacement);
                        questionDeps.addAll(argWithDeps.dependencies);
                        argWords = argWithDeps.tokens;
                    } else {
                        TextWithDependencies argWithDeps = TextGenerationHelper.getRepresentativePhrase(Optional.of(argIndex), argCategory, parse);
                        questionDeps.addAll(argWithDeps.dependencies);
                        argWords = argWithDeps.tokens;
                    }
                }
            }

            // add the argument on the left or right side, depending on the slash
            final Slash slash = currentCategory.getSlash();
            switch(slash) {
            case FWD:
                right.addAll(argWords);
                break;
            case BWD:
                left.addAll(0, argWords);
                break;
            case EITHER:
                System.err.println("Undirected slash appeared in supertagged data :(");
                right.addAll(argWords);
                break;
            }

            // proceed to the next argument
            currentCategory = currentCategory.getLeft();
        }

        final List<String> questionWords = new ArrayList<>();
        questionWords.add(wh);
        questionWords.addAll(auxiliaries);
        questionWords.addAll(left);
        questionWords.addAll(pred);
        questionWords.addAll(right);

        final List<String> question = questionWords
            .stream()
            .filter(s -> s != null && !s.isEmpty()) // to mitigate oversights. harmless anyway
            .collect(Collectors.toList());
        final List<ResolvedDependency> targetDeps = allArgDeps.get(targetArgNum);
        final List<TextWithDependencies> answers = targetDeps
                .stream()
                .map(ResolvedDependency::getArgument)
                .sorted(Integer::compare)
                .map(index -> { // we need to un-tense verbs that appear as answers
                    Optional<String> replaceOpt = Optional.of(argCategories.get(targetArgNum))
                            .filter(cat -> cat.isFunctionInto(Category.valueOf("S\\NP")))
                            .flatMap(arg -> verbHelper.getAuxiliaryAndVerbStrings(words, categories, index))
                            .map(arr -> arr[1]);
                    return TextGenerationHelper.getRepresentativePhrase(Optional.of(index), argCategories.get(targetArgNum), parse, replaceOpt);
                })
            .collect(Collectors.toList());

        return Optional.of(new QuestionAnswerPair(predicateIndex,
                                                  predicateCategory,
                                                  questionDeps,
                                                  question,
                                                  targetDeps,
                                                  answers));
    }

    /**
     * Get the wh-word (and extra words to append to the question) associated with
     * the expected answer to a question about argument argNum.
     * extra words e.g. in "what did someone he do X for?" "what did someone want X to do?"
     * @param argNum the argument number of the word we're abstracting away
     * @return a 2-element array of { "wh-word", "extra words" } where extra words may be empty
     */
    public String getWhWordByArgNum(int argNum) {
        // assume the argument is realized, since we don't ask for unrealized ones.
        return argIndices.get(argNum)
            .map(words::get)
            .flatMap(Pronoun::fromString)
            .filter(Pronoun::isAnimate)
            .map(x -> "who")
            .orElse("what");
    }

    public List<String> getAuxiliariesForArgVerb(int verbIndex) {
        List<String> result = new ArrayList<>();
        result.add("would");
        return result;
    }

    public List<String> getTargetMainVerbPlaceholder(int argNum) {
        int argIndex = argIndices.get(argNum).get();
        // category of actual arg as it appears in the sentence.
        Category argCategory = categories.get(argIndex);
        List<String> result = new ArrayList<>();
        // "... X to V" -> "what would X do? -- V"
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add("do");
        // "... X verbs Ving adverbly" -> "what would X be doing? -- Ving" <-- let's stick to this
        // "... X is Ving adverbly" -> "what is X doing? -- Ving"
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("be doing");
        // "... X has Ved adverbly" -> "what would X have done? -- Ved" <--
        // "... X is Ving adverbly" -> "what is X doing? -- V"
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
            result.add("have done");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add("do");
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add("do");
        }
        // TODO maybe add preposition
        return result;
    }

    /**
     * Assumes the argument at argNum is present.
     */
    public List<String> getTargetArgumentPlaceholder(int argNum) {
        ArrayList<String> result = new ArrayList<>();
        if(type == QuestionType.NOUN_ADJUNCT) {
            return result;
        }
        int argIndex = argIndices.get(argNum).get();
        // category of actual arg as it appears in the sentence.
        Category argCategory = categories.get(argIndex);
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add("to do");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("doing");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
            result.add("done");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add("do");
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add("do");
        }
        // TODO maybe add preposition
        return result;
    }

    public List<String> getNonTargetBareArgumentVerb(int argIndex) {
        ArrayList<String> result = new ArrayList<>();
        if(type == QuestionType.NOUN_ADJUNCT) {
            return result;
        }
        SyntaxTreeNode verbLeaf = tree.getLeaves().get(argIndex);
        String verb = TextGenerationHelper.getNodeWords(verbLeaf, Optional.empty(), Optional.empty()).get(0);
        String uninflectedVerb = verbHelper.getUninflected(verb).orElse(verb);
        // category of actual arg as it appears in the sentence.
        Category argCategory = categories.get(argIndex);
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("be");
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
            result.add("have");
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pss]\\NP"))) {
            result.add("be");
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add(uninflectedVerb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add(uninflectedVerb);
        }
        // TODO maybe add preposition
        return result;
    }


    /**
     * Create the pred as it should be realized in a question, possibly with a modal.
     * We try to keep in in the tense/aspect/voice/etc. of the clause it appeared in.
     * @return a 2-element array of { "modal", "verb" } where modal may be empty
     */
    public List<String> getUnsplitPredVerb(int index) {
        String verbStr = words.get(index);
        Category verbCategory = categories.get(index);
        List<Integer> auxiliaries = verbHelper.getAuxiliaryChain(words, categories, index);
        List<String> result = new ArrayList<>();
        // if (predicateCategory.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
        // If we have the infinitive such as "to allow", change it to would allow.
        // TODO more robust might be to do it based on clause type S[to]
        if (auxiliaries.size() > 0 && words.get(auxiliaries.get(0)).equalsIgnoreCase("to")) {
            result.add("would");
        } else if (auxiliaries.size() > 0) {
            String aux = "";
            for (int id : auxiliaries) {
                result.add(words.get(id));
            }
        } else if (verbHelper.isCopulaVerb(words.get(index))) {
            result.add(verbStr);
            Optional<String> negOpt = verbHelper.getCopulaNegation(words, categories, index);
            negOpt.ifPresent(result::add);
            return result;
        } else if (verbCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
            verbCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
            verbCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("would");
            result.add("be");
        } else if (verbHelper.isUninflected(words, categories, index)) {
            result.add("would");
        }
        result.add(verbStr);
        return result;
    }

    public List<String> getAuxiliariesForPredVerb(int index) {
        List<String> result = new ArrayList<>();
        result.addAll(getSplitPredVerb(index)[0]);
        return result;
    }

    public List<String> getBarePredVerb(int index) {
        List<String> result = new ArrayList<>();
        result.addAll(getSplitPredVerb(index)[1]);
        return result;
    }

    /**
     * If the argument in question is not the subject,
     * we will need to split the pred from its auxiliary,
     * e.g., "built" -> {"did", "build"}
     * TODO is the below description correct?
     * @return a 2-element array of { "aux", "pred" }
     */
    public List<String>[] getSplitPredVerb(int index) {
        String verbStr = words.get(index);
        Category verbCategory = categories.get(index);
        List<Integer> auxiliaries = verbHelper.getAuxiliaryChain(words, categories, index);
        List<String>[] result = new ArrayList[2];
        result[0] = new ArrayList<String>();
        result[1] = new ArrayList<String>();
        if (auxiliaries.size() == 0 ) {
            if (verbCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) || // predicative adjectives
                verbCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) || // passive verbs
                verbCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) { // progressive verbs
                result[0].add("would");
                result[1].add("be");
                result[1].add(verbStr);
                return result;
            } else if (verbCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
                result[0].add("would");
                result[1].add("have");
                result[1].add(verbStr);
                return result;
            } else if (verbHelper.isCopulaVerb(words.get(index))) {
                result[0].add(verbStr);
                Optional<String> negOpt = verbHelper.getCopulaNegation(words, categories, index);
                negOpt.ifPresent(neg -> {
                        if(neg.equals("not")) {
                            result[1].add(neg);
                        } else if(neg.equals("n't")) {
                            result[0].add(neg);
                        }
                    });
                return result;
            } else {
                Optional<String[]> auxAndVerbStringsOpt = verbHelper.getAuxiliaryAndVerbStrings(words, categories, index);
                if(auxAndVerbStringsOpt.isPresent()) {
                    String[] strs = auxAndVerbStringsOpt.get();
                    result[0].add(strs[0]);
                    result[1].add(strs[1]);
                    return result;
                } else {
                    result[1].add(verbStr);
                    return result;
                }
            }
        } else {
            List<String> rw = getUnsplitPredVerb(index);
            result[0].add(rw.get(0));
            // i.e. What {does n't} someone say ?
            //      What {is n't} someone going to say ?
            if (rw.size() > 1 && VerbHelper.isNegationWord(rw.get(1))) {
                result[0].add(rw.get(1));
                for (int i = 2; i < rw.size(); i++) {
                    result[1].add(rw.get(i));
                }
            } else { // i.e. What {is} someone going to say?
                for (int i = 1; i < rw.size(); i++) {
                    result[1].add(rw.get(i));
                }
            }
        }
        return result;
    }

    public String toString() {
        String str = "";
        /*
        str += predSlot.toString(words) + ":\t";
        for (ArgumentSlot slot : slots) {
            str += slot.toString(words) + "\t";
        }
        */
        str += "herp";
        return str.trim();
    }

}
