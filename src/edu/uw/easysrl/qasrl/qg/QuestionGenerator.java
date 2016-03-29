package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

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

    private static final boolean indefinitesOnly = false; // if true: only ask questions with indefinite noun args
    private static final boolean askAllStandardQuestions = true; // all (false: just one) standard questions
    private static final boolean includeSupersenseQuestions = false; // if true: include supersense questions

    /**
     * Generate all question answer pairs for a sentence, given the n-best list.
     * @param sentenceId: unique identifier of the sentence.
     * @param words: words in the sentence.
     * @param nBestList: the nbest list.
     * @return
     */
    public static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId, ImmutableList<String> words,
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

    public static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId, ImmutableList<String> words,
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

    private static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                              int parseId,
                                                              int predicateIdx,
                                                              int argumentNumber,
                                                              List<String> words,
                                                              Parse parse) {
        MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return ImmutableList.copyOf(template.getAllQAPairsForArgument(argumentNumber,
                                                                      indefinitesOnly,
                                                                      askAllStandardQuestions,
                                                                      includeSupersenseQuestions));
    }

    /**
     * Run this to print all the generated QA pairs to stdout.
     */
    public static void main(String[] args) {
        ParseData devData = ParseData.loadFromDevPool().get();
        if(args.length == 0 || (!args[0].equalsIgnoreCase("gold") && !args[0].equalsIgnoreCase("tricky"))) {
            System.err.println("requires argument: \"gold\" or \"tricky\"");
        } else if(args[0].equalsIgnoreCase("gold")) {
            System.out.println("\nQA Pairs for Gold Parses:\n");
            for(int sentenceId = 0; sentenceId < devData.getSentences().size(); sentenceId++) {
                ImmutableList<String> words = devData.getSentences().get(sentenceId);
                Parse goldParse = devData.getGoldParses().get(sentenceId);
                System.out.println(String.format("========== SID = %04d ==========", sentenceId));
                System.out.println(TextGenerationHelper.renderString(words));
                for(QuestionAnswerPair qaPair : generateAllQAPairs(sentenceId, words, -1, goldParse)) {
                    System.out.println(qaPair.getQuestion());
                    System.out.println("\t" + qaPair.getAnswer());
                }
            }
        } else if (args[0].equalsIgnoreCase("tricky")) {
            System.out.println("\nQA Pairs for tricky sentences:\n");
            ImmutableList<Integer> trickySentences = new ImmutableList.Builder<Integer>()
                .add(42)
                .build();
            ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
            for(int sentenceId : trickySentences) {
                System.out.println(String.format("========== SID = %04d ==========", sentenceId));
                ImmutableList<String> words = devData.getSentences().get(sentenceId);
                System.out.println(String.format("--------- Gold QA Pairs --------", sentenceId));
                Parse goldParse = devData.getGoldParses().get(sentenceId);
                System.out.println(TextGenerationHelper.renderString(words));
                for(QuestionAnswerPair qaPair : generateAllQAPairs(sentenceId, words, -1, goldParse)) {
                    System.out.println(qaPair.getQuestion());
                    System.out.println("\t" + qaPair.getAnswer());
                }
                System.out.println(String.format("--------- All QA Pairs ---------", sentenceId));
                NBestList nBestList = nBestLists.get(sentenceId);
                ImmutableList<QuestionAnswerPair> qaPairs = generateAllQAPairs(sentenceId, words, nBestList);
                for(QuestionAnswerPair qaPair : qaPairs) {
                    System.out.println("--");
                    System.out.println(words.get(qaPair.getPredicateIndex()));
                    System.out.println(qaPair.getPredicateCategory());
                    for(ResolvedDependency dep : qaPair.getQuestionDependencies()) {
                        System.out.println("\t" + words.get(dep.getHead()) + "\t-"
                                           + dep.getArgNumber() + "->\t"
                                           + words.get(dep.getArgument()));
                    }
                    System.out.println(qaPair.getQuestion());
                    for(ResolvedDependency dep : qaPair.getAnswerDependencies()) {
                        System.out.println("\t" + words.get(dep.getHead()) + "\t-"
                                           + dep.getArgNumber() + "->\t"
                                           + words.get(dep.getArgument()));
                    }
                    System.out.println("\t" + qaPair.getAnswer());
                }
                System.out.println(String.format("------- All Pair Queries -------", sentenceId));
                ImmutableList<QAPairSurfaceForm> surfaceForms = QAPairAggregators.aggregateByString().aggregate(qaPairs);
                ImmutableList<Query<QAPairSurfaceForm>> queries = QueryGenerators.maximalForwardGenerator().generate(surfaceForms);
                for(Query<QAPairSurfaceForm> query : queries) {
                    System.out.println(query.getPrompt());
                    for(String option : query.getOptions()) {
                        System.out.println("\t" + option);
                    }
                }
            }
        }
    }
}

