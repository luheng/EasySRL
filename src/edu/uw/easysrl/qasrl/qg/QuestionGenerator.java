package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.util.*;
import edu.uw.easysrl.qasrl.qg.QAPairAggregator;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.qasrl.model.HITLParser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import static edu.uw.easysrl.qasrl.TextGenerationHelper.TextWithDependencies;

import java.util.*;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.function.Function;
import java.util.function.Consumer;

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
            .flatMap(predicateIndex -> {
                    Verb v = null;
                    try {
                        v = Verb.getFromParse(predicateIndex, preds, parse);
                    } catch(IllegalArgumentException e) {
                        // System.err.println(e.getMessage());
                        v = null;
                    }
                    return Stream.of(v)
            .filter(verb -> verb != null && !verb.isCopular())
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
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), askingArgNum,
                                                       predicateIndex, null,
                                                       questionPred.getAllDependencies(), questionPred.getQuestionWords(),
                                                       answerArg.getDependency().get(),
                                                       new TextWithDependencies(answerArg.getPredication().getPhrase(Category.NP),
                                                                                answerArg.getPredication().getAllDependencies()));
                }))));})
            .collect(toImmutableList());
        return qaPairs;
    }

    /* THESE ARE UNNECESSARY. don't bother with them. We have very few errors on these. */
    public static ImmutableList<QuestionAnswerPair> newCoreXCompArgQuestions(int sentenceId, int parseId, Parse parse) {
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
            .filter(argNum -> Category.valueOf("S\\NP").matches(verb.getPredicateCategory().getArgument(argNum)))
            .flatMap(askingArgNum -> verb.getArgs().get(askingArgNum).stream()
            .filter(answerArg -> answerArg.getDependency().isPresent() && !((Verb) answerArg.getPredication()).isCopular())
            .flatMap(answerArg -> PredicationUtils.sequenceArgChoices(PredicationUtils.addPlaceholderArguments(answerArg.getPredication())).stream()
            .map(sequencedArgPred -> new Argument(answerArg.getDependency(), sequencedArgPred)))
            .flatMap(answerArg -> {
                    final ResolvedDependency argDep = answerArg.getDependency().get();
                    final Category argCat = verb.getPredicateCategory().getArgument(askingArgNum);
                    final Verb xcomp = (Verb) PredicationUtils.addPlaceholderArguments(answerArg.getPredication());
                    final Verb answerPred;
                    final String whWord;
                    final Predication proXcomp;
                    if(Category.valueOf("S[adj]\\NP").matches(argCat)) {
                        answerPred = xcomp; //.withTense(Verb.Tense.BARE_VERB);
                        whWord = "how";
                        proXcomp = new Gap(Category.valueOf("S[adj]\\NP"));
                    } else {
                        answerPred = xcomp.withTense(Verb.Tense.BARE);
                        whWord = "what";
                        proXcomp = new Verb("do", Category.valueOf("(S[dcl]\\" + xcomp.getSubject().getPredicateCategory() + ")/NP"),
                                                new ImmutableMap.Builder<Integer, ImmutableList<Argument>>()
                                                .put(1, ImmutableList.of(Argument.withNoDependency(xcomp.getSubject()))) // remove the dep since it won't show up
                                                .put(2, ImmutableList.of(Argument.withNoDependency(new Gap(Category.NP))))
                                                .build(),
                                                xcomp.getTense(), xcomp.getModal(),
                                                Verb.Voice.ACTIVE,
                                                xcomp.isPerfect(), xcomp.isProgressive(),
                                                xcomp.isNegated(), Optional.empty());
                    }

                    Verb verbWithProXcompArg = verb.transformArgs((argNum, args) -> argNum == askingArgNum
                                                                  ? ImmutableList.of(Argument.withNoDependency(proXcomp))
                                                                  : args);
                    Verb verbWithPlaceholders = PredicationUtils.addPlaceholderArguments(verbWithProXcompArg);
                    Verb verbWithPronouns = PredicationUtils.withIndefinitePronouns(verbWithPlaceholders).withModal("would");
                    return PredicationUtils.sequenceArgChoices(verbWithPronouns).stream()
            .map(questionPred -> {
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), askingArgNum,
                                                       predicateIndex, null,
                                                       questionPred.getAllDependencies(),
                                                       new ImmutableList.Builder<String>()
                                                       .add(whWord)
                                                       .addAll(questionPred.getQuestionWords())
                                                       .build(),
                                                       argDep,
                                                       new TextWithDependencies(answerPred.getPhrase(argCat),
                                                                                answerPred.getAllDependencies()));
                });}))))
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

    // TODO filter out punctuation predicates
    public static ImmutableList<QuestionAnswerPair> newVPAttachmentQuestions(int sentenceId, int parseId, Parse parse) {
        class QATemplate {
            final int predicateIndex;
            final Verb verb;
            final int dirObjectArgNum;
            final Optional<TextWithDependencies> attachedPP;
            final Optional<ResolvedDependency> vpAttachmentDep;
            QATemplate(int predicateIndex, Verb verb, int dirObjectArgNum,
                       Optional<TextWithDependencies> attachedPP, Optional<ResolvedDependency> vpAttachmentDep) {
                this.predicateIndex = predicateIndex;
                this.verb = verb; // sequenced
                this.dirObjectArgNum = dirObjectArgNum;
                this.attachedPP = attachedPP;
                this.vpAttachmentDep = vpAttachmentDep;
            }
        }

        final PredicateCache preds = new PredicateCache(parse);
        final ImmutableList<String> words = parse.syntaxTree.getLeaves().stream().map(l -> l.getWord()).collect(toImmutableList());

        final Stream<QATemplate> verbTemplates = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("((S\\NP)/NP)/PP")) ||
                    parse.categories.get(index).isFunctionInto(Category.valueOf("((S\\NP)/PP)/NP")))
            .flatMap(predicateIndex -> {
                    Verb v = null;
                    try {
                        v = Verb.getFromParse(predicateIndex, preds, parse);
                    } catch(IllegalArgumentException e) {
                        // System.err.println(e.getMessage());
                        v = null;
                    }
                    return Stream.of(v)
            .filter(verb -> verb != null && !verb.isCopular() && verb.getVoice() == Verb.Voice.ACTIVE)
            .map(verb -> {
                    if(parse.categories.get(predicateIndex).isFunctionInto(Category.valueOf("((S\\NP)/NP)/PP"))) {
                        return verb.permuteArgs(i -> i == 2 ? 3 : (i == 3 ? 2 : i)); // swap 2 and 3
                    } else {
                        return verb;
                    }
                })
            .flatMap(verb -> PredicationUtils.sequenceArgChoices(PredicationUtils.addPlaceholderArguments(verb)).stream()
            .filter(sequencedVerb -> sequencedVerb.getArgs().get(2).get(0).getDependency().isPresent() &&
                    sequencedVerb.getArgs().get(3).get(0).getDependency().isPresent()) // both have to be taken from the sentence
            .map(sequencedVerb -> new QATemplate(predicateIndex, sequencedVerb, 3, Optional.empty(), Optional.empty())));});

        final Stream<QATemplate> adverbTemplates = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) &&
                    !VerbHelper.isAuxiliaryVerb(words.get(index), parse.categories.get(index)) &&
                    !VerbHelper.isNegationWord(words.get(index)))
            .flatMap(predicateIndex -> {
                    Adverb adv = null;
                    try {
                        adv = Adverb.getFromParse(predicateIndex, preds, parse);
                    } catch(IllegalArgumentException e) {
                        // System.err.println(e.getMessage());
                        adv = null;
                    }
                    return Stream.of(adv)
            .filter(adverb -> adverb != null)
            .filter(adverb -> !(Prepositions.prepositionWords.contains(adverb.getPredicate()) &&
                                parse.categories.get(predicateIndex).matches(Category.valueOf("(S\\NP)\\(S\\NP)"))))
            .flatMap(adverb -> adverb.getArgs().get(2).stream()
            .filter(answerArg -> answerArg.getDependency().isPresent())
            .flatMap(answerArg -> PredicationUtils
                     .sequenceArgChoices(PredicationUtils
                    .addPlaceholderArguments(adverb.transformArgs((argNum, args) -> argNum <= 2
                                                                  ? ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("something").get()))
                                                                  : args))).stream()
                     // .withIndefinitePronouns(PredicationUtils // this seems to just make them confusing. include the arg!
            .flatMap(sequencedAdverb -> PredicationUtils.sequenceArgChoices(PredicationUtils.addPlaceholderArguments(answerArg.getPredication())).stream()
            .map(verb -> (Verb) verb)
            .filter(verb -> !verb.isCopular() && verb.getVoice() != Verb.Voice.ADJECTIVE)
            .flatMap(verb -> {
                    OptionalInt dirObjectArgNumOpt = IntStream.range(1, verb.getPredicateCategory().getNumberOfArguments() + 1)
                    .filter(i -> i > 1 && Category.NP.matches(verb.getPredicateCategory().getArgument(i)))
                    .max();
                    if(dirObjectArgNumOpt.isPresent() && verb.getArgs().get(dirObjectArgNumOpt.getAsInt()).get(0).getDependency().isPresent()) {
                        return Stream.of(new QATemplate(answerArg.getDependency().get().getArgument(), verb, dirObjectArgNumOpt.getAsInt(),
                                                        Optional.of(new TextWithDependencies(sequencedAdverb.getPhrase(Category.ADVERB),
                                                                                             sequencedAdverb.getAllDependencies())),
                                                        answerArg.getDependency()));
                    } else {
                        return Stream.empty();
                    }
                }))));});

        final Stream<QATemplate> templates = Stream.concat(verbTemplates, adverbTemplates);

        return templates.flatMap(template -> {
                final Argument dirObjectArgument = template.verb.getArgs().get(template.dirObjectArgNum).get(0);
                final Noun dirObject = (Noun) dirObjectArgument.getPredication();
                final ResolvedDependency dirObjectDep = dirObjectArgument.getDependency().get();
                final Noun subject = template.verb.getSubject();
                Verb questionProVerb = new Verb("do", Category.valueOf("((S[dcl]\\" + subject.getPredicateCategory() + ")/NP)/PP"),
                                                new ImmutableMap.Builder<Integer, ImmutableList<Argument>>()
                                                .put(1, ImmutableList.of(Argument.withNoDependency(subject.isExpletive()
                                                        ? subject
                                                        : subject.getPronoun().withDefiniteness(Noun.Definiteness.INDEFINITE))))
                                                .put(2, ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("what").get())))
                                                .put(3, ImmutableList.of(Argument.withNoDependency(Preposition.makeSimplePP("to", dirObject))))
                                                .build(),
                                                Verb.Tense.MODAL, Optional.of("would"),
                                                Verb.Voice.ACTIVE,
                                                false, false,
                                                /*template.verb.isPerfect(), template.verb.isProgressive(),*/
                                                template.verb.isNegated(), Optional.empty());
                    Verb.Tense answerTense = template.verb.getVoice() == Verb.Voice.ACTIVE ? Verb.Tense.BARE_VERB : Verb.Tense.BARE;
                    Verb answerVerb = template.verb
                        .withTense(answerTense)
                        .withNegation(false) // trying this out
                        .withPerfect(false) // trying this out
                        .withProgressive(false) // trying this out
                        .transformArgs((argNum, args) -> argNum == 1
                                       ? ImmutableList.of(Argument.withNoDependency(template.verb.getSubject().getPronounOrExpletive()))
                                       : (argNum != template.dirObjectArgNum ? args
                                       : ImmutableList.of(Argument.withNoDependency(dirObject.getPronoun()
                                                                                    .withCase(Noun.Case.ACCUSATIVE)
                                                                                    .withDefiniteness(Noun.Definiteness.DEFINITE)))));
                    TextWithDependencies answerTWD = new TextWithDependencies(answerVerb.getPhrase(Category.valueOf("S\\NP")),
                                                                             answerVerb.getAllDependencies());

                    QuestionAnswerPair withoutPPqaPair = new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                                                     template.predicateIndex, parse.categories.get(template.predicateIndex), 2,
                                                                                     template.predicateIndex, null,
                                                                                     ImmutableSet.of(), questionProVerb.getQuestionWords(),
                                                                                     dirObjectDep, answerTWD);
                    if(template.attachedPP.isPresent()) {
                        answerTWD = answerTWD.concatWithDep(template.attachedPP.get(), template.vpAttachmentDep);
                        return Stream.of(withoutPPqaPair,
                                         new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                                     template.predicateIndex, parse.categories.get(template.predicateIndex), 2,
                                                                     template.predicateIndex, null,
                                                                     ImmutableSet.of(), questionProVerb.getQuestionWords(),
                                                                     dirObjectDep, answerTWD));
                    } else {
                        return Stream.of(withoutPPqaPair);
                    }
            })
            .collect(toImmutableList());
    }

    /* We aren't using these, don't bother with them. */
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
                    Verb.Tense answerTense = originalVerb.getVoice() == Verb.Voice.ACTIVE ? Verb.Tense.BARE_VERB : Verb.Tense.BARE;
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

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllVPAttachmentQueries(int sentenceId, NBestList nBestList) {
        final ImmutableList<QuestionAnswerPair> allQAPairs = IntStream.range(0, nBestList.getN()).boxed()
            .flatMap(parseId -> newVPAttachmentQuestions(sentenceId, parseId, nBestList.getParse(parseId)).stream())
            .collect(toImmutableList());

        final QAPairAggregator<QAStructureSurfaceForm> qaPairAggregator =
            QAPairAggregators.aggregateWithAnswerAdverbAndPPArgDependencies();
        final QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> queryGenerator =
            QueryGenerators.checkboxQueryGenerator();

        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
            queryGenerator.generate(qaPairAggregator.aggregate(allQAPairs));
        for(ScoredQuery<QAStructureSurfaceForm> query : queries) {
            query.computeScores(nBestList);
        }
        return queries;
    }

    private static abstract class QuestionGenerationPipeline {
        abstract ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse);

        ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId, NBestList nBestList) {
            return IntStream.range(0, nBestList.getN()).boxed()
                .flatMap(parseId -> generateQAPairs(sentenceId, parseId, nBestList.getParse(parseId)).stream())
                .collect(toImmutableList());
        }

        abstract QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator();
        abstract QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator();
        Optional<QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>>> getQueryFilter() {
            return Optional.empty();
        }
        QueryPruningParameters getQueryPruningParameters() {
            return new QueryPruningParameters();
        }

        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(int sentenceId, NBestList nBestList) {
            final ImmutableList<QuestionAnswerPair> allQAPairs = IntStream.range(0, nBestList.getN()).boxed()
                .flatMap(parseId -> this.generateQAPairs(sentenceId, parseId, nBestList.getParse(parseId)).stream())
                .collect(toImmutableList());

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                getQueryGenerator().generate(getQAPairAggregator().aggregate(allQAPairs));
            for(ScoredQuery<QAStructureSurfaceForm> query : queries) {
                query.computeScores(nBestList);
            }
            if(getQueryFilter().isPresent()) {
                return getQueryFilter().get().filter(queries, nBestList, getQueryPruningParameters());
            } else {
                return queries;
            }
        }

        abstract ResponseSimulator getResponseSimulator(ImmutableMap<Integer, Parse> parses);
    }

    private static QuestionGenerationPipeline vpAttachmentQGPipeline = new QuestionGenerationPipeline() {
            @Override
            ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse) {
                return newVPAttachmentQuestions(sentenceId, parseId, parse);
            }

            @Override
            QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator() {
                return QAPairAggregators.aggregateWithAnswerAdverbAndPPArgDependencies();
            }

            @Override
            QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator() {
                return QueryGenerators.checkboxQueryGenerator();
            }

            @Override
            ResponseSimulator getResponseSimulator(ImmutableMap<Integer, Parse> parses) {
                return new VPAttachmentResponseSimulator(parses);
            }
        };

    private static QuestionGenerationPipeline newCoreArgQGPipeline = new QuestionGenerationPipeline() {

            @Override
            ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse) {
                return newCoreNPArgPronounQuestions(sentenceId, parseId, parse);
            }

            @Override
            QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator() {
                return QAPairAggregators.aggregateForMultipleChoiceQA();
            }

            @Override
            QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator() {
                return QueryGenerators.checkboxQueryGenerator();
            }

            @Override
            ResponseSimulator getResponseSimulator(ImmutableMap<Integer, Parse> parses) {
                throw new UnsupportedOperationException("must use one-best or gold simulator manually");
            }
        };

    public static void compareCoreArgQueries() {
        final ParseData parseData = ParseData.loadFromDevPool().get();
        ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
        HITLParser hitlParser = new HITLParser(100);
        long predsCoveredBoth = 0, predsCoveredOldOnly = 0, predsCoveredNewOnly = 0, totalPredsCovered = 0;
        for(int sentenceId = 0; sentenceId < parseData.getSentences().size(); sentenceId++) {
            System.out.println(String.format("========== SID = %04d ==========", sentenceId));
            ImmutableList<String> words = parseData.getSentences().get(sentenceId);
            System.out.println(TextGenerationHelper.renderString(words));
            NBestList nBestList = nBestLists.get(sentenceId);

            if(nBestList == null) {
                continue;
            }

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> oldQueries = hitlParser.getPronounCoreArgQueriesForSentence(sentenceId);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> newQueries = newCoreArgQGPipeline.generateAllQueries(sentenceId, nBestList);

            final ResponseSimulator oneBestResponseSimulator = new OneBestResponseSimulator();
            final ResponseSimulator goldResponseSimulator = new ResponseSimulatorGold(parseData);

            Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> oldQueriesByPredId = oldQueries.stream()
                .collect(groupingBy(query -> query.getPredicateId().getAsInt()));
            Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> newQueriesByPredId = newQueries.stream()
                .collect(groupingBy(query -> query.getPredicateId().getAsInt()));

            ImmutableSet<Integer> overlap = oldQueriesByPredId.keySet().stream()
                .filter(newQueriesByPredId.keySet()::contains)
                .collect(toImmutableSet());
            ImmutableSet<Integer> oldOnly = oldQueriesByPredId.keySet().stream()
                .filter(id -> !newQueriesByPredId.keySet().contains(id))
                .collect(toImmutableSet());
            ImmutableSet<Integer> newOnly = newQueriesByPredId.keySet().stream()
                .filter(id -> !oldQueriesByPredId.keySet().contains(id))
                .collect(toImmutableSet());
            long totalPredIdsCovered = overlap.size() + oldOnly.size() + newOnly.size();

            predsCoveredBoth += overlap.size();
            predsCoveredOldOnly += oldOnly.size();
            predsCoveredNewOnly += newOnly.size();
            totalPredsCovered += totalPredIdsCovered;

            Consumer<List<ScoredQuery<QAStructureSurfaceForm>>> printQueries = queries -> {
                for(ScoredQuery<QAStructureSurfaceForm> query : queries) {
                    final ImmutableList<Integer> goldOptions = goldResponseSimulator.respondToQuery(query);
                    final ImmutableList<Integer> oneBestOptions = oneBestResponseSimulator.respondToQuery(query);
                    System.out.println();
                    System.out.println(query.toString(words,
                                                      'G', goldOptions,
                                                      'B', oneBestOptions));
                }
            };

            Consumer<ImmutableSet<Integer>> printQueriesOfBothMethods = ids -> {
                for(int predicateIndex : ids) {
                    System.out.println("Predicate: " + predicateIndex + ": " + words.get(predicateIndex));
                    if(oldQueriesByPredId.get(predicateIndex) != null) {
                        System.out.println("- Old -");
                        printQueries.accept(oldQueriesByPredId.get(predicateIndex));
                    }
                    if(newQueriesByPredId.get(predicateIndex) != null) {
                        System.out.println("- New -");
                        printQueries.accept(newQueriesByPredId.get(predicateIndex));
                    }
                }
            };

            System.out.println("------- Overlap Queries  ------");
            printQueriesOfBothMethods.accept(overlap);

            System.out.println("------- Old Only Queries -------");
            printQueriesOfBothMethods.accept(oldOnly);

            System.out.println("------- New Only Queries -------");
            printQueriesOfBothMethods.accept(newOnly);
        }

        System.out.println(String.format("Total predicates covered by both methods: %d (%.2f%%)",
                                         predsCoveredBoth, ((double) predsCoveredBoth) / totalPredsCovered));
        System.out.println(String.format("Predicates covered by old method only: %d (%.2f%%)",
                                         predsCoveredOldOnly, ((double) predsCoveredOldOnly) / totalPredsCovered));
        System.out.println(String.format("Predicates covered by new method only: %d (%.2f%%)",
                                         predsCoveredNewOnly, ((double) predsCoveredNewOnly) / totalPredsCovered));
    }


    /**
     * Run this to print all the generated QA pairs to stdout.
     */
    public static void main(String[] args) {
        compareCoreArgQueries();
        System.exit(0);
        // what kind of questions we generate
        final QuestionGenerationPipeline qgPipeline = vpAttachmentQGPipeline;

        final ParseData devData = ParseData.loadFromDevPool().get();
        if(args.length == 0 || (!args[0].equalsIgnoreCase("gold") && !args[0].equalsIgnoreCase("tricky") &&
                                !args[0].equalsIgnoreCase("queries"))) {
            System.err.println("requires argument: \"gold\" or \"queries\"");
        } else if(args[0].equalsIgnoreCase("gold")) {
            printGoldQAPairs(devData, qgPipeline);
        } else if (args[0].equalsIgnoreCase("queries")) {
            ImmutableList<Integer> testSentences = new ImmutableList.Builder<Integer>()
                // .add(42).add(1489).add(36)
                // .add(90)
                // .add(3)
                .add(268, 1016, 1232, 1695, 444, 1495, 1516, 1304, 1564, 397, 217, 90, 224, 563, 1489, 199, 1105, 1124, 1199, 294,
                     1305, 705, 762)
                .build();
            printQueries(devData, testSentences, qgPipeline);
        }
    }

    private static void printQAPair(ImmutableList<String> words, QuestionAnswerPair qaPair) {
        System.out.println("--");
        System.out.println(words.get(qaPair.getPredicateIndex()));
        System.out.println(qaPair.getPredicateCategory());
        String targetDepString = Optional.ofNullable(qaPair.getTargetDependency()).map(dep -> TextGenerationHelper.dependencyString(words, dep)).orElse("???\t-?->\t???");
        System.out.println(targetDepString);
        for(ResolvedDependency dep : qaPair.getQuestionDependencies()) {
            System.out.println("\t" + TextGenerationHelper.dependencyString(words, dep));
        }
        System.out.println(qaPair.getQuestion());
        for(ResolvedDependency dep : qaPair.getAnswerDependencies()) {
            System.out.println("\t" + TextGenerationHelper.dependencyString(words, dep));
        }
        System.out.println("\t" + qaPair.getAnswer());
        System.out.println("--");
    }

    private static void printGoldQAPairs(ParseData parseData, QuestionGenerationPipeline qgPipeline) {
        System.out.println("\nQA Pairs for Gold Parses:\n");
        for(int sentenceId = 0; sentenceId < parseData.getSentences().size(); sentenceId++) {
            ImmutableList<String> words = parseData.getSentences().get(sentenceId);
            Parse goldParse = parseData.getGoldParses().get(sentenceId);
            System.out.println(String.format("========== SID = %04d ==========", sentenceId));
            System.out.println(TextGenerationHelper.renderString(words));
            for(QuestionAnswerPair qaPair : qgPipeline.generateQAPairs(sentenceId, -1, goldParse)) {
                printQAPair(words, qaPair);
            }
        }
    }

    private static void printQueries(ParseData parseData, ImmutableList<Integer> sentenceIds, QuestionGenerationPipeline qgPipeline) {
        System.out.println("\nQA Pairs for specific sentences:\n");
        ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
        for(int sentenceId : sentenceIds) {
            System.out.println(String.format("========== SID = %04d ==========", sentenceId));
            ImmutableList<String> words = parseData.getSentences().get(sentenceId);
            System.out.println(TextGenerationHelper.renderString(words));
            System.out.println("--------- Gold QA Pairs --------");
            Parse goldParse = parseData.getGoldParses().get(sentenceId);
            for(QuestionAnswerPair qaPair : qgPipeline.generateQAPairs(sentenceId, -1, goldParse)) {
                printQAPair(words, qaPair);
            }
            System.out.println("------- All Structured Queries -------");
            final ImmutableMap<Integer, Parse> oneBestParses = nBestLists.entrySet().stream()
                .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().getParse(0)));
            final ResponseSimulator oneBestResponseSimulator = qgPipeline.getResponseSimulator(oneBestParses);
            final ImmutableMap<Integer, Parse> goldParses = IntStream.range(0, parseData.getGoldParses().size())
                .boxed()
                .collect(toImmutableMap(i -> i, i -> parseData.getGoldParses().get(i)));
            final ResponseSimulator goldResponseSimulator = qgPipeline.getResponseSimulator(goldParses);
            for(ScoredQuery<QAStructureSurfaceForm> query : qgPipeline.generateAllQueries(sentenceId, nBestLists.get(sentenceId))) {
                final ImmutableList<Integer> goldOptions = goldResponseSimulator.respondToQuery(query);
                final ImmutableList<Integer> oneBestOptions = oneBestResponseSimulator.respondToQuery(query);
                System.out.println();
                System.out.println(query.toString(words,
                                                  'G', goldOptions,
                                                  'B', oneBestOptions));
            }
        }
    }

    private static void printStringAggregatedQueries(ParseData parseData, ImmutableList<Integer> sentenceIds, QuestionGenerationPipeline qgPipeline) {
        System.out.println("\nQA Pairs for specific sentences:\n");
        ImmutableMap<Integer, NBestList> nBestLists = NBestList.loadNBestListsFromFile("parses.100best.out", 100).get();
        for(int sentenceId : sentenceIds) {
            System.out.println(String.format("========== SID = %04d ==========", sentenceId));
            ImmutableList<String> words = parseData.getSentences().get(sentenceId);
            System.out.println(TextGenerationHelper.renderString(words));
            System.out.println("--------- Gold QA Pairs --------");
            Parse goldParse = parseData.getGoldParses().get(sentenceId);
            for(QuestionAnswerPair qaPair : qgPipeline.generateQAPairs(sentenceId, -1, goldParse)) {
                printQAPair(words, qaPair);
            }
            NBestList nBestList = nBestLists.get(sentenceId);
            ImmutableList<QuestionAnswerPair> qaPairs = qgPipeline.generateAllQAPairs(sentenceId, nBestList);
            System.out.println("------- All String-aggregated Queries -------");
            System.out.println(TextGenerationHelper.renderString(words));
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

