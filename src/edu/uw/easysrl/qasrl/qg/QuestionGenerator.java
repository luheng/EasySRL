package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.util.*;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

import com.google.common.collect.ImmutableList;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.*;
import java.util.stream.IntStream;

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
        MultiQuestionTemplate template = new MultiQuestionTemplate(-1, -1, predicateIdx, words, parse);
        return template.getAllQAPairsForArgument(argumentNumber);
    }

    public static List<QuestionAnswerPairReduced> generateAllQAPairs(int sentenceId,
                                                                     int parseId,
                                                                     int predicateIdx,
                                                                     int argumentNumber,
                                                                     List<String> words,
                                                                     Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return template.getAllQAPairsForArgument(argumentNumber);
    }

    public static ImmutableList<IQuestionAnswerPair> generateAllQAPairs(int sentenceId, int parseId,
                                                                        ImmutableList<String> words, Parse parse) {
        return IntStream.range(0, words.size())
            .mapToObj(i -> new Integer(i))
            .flatMap(predIndex -> IntStream.range(1, parse.categories.get(predIndex).getNumberOfArguments() + 1)
            .mapToObj(i -> new Integer(i))
            .flatMap(argNum -> QuestionGenerator.generateAllQAPairs(sentenceId, parseId, predIndex, argNum, words, parse).stream()))
            .collect(toImmutableList());
    }
}

