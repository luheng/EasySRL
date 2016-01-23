package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;

/**
 * New query structure ...
 * Created by luheng on 1/20/16.
 */
public class Query {
    // < p, c, n, a, k, Q, A >
    int predicateIndex;
    Category category;
    int argumentNumber;
    List<Integer> argumentIds;
    int parseId;

    String question;
    String answer;

    public Query(int predicateIndex, final Category category, int argumentNumber,
                 final List<Integer> argumentIds, int parseId, String question, String answer) {
        this.predicateIndex = predicateIndex;
        this.category = category;
        this.argumentNumber = argumentNumber;
        this.argumentIds = argumentIds;
        this.parseId = parseId;
        this.question = question;
        this.answer = answer;
        // Let's say for now: the number of answerOptions don't have to match number of argument ids. They are just the set of
        // correct answerOptions that can be accepted regarding to this query. (still confusing?)
    }

}
