package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A query is associated with a single Parse. It may have multiple answer heads in case of conjunctions and appositives.
 * Created by luheng on 1/20/16.
 */
public class Query {
    // < p, c, n, a, k, Q, A >
    int predicateIndex;
    Category category;
    int argumentNumber;
    List<Integer> argumentIds;
    int parseId;
    QuestionAnswerPair qaPair;
    String question;

    public Query(int predicateIndex, final Category category, int argumentNumber, final List<Integer> argumentIds,
                 int parseId, QuestionAnswerPair qaPair) {
        this.predicateIndex = predicateIndex;
        this.category = category;
        this.argumentNumber = argumentNumber;
        this.argumentIds = argumentIds;
        this.parseId = parseId;
        this.qaPair = qaPair;
        this.question = qaPair.renderQuestion();
    }
}
