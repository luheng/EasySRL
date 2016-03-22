package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.NBestList;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.grammar.Category;

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
    public static List<QuestionAnswerPair> generateAllQAPairs(int predicateIdx,
                                                              int argumentNumber,
                                                              List<String> words,
                                                              Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(-1, -1, predicateIdx, words, parse);
        return template.getAllQAPairsForArgument(argumentNumber);
    }

    public static List<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                              int parseId,
                                                              int predicateIdx,
                                                              int argumentNumber,
                                                              List<String> words,
                                                              Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return template.getAllQAPairsForArgument(argumentNumber);
    }

    public static ImmutableList<IQuestionAnswerPair> generateAllQAPairs(int sentenceId, ImmutableList<String> words,
                                                                        int parseId, Parse parse) {
        return IntStream.range(0, words.size())
            .mapToObj(Integer::new)
            .flatMap(predIndex -> IntStream.range(1, parse.categories.get(predIndex).getNumberOfArguments() + 1)
                    .boxed()
                    .flatMap(argNum -> QuestionGenerator
                            .generateAllQAPairs(sentenceId, parseId, predIndex, argNum, words, parse)
                            .stream()))
            .collect(toImmutableList());
    }

    /**
     * Generate all question answer pairs for a sentence, given the n-best list.
     * @param sentenceId: unique identifier of the sentence.
     * @param words: words in the sentence.
     * @param nBestList: the nbest list.
     * @return
     */
    public static ImmutableList<IQuestionAnswerPair> generateAllQAPairs(int sentenceId, ImmutableList<String> words,
                                                                        NBestList nBestList) {
        return IntStream.range(0, words.size()).boxed()
                .flatMap(predId -> IntStream.range(0, nBestList.getN()).boxed()
                                .flatMap(parseId -> {
                                    generateAllQAPairs(sentenceId, words, parseId, nBestList.getParse(parseId));
                                    final Parse parse = nBestList.getParse(parseId);
                                    final Category category = parse.categories.get(predId);
                                    return IntStream.range(1, category.getNumberOfArguments() + 1).boxed()
                                            .flatMap(argNum -> QuestionGenerator
                                                    .generateAllQAPairs(sentenceId, parseId, predId, argNum, words, parse)
                                                    .stream());
                                })
                ).collect(toImmutableList());
    }
}

