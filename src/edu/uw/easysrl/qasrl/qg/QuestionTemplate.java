package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
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
    // TODO: make these tuples ..
    // Examples:
    // { "what", "for" }, {"what", "to do" }
    public String[] getWhWord(int argNum) {
        ArgumentSlot slot = (ArgumentSlot) slots[argNumToSlotId.get(argNum)];
        if (slot.hasPreposition) {
            // FIXME: this is a hack. How can we get PPs?
            return new String[] { "what", words.get(verbSlot.indexInSentence + 1),  };
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return new String[] { "what", "to do" };
        }
        if (argNum == getNumArguments()) {
            return new String[] { "what", ""};
        }
        return new String[] { "who", ""};
    }

    // phMapper
    // Examples:
    // { "", "something" }, {"for", "something" }
    public String[] getPlaceHolderWord(int argNum) {
        ArgumentSlot slot = (ArgumentSlot) slots[argNumToSlotId.get(argNum)];
        if (UnrealizedArgumentSlot.class.isInstance(slot)) {
            return new String[] {"", "something"};
        }
        if (slot.category.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            return new String[] {"to do", "something"};
        }
        int argumentIndex = slot.indexInSentence;
        String phStr = "";
        if (argumentIndex > 1 && categories.get(argumentIndex - 1).isFunctionInto(Category.valueOf("NP|N"))) {
            phStr =  words.get(argumentIndex - 1) + " " + words.get(argumentIndex);
        } else {
            phStr = words.get(argumentIndex).equalsIgnoreCase("who") ? "someone" : "something";
        }
        if (slot.hasPreposition) {
            return new String[] {words.get(verbSlot.indexInSentence + 1), phStr};
        }
        return new String[] {"", phStr};
    }

    // i.e. {"", "built"}, or {"might", "build"}
    // if the verb was passive to start with, as in S[adj]\NP, or S[pss]\NP, we keep the voice unchanged.
    public String[] getActiveVerb(VerbHelper verbHelper) {
        List<Integer> auxiliaries = verbSlot.auxiliaries;
        String verbStr = words.get(verbSlot.indexInSentence);
        // i.e. to allow
        if (predicateCategory.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
            if (auxiliaries.size() > 0 && words.get(auxiliaries.get(0)).equalsIgnoreCase("to")) {
                return new String[]{"would", verbStr};
            }
        }
        if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
            predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP"))) {
            return new String[] {"might be", verbStr};
        }
        if (auxiliaries.size() > 0) {
            String aux = "";
            for (int id : auxiliaries) {
                aux += words.get(id) + " ";
            }
            return new String[] {aux.trim(), verbStr};
        }
        if (verbHelper.isUninflected(words, categories, verbSlot.indexInSentence)) {
            return new String[] {"might", verbStr};
        }
        return new String[] {"", verbStr};
    }

    // If the verb is a single inflected one, we need to change it: "built" -> {"did", "build"}
    public String[] getActiveSplitVerb(VerbHelper verbHelper) {
        if (verbSlot.auxiliaries.size() == 0) {
            return verbHelper.getAuxiliaryAndVerbStrings(words, null /* categories */,  verbSlot.indexInSentence);
        }
        return getActiveVerb(verbHelper);

    }

    // i.e. {"was", "built"}, {"have been", "built"}
    public List<String> getPassiveVerb() {
        return null;
    }

}
