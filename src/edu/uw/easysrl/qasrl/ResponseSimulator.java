package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;

import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates user response in active learning. When presented with a question, the simulator answers with its knowledge
 * of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulator {

    private QuestionGenerator questionGenerator;

    // TODO: simulate noise level.
    public ResponseSimulator(QuestionGenerator questionGenerator) {
        this.questionGenerator = questionGenerator;
    }

    /**
     * If exists a gold dependency that generates the same question ...
     * @param query: question
     * @param sentence: sentence
     * @param goldParse: gold categories and dependencies.
     * @return Answer is represented a list of indices in the sentence.
     *          A single -1 in the list means ``unintelligible/unanswerable question.
     */
    public Response answerQuestion(Query query, List<String> sentence, Parse goldParse) {
        List<Integer> answerIndices = new ArrayList<>();
        String questionStr = StringUtils.join(query.question);
        for (ResolvedDependency dep : goldParse.dependencies) {
            List<String> goldQuestion = questionGenerator.generateQuestion(dep, sentence, goldParse.categories,
                    goldParse.dependencies);
            if (goldQuestion == null || goldQuestion.size() == 0) {
                continue;
            }
            if (questionStr.equalsIgnoreCase(StringUtils.join(goldQuestion))) {
                answerIndices.add(dep.getArgumentIndex());
            }
        }
        if (answerIndices.size() == 0) {
            answerIndices.add(-1);
        }
        return new Response(answerIndices);
    }
}
