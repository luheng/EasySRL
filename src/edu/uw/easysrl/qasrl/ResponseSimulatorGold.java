package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simulates user response in active learning. When presented with a question, the simulator answerOptions with its knowledge
 * of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulatorGold extends ResponseSimulator {
    private final List<Parse> goldParses;
    private QuestionGenerator questionGenerator;
    private boolean allowLabelMatch = true;

    // TODO: simulate noise level.
    // TODO: partial reward for parses that got part of the answer heads right ..
    public ResponseSimulatorGold(List<Parse> goldParses, QuestionGenerator questionGenerator) {
        this.goldParses = goldParses;
        this.questionGenerator = questionGenerator;
    }

    public ResponseSimulatorGold(List<Parse> goldParses, QuestionGenerator questionGenerator, boolean allowLabelMatch) {
        this(goldParses, questionGenerator);
        this.allowLabelMatch = allowLabelMatch;
    }


    /**
     * If exists a gold dependency that generates the same question ...
     * @param query: question
     * @return Answer is represented a list of indices in the sentence.
     *          A single -1 in the list means ``unintelligible/unanswerable question.
     */
     public Response answerQuestion(GroupedQuery query) {
        final Parse goldParse = goldParses.get(query.sentenceId);
        final List<String> sentence = query.sentence;
        List<Integer> answerIndices = new ArrayList<>();
        for (ResolvedDependency dep : goldParse.dependencies) {
            if (dep.getHead() != query.predicateIndex) {
                continue;
            }
            // TODO: use qaPair.
            // TODO: try gold simulator without label match
            QuestionAnswerPair goldQA = questionGenerator.generateQuestion(dep, sentence, goldParse);
            String goldQuestionStr = (goldQA == null || goldQA.questionWords.size() == 0) ?
                    "-NOQ-" : goldQA.renderQuestion();
            boolean questionMatch = query.question.equalsIgnoreCase(goldQuestionStr) && !goldQuestionStr.equals("-NOQ-");
            boolean labelMatch = (dep.getCategory() == query.category && dep.getArgNumber() == query.argumentNumber);
            if (questionMatch || (allowLabelMatch && labelMatch)) {
                answerIndices.addAll(AnswerGenerator.getArgumentIdsForDependency(sentence, goldParse, dep));
            }
        }
        Response response = new Response();
        for (int i = 0; i < query.answerOptions.size(); i++) {
            GroupedQuery.AnswerOption option = query.answerOptions.get(i);
            // If gold choose N/A option.
            if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                if (answerIndices.size() == 0) {
                    response.add(i);
                }
            } else if (option.argumentIds.containsAll(answerIndices) && answerIndices.containsAll(option.argumentIds)) {
                response.add(i);
            }
        }
        return response;
    }
}
