package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.util.*;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;


import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {

    public static final VerbHelper verbHelper = new VerbHelper(VerbInflectionDictionary.buildFromPropBankTraining());

    /**
     * Generates a question given a supertagged sentence, a dependency in question,
     * and all of the dependencies sharing their predicate with that dependency.
     * Constructs a QuestionTemplate by locating the target dep's arguments in the sentence,
     * then instantiates it for the appropriate argument.
     * @param targetDependency    : the dependency to ask about
     * @param words               : the sentence
     * @param parse               : parse information containing categories and dependencies
     * @return a QuestionAnswerPair if we could construct an answer successfully
     */
    public static Optional<QuestionAnswerPair> generateQuestion(ResolvedDependency targetDependency, List<String> words,
                                                         Parse parse) {
        int predicateIdx = targetDependency.getHead();
        QuestionTemplate template = new QuestionTemplate(predicateIdx, words, parse, verbHelper);
        // List<QuestionAnswerPair> qaPairs = template.instantiateForArgument(targetDependency.getArgNumber());
        // XXX for a given dependency we only want the specific question.
        assert false;
        return null;
    }


    public static Optional<QuestionAnswerPair> generateQuestion(int predicateIdx, int argumentNumber, List<String> words,
                                                         Parse parse) {
        QuestionTemplate template = new QuestionTemplate(predicateIdx, words, parse, verbHelper);
        // List<QuestionAnswerPair> qaPairs = template.instantiateForArgument(argumentNumber);
        // if(qaPairs.size() == 0) {
        //     return Optional.empty();
        // } else {
        //     return Optional.of(qaPairs.get(0));
        // }
        assert false;
        return null;
    }

    public static List<QuestionAnswerPairReduced> generateAllQAPairs(int predicateIdx, int argumentNumber, List<String> words,
                                                         Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(predicateIdx, words, parse, verbHelper);
        return template.getAllQAPairsForArgument(argumentNumber);
    }
}

