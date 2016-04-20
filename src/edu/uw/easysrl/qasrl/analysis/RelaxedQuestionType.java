package edu.uw.easysrl.qasrl.analysis;

import edu.uw.easysrl.syntax.grammar.Category;

/**
 * Created by luheng on 4/19/16.
 */

public enum RelaxedQuestionType {
    VERB (
            Category.valueOf("S\\NP") // intransitives
            ,Category.valueOf("(S\\NP)/NP") // transitives
            ,Category.valueOf("(S\\NP)/PP")
            ,Category.valueOf("((S\\NP)/PP)/NP")
            ,Category.valueOf("(S\\NP)/(PP/NP)") // e.g., an action was called for
            ,Category.valueOf("((S\\NP)/NP)/PR")
            ,Category.valueOf("((S\\NP)/PP)/PR")
            // T1 said (that) T2
            ,Category.valueOf("(S[dcl]\\NP)/S")
            // T1, T2 said, or T1, said T2
            ,Category.valueOf("(S[dcl]\\S[dcl])|NP")
            // T1 agreed to do T2
            ,Category.valueOf("(S\\NP)/(S[to]\\NP)")
            // T1 stopped using T2
            ,Category.valueOf("(S\\NP)/(S[ng]\\NP)")
            // T1 made T3 T2; ditransitives
            ,Category.valueOf("((S\\NP)/NP)/NP")
            // T1 promised T3 to do T2
            ,Category.valueOf("((S\\NP)/(S[to]\\NP))/NP") // Category.valueOf("((S[dcl]\\NP)/(S[to]\\NP))/NP")
    ),

    ADJECTIVE_ADJUNCT(
            Category.valueOf("((S[adj]\\NP)\\(S[adj]\\NP))/NP")
    ),

    NOUN_ADJUNCT(
            Category.valueOf("(NP\\NP)/NP")
            // ,Category.valueOf("N|N"),
    ),

    // right now we're assuming the second arg of a verb adjunct is always the main verb.
    VERB_ADJUNCT(
            Category.valueOf("((S\\NP)\\(S\\NP))/NP")
            ,Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]")
                 /* ,Category.valueOf("(S\\NP)\\(S\\NP)")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/S")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/(S[ng]\\NP)") // ``by'' as in ``by doing something''.
                    ,Category.valueOf("((S\NP)\(S\NP))/PP") // according (to):
                    ,Category.valueOf("((S\NP)\(S\NP))/PP") // down (from):
                 */
    ),

    CLAUSE_ADJUNCT(
            // Category.valueOf("S|S"),
    ),

    RELATIVIZER(
            // Category.valueOf("(NP\\NP)/(S[dcl]\\NP)")
    ),

    INVALID();

    public final Category[] categories;

    RelaxedQuestionType(Category... categories) {
        this.categories = categories;
    }

    public boolean admits(Category category) {
        for (Category c : categories) {
            if (c.matches(category)) {
                return true;
            }
        }
        return false;
    }

    public static RelaxedQuestionType getTypeFor(Category category) {
        for (RelaxedQuestionType type : RelaxedQuestionType.values()) {
            if(type.admits(category)) {
                return type;
            }
        }
        return INVALID;
    }


}