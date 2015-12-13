package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;

import java.util.*;

/**
 * Created by luheng on 12/10/15.
 */
public class QuestionTemplate {
    public Category predicateCategory;
    public List<String> words;
    public List<Category> categories;
    public Collection<CCGBankDependency> dependencies;

    public QuestionSlot[] slots;
    public VerbSlot verbSlot;
    public Map<Integer, Integer> argNumToSlotId;

    public QuestionTemplate(QuestionSlot[] slots, List<String> words, List<Category> categories,
                            Collection<CCGBankDependency> dependencies) {
        this.slots = slots;
        this.words = words;
        this.categories = categories;
        this.dependencies = dependencies;
        this.argNumToSlotId = new HashMap<>();
        // do something ...
        for (int slotId = 0; slotId < slots.length; slotId++) {
            argNumToSlotId.put(slots[slotId].argumentNumber, slotId);
            if (VerbSlot.class.isInstance(slots[slotId])) {
                verbSlot = (VerbSlot) slots[slotId];
                predicateCategory = verbSlot.category;
            }
        }
    }

    public int getNumArguments() {
        return slots.length - 1;
    }

    // whMapper
    // TODO: split "What is something given to", "What was someone expected to do"
    public String getWhWord(int argNum) {
        ArgumentSlot slot = (ArgumentSlot) slots[argNumToSlotId.get(argNum)];
        if (slot.hasPreposition) {
            // FIXME: this is a hack. How can we get PPs?
            return words.get(verbSlot.indexInSentence + 1) + " what";
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return "to do what";
        }
        return argNum == getNumArguments() ?  "what" : "who";
    }

    // phMapper
    public String getPlaceHolderWord(int argNum) {
        ArgumentSlot slot = (ArgumentSlot) slots[argNumToSlotId.get(argNum)];
        if (UnrealizedArgumentSlot.class.isInstance(slot)) {
            return "something";
        }
        int argumentIndex = slot.indexInSentence;
        if (argumentIndex > 1 && categories.get(argumentIndex - 1).isFunctionInto(Category.valueOf("NP|N"))) {
            return words.get(argumentIndex - 1) + " " + words.get(argumentIndex);
        }
        // FIXME: this is a hack.
        if (slot.hasPreposition) {
            return words.get(verbSlot.indexInSentence + 1) + " something";
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return "to do something";
        }
        if (words.get(argumentIndex).equalsIgnoreCase("what")) {
            return "something";
        }
        if (words.get(argumentIndex).equalsIgnoreCase("who")) {
            return "someone";
        }
        return "something";
    }

    // i.e. {"", "built"}, or {"might", "build"}
    // if the verb was passive to start with, as in S[adj]\NP, or S[pss]\NP, we keep the voice unchanged.
    public List<String> getActiveVerb(VerbHelper verbHelper) {
        List<String> result = new ArrayList<>();
        List<Integer> auxiliaries = verbSlot.auxiliaries;
        if (auxiliaries.size() > 0) {
            auxiliaries.forEach(aux -> result.add(words.get(aux)));
        } else if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
                   predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP"))) {
            result.add("might");
            result.add("be");
        } else if (verbHelper.isUninflected(words, categories, verbSlot.indexInSentence)) {
            result.add("might");
        }
        result.add(words.get(verbSlot.indexInSentence));
        return result;
    }

    // i.e. "built" -> {"did", "build"}
    public List<String> getActiveSplitVerb(VerbHelper verbHelper) {
        // if the verb was passive to start with, or has auxiliaries by itself ..
        List<String> result = new ArrayList<>();
        List<Integer> auxiliaries = verbSlot.auxiliaries;
        int verbIndex = verbSlot.indexInSentence;
        if (auxiliaries.size() > 0) {
            auxiliaries.forEach(aux -> result.add(words.get(aux)));
            result.add(words.get(verbIndex));
        } else {
            String[] split = verbHelper.getAuxiliaryAndVerbStrings(words, null /* categories */,  verbIndex);
            result.add(split[0]);
            result.add(split[1]);
        }
        return result;
    }

    // i.e. {"was", "built"}, {"have been", "built"}
    public List<String> getPassiveVerb() {
        return null;
    }

}
