package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import static edu.uw.easysrl.qasrl.TextGenerationHelper.TextWithDependencies;

import java.util.*;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private static boolean askPPAttachmentQuestions = false; // if true: include PP attachment questions
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

    public static ImmutableList<QuestionAnswerPair> newCoreNPArgPronounQuestions(int sentenceId, int parseId, Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        final ImmutableList<QuestionAnswerPair> qaPairs = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("S\\NP"))
                    && !parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")))
            .flatMap(predicateIndex -> Stream.of(Verb.getFromParse(predicateIndex, preds, parse))
            .filter(verb -> !verb.isCopular())
            .flatMap(verb -> IntStream.range(1, verb.getPredicateCategory().getNumberOfArguments() + 1)
            .boxed()
            .filter(argNum -> Category.NP.matches(verb.getPredicateCategory().getArgument(argNum)))
            .flatMap(askingArgNum -> verb.getArgs().get(askingArgNum).stream()
            .filter(answerArg -> !((Noun) answerArg.getPredication()).isExpletive()) // this should always be true when arg cat is NP
            .filter(answerArg -> answerArg.getDependency().isPresent())
            .flatMap(answerArg -> PredicationUtils
                     .sequenceArgChoices(PredicationUtils
                     .withIndefinitePronouns(PredicationUtils
                     .addPlaceholderArguments(verb))).stream()
            .map(sequencedVerb -> {
                    Verb questionPred = sequencedVerb.transformArgs((argNum, args) -> argNum == askingArgNum
                                   ? ImmutableList.of(Argument.withNoDependency(((Noun) args.get(0).getPredication()).withDefiniteness(Noun.Definiteness.FOCAL)))
                                   : args)
                    .withModal("would");
                    // ImmutableList<String> question = questionPredication.getQuestionWords();
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), askingArgNum,
                                                       predicateIndex, null,
                                                       questionPred.getAllDependencies(), questionPred.getQuestionWords(),
                                                       answerArg.getDependency().get(),
                                                       new TextWithDependencies(answerArg.getPredication().getPhrase(Category.NP),
                                                                                answerArg.getPredication().getAllDependencies()));
                })))))
            .collect(toImmutableList());
        return qaPairs;
    }

    public static ImmutableList<QuestionAnswerPair> newCopulaQuestions(int sentenceId, int parseId, Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        final ImmutableList<QuestionAnswerPair> qaPairs = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("S\\NP"))
                    && !parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")))
            .flatMap(predicateIndex -> Stream.of(Verb.getFromParse(predicateIndex, preds, parse))
            .filter(verb -> verb.isCopular())
            .flatMap(verb -> IntStream.range(1, verb.getPredicateCategory().getNumberOfArguments() + 1)
            .boxed()
            .filter(argNum -> Category.NP.matches(verb.getPredicateCategory().getArgument(argNum)))
            .flatMap(askingArgNum -> verb.getArgs().get(askingArgNum).stream()
            .filter(answerArg -> !((Noun) answerArg.getPredication()).isExpletive()) // this should always be a noun when arg cat is NP
            .filter(answerArg -> answerArg.getDependency().isPresent())
            .flatMap(answerArg -> PredicationUtils.sequenceArgChoices(PredicationUtils.addPlaceholderArguments(verb)).stream()
            .map(reducedVerb -> {
                    Verb whPred = reducedVerb.withModal("would").transformArgs((argNum, args) -> {
                            if(argNum == askingArgNum) {
                                // args is not empty; was filled in before.
                                return ImmutableList.of(Argument.withNoDependency(((Noun) args.get(0).getPredication())
                                                                                  .getPronoun()
                                                                                  .withPerson(Noun.Person.THIRD)
                                                                                  .withDefiniteness(Noun.Definiteness.FOCAL)));
                            } else {
                                Category argCat = verb.getPredicateCategory().getArgument(argNum);
                                if(!Category.NP.matches(argCat)) {
                                    System.err.println("gapping arg category: " + argCat);
                                    System.err.println("gapping preds:\n" + args.stream()
                                                       .map(arg -> "\t" + arg.getPredication().getPredicate())
                                                       // .map(arg -> "\t" + PredicationUtils.addPlaceholderArgumentsIfVerb(arg.getPredication()).getPhrase())
                                                       .collect(joining("\n")));
                                    return ImmutableList.of(Argument.withNoDependency(new Gap(argCat)));
                                } else {
                                    return args;
                                }
                            }
                        });
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), askingArgNum,
                                                       predicateIndex, null,
                                                       whPred.getAllDependencies(), whPred.getQuestionWords(),
                                                       answerArg.getDependency().get(),
                                                       new TextWithDependencies(answerArg.getPredication().getPhrase(Category.NP),
                                                                                answerArg.getPredication().getAllDependencies()));
                })))))
            .collect(toImmutableList());
        return qaPairs;
    }

    public static ImmutableList<QuestionAnswerPair> newVPAttachmentQuestions(int sentenceId, int parseId, Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        // class QATemplate {
        //     final int predicateIndex;
        //     final Verb questionVerb;
        //     final Predication answerVerb;
        // }
        // final ImmutableList<Verb> verbs =
        final ImmutableList<QuestionAnswerPair> qaPairs = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("((S\\NP)/NP)/PP")) ||
                    parse.categories.get(index).isFunctionInto(Category.valueOf("((S\\NP)/PP)/NP")))
            .flatMap(predicateIndex -> Stream.of(Verb.getFromParse(predicateIndex, preds, parse))
            .filter(verb -> !verb.isCopular())
            .flatMap(verb -> PredicationUtils
                     .sequenceArgChoices(PredicationUtils
                     .addPlaceholderArguments(verb)).stream()
            .filter(sequencedVerb -> sequencedVerb.getArgs().get(2).get(0).getDependency().isPresent() &&
                    sequencedVerb.getArgs().get(3).get(0).getDependency().isPresent()) // both have to be taken from the sentence
            .map(sequencedVerb -> {
                    int dirObjectArgNum = Category.NP.matches(parse.categories.get(predicateIndex).getArgument(2)) ? 2 : 3;
                    int ppArgNum = dirObjectArgNum == 2 ? 3 : 2;
                    Noun dirObject = (Noun) sequencedVerb.getArgs().get(dirObjectArgNum).get(0).getPredication();
                    ResolvedDependency dirObjectDep = sequencedVerb.getArgs().get(dirObjectArgNum).get(0).getDependency().get();
                    Verb questionProVerb = new Verb("do", Category.valueOf("((S[dcl]\\NP)/NP)/PP"),
                                                    new ImmutableMap.Builder<Integer, ImmutableList<Argument>>()
                                                    .put(1, ImmutableList.of(Argument.withNoDependency(sequencedVerb.getSubject().getPronoun().withDefiniteness(Noun.Definiteness.INDEFINITE))))
                                                    .put(2, ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("what").get())))
                                                    .put(3, ImmutableList.of(Argument.withNoDependency(Preposition.makeSimplePP("to", dirObject))))
                                                    .build(),
                                                    Verb.Tense.MODAL, Optional.of("would"),
                                                    Verb.Voice.ACTIVE, sequencedVerb.isPerfect(), sequencedVerb.isProgressive(),
                                                    sequencedVerb.isNegated(), Optional.empty());
                    Verb.Tense answerTense = sequencedVerb.getVoice() == Verb.Voice.ACTIVE ? Verb.Tense.BARE_VERB : Verb.Tense.BARE_COPULA;
                    Verb answerVerb = sequencedVerb
                    .withTense(answerTense)
                    .withNegation(false)
                    .transformArgs((argNum, args) -> argNum != dirObjectArgNum ? args
                                   : ImmutableList.of(Argument.withNoDependency(dirObject
                                                                                .getPronoun()
                                                                                .withCase(Noun.Case.ACCUSATIVE)
                                                                                .withDefiniteness(Noun.Definiteness.DEFINITE))));
                    // TODO permute args
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), 2,
                                                       predicateIndex, null,
                                                       questionProVerb.getAllDependencies(), questionProVerb.getQuestionWords(),
                                                       dirObjectDep,
                                                       new TextWithDependencies(answerVerb.getPhrase(Category.valueOf("S\\NP")),
                                                                                answerVerb.getAllDependencies()));
                })))
            .collect(toImmutableList());
        return qaPairs;
    }

    /*
    public static ImmutableList<QuestionAnswerPair> newAdverbialQuestions(int sentenceId, int parseId, Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        final ImmutableList<QuestionAnswerPair> qaPairs = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")))
            .flatMap(predicateIndex -> Stream.of(Adverb.getFromParse(predicateIndex, preds, parse))
            .filter(adverb -> !(Prepositions.prepositionWords.contains(adverb.getPredicate()) &&
                                   parse.categories.get(predicateIndex).matches(Category.valueOf("(S\\NP)\\(S\\NP)"))))
            .flatMap(adverb -> adverb.getArgs().get(2).stream()
            .filter(answerArg -> answerArg.getDependency().isPresent())
            .flatMap(answerArg -> PredicationUtils
                     .sequenceArgChoices(PredicationUtils
                     .addPlaceholderArguments(adverb.transformArgs((argNum, args) -> argNum <= 2 ? ImmutableList.of() : args))).stream()
                     // .withIndefinitePronouns(PredicationUtils // this seems to just make them confusing. include the arg!
            .flatMap(sequencedAdverb -> PredicationUtils.sequenceArgChoices(PredicationUtils.addPlaceholderArguments(answerArg.getPredication())).stream()
            .map(originalVerb -> (Verb) originalVerb)
            .filter(originalVerb -> !originalVerb.isCopular())
            .map(originalVerb -> {
                    Verb.Tense answerTense = originalVerb.getVoice() == Verb.Voice.ACTIVE ? Verb.Tense.BARE_VERB : Verb.Tense.BARE_COPULA;
                    Verb answerVerb = originalVerb.withTense(answerTense).withNegation(false);
                    Verb questionProVerb = new Verb("do", Category.valueOf("((S[dcl]\\NP)/NP)"),
                                                    new ImmutableMap.Builder<Integer, ImmutableList<Argument>>()
                                                    .put(1, ImmutableList.of(Argument.withNoDependency(answerVerb.getSubject().getPronoun().withDefiniteness(Noun.Definiteness.INDEFINITE))))
                                                    .put(2, ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("what").get())))
                                                    .build(),
                                                    Verb.Tense.MODAL, Optional.of("would"),
                                                    Verb.Voice.ACTIVE, originalVerb.isPerfect(), originalVerb.isProgressive(),
                                                    originalVerb.isNegated(), Optional.empty());
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, sequencedAdverb.getPredicateCategory(), 2,
                                                       predicateIndex, null,
                                                       new ImmutableSet.Builder<ResolvedDependency>()
                                                       .addAll(questionProVerb.getAllDependencies())
                                                       .addAll(sequencedAdverb.getAllDependencies())
                                                       .build(),
                                                       new ImmutableList.Builder<String>()
                                                       .addAll(questionProVerb.getQuestionWords())
                                                       .addAll(sequencedAdverb.getPhrase(Category.valueOf("(S\\NP)\\(S\\NP)")))
                                                       .build(),
                                                       answerArg.getDependency().get(),
                                                       new TextWithDependencies(answerVerb.getPhrase(Category.valueOf("S\\NP")),
                                                                                answerVerb.getAllDependencies()));
                })))))
            .collect(toImmutableList());
        return qaPairs;
    }
    */

    public static ImmutableList<QuestionAnswerPair> generateNewQAPairs(int sentenceId, int parseId, Parse parse) {
        // return newAdverbialQuestions(sentenceId, parseId, parse);
        // return newCopulaQuestions(sentenceId, parseId, parse);
        // return newCoreNPArgPronounQuestions(sentenceId, parseId, parse);
        return newVPAttachmentQuestions(sentenceId, parseId, parse);
    }

    public static ImmutableList<QuestionAnswerPair> generateAllNewQAPairs(int sentenceId,
                                                                          NBestList nBestList) {
        return IntStream.range(0, nBestList.getN()).boxed()
            .flatMap(parseId -> generateNewQAPairs(sentenceId, parseId, nBestList.getParse(parseId)).stream())
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
                for(QuestionAnswerPair qaPair : generateNewQAPairs(sentenceId, -1, goldParse)) {
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
                for(QuestionAnswerPair qaPair : generateNewQAPairs(sentenceId, -1, goldParse)) {
                    printQAPair(words, qaPair);
                }
                System.out.println("--------- All QA Pairs ---------");
                NBestList nBestList = nBestLists.get(sentenceId);
                ImmutableList<QuestionAnswerPair> qaPairs = generateAllNewQAPairs(sentenceId, nBestList);
                for(QuestionAnswerPair qaPair : qaPairs) {
                    printQAPair(words, qaPair);
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
            final ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
            for(int sentenceId = 0; sentenceId < 40; sentenceId++) {
                if(!nBestLists.containsKey(sentenceId)) {
                    continue;
                }
                ImmutableList<String> words = devData.getSentences().get(sentenceId);
                NBestList nBestList = nBestLists.get(sentenceId);
                System.out.println("--------- All Queries --------");
                System.out.println(TextGenerationHelper.renderString(words));
                ImmutableList<QuestionAnswerPair> qaPairs = generateAllNewQAPairs(sentenceId, nBestList);

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
        String targetDepString = Optional.ofNullable(qaPair.getTargetDependency()).map(dep -> dependencyString(words, dep)).orElse("???\t-?->\t???");
        System.out.println(targetDepString);
        for(ResolvedDependency dep : qaPair.getQuestionDependencies()) {
            System.out.println("\t" + dependencyString(words, dep));
        }
        System.out.println(qaPair.getQuestion());
        for(ResolvedDependency dep : qaPair.getAnswerDependencies()) {
            System.out.println("\t" + dependencyString(words, dep));
        }
        System.out.println("\t" + qaPair.getAnswer());
        System.out.println("--");
    }

    private static String dependencyString(ImmutableList<String> words, ResolvedDependency dep) {
        return words.get(dep.getHead()) + "\t-"
            + dep.getArgNumber() + "->\t"
            + words.get(dep.getArgument());
    }
}

