package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;

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
    public Category predicateCategory;
    public List<String> words;
    public List<Category> categories;

    public QuestionSlot[] slots;
    public VerbSlot verbSlot;
    public Map<Integer, Integer> argNumToSlotId;

    public QuestionTemplate(QuestionSlot[] slots, List<String> words, List<Category> categories) {
        this.slots = slots;
        this.words = words;
        this.categories = categories;
        this.argNumToSlotId = new HashMap<>();
        for (int slotId = 0; slotId < slots.length; slotId++) {
            argNumToSlotId.put(slots[slotId].argumentNumber, slotId);
            if (VerbSlot.class.isInstance(slots[slotId])) {
                verbSlot = (VerbSlot) slots[slotId];
                predicateCategory = verbSlot.category;
            }
        }
    }

    // Total number of slots, minus the verb.
    public int getNumArguments() {
        return slots.length - 1;
    }

    /**
     * Instantiate into a question about a particular argument.
     * @param targetArgNum : the argument number under the predicate
     *                       associated with what's being asked about in the question
     * @param verbHelper
     * @return a question asking for the argument in slot targetArgNum of template's predicate
     */
    public List<String> instantiateForArgument(int targetArgNum, VerbHelper verbHelper) {
        int totalArgs = getNumArguments();
        List<String> question = new ArrayList<>();
        if (!argNumToSlotId.containsKey(targetArgNum)) {
            return question;
        }
        String[] wh = getWhWordByArgNum(targetArgNum);
        // Wh-word for the argument.
        add(question, wh[0]);
        // Add verb or aux-subject-verb.
        if (targetArgNum == slots[0].argumentNumber) {
            // If target argument is the subject (occupies the first slot).
            // Not necessary Arg#1, for example, "xxxx", said she.
            addAll(question, getActiveVerb(verbHelper));
        } else {
            String[] verb = getActiveSplitVerb(verbHelper);
            // Add auxiliaries, as "did" in "What did someone build?".
            add(question, verb[0]);
            addAll(question, getPlaceHolderWordByArgNum(slots[0].argumentNumber));
            add(question, verb[1]);
        }
        // Add other Arguments.
        for (int slotId = 2; slotId < slots.length; slotId++) {
            ArgumentSlot argSlot = (ArgumentSlot) slots[slotId];
            if (targetArgNum == argSlot.argumentNumber || (totalArgs >= 3 && !argSlot.preposition.isEmpty())) {
                continue;
            }
            addAll(question, getPlaceHolderWordByArgNum(argSlot.argumentNumber));
        }
        // Put preposition to the end.
        add(question, wh[1]);
        return question;
    }

    private static void add(List<String> question, String word) {
        if (!word.isEmpty()) {
            question.add(word);
        }
    }

    private static void addAll(List<String> question, String[] words) {
        for (String w : words) {
            add(question, w);
        }
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
        if (slotId == 0 && getNumArguments() > 1) {
            return new String[] { "who", "" };
        }
        return new String[] { "what", slot.preposition };
    }

    /**
     * Get placeholder words for arguments that aren't being asked about.
     * For an object this would be e.g., { "", "something" };
     * For an oblique argument this would be e.g., { "for", "something" }.
     * @param argNum argument not in question
     * @return a 2-element array of { "preposition", "placeholder" } where prep may be empty
     */
    public String[] getPlaceHolderWordByArgNum(int argNum) {
        int slotId = argNumToSlotId.get(argNum);
        ArgumentSlot slot = (ArgumentSlot) slots[slotId];
        int argumentIndex = slot.indexInSentence;
        if (UnrealizedArgumentSlot.class.isInstance(slot)) {
            return new String [] { slot.preposition, argNum == 1 && getNumArguments() > 1 ? "someone" : "something" };
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return new String[] { "to do", "something" };
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            return new String[] { "doing", "something" };
        }
        // i.e. say something, says that ...
        if (slot.category.isFunctionInto(Category.valueOf("S"))) {
            return new String[] { "", "something" };
        }
        String phStr;
        if (categories.get(argumentIndex).equals(Category.NP)) {
            phStr =  words.get(argumentIndex);
        } else if (argumentIndex > 1 && categories.get(argumentIndex - 1).isFunctionInto(Category.valueOf("NP|N"))) {
            phStr =  words.get(argumentIndex - 1) + " " + words.get(argumentIndex);
        } else {
            phStr = (slotId == 0 && getNumArguments() > 1 ? "someone" : "something");
        }
        return new String[] { slot.preposition, phStr };
    }

    /**
     * Create the verb as it should be realized in a question, possibly with a modal.
     * if the verb was passive to start with, as in S[adj]\NP, or S[pss]\NP, we keep the voice unchanged.
     * @param verbHelper helps us figure out whether verb is inflected (proxy for veridicality)
     * @return a 2-element array of { "modal", "verb" } where modal may be empty
     */
    public String[] getActiveVerb(VerbHelper verbHelper) {
        List<Integer> auxiliaries = verbSlot.auxiliaries;
        String verbStr = words.get(verbSlot.indexInSentence);
        if (verbSlot.hasParticle) {
            verbStr += " " + words.get(verbSlot.particleIndex);
        }
        // If we have the infinitive such as "to allow", change it to would allow.
        //if (predicateCategory.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
        if (auxiliaries.size() > 0 && words.get(auxiliaries.get(0)).equalsIgnoreCase("to")) {
            return new String[]{ "would", verbStr };
        }

        // If the verb has its own set of auxiliaries, return those as is.
        if (auxiliaries.size() > 0) {
            String aux = "";
            for (int id : auxiliaries) {
                aux += words.get(id) + " ";
            }
            return new String[] { aux.trim(), verbStr };
        }
        if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
            predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
            predicateCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            return new String[] { "might be", verbStr };
        }
        if (verbHelper.isUninflected(words, categories, verbSlot.indexInSentence)) {
            return new String[] { "might", verbStr };
        }
        return new String[] { "", verbStr };
    }

    /**
     * If the argument in question is not the subject,
     * we will need to split the verb from its auxiliary,
     * e.g., "built" -> {"did", "build"}
     * TODO is the below description correct?
     * @param verbHelper
     * @return a 2-element array of { "aux", "verb" } where verb is uninflected
     */
    public String[] getActiveSplitVerb(VerbHelper verbHelper) {
        String[] result;
        if (verbSlot.auxiliaries.size() == 0 ) {
            if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
                    predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
                    predicateCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
                return new String[] { "was", words.get(verbSlot.indexInSentence) };
            }
            result = verbHelper.getAuxiliaryAndVerbStrings(words, null /* categories */,  verbSlot.indexInSentence);
            if (verbSlot.hasParticle) {
                result[1] += " " + words.get(verbSlot.particleIndex);
            }
        } else {
            String[] r = getActiveVerb(verbHelper);
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
        return result;
    }

    public String toString() {
        String str = "";
        for (QuestionSlot slot : slots) {
            str += slot.toString(words) + "\t";
        }
        return str.trim();
    }

}
