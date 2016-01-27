package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simulates user response in active learning. When presented with a question, the simulator answerOptions with its knowledge
 * of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulatorGold extends ResponseSimulator {

    private QuestionGenerator questionGenerator;

    // TODO: simulate noise level.
    // TODO: partial reward for parses that got part of the answer heads right ..
    public ResponseSimulatorGold(QuestionGenerator questionGenerator) {
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
     public int answerQuestion(GroupedQuery query, List<String> sentence, Parse goldParse) {
        List<Integer> answerIndices = new ArrayList<>();
        for (ResolvedDependency dep : goldParse.dependencies) {
            if (dep.getHead() != query.predicateIndex) {
                continue;
            }
            List<String> goldQuestion = questionGenerator.generateQuestion(dep, sentence, goldParse.categories,
                    goldParse.dependencies);
            /*
            if (goldQuestion == null || goldQuestion.size() == 0) {
                continue;
            }
            */
            String goldQuestionStr = (goldQuestion == null || goldQuestion.size() == 0) ? "-NOQ-" :
                    goldQuestion.stream().collect(Collectors.joining(" "));
            if (query.question.equalsIgnoreCase(goldQuestionStr)) {
                if (!goldQuestionStr.equals("-NOQ-") ||
                        (dep.getCategory() == query.category && dep.getArgNumber() == query.argumentNumber)) {
                    answerIndices.addAll(AnswerGenerator.getArgumentIds(sentence, goldParse, dep));
                }
            }
        }
        for (int i = 0; i < query.answerOptions.size(); i++) {
            GroupedQuery.AnswerOption option = query.answerOptions.get(i);
            // If gold choose N/A option.
            if (answerIndices.size() == 0 && option.argumentIds.get(0) == -1) {
                return i;
            }
            // If argument set matches exactly.
            if (option.argumentIds.containsAll(answerIndices) && answerIndices.containsAll(option.argumentIds)) {
                return i;
            }
        }
        // System.out.println("[gold]:\t" + answerIndices.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return -1;
    }
}
