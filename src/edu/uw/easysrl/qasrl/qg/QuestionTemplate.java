package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;

import java.util.*;

/**
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

    // whMapper
    // Examples:
    // { "what", "for" }, {"what", "to do" }
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

    // phMapper
    // Examples:
    // { "", "something" }, {"for", "something" }
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

    // i.e. {"", "built"}, or {"might", "build"}
    // if the verb was passive to start with, as in S[adj]\NP, or S[pss]\NP, we keep the voice unchanged.
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

    // If the verb is a single inflected one, we need to change it: "built" -> {"did", "build"}
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

    // i.e. {"was", "built"}, {"have been", "built"}
    /*public List<String> getPassiveVerb() {
        return null;
    }*/

}
