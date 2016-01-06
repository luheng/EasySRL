package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
     * @param question: question
     * @param sentence: sentence
     * @param targetDependency: the dependency that's used to generate the question.
     * @param goldDependencies: gold dependencies, for simulating user response.
     * @return Answer is represented a list of indices in the sentence.
     *          A single -1 in the list means ``unintelligible/unanswerable question.
     */
    public List<Integer> answerQuestion(List<String> question, List<String> sentence,
                                        ResolvedDependency targetDependency,
                                        List<Category> goldCategories,
                                        Set<ResolvedDependency> goldDependencies) {
        List<Integer> answerIndices = new ArrayList<>();
        String questionStr = StringUtils.join(question);
        for (ResolvedDependency dep : goldDependencies) {
            List<String> goldQuestion = questionGenerator.generateQuestion(dep, sentence, goldCategories,
                    goldDependencies);
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
        return answerIndices;
    }

    /**
     * @param question: question
     * @param sentence: sentence
     * @param targetDependency: the dependency that's used to generate the question.
     * @param goldDependencies: gold dependencies, for simulating user response.
     * @return Answer is represented a list of indices in the sentence.
     *          A single -1 in the list means ``unintelligible/unanswerable question.
     */
    public List<Integer> answerQuestion2(List<String> question, List<String> sentence,
                                        ResolvedDependency targetDependency, Set<ResolvedDependency> goldDependencies) {
        List<Integer> answerIndices = new ArrayList<>();
        for (ResolvedDependency dep : goldDependencies) {
            if (dep.getHead() == targetDependency.getHead() && dep.getCategory() == targetDependency.getCategory() &&
                    dep.getArgNumber() == targetDependency.getArgNumber()) {
                answerIndices.add(dep.getArgumentIndex());
            }
        }
        if (answerIndices.size() == 0) {
            answerIndices.add(-1);
        }
        return answerIndices;
    }
}
