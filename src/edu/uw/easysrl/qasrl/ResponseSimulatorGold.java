package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            Optional<QuestionAnswerPair> goldQaPairOpt = questionGenerator.generateQuestion(dep, sentence, goldParse);
            String goldQuestionStr = goldQaPairOpt.map(QuestionAnswerPair::renderQuestion).orElse("-NOQ-");
            boolean questionMatch = query.question.equalsIgnoreCase(goldQuestionStr);
            boolean labelMatch = (dep.getCategory() == query.category && dep.getArgNumber() == query.argumentNumber);
            if (questionMatch || (allowLabelMatch && labelMatch)) {
                answerIndices.addAll(TextGenerationHelper.getArgumentIdsForDependency(sentence, goldParse, dep));
            }
        }
        Response response = new Response();
        int bestOption = -1;
        int maxOverlap = 0;
        int badQuestionOptionId = -1;
        for (int i = 0; i < query.answerOptions.size(); i++) {
            GroupedQuery.AnswerOption option = query.answerOptions.get(i);
            if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                badQuestionOptionId = i;
                continue;
            }
            int argOverlap = (int) option.argumentIds.stream().filter(answerIndices::contains).count();
            //if (argOverlap > maxOverlap) {
            if (argOverlap == answerIndices.size() && argOverlap == option.argumentIds.size()) {
                maxOverlap = argOverlap;
                //bestOption = i;
                response.add(i);
            }
        }
        //response.add(bestOption >= 0 ? bestOption : badQuestionOptionId);
        if (response.chosenOptions.size() == 0) {
            response.add(badQuestionOptionId);
        }
        return response;
    }
}
