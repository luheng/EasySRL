package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.util.*;

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
                                                                      false, // if true: only ask questions with indefinite noun args
                                                                      true, // all (false: just one) standard questions
                                                                      false // if true: include supersense questions
                                                                      ));
    }

    public static ImmutableList<ImmutableList<String>[]> simpleVerbNPArgNewQAPairs(Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        ImmutableList.Builder<Verb> verbsBuilder = new ImmutableList.Builder<>();
        for(int index = 0; index < parse.categories.size(); index++) {
            if(parse.categories.get(index).isFunctionInto(Category.valueOf("S\\NP"))
               && !parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)"))) {
                verbsBuilder = verbsBuilder.add(Verb.getFromParse(index, preds, parse));
            }
        }
        final ImmutableList<Verb> verbs = verbsBuilder.build();
        final ImmutableList<ImmutableList<String>[]> qaPairStrings = verbs.stream()
            .flatMap(verb -> IntStream.range(1, verb.getPredicateCategory().getNumberOfArguments() + 1)
            .boxed()
            .filter(argNum -> Category.NP.matches(verb.getPredicateCategory().getArgument(argNum)))
            .flatMap(askingArgNum -> {
                    Verb questionPredication = verb.transformArgs((argNum, args) -> {
                            if(argNum == askingArgNum) {
                                if(args.isEmpty()) {
                                    return ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("what").get()));
                                } else {
                                    return ImmutableList.of(Argument.withNoDependency(((Noun) args.get(0).getPredication())
                                                                                      .getPronoun()
                                                                                      .withPerson(Noun.Person.THIRD)
                                                                                      .withDefiniteness(Noun.Definiteness.FOCAL)));
                                }
                            } else {
                                Category argCat = verb.getPredicateCategory().getArgument(argNum);
                                if(!Category.NP.matches(argCat)) {
                                    return ImmutableList.of(Argument.withNoDependency(new Gap(argCat)));
                                } else if(args.isEmpty()) {
                                    return ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("something").get()));
                                } else {
                                    return ImmutableList.of(Argument.withNoDependency(((Noun) args.get(0).getPredication())
                                                                                      .getPronoun().withDefiniteness(Noun.Definiteness.INDEFINITE)));
                                }
                            }
                        }).withModal("would");
                    ImmutableList<String> question = questionPredication.getQuestionWords();
                    ImmutableList<ImmutableList<String>> answers = verb.getArgs().get(askingArgNum).stream()
                    .map(Argument::getPredication)
                    .map(Predication::getPhrase)
                    .collect(toImmutableList());
                    return answers.stream()
                    .map(ans -> {
                            ImmutableList<String>[] qaStrings = new ImmutableList[2];
                            qaStrings[0] = question;
                            qaStrings[1] = ans;
                            return qaStrings;
                        });
                }))
            .collect(toImmutableList());
        return qaPairStrings;
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
            for(ImmutableList<String>[] qaPairStrings : simpleVerbNPArgNewQAPairs(goldParse)) {
                System.out.println(TextGenerationHelper.renderString(qaPairStrings[0]));
                System.out.println("\t" + TextGenerationHelper.renderString(qaPairStrings[1]));
            }
        }
    }
}

