package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.util.*;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

import com.google.common.collect.ImmutableList;


import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {

    @Deprecated
    public static Optional<QuestionAnswerPair> generateQuestion(ResolvedDependency targetDependency,
                                                                List<String> words,
                                                                Parse parse) {
        assert false;
        return null;
    }


    @Deprecated
    public static Optional<QuestionAnswerPair> generateQuestion(int predicateIdx,
                                                                int argumentNumber,
                                                                List<String> words,
                                                                Parse parse) {
        assert false;
        return null;
    }

    @Deprecated
    public static List<QuestionAnswerPairReduced> generateAllQAPairs(int predicateIdx,
                                                                     int argumentNumber,
                                                                     List<String> words,
                                                                     Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(predicateIdx, words, parse);
        return template.getAllQAPairsForArgument(argumentNumber);
    }

    public static ImmutableList<IQuestionAnswerPair> generateAllQAPairs(ImmutableList<String> words, Parse parse) {
        return null; // TODO
    }
}

