package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Latest query generator.
 * Created by luheng on 2/18/16.
 */
public class QueryGeneratorBothWays {

    final int sentenceId;
    final List<String> words;
    final List<Parse> parses;

    final int numParses;
    final double logNormalizer;

    public final List<QuestionAnswerPairReduced> allQAPairs;
    public final Map<String, List<QuestionAnswerPairReduced>> questionToPairs;
    public final Map<String, List<QuestionAnswerPairReduced>> answerToPairs;
    public final Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs;
    public final Table<String, String, Set<Parse>> qaStringsToParses;

    public QueryGeneratorBothWays(final int sentenceId,
                          final List<String> words,
                          final List<Parse> parses) {

        this.sentenceId = sentenceId;
        this.words = words;
        this.parses = parses;
        this.numParses = parses.size();
        this.logNormalizer = Math.log(parses.stream().mapToDouble(p -> Math.exp(p.score)).sum());

        this.allQAPairs = new LinkedList<>();
        this.questionToPairs = new HashMap<>();
        this.answerToPairs = new HashMap<>();
        this.qaStringsToQAPairs = HashBasedTable.create();
        this.qaStringsToParses = HashBasedTable.create();

        for(final Parse parse : parses) {
            for(int predIndex = 0; predIndex < words.size(); predIndex++) {
                Category predCategory = parse.categories.get(predIndex);
                for(int argNum = 1; argNum <= predCategory.getNumberOfArguments(); argNum++) {
                    List<QuestionAnswerPairReduced> qaPairs = QuestionGenerator.generateAllQAPairs(predIndex, argNum, words, parse);
                    for(QuestionAnswerPairReduced qaPair : qaPairs) {
                        registerQAPair(qaPair, parse);
                    }
                }
            }
        }
    }

    private double parseLogProbability(Parse p) {
        return p.score - logNormalizer;
    }

    // just gets the entropy of the distribution over whether a QA pair is correct.
    // if we wish to incorporate noisy responses, this is not necessarily what we want.
    private double getEntropy(final QuestionAnswerPairReduced qaPair) {
        // TODO could probably just implement this with partitioning functions below
        double prob = parses.stream()
            .filter(parse -> supports(parse, qaPair.renderQuestion(), qaPair.renderAnswer()))
            .map(this::parseLogProbability)
            .mapToDouble(Math::exp)
            .sum();
        return -1.0 * ((prob * Math.log(prob)) + ((1.0 - prob) * Math.log(1.0 - prob)));
    }

    // used to add a "decision" to the "decision tree" of possible outcomes of QA annotation
    private List<List<Parse>> partitionParses(final List<List<Parse>> alreadyPartitioned,
                                              final QuestionAnswerPairReduced qaPair) {
        return alreadyPartitioned
            .stream()
            .flatMap(parseSet -> {
                Map<Boolean, List<Parse>> smallPartition = parseSet
                    .stream()
                    .collect(Collectors.partitioningBy(parse -> supports(parse, qaPair.renderQuestion(), qaPair.renderAnswer())));
                List<List<Parse>> newSmallPartition = new LinkedList<>();
                newSmallPartition.add(smallPartition.get(false));
                newSmallPartition.add(smallPartition.get(true));
                return newSmallPartition.stream();
            })
            .collect(Collectors.toList());
    }

    private double partitionEntropy(final List<List<Parse>> partitionedParses) {
        double negEntropy = partitionedParses
            .stream()
            .mapToDouble(parseSet -> parseSet
                 .stream()
                 .mapToDouble(this::parseLogProbability)
                 .map(Math::exp)
                 .sum())
            .map(prob -> prob * Math.log(prob))
            .sum();
        return -1.0 * negEntropy;
    }

    private Optional<QuestionAnswerPairReduced> chooseNextQAPairFromList(final List<List<Parse>> givenPartition,
                                                        final List<QuestionAnswerPairReduced> candidateQAPairs) {
        // choose the max entropy next question.
        return candidateQAPairs
            .stream()
            .collect(Collectors.maxBy(Comparator.comparing(qaPair -> partitionEntropy(partitionParses(givenPartition, qaPair)))));
    }

    private boolean supports(final Parse parse, String question, String answer) {
        if(qaStringsToParses.contains(question, answer)) {
            return qaStringsToParses.get(question, answer).contains(parse);
        } else {
            assert false : "qaStringsToParses missing qa pair: " + question + " --- " + answer;
            return false;
        }
    }

    public List<MultiQuery> generateQueries() {
        List<MultiQuery> queries = new LinkedList<MultiQuery>();
        for(String question : questionToPairs.keySet()) {
            List<QuestionAnswerPairReduced> qaPairs = questionToPairs.get(question);
            Set<String> answers = qaPairs.stream()
                .map(QuestionAnswerPairReduced::renderAnswer)
                .collect(Collectors.toSet());
            queries.add(new MultiQuery.Forward(sentenceId, question, answers, qaStringsToQAPairs, qaStringsToParses));
        }
        for(String answer : answerToPairs.keySet()) {
            List<QuestionAnswerPairReduced> qaPairs = answerToPairs.get(answer);
            Set<String> questions = qaPairs.stream()
                .map(QuestionAnswerPairReduced::renderQuestion)
                .collect(Collectors.toSet());
            queries.add(new MultiQuery.Backward(sentenceId, answer, questions, qaStringsToQAPairs, qaStringsToParses));
        }
        return queries;
    }

    private void registerQAPair(QuestionAnswerPairReduced qaPair, Parse parse) {
        allQAPairs.add(qaPair);
        final String question = qaPair.renderQuestion();
        final String answer = qaPair.renderAnswer();
        if(!questionToPairs.containsKey(question)) {
            questionToPairs.put(question, new LinkedList<QuestionAnswerPairReduced>());
        }
        questionToPairs.get(question).add(qaPair);
        if(!answerToPairs.containsKey(answer)) {
            answerToPairs.put(answer, new LinkedList<QuestionAnswerPairReduced>());
        }
        answerToPairs.get(answer).add(qaPair);
        if(!qaStringsToQAPairs.contains(question, answer)) {
            qaStringsToQAPairs.put(question, answer, new LinkedList<QuestionAnswerPairReduced>());
        }
        qaStringsToQAPairs.get(question, answer).add(qaPair);
        if(!qaStringsToParses.contains(question, answer)) {
            qaStringsToParses.put(question, answer, new HashSet<Parse>());
        }
        qaStringsToParses.get(question, answer).add(parse);
    }

}

