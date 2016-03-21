package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;

/**
 * Created by luheng on 3/20/16.
 */
public class QuestionKey {
    public final int predicateIndex;
    public final Category predicateCategory;
    public final int argumentNumber;

    public QuestionKey(int predId, Category category, int argumentNumber) {
        this.predicateIndex = predId;
        this.predicateCategory = category;
        this.argumentNumber = argumentNumber;
    }
}
