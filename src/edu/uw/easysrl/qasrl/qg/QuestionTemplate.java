package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.*;

/**
 * A QuestionTemplate is primarily a list of QuestionSlots.
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
    private static final Category prepositions = Category.valueOf("((S\\NP)\\(S\\NP))/NP");
    private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
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


    public Category predicateCategory;
    public List<String> words;
    public List<Category> categories;
    public SyntaxTreeNode tree;

    // the first element of `slots` is understood to be the subject
    public ArgumentSlot[] slots;
    public PredicateSlot predSlot;
    public Map<Integer, Integer> argNumToSlotId;

    public VerbHelper verbHelper;

    public QuestionType type;

    public QuestionTemplate(PredicateSlot predSlot, ArgumentSlot[] slots, SyntaxTreeNode tree, List<String> words, List<Category> categories, VerbHelper verbHelper) {
        this.predSlot = predSlot;
        this.predicateCategory = predSlot.category;
        this.slots = slots;
        this.tree = tree;
        this.words = words;
        this.categories = categories;
        this.verbHelper = verbHelper;
        this.argNumToSlotId = new HashMap<>();
        for (int slotId = 0; slotId < slots.length; slotId++) {
            argNumToSlotId.put(slots[slotId].argumentNumber, slotId);
        }

        type = QuestionType.INVALID;
        // only allow the following categories of words to ask questions about
        if(Category.valueOf("(NP\\NP)/NP").matches(predicateCategory)) {
            type = QuestionType.NOUN_ADJUNCT;
            
        } /*else if(Category.valueOf("((S\\NP)\\(S\\NP))/NP").matches(predicateCategory)) {
            type = QuestionType.VERB_ADJUNCT;
        } */

        /*
        // determine question type---here is the logic that used to be in filterPredicate
        String word = words.get(predSlot.indexInSentence);
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
        VERB, NOUN_ADJUNCT, VERB_ADJUNCT, INVALID
    }

    /**
     * Instantiate into a question about a particular argument.
     * @param targetArgNum : the argument number under the predicate
     *                       associated with what's being asked about in the question
     * @return a question asking for the argument in slot targetArgNum of template's predicate
     */
    public List<String> instantiateForArgument(int targetArgNum) {
        int totalArgs = predicateCategory.getNumberOfArguments();
        List<String> question = new ArrayList<>();
        if (!argNumToSlotId.containsKey(targetArgNum) || type == QuestionType.INVALID) {
            return question;
        }
        // "of" is just a doozy
        if (words.get(predSlot.indexInSentence).equals("of")) {
            return question;
        }
        // new approach: add arguments on either side until done, according to CCG category.
        // split the PRED if the target was not the last argument added to the left.
        List<String> left = new ArrayList<>();
        List<String> right = new ArrayList<>();
        boolean isTargetLastAddedToLeft = false;
        int currentArgNum = totalArgs;
        Category currentCategory = predicateCategory;
        while(currentArgNum > 0) {
            // get the surface form of the argument in question
            List<String> argWords;
            boolean addingTarget = currentArgNum == targetArgNum;
            if(addingTarget) {
                argWords = getTargetPlaceholderWords(currentArgNum);
            } else {
                argWords = getArgPlaceholderWords(currentArgNum);
            }

            // add the argument on the left or right side, depending on the slash
            Slash slash = currentCategory.getSlash();
            switch(slash) {
            case FWD:
                right = Stream.concat(right.stream(), argWords.stream()).collect(Collectors.toList());
                break;
            case BWD:
                left = Stream.concat(argWords.stream(), left.stream()).collect(Collectors.toList());
                isTargetLastAddedToLeft = addingTarget;
                break;
            case EITHER:
                System.err.println("Undirected slash appeared in supertagged data :(");
                break;
            }

            // proceed to the next argument
            currentCategory = currentCategory.getLeft();
            currentArgNum--;
        }

        String wh = getWhWordByArgNum(targetArgNum)[0];
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
            .filter(s -> !s.isEmpty()) // to mitigate oversights. harmless anyway
            .collect(Collectors.toList());

        /*
        // Wh-word for the argument.
        add(question, wh[0]);
        // Add verb or aux-subject-verb.
        if (targetArgNum == slots[0].argumentNumber) {
            // If target argument is the subject (occupies the first slot).
            // This is not necessary arg1, for example: "xxxx", said she.
            addAll(question, getUnsplitPred());
        } else {
            String[] pred = getSplitPred();
            // Add auxiliaries, as "did" in "What did someone build?"
            // or "what was something among?"
            add(question, pred[0]);
            addAll(question, getPlaceholderWordsByArgNum(slots[0].argumentNumber));
            add(question, pred[1]);
        }
        // Add other arguments.
        for (int slotId = 2; slotId < slots.length; slotId++) {
            ArgumentSlot argSlot = slots[slotId];
            if (targetArgNum == argSlot.argumentNumber || (totalArgs >= 3 && !argSlot.preposition.isEmpty())) {
                continue;
            }
            addAll(question, getPlaceholderWordsByArgNum(argSlot.argumentNumber));
        }
        // Put preposition at the end.
        add(question, wh[1]);
        return question;
        */
    }

    /**
     * Get the wh-word (and extra words to append to the question) associated with
     * the expected answer to a question about argument argNum.
     * extra words e.g. in "what did someone he do X for?" "what did someone want X to do?"
     * @param argNum the argument number of the word we're abstracting away
     * @return a 2-element array of { "wh-word", "extra words" } where extra words may be empty
     */
    public String[] getWhWordByArgNum(int argNum) {
        int slotId = argNumToSlotId.get(argNum);
        ArgumentSlot slot = (ArgumentSlot) slots[slotId];
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return new String[] { "what", "to do" };
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            return new String[] { "what", "doing" };
        }
        if (slotId == 0) {
            return new String[] { "what", "" };
        }
        return new String[] { "what", slot.preposition };
    }

    public List<String> getTargetPlaceholderWords(int argNum) {
        if(type == QuestionType.NOUN_ADJUNCT) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>();
    }

    /**
     * Get placeholder words for arguments that aren't being asked about.
     * For an object this would be e.g., { "", "something" };
     * For an oblique argument this would be e.g., { "for", "something" }.
     * @param argNum argument not in question
     * @return a 2-element array of { "preposition", "placeholder" } where prep may be empty
     */
    public List<String> getArgPlaceholderWords(int argNum) {
        ArrayList<String> result = new ArrayList<>();
        int slotId = argNumToSlotId.get(argNum);
        ArgumentSlot slot = slots[slotId];
        int argumentIndex = slot.indexInSentence;
        if (UnrealizedArgumentSlot.class.isInstance(slot)) {
            return Arrays.asList(new String [] { slot.preposition, "something" });
        }

        // return Arrays.asList(new String[] { words.get(argumentIndex) });

        // what we ACTUALLY want is the smallest containing tree of the arg head
        // that has the same category as we take as an argument.
        SyntaxTreeNode argNode = tree.getLeaves().get(argumentIndex);
        Category argNodeCat = argNode.getCategory();
        while(!argNodeCat.matches(slot.category)) {
            argNode = TreeWalker.getParent(argNode, tree).get();
            argNodeCat = argNode.getCategory();
        }
        return Arrays.asList(new String [] { argNode.getWord() });

        /*
        // what we really want is the bigged constituent headed by the argument that doesn't contain the pred.
        Optional<SyntaxTreeNode> currentConstituent = Optional.of(tree);
        while(currentConstituent.isPresent() && currentConstituent.get().getDependencyStructure().getArbitraryHead() != argumentIndex) {
            currentConstituent = currentConstituent.get().getChildren().stream().filter(child -> {
                    int minIndex = child.getStartIndex();
                    int maxIndex = child.getEndIndex();
                    // this should happen exactly once
                    boolean argInConstituent = minIndex <= argumentIndex && maxIndex >= argumentIndex;
                    boolean predInConstituent = minIndex <= predSlot.indexInSentence && maxIndex >= predSlot.indexInSentence;
                    return argInConstituent && !predInConstituent;
                }).findFirst();
        }
        return Arrays.asList(new String [] { currentConstituent.map(node -> node.getWord()).orElse("OHNOES") });
        */

        /*
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return Arrays.asList(new String[] { "to do", "something" });
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            return Arrays.asList(new String[] { "doing", "something" });
        }
        // i.e. say something, says that ...
        if (slot.category.isFunctionInto(Category.valueOf("S"))) {
            return Arrays.asList(new String[] { "", "something" });
        }
        String phStr;
        if (categories.get(argumentIndex).equals(Category.NP)) {
            phStr =  words.get(argumentIndex);
        } else if (argumentIndex > 1 && categories.get(argumentIndex - 1).isFunctionInto(Category.valueOf("NP|N"))) {
            phStr =  words.get(argumentIndex - 1) + " " + words.get(argumentIndex);
        } else {
            phStr = (slotId == 0 ? "someone" : "something");
        }
        return Arrays.asList(new String[] { slot.preposition, phStr });
        */
    }

    /**
     * Create the pred as it should be realized in a question, possibly with a modal.
     * We try to keep in in the tense/aspect/voice/etc. of the clause it appeared in.
     * @return a 2-element array of { "modal", "verb" } where modal may be empty
     */
    public List<String> getUnsplitPred() {
        List<Integer> auxiliaries = predSlot.auxiliaries;
        String predStr = words.get(predSlot.indexInSentence);
        if (predSlot.hasParticle) {
            predStr += " " + words.get(predSlot.particleIndex);
        }
        if(type == QuestionType.VERB) {
            // If we have the infinitive such as "to allow", change it to would allow.
            //if (predicateCategory.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
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
            if (verbHelper.isUninflected(words, categories, predSlot.indexInSentence)) {
                return Arrays.asList(new String[] { "would", predStr });
            }
        } else if (type == QuestionType.NOUN_ADJUNCT) {
            return Arrays.asList(new String[] { predStr });
        } else if (type == QuestionType.VERB_ADJUNCT) {
            return Arrays.asList(new String[] { "happened", predStr });
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
        String[] result = new String[2];
        if (type == QuestionType.VERB) {
            if (predSlot.auxiliaries.size() == 0 ) {
                if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) || // predicative adjectives
                    predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) || // passive verbs
                    predicateCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) { // progressive verbs
                    return Arrays.asList(new String[] { "was", words.get(predSlot.indexInSentence) });
                }
                result = verbHelper.getAuxiliaryAndVerbStrings(words, categories, predSlot.indexInSentence);
                if (predSlot.hasParticle) {
                    result[1] += " " + words.get(predSlot.particleIndex);
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
        } else if (type == QuestionType.NOUN_ADJUNCT) {
            return Arrays.asList(new String[] { "was", words.get(predSlot.indexInSentence) });
        } else if (type == QuestionType.VERB_ADJUNCT) {
            return Arrays.asList(new String[] { "did", "do " + words.get(predSlot.indexInSentence) });
        }
        return Arrays.asList(result);
    }

    public String toString() {
        String str = "";
        str += predSlot.toString(words) + ":\t";
        for (ArgumentSlot slot : slots) {
            str += slot.toString(words) + "\t";
        }
        return str.trim();
    }

}
