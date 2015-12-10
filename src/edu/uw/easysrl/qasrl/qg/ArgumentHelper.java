package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;

/**
 * Find constituents in a sentence, or transform the constituents into someone/something to place in generated questions.
 * Created by luheng on 12/9/15.
 */

public class ArgumentHelper {

    public ArgumentHelper() {

    }

    public String getPlaceHolderString(List<String> words, List<Category> categories, int predicateIndex,
                                       int argumentHeadIndex, int argumentSlotId) {
        if (argumentHeadIndex > 1 && categories.get(argumentHeadIndex - 1).isFunctionInto(Category.valueOf("NP|N"))) {
            return words.get(argumentHeadIndex - 1) + " " + words.get(argumentHeadIndex);
        }
        if (words.get(argumentHeadIndex).equalsIgnoreCase("what")) {
            return "something";
        }
        if (words.get(argumentHeadIndex).equalsIgnoreCase("who")) {
            return "someone";
        }
        return words.get(argumentHeadIndex);
    }

    public List<Integer> findConstituent(List<String> words, List<Category> categories, int headIndex) {
        return null;
    }
}
