package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.Query;
import edu.uw.easysrl.qasrl.query.QueryGenerator;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simulates user response in active learning. When presented with a question, the simulator answerOptions with its knowledge
 * of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulatorGold extends ResponseSimulator {
    private final List<Parse> goldParses;
    private boolean allowLabelMatch = true;

    private final ParseData parseData;

    public ResponseSimulatorGold(ParseData parseData) {
        this.parseData = parseData;
        this.goldParses = parseData.getGoldParses();
    }

    @Deprecated
    public ResponseSimulatorGold(List<Parse> goldParses) {
        this.parseData = null;
        this.goldParses = goldParses;
    }

    @Deprecated
    public ResponseSimulatorGold(List<Parse> goldParses, boolean allowLabelMatch) {
        this(goldParses);
        this.allowLabelMatch = allowLabelMatch;
    }


    /**
     * If exists a gold dependency that generates the same question ...
     * @param query: question
     * @return Answer is represented a list of indices in the sentence.
     *          A single -1 in the list means ``unintelligible/unanswerable question.
     */
     @Deprecated
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
            List<RawQuestionAnswerPair> qaList = QuestionGenerator
                    .generateAllQAPairs(predId, argNum, sentence, goldParse).stream()
                    .sorted((a1, a2) -> Integer.compare(a1.targetDep.getArgument(), a2.targetDep.getArgument()))
                    .collect(Collectors.toList());
            if (qaList == null || qaList.size() == 0) {
                continue;
            }
            String questionStr = qaList.get(0).renderQuestion();
            String answerStr = qaList.stream()
                    .map(RawQuestionAnswerPair::renderAnswer)
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


     public ImmutableList<Integer> respondToQuery(Query query) {
         final int sentenceId = query.getSentenceId();
         final Parse goldParse = goldParses.get(sentenceId);

         Set<Integer> chosenOptions = new HashSet<>();
         if (!query.isJeopardyStyle()) {
             final ImmutableList<QAPairSurfaceForm> qaOptions = query.getQAPairSurfaceForms();
             final int predicateId = query.getPredicateId().getAsInt();
             final ImmutableSet<Integer> goldArgIds = goldParse.dependencies.stream()
                     .filter(dep -> dep.getHead() == predicateId)
                     .map(ResolvedDependency::getArgument)
                     .distinct()
                     .collect(GuavaCollectors.toImmutableSet());
             if (query.allowMultipleChoices()) {
                 IntStream.range(0, qaOptions.size())
                         .filter(optionId -> {
                             final int argId = qaOptions.get(optionId).getQAPairs().get(0).getArgumentIndex();
                             return goldArgIds.contains(argId);
                         })
                         .forEach(chosenOptions::add);
             } else {
                 IntStream.range(0, qaOptions.size())
                         .filter(optionId -> {
                                     final ImmutableList<Integer> argIds = qaOptions.get(optionId).getQAPairs().stream()
                                             .map(IQuestionAnswerPair::getArgumentIndex)
                                             .distinct()
                                             .collect(GuavaCollectors.toImmutableList());
                                     return goldArgIds.containsAll(argIds);
                                 })
                         .forEach(chosenOptions::add);
             }
             if (chosenOptions.size() == 0) {
                 if (goldArgIds.size() > 0 && query.getUnlistedAnswerOptionId().isPresent()) {
                     chosenOptions.add(query.getUnlistedAnswerOptionId().getAsInt());
                 } else if (goldArgIds.size() == 0 && query.getBadQuestionOptionId().isPresent()){
                     chosenOptions.add(query.getBadQuestionOptionId().getAsInt());
                 }
             }
         }
         // TODO: jeopardy style questions
         return chosenOptions.stream().sorted().collect(GuavaCollectors.toImmutableList());
     }
}
