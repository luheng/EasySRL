package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {

    private static boolean indefinitesOnly = true; // if true: only ask questions with indefinite noun args
    public static void setIndefinitesOnly(boolean flag) {
        indefinitesOnly = flag;
    }

    private static boolean askAllStandardQuestions = false; // all (false: just one) standard questions
    public static void setAskAllStandardQuestions(boolean flag) {
        askAllStandardQuestions = flag;
    }

    private static boolean includeSupersenseQuestions = false; // if true: include supersense questions
    public static void setIncludeSupersenseQuestions(boolean flag) {
        includeSupersenseQuestions = flag;
    }

    // if this is true, the previous three don't matter
    private static boolean askPPAttachmentQuestions = true; // if true: include PP attachment questions
    public static void setAskPPAttachmentQuestions(boolean flag) {
        askPPAttachmentQuestions = flag;
    }

    /**
     * Generate all queryPrompt answer pairs for a sentence, given the n-best list.
     * @param sentenceId: unique identifier of the sentence.
     * @param words: words in the sentence.
     * @param nBestList: the nbest list.
     * @return
     */
    public static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                                       ImmutableList<String> words,
                                                                       NBestList nBestList) {
        return IntStream.range(0, nBestList.getN()).boxed()
                .flatMap(parseId -> generateQAPairsForParse(sentenceId, parseId, words, nBestList.getParse(parseId)).stream())
                .collect(toImmutableList());
    }

    public static ImmutableList<QuestionAnswerPair> generateQAPairsForParse(int sentenceId,
                                                                            int parseId,
                                                                            ImmutableList<String> words,
                                                                            Parse parse) {
        return IntStream.range(0, words.size())
                .mapToObj(Integer::new)
                .flatMap(predIndex -> generateQAPairsForPredicate(sentenceId, parseId, predIndex, words, parse).stream())
                .collect(toImmutableList());
    }

    private static ImmutableList<QuestionAnswerPair> generateQAPairsForPredicate(int sentenceId,
                                                                                 int parseId,
                                                                                 int predicateIdx,
                                                                                 List<String> words,
                                                                                 Parse parse) {
        final MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return askPPAttachmentQuestions ?
                template.getAllPPAttachmentQAPairs(indefinitesOnly)
                        .stream()
                        .collect(toImmutableList()) :
                IntStream.range(1, parse.categories.get(predicateIdx).getNumberOfArguments() + 1)
                        .boxed()
                        .flatMap(argNum -> template.getAllQAPairsForArgument(argNum, indefinitesOnly,
                                askAllStandardQuestions, includeSupersenseQuestions).stream())
                        .collect(toImmutableList());
    }

    /**
     * Run this to print all the generated QA pairs to stdout.
     */
    public static void main(String[] args) {
        ParseData devData = ParseData.loadFromDevPool().get();
        if(args.length == 0 || (!args[0].equalsIgnoreCase("gold") && !args[0].equalsIgnoreCase("tricky") &&
                                !args[0].equalsIgnoreCase("queries"))) {
            System.err.println("requires argument: \"gold\" or \"tricky\" or \"queries\"");
        } else if(args[0].equalsIgnoreCase("gold")) {
            System.out.println("\nQA Pairs for Gold Parses:\n");
            for(int sentenceId = 0; sentenceId < devData.getSentences().size(); sentenceId++) {
                ImmutableList<String> words = devData.getSentences().get(sentenceId);
                Parse goldParse = devData.getGoldParses().get(sentenceId);
                System.out.println(String.format("========== SID = %04d ==========", sentenceId));
                System.out.println(TextGenerationHelper.renderString(words));
                for(QuestionAnswerPair qaPair : generateQAPairsForParse(sentenceId, -1, words, goldParse)) {
                    printQAPair(words, qaPair);
                }
            }
        } else if (args[0].equalsIgnoreCase("tricky")) {
            System.out.println("\nQA Pairs for tricky sentences:\n");
            ImmutableList<Integer> trickySentences = new ImmutableList.Builder<Integer>()
                // .add(42).add(1489).add(36)
                .add(90)
                .build();
            ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
            for(int sentenceId : trickySentences) {
                System.out.println(String.format("========== SID = %04d ==========", sentenceId));
                ImmutableList<String> words = devData.getSentences().get(sentenceId);
                System.out.println(TextGenerationHelper.renderString(words));
                System.out.println("--------- Gold QA Pairs --------");
                Parse goldParse = devData.getGoldParses().get(sentenceId);
                for(QuestionAnswerPair qaPair : generateQAPairsForParse(sentenceId, -1, words, goldParse)) {
                    printQAPair(words, qaPair);
                }
                System.out.println("--------- All QA Pairs ---------");
                NBestList nBestList = nBestLists.get(sentenceId);
                ImmutableList<QuestionAnswerPair> qaPairs = generateAllQAPairs(sentenceId, words, nBestList);
                for(QuestionAnswerPair qaPair : qaPairs) {
                    printQAPair(words, qaPair);
                    // for(int i = 0; i < words.size(); i++) {
                    //     System.out.println(words.get(i) + "\t" + qaPair.getParse().categories.get(i));
                    // }
                    // System.out.println(qaPair.getParse().syntaxTree);
                    // System.out.println();
                }
                System.out.println("------- All Pair Queries -------");
                ImmutableList<QAPairSurfaceForm> surfaceForms = QAPairAggregators.aggregateByString().aggregate(qaPairs);
                ImmutableList<Query<QAPairSurfaceForm>> queries = QueryGenerators.maximalForwardGenerator().generate(surfaceForms);
                for(Query<QAPairSurfaceForm> query : queries) {
                    System.out.println(query.getPrompt());
                    for(String option : query.getOptions()) {
                        System.out.println("\t" + option);
                    }
                }
            }
        } else if (args[0].equalsIgnoreCase("queries")) {
            System.out.println("All queries:\n");
            ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
            for(int sentenceId = 0; sentenceId < 20; sentenceId++) {
                if(!nBestLists.containsKey(sentenceId)) {
                    continue;
                }
                ImmutableList<String> words = devData.getSentences().get(sentenceId);
                NBestList nBestList = nBestLists.get(sentenceId);
                System.out.println("--------- All Queries --------");
                System.out.println(TextGenerationHelper.renderString(words));
                ImmutableList<QuestionAnswerPair> qaPairs = generateAllQAPairs(sentenceId, words, nBestList);

                /*
                qaPairs.forEach(qa -> {
                    System.out.println(qa.getQuestion() + "\t" + qa.getAnswer());
                    System.out.println(qa.getQuestionDependencies().stream().map(dep -> dep.toString(words)).collect(Collectors.joining(";\t")));
                    System.out.println(qa.getTargetDependency().toString(words));
                    System.out.println(qa.getAnswerDependencies().stream().map(dep -> dep.toString(words)).collect(Collectors.joining(";\t")));
                    System.out.println();
                }); */

                if(args.length > 1 && args[1].equalsIgnoreCase("aggDeps")) {
                    ImmutableList<QADependenciesSurfaceForm> surfaceForms = QAPairAggregators
                        .aggregateBySalientDependencies().aggregate(qaPairs);
                    ImmutableList<Query<QADependenciesSurfaceForm>> queries = QueryGenerators
                        .answerAdjunctPartitioningQueryGenerator().generate(surfaceForms);
                    for(Query<QADependenciesSurfaceForm> query : queries) {
                        System.out.println(query.getPrompt());
                        for(String option : query.getOptions()) {
                            System.out.println("\t" + option);
                        }
                    }
                } else {
                    QAPairAggregator<QAPairSurfaceForm> qaPairAggregator = QAPairAggregators.aggregateByString();
                    ImmutableList<QAPairSurfaceForm> surfaceForms = qaPairAggregator.aggregate(qaPairs);
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

    private static void printQAPair(ImmutableList<String> words, QuestionAnswerPair qaPair) {
        System.out.println("--");
        System.out.println(words.get(qaPair.getPredicateIndex()));
        System.out.println(qaPair.getPredicateCategory());
        System.out.println(Optional.ofNullable(qaPair.getTargetDependency()).map(targetDep -> words.get(targetDep.getHead())).orElse("something") + "\t-"
                           + Optional.ofNullable(qaPair.getTargetDependency()).map(dep -> new Integer(dep.getArgNumber()).toString()).orElse("?") + "->\t"
                           + Optional.ofNullable(qaPair.getTargetDependency()).map(targetDep -> words.get(targetDep.getArgument())).orElse("something"));
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
        System.out.println("--");
    }
}

