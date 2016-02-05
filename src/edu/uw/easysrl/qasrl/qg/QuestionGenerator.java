package edu.uw.easysrl.qasrl.qg;

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

    public VerbHelper verbHelper;

    public QuestionGenerator() {
        // FIXME: build from unlabeled corpora.
        verbHelper = new VerbHelper(VerbInflectionDictionary.buildFromPropBankTraining());
    }

    /**
     * Generates a question given a supertagged sentence, a dependency in question,
     * and all of the dependencies sharing their predicate with that dependency.
     * Constructs a QuestionTemplate by locating the target dep's arguments in the sentence,
     * then instantiates it for the appropriate argument.
     * @param targetDependency    : the dependency to ask about
     * @param words               : the sentence
     * @param parse               : parse information containing categories and dependencies
     * @return the question as a list of non-empty strings
     */
    public QuestionAnswerPair generateQuestion(ResolvedDependency targetDependency, List<String> words, Parse parse) {
        int predicateIdx = targetDependency.getHead();
        QuestionTemplate template = new QuestionTemplate(predicateIdx, words, parse, verbHelper);
        if (template == null) {
            return null;
        }
        QuestionAnswerPair qaPair = template.instantiateForArgument(targetDependency.getArgNumber());
        return qaPair;
        /*
        List<String> result = new ArrayList<String>();
        String question = qaPair.renderQuestion();
        if(!question.isEmpty()) {
            result.add(question);
        }
        return result;
        */
    }

}

