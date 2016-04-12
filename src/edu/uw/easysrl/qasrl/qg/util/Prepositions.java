package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by luheng on 4/11/16.
 */
public class Prepositions {
    public static Set<Category> prepositionalCategories = new HashSet<>();
    static {
        Collections.addAll(prepositionalCategories,
                Category.valueOf("((S\\NP)\\(S\\NP))/NP"),
                Category.valueOf("(NP\\NP)/NP"),
                Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"),
                Category.valueOf("PP\\NP")
        );
    }
}
