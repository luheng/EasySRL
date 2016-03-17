package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;

/**
 * Created by luheng on 12/12/15.
 */
public class PrepositionHelper {
    public static String getPreposition(List<String> words, List<Category> categories, int index) {
        for (int i = index; i >= 0; i--) {
            if (categories.get(i).isFunctionInto(Category.PP)) {
                return words.get(i);
            }
        }
        return "";
    }
}
