package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.syntax.grammar.Category;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
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

    // Evidence propagation switches
    // TODO: debug this ..
    public boolean propagateArgumentAdjunctEvidence = false;

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
         Response response = new Response();
         int badQuestionOptionId = -1, noAnswerOptionId = -1;
         String goldQuestion = "", goldAnswer = "";

         final Parse goldParse = goldParses.get(query.sentenceId);
         final List<String> sentence = query.sentence;
         int predId = query.predicateIndex;
         Category goldCategory = goldParse.categories.get(predId);

         for (int i = 0; i < query.answerOptions.size(); i++) {
             GroupedQuery.AnswerOption option = query.answerOptions.get(i);
             if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                 badQuestionOptionId = i;
             } else if (GroupedQuery.NoAnswerOption.class.isInstance(option)) {
                 noAnswerOptionId = i;
             }
         }

         for (int argNum = 1; argNum <= goldCategory.getNumberOfArguments(); argNum++) {
            List<QuestionAnswerPairReduced> qaList = QuestionGenerator
                    .generateAllQAPairs(predId, argNum, sentence, goldParse).stream()
                    .sorted((a1, a2) -> Integer.compare(a1.targetDep.getArgument(), a2.targetDep.getArgument()))
                    .collect(Collectors.toList());
            if (qaList == null || qaList.size() == 0) {
                continue;
            }
            String questionStr = qaList.get(0).renderQuestion();
            String answerStr = qaList.stream()
                    .map(QuestionAnswerPairReduced::renderAnswer)
                    .collect(Collectors.joining(QuestionAnswerPair.answerDelimiter));
            boolean questionMatch = query.question.equalsIgnoreCase(questionStr);
            boolean labelMatch = (goldCategory == query.category && argNum == query.argumentNumber);
            if (!questionMatch && !(allowLabelMatch && labelMatch)) {
                continue;
            }
            for (int i = 0; i < query.answerOptions.size(); i++) {
                GroupedQuery.AnswerOption option = query.answerOptions.get(i);
                if (!option.isNAOption() && option.getAnswer().equals(answerStr)) {
                    response.add(i);
                }
            }
            goldQuestion = questionStr;
            goldAnswer = answerStr;
            break;
         }

         if (response.chosenOptions.size() == 0) {
            if (!goldAnswer.isEmpty()) {
                response.add(noAnswerOptionId);
                response.debugInfo = "[gold]:\t" + goldAnswer;
            } else {
                response.add(badQuestionOptionId);
            }
         }
        return response;
    }
}
