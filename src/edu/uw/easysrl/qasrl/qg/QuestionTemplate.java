package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.AnswerGenerator;
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

    private static final Category[] nounAdjuncts = {
        Category.valueOf("N|N"),
        Category.valueOf("NP|NP"),
    };

    private static final Category[] verbAdjuncts = {
        Category.valueOf("(S\\NP)\\(S\\NP)"),
        Category.valueOf("((S\\NP)\\(S\\NP))/S"),
        Category.valueOf("((S\\NP)\\(S\\NP))/(S[ng]\\NP)"), // ``by'' as in ``by doing something''.
    };

    private static final Category[] clauseAdjuncts = {
        Category.valueOf("S|S"),
    };

    private static final Category somethingVerbal = Category.valueOf("S|NP");
    private static final Category intransitiveVerb = Category.valueOf("S\\NP");

    private static final Category[] transitiveVerbs = {
        Category.valueOf("(S\\NP)/NP"),
        Category.valueOf("(S\\NP)/PP"),
        Category.valueOf("((S\\NP)/NP)/PR"),
        Category.valueOf("((S\\NP)/PP)/PR"),
        // T1 said (that) T2
        Category.valueOf("(S[dcl]\\NP)/S"),
        // T1, T2 said, or T1, said T2
        Category.valueOf("(S[dcl]\\S[dcl])|NP"),
        // T1 agreed to do T2
        Category.valueOf("(S\\NP)/(S[to]\\NP)"),
        // T1 stopped using T2
        Category.valueOf("(S\\NP)/(S[ng]\\NP)"),
    };

    private static final Category[] ditransitiveVerbs = {
        // T1 made T3 T2
        Category.valueOf("((S\\NP)/NP)/NP"),
        // T1 gave T3 to T2
        Category.valueOf("((S\\NP)/PP)/NP"),
        // T1 promised T3 to do T2
        Category.valueOf("((S\\NP)/(S[to]\\NP))/NP"), // Category.valueOf("((S[dcl]\\NP)/(S[to]\\NP))/NP")
    };

    // TODO: special cases:
    // according (to): ((S\NP)\(S\NP))/PP
    // down (from): ((S\NP)\(S\NP))/PP
    // Possible questions: What is the case, according to someone? What is down from something?
    // What is something according to? What something down from?

    // Categories to skip ..
    // private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
    //private static final Category controlParticles = Category.valueOf("(S[to]\\NP)/(S[b]\\NP)");
    private static final Category controlParticles = Category.valueOf("(S\\NP)/(S[b]\\NP)");
    private static final Category pastParticiples = Category.valueOf("(S[dcl]\\NP)/(S[pt]\\NP)");

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

    private static final Set<String> otherFilteredCategorySet;
    static {
        otherFilteredCategorySet = new HashSet<>();
        Collections.addAll(otherFilteredCategorySet, otherFilteredCategories);
    }

    private static boolean belongsTo(Category category, Category[] categoryList) {
        for (Category c : categoryList) {
            if (category.isFunctionInto(c)) {
                return true;
            }
        }
        return false;
    }


    public Parse parse;
    public List<String> words;
    public List<Category> categories;
    public SyntaxTreeNode tree;

    public List<Integer> auxiliaries;
    public int predicateIndex;
    public Category predicateCategory;

    public Map<Integer, Integer> argIndices;
    public Map<Integer, Category> argCategories;

    public VerbHelper verbHelper;

    public QuestionType type;

    public QuestionTemplate(int predicateIndex, List<String> words, Parse parse, VerbHelper verbHelper) {
        this.categories = parse.categories;
        this.predicateIndex = predicateIndex;
        this.predicateCategory = categories.get(predicateIndex);
        this.auxiliaries = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        this.parse = parse;
        this.tree = parse.syntaxTree;
        this.verbHelper = verbHelper;
        this.words = words;
        this.argIndices = new HashMap<Integer, Integer>();
        for (ResolvedDependency dep : parse.dependencies) {
            if (dep.getHead() == predicateIndex && dep.getArgument() != dep.getHead()) {
                argIndices.put(dep.getArgNumber(), dep.getArgument());
            }
        }
        int numArguments = predicateCategory.getNumberOfArguments();
        this.argCategories = new HashMap<Integer, Category>();
        for (int i = 1; i <= numArguments; i++) {
            if(!argIndices.containsKey(i)) argIndices.put(i, -1);
            argCategories.put(i, predicateCategory.getArgument(i));
        }
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

        this.type = QuestionType.INVALID;
        if (numArguments == 0) {
            type = QuestionType.INVALID;
        } else if(Category.valueOf("(NP\\NP)/NP").matches(predicateCategory)) {
            type = QuestionType.NOUN_ADJUNCT;
        } else if(Category.valueOf("((S\\NP)\\(S\\NP))/NP").matches(predicateCategory)) {
            if(Category.valueOf("((S[adj]\\NP)\\(S[adj]\\NP))/NP").matches(predicateCategory)) {
                type = QuestionType.ADJECTIVE_ADJUNCT;
            } else {
                type = QuestionType.VERB_ADJUNCT;
            }
        } else if(Category.valueOf("(S\\NP)/NP").matches(predicateCategory)) {
            type = QuestionType.VERB;
        } else if(Category.valueOf("(NP\\NP)/(S[dcl]\\NP)").matches(predicateCategory)) {
            type = QuestionType.INVALID;
            // type = QuestionType.RELATIVIZER;
        }

        /*
        // here is the logic that used to be in filterPredicate
        String word = words.get(predicateIndex);
        // adjuncts first: because last applied arg of an adjunct is really just an arg of its arg
        // for example, (S\NP)\(S\NP) reads as a verb because it's a function into (S\NP)
        if (belongsTo(predicateCategory, nounAdjuncts)) {
            type = QuestionType.NOUN_ADJUNCT;
        } else if (belongsTo(predicateCategory, verbAdjuncts)) {
            type = QuestionType.VERB_ADJUNCT;
        } else if (belongsTo(predicateCategory, clauseAdjuncts)) {
            type = QuestionType.INVALID;
        } else if (VerbHelper.isCopulaVerb(word)) {
            type = QuestionType.INVALID;
        } else if (!verbHelper.hasInflectedForms(word) && !predicateCategory.equals(Category.valueOf("(S[adj]\\NP)/(S[to]\\NP)"))  ) {
            type = QuestionType.INVALID;
        } else if (predicateCategory.isFunctionInto(intransitiveVerb) ||
                   belongsTo(predicateCategory, transitiveVerbs) ||
                   belongsTo(predicateCategory, ditransitiveVerbs)) {
            type = QuestionType.VERB;
        } else if (predicateCategory.isFunctionInto(prepositions) ||
            predicateCategory.isFunctionInto(auxiliaries) ||
            predicateCategory.isFunctionInto(controlParticles) ||
            predicateCategory.isFunctionInto(pastParticiples) ||
            otherFilteredCategorySet.contains(predicateCategory.toString())) {
            // System.out.println("skipping because in other filtered list");
            type = QuestionType.INVALID;
        } else {
            type = QuestionType.INVALID;
        }
        */
    }

    public enum QuestionType {
        VERB,
        ADJECTIVE_ADJUNCT, NOUN_ADJUNCT, VERB_ADJUNCT,
        RELATIVIZER,
        INVALID
    }

    private boolean cantAskQuestion(int targetArgNum) {
        Optional<Integer> argIndex = Optional.of(argIndices.get(targetArgNum));
        boolean cantAsk = type == QuestionType.INVALID ||
            !argIndex.isPresent() ||
            argIndex.get() == -1 || // don't ask about an unrealized arg
            (type == QuestionType.NOUN_ADJUNCT &&
             words.get(predicateIndex).equals("of")) || // "of" is just a doozy
            (type == QuestionType.VERB_ADJUNCT &&
             argIndices.values().stream()
             .filter(index -> index >= 0)
             .anyMatch(index -> verbHelper.isCopulaVerb(words.get(index)))) || // adverbs of copulas are wonky and not helpful
            (type == QuestionType.ADJECTIVE_ADJUNCT &&
             targetArgNum == 2) || // "full of promise" -> "something was _ of promise; what's _?" --- can't really ask it.
            categories.get(argIndex.get()).matches(Category.valueOf("PR")) // don't ask about a particle
            ;
        // return type != QuestionType.VERB;
        return cantAsk;
    }

    /**
     * Instantiate into a question about a particular argument.
     * @param targetArgNum : the argument number under the predicate
     *                       associated with what's being asked about in the question
     * @return a question asking for the targetArgNum'th argument of template's predicate
     */
    public List<String> instantiateForArgument(int targetArgNum) {
        List<String> question = new ArrayList<>();
        if (cantAskQuestion(targetArgNum)) {
            return question;
        }
        // add arguments on either side until done, according to CCG category.
        // split the PRED if the target was not the last argument added to the left.
        List<String> left = new ArrayList<>();
        List<String> right = new ArrayList<>();
        boolean isTargetLastAddedToLeft = false;
        Category currentCategory = predicateCategory;
        for(int currentArgNum = predicateCategory.getNumberOfArguments(); currentArgNum > 0; currentArgNum--) {
            // get the surface form of the argument in question
            List<String> argWords;
            boolean addingTarget = currentArgNum == targetArgNum;
            if(addingTarget) {
                argWords = getTargetPlaceholderWords(currentArgNum);
            } else {
                int argIndex = argIndices.get(currentArgNum);
                Category argCategory = argCategories.get(currentArgNum);
                argWords = getRepresentativePhrase(argIndex, argCategory);
            }

            // add the argument on the left or right side, depending on the slash
            Slash slash = currentCategory.getSlash();
            switch(slash) {
            case FWD:
                right.addAll(argWords);
                break;
            case BWD:
                argWords.addAll(left);
                left = argWords;
                isTargetLastAddedToLeft = addingTarget;
                break;
            case EITHER:
                System.err.println("Undirected slash appeared in supertagged data :(");
                break;
            }

            // proceed to the next argument
            currentCategory = currentCategory.getLeft();
        }

        String wh = getWhWordByArgNum(targetArgNum);
        question.add(wh);

        // split the predicate or don't, depending:
        if(!isTargetLastAddedToLeft || type == QuestionType.NOUN_ADJUNCT) {
            List<String> splitPred = getSplitPred();
            question.add(splitPred.get(0));
            question.addAll(left);
            question.add(splitPred.get(1));
        } else {
            question.addAll(left);
            question.addAll(getUnsplitPred());
        }

        question.addAll(right);
        return question.stream()
            .filter(s -> s != null && !s.isEmpty()) // to mitigate oversights. harmless anyway
            .collect(Collectors.toList());
    }

    /**
     * Get the wh-word (and extra words to append to the question) associated with
     * the expected answer to a question about argument argNum.
     * extra words e.g. in "what did someone he do X for?" "what did someone want X to do?"
     * @param argNum the argument number of the word we're abstracting away
     * @return a 2-element array of { "wh-word", "extra words" } where extra words may be empty
     */
    public String getWhWordByArgNum(int argNum) {
        return "what";
    }

    public List<String> getTargetPlaceholderWords(int argNum) {
        ArrayList<String> result = new ArrayList<>();
        if(type == QuestionType.NOUN_ADJUNCT) {
            return result;
        }
        int argIndex = argIndices.get(argNum);
        Category argCategory = argCategories.get(argNum);
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add("to do");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("doing");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add("do");
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add("do");
        }
        // TODO maybe add preposition
        return result;
    }

    private List<String> getRepresentativePhraseForUnrealized(Category category) {
        List<String> result = new ArrayList<>();
        result.add("something");
        return result;
    }

    /**
     * Constructs a phrase with the desired head and category label.
     */
    private List<String> getRepresentativePhrase(int headIndex, Category neededCategory) {
        if(headIndex == -1) {
            return getRepresentativePhraseForUnrealized(neededCategory);
        }
        SyntaxTreeNode headLeaf = tree.getLeaves().get(headIndex);
        Optional<SyntaxTreeNode> nodeOpt = AnswerGenerator
            .getLowestAncestorFunctionIntoCategory(headLeaf, neededCategory, tree);
        if(!nodeOpt.isPresent()) {
            // fall back to just the original leaf. this failure case is very rare.
            List<String> result = new ArrayList<>();
            result.add(headLeaf.getWord());
            return result;
        }
        // here we don't necessarily have the whole phrase. `node` is a function into the phrase.
        // especially common is the case where we get a transitive verb and it doesn't bother including the object.
        // so we need to populate the remaining spots by accessing the arguments of THIS guy,
        // until he exactly matches the category we're looking for.
        // using this method will capture and appropriately rearrange extracted arguments and such.

        SyntaxTreeNode node = nodeOpt.get();
        String center = node.getWord();
        List<String> left = new ArrayList<>();
        List<String> right = new ArrayList<>();

        // add arguments on either side until done, according to CCG category.
        Category currentCategory = node.getCategory();
        for(int currentArgNum = currentCategory.getNumberOfArguments();
            currentArgNum > neededCategory.getNumberOfArguments();
            currentArgNum--) {
            Category argCat = currentCategory.getRight();
            // recover arg index using the fact that we know the head leaf and the arg num.
            Set<ResolvedDependency> deps = parse.dependencies;
            int curArg = currentArgNum; // just so we can use it in the lambda below
            Optional<ResolvedDependency> depOpt = deps.stream().filter(dep -> {
                    return dep.getHead() == headIndex && dep.getArgNumber() == curArg;
                }).findFirst();
            int argIndex = depOpt.map(dep -> dep.getArgument()).orElse(-1);
            List<String> argPhrase = getRepresentativePhrase(argIndex, argCat);
            // add the argument on the left or right side, depending on the slash
            Slash slash = currentCategory.getSlash();
            switch(slash) {
            case FWD:
                right = Stream.concat(right.stream(), argPhrase.stream()).collect(Collectors.toList());
                break;
            case BWD:
                left = Stream.concat(argPhrase.stream(), left.stream()).collect(Collectors.toList());
                break;
            case EITHER:
                System.err.println("Undirected slash appeared in supertagged data :(");
                break;
            }
            // proceed to the next argument
            currentCategory = currentCategory.getLeft();
        }

        List<String> result = new ArrayList<>();
        result.addAll(left);
        result.add(center);
        result.addAll(right);
        return result;
    }

    /**
     * Create the pred as it should be realized in a question, possibly with a modal.
     * We try to keep in in the tense/aspect/voice/etc. of the clause it appeared in.
     * @return a 2-element array of { "modal", "verb" } where modal may be empty
     */
    public List<String> getUnsplitPred() {
        String predStr = words.get(predicateIndex);
        if(type == QuestionType.VERB) {
            // If we have the infinitive such as "to allow", change it to would allow.
            //if (predicateCategory.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
            // TODO more robust might be to do it based on clause type S[to]
            if (auxiliaries.size() > 0 && words.get(auxiliaries.get(0)).equalsIgnoreCase("to")) {
                return Arrays.asList(new String[]{ "would", predStr });
            }

            // If the verb has its own set of auxiliaries, return those as is.
            if (auxiliaries.size() > 0) {
                String aux = "";
                for (int id : auxiliaries) {
                    aux += words.get(id) + " ";
                }
                return Arrays.asList(new String[] { aux.trim(), predStr });
            }
            if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
                predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
                predicateCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
                return Arrays.asList(new String[] { "would be", predStr });
            }
            if (verbHelper.isUninflected(words, categories, predicateIndex)) {
                return Arrays.asList(new String[] { "would", predStr });
            }
        } else if (type == QuestionType.NOUN_ADJUNCT) {
            return Arrays.asList(new String[] { predStr });
        } else if (type == QuestionType.VERB_ADJUNCT) {
            return Arrays.asList(new String[] { "happened", predStr });
        } else if (type == QuestionType.ADJECTIVE_ADJUNCT) {
            System.err.println("Not trying to split the pred for an adjunct of an adjective. Shouldn't happen.");
            return Arrays.asList(new String[] { predStr });
        }
        return Arrays.asList(new String[] { "", predStr });
    }

    /**
     * If the argument in question is not the subject,
     * we will need to split the pred from its auxiliary,
     * e.g., "built" -> {"did", "build"}
     * TODO is the below description correct?
     * @return a 2-element array of { "aux", "pred" }
     */
    public List<String> getSplitPred() {
        String predStr = words.get(predicateIndex);
        String[] result = new String[2];
        if (type == QuestionType.VERB) {
            if (auxiliaries.size() == 0 ) {
                if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) || // predicative adjectives
                    predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) || // passive verbs
                    predicateCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) { // progressive verbs
                    return Arrays.asList(new String[] { "was", predStr });
                } else if (verbHelper.isCopulaVerb(words.get(predicateIndex))) {
                    return Arrays.asList(new String[] { predStr, "" });
                } else {
                    result = verbHelper.getAuxiliaryAndVerbStrings(words, categories, predicateIndex).orElse(new String [] { "", predStr });
                }
            } else {
                String[] r = (String[]) getUnsplitPred().toArray();
                String[] rw = (r[0] + " " + r[1]).split("\\s+");
                result = new String[] { rw[0], "" };
                // i.e. What {does n't} someone say ?
                //      What {is n't} someone going to say ?
                if (rw.length > 1 && VerbHelper.isNegationWord(rw[1])) {
                    result[0] += " " + rw[1];
                    for (int i = 2; i < rw.length; i++) {
                        result[1] += (i > 2 ? " " : "") + rw[i];
                    }
                }
                // i.e. What {is} someone going to say?
                else {
                    for (int i = 1; i < rw.length; i++) {
                        result[1] += (i > 1 ? " " : "") + rw[i];
                    }
                }
            }
            // TODO: get information about the clause to use in other cases
        } else if (type == QuestionType.NOUN_ADJUNCT) {
            return Arrays.asList(new String[] { "was", predStr });
        } else if (type == QuestionType.VERB_ADJUNCT) {
            return Arrays.asList(new String[] { "did", predStr });
        } else if (type == QuestionType.ADJECTIVE_ADJUNCT) {
            return Arrays.asList(new String[] { "was", predStr });
        }
        return Arrays.asList(result);
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
