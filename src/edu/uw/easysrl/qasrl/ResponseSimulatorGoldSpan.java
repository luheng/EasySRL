package edu.uw.easysrl.qasrl;

/**
 * Created by luheng on 2/3/16.
 */

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simulates user response in active learning. When presented with a question, the simulator answerOptions with its knowledge
 * of gold dependencies.
 * Created by luheng on 1/5/16.
 */

/*
public class ResponseSimulatorGoldSpan extends ResponseSimulator {
    private final List<Parse> goldParses;
    private QuestionGenerator questionGenerator;

    // TODO: simulate noise level.
    // TODO: partial reward for parses that got part of the answer heads right ..
    public ResponseSimulatorGoldSpan(List<Parse> goldParses, QuestionGenerator questionGenerator) {
        this.goldParses = goldParses;
        this.questionGenerator = questionGenerator;
    }


    public Response answerQuestion(GroupedQuery query) {
        final Parse goldParse = goldParses.get(query.sentenceId);
        final List<String> sentence = query.sentence;
        final int predId = query.predicateIndex;
        List<Integer> answerIndices = new ArrayList<>();
        for (ResolvedDependency dep : goldParse.dependencies) {
            if (dep.getHead() != predId) {
                continue;
            }
            List<String> goldQuestion = questionGenerator.generateQuestion(dep, sentence, goldParse).questionWords;
            String goldQuestionStr = (goldQuestion == null || goldQuestion.size() == 0) ? "-NOQ-" :
                    goldQuestion.stream().collect(Collectors.joining(" "));
            boolean questionMatch = query.question.equalsIgnoreCase(goldQuestionStr);
            boolean labelMatch = (dep.getCategory() == query.category && dep.getArgNumber() == query.argumentNumber);
            if (questionMatch || labelMatch) {
                if (!goldQuestionStr.equals("-NOQ-") || labelMatch) {
                    answerIndices.addAll(AnswerGenerator.getArgumentIdsForDependency(sentence, goldParse, dep));
                }
            }
        }

        Category goldPredCategory = goldParse.categories.get(predId),
                 goldArgCategory =  goldPredCategory.getArgument(query.argumentNumber);
        String goldSpan = answerIndices.size() == 0 ? "" :
                AnswerGenerator.getAnswerSpan(goldParse, sentence, query.predicateIndex, goldPredCategory,
                                              answerIndices, goldArgCategory);

        Response response = new Response();
        int noAnswerOptionId = 0;
        for (int i = 0; i < query.answerOptions.size(); i++) {
            GroupedQuery.AnswerOption option = query.answerOptions.get(i);
            if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                if (answerIndices.size() == 0) {
                    response.add(i);
                }
            } else if (GroupedQuery.NoAnswerOption.class.isInstance(option)) {
                noAnswerOptionId = i;
            } else if (option.answer.equalsIgnoreCase(goldSpan)) {
                response.add(i);
            }
        }
        if (response.chosenOptions.isEmpty()) {
            response.add(noAnswerOptionId);
        }
        response.debugInfo = "[gold-span]:\t" + goldSpan;
        return response;
    }
}

*/
