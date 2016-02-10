package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A wrapper around QuestionAnswerPair.
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
    String question, answer;

    public Query(QuestionAnswerPair qaPair, int parseId) {
        this.predicateIndex = qaPair.predicateIndex;
        this.category = qaPair.predicateCategory;
        this.argumentNumber = qaPair.targetDeps.get(0).getArgNumber();
        this.argumentIds = qaPair.targetDeps.stream()
                .map(ResolvedDependency::getArgumentIndex)
                .sorted(Integer::compare)
                .collect(Collectors.toList());
        this.parseId = parseId;
        this.qaPair = qaPair;
        this.question = qaPair.renderQuestion();
        this.answer = qaPair.renderAnswer();
    }
}
