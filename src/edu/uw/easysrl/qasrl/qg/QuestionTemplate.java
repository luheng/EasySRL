package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    public QuestionTemplate(QuestionSlot[] slots, List<String> words, List<Category> categories,
                            Collection<CCGBankDependency> dependencies) {
        this.slots = slots;
        this.words = words;
        this.categories = categories;
        this.dependencies = dependencies;

        // do something ...
        for (int i = 0; i < slots.length; i++) {
            if (VerbSlot.class.isInstance(slots[i])) {
                verbSlot = (VerbSlot) slots[i];
                predicateCategory = verbSlot.category;
            }
        }
    }

    // whMapper
    public String getWhWord(int slotId) {
        // very dumb way ...
        return slotId == 0 ? "who" : "what";
    }

    // phMapper
    public String getPlaceHolderWord(int slotId) {
        ArgumentSlot slot = (ArgumentSlot) slots[slotId];
        if (UnrealizedArgumentSlot.class.isInstance(slot)) {
            return "something";
        }
        int argumentIndex = slot.indexInSentence;
        if (argumentIndex > 1 && categories.get(argumentIndex - 1).isFunctionInto(Category.valueOf("NP|N"))) {
            return words.get(argumentIndex - 1) + " " + words.get(argumentIndex);
        }
        if (words.get(argumentIndex).equalsIgnoreCase("what")) {
            return "something";
        }
        if (words.get(argumentIndex).equalsIgnoreCase("who")) {
            return "someone";
        }
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

    // i.e. {"did", "build"}
    public String[] getActiveSplitVerb(VerbHelper verbHelper) {

    }

    // i.e. {"was", "built"}, {"have been", "built"}
    public String[] getPassiveVerb() {

    }

}
