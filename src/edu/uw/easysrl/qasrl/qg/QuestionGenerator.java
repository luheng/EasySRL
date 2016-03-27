package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {

    /**
     * Generate all question answer pairs for a sentence, given the n-best list.
     * @param sentenceId: unique identifier of the sentence.
     * @param words: words in the sentence.
     * @param nBestList: the nbest list.
     * @return
     */
    public static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                                       ImmutableList<String> words,
                                                                       NBestList nBestList,
                                                                       boolean usePronouns) {
        return IntStream.range(0, words.size()).boxed()
                .flatMap(predId -> IntStream.range(0, nBestList.getN()).boxed()
                        .flatMap(parseId -> {
                            final Parse parse = nBestList.getParse(parseId);
                            final Category category = parse.categories.get(predId);
                            return IntStream.range(1, category.getNumberOfArguments() + 1).boxed()
                                    .flatMap(argNum -> QuestionGenerator.generateAllQAPairs(
                                                sentenceId, parseId, predId, argNum, words, parse,
                                                usePronouns, true /* standard questions */, false /* supersense */)
                                            .stream());
                        })
                ).collect(toImmutableList());
    }

    public static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                                       ImmutableList<String> words,
                                                                       int parseId,
                                                                       Parse parse,
                                                                       boolean usePronouns) {
        return IntStream.range(0, words.size())
            .mapToObj(Integer::new)
            .flatMap(predIndex -> IntStream.range(1, parse.categories.get(predIndex).getNumberOfArguments() + 1)
                    .boxed()
                    .flatMap(argNum -> QuestionGenerator
                            .generateAllQAPairs(sentenceId, parseId, predIndex, argNum, words, parse, usePronouns,
                                    true /* standard questions */, false /* supersense */)
                            .stream()))
            .collect(toImmutableList());
    }

    private static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                                        int parseId,
                                                                        int predicateIdx,
                                                                        int argumentNumber,
                                                                        List<String> words,
                                                                        Parse parse,
                                                                        boolean indefinitesOnly,
                                                                        boolean askAllStandardQuestions,
                                                                        boolean askSupersenseQuestions) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return ImmutableList.copyOf(template.getAllQAPairsForArgument(
                argumentNumber,
                indefinitesOnly,
                askAllStandardQuestions,
                askSupersenseQuestions
        ));
    }

    private static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                                        int parseId,
                                                                        int predicateIdx,
                                                                        int argumentNumber,
                                                                        List<String> words,
                                                                        Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return ImmutableList.copyOf(template.getAllQAPairsForArgument(
                argumentNumber,
                false, // if true: only ask questions with indefinite noun args
                true, // all (false: just one) standard questions
                false // if true: include supersense questions
        ));
    }

    /**
     * Run this to print all the generated QA pairs to stdout.
     */
    public static void main(String[] args) {
        ParseData devData = ParseData.loadFromDevPool().get();
        for(int sentenceId = 0; sentenceId < devData.getSentences().size(); sentenceId++) {
            ImmutableList<String> words = devData.getSentences().get(sentenceId);
            Parse goldParse = devData.getGoldParses().get(sentenceId);
            System.out.println("==========");
            System.out.println(TextGenerationHelper.renderString(words));
            for(QuestionAnswerPair qaPair : generateAllQAPairs(sentenceId, words, -1, goldParse, false /* use pronoun */)) {
                System.out.println(qaPair.getQuestion());
                System.out.println("\t" + qaPair.getAnswer());
            }
        }
        // ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
        // System.err.println("loaded n-best lists");
        // for(int sentenceId : nBestLists.keySet()) {
        //     ImmutableList<String> words = devData.getSentences().get(sentenceId);
        //     NBestList nBestList = nBestLists.get(sentenceId);
        //     for(QuestionAnswerPair qaPair : generateAllQAPairs(sentenceId, words, nBestList)) {
        //         System.out.println(qaPair.getQuestion());
        //         System.out.println("\t" + qaPair.getAnswer());
        //     }
        // }
    }
}

