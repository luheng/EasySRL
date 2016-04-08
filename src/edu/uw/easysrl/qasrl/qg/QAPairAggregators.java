package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;

import edu.uw.easysrl.syntax.grammar.Category;

import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class where we put all of our useful QAPairAggregator instances
 * (which in general may be polymorphic over subtypes of QAPairSurfaceForm).
 *
 * This class is for LOGIC, NOT DATA.
 *
 * Created by julianmichael on 3/17/2016.
 */
public final class QAPairAggregators {

    public static QAPairAggregator<QAPairSurfaceForm> aggregateByString() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(QuestionAnswerPair::getQuestion))
            .entrySet()
            .stream()
            .flatMap(eQuestion -> eQuestion.getValue()
                    .stream()
                    .collect(groupingBy(QuestionAnswerPair::getAnswer))
                    .entrySet()
                    .stream()
                    .map(eAnswer -> {
                        assert eAnswer.getValue().size() > 0
                                : "list in group should always be nonempty";
                        int sentenceId = eAnswer.getValue().get(0).getSentenceId();
                        return new BasicQAPairSurfaceForm(sentenceId,
                                eQuestion.getKey(),
                                eAnswer.getKey(),
                                ImmutableList.copyOf(eAnswer.getValue()));
                    }))
            .collect(toImmutableList());
    }

    public static QAPairAggregator<TargetDependencySurfaceForm> aggregateByTargetDependency() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(QuestionAnswerPair::getTargetDependency))
            .entrySet()
            .stream()
            .map(e -> {
                final ResolvedDependency targetDep = e.getKey();
                final Collection<QuestionAnswerPair> qaList = e.getValue();
                assert qaList.size() > 0
                        : "list in group should always be nonempty";
                int sentenceId = e.getValue().get(0).getSentenceId();
                // plurality vote on question and answer
                String pluralityQuestion = HashMultiset
                        .create(qaList.stream()
                                .map(QuestionAnswerPair::getQuestion)
                                .collect(toList()))
                        .entrySet()
                        .stream()
                        .max(Comparator.comparing(Multiset.Entry::getCount))
                        .map(Multiset.Entry::getElement)
                        .get(); // there should always be one because our list is nonempty
                String pluralityAnswer = HashMultiset
                        .create(qaList.stream()
                                .map(QuestionAnswerPair::getAnswer)
                                .collect(toList()))
                        .entrySet()
                        .stream()
                        .max(Comparator.comparing(Multiset.Entry::getCount))
                        .map(Multiset.Entry::getElement)
                        .get(); // there should always be one because our list is nonempty
                return new TargetDependencySurfaceForm(sentenceId,
                                                       pluralityQuestion,
                                                       pluralityAnswer,
                                                       ImmutableList.copyOf(qaList),
                                                       targetDep);
            })
            .collect(toImmutableList());
    }


    /**
     * for the below aggregator. tells us whether we want to group based on a dependency.
     */
    private static boolean isDependencySalient(ResolvedDependency dep, QuestionAnswerPair qaPair) {
        ImmutableList<Category> categories = ImmutableList.copyOf(qaPair.getParse().categories);
        ImmutableList<String> words = qaPair.getParse().syntaxTree.getLeaves().stream()
            .map(l -> l.getWord())
            .collect(toImmutableList());
        int index = dep.getHead();
        Category cat = categories.get(index);
        return (qaPair.getTargetDependency() != null && dep.equals(qaPair.getTargetDependency())) ||
            (cat.isFunctionInto(Category.valueOf("S\\NP")) && !cat.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"))) ||
            (cat.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) && dep.getArgNumber() == 2) ||
            (cat.isFunctionInto(Category.valueOf("NP\\NP")) && dep.getArgNumber() == 1 && !words.get(index).equalsIgnoreCase("of"));
            // (Category.valueOf("((S\\NP)\\(S\\NP))/NP").matches(cat) && dep.getArgNumber() == 2) ||
            // (Category.valueOf("(NP\\NP)/NP").matches(cat) && dep.getArgNumber() == 1 && !words.get(index).equalsIgnoreCase("of"));
    }
    /**
     * Aggregates by question and answer deps connected to verbs or noun/verb adjuncts.
     */
    public static QAPairAggregator<QADependenciesSurfaceForm> aggregateBySalientDeps() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(qaPair -> qaPair.getQuestionDependencies().stream()
                                .filter(dep -> isDependencySalient(dep, qaPair))
                                .collect(toImmutableSet())))
            .entrySet()
            .stream()
            .flatMap(eQuestion -> eQuestion.getValue()
                    .stream()
                    .collect(groupingBy(qaPair -> qaPair.getAnswerDependencies().stream()
                                        .filter(dep -> isDependencySalient(dep, qaPair))
                                        .collect(toImmutableSet())))
                    .entrySet()
                    .stream()
                    .map(eAnswer -> {
                        assert eAnswer.getValue().size() > 0
                                : "list in group should always be nonempty";
                        int sentenceId = eAnswer.getValue().get(0).getSentenceId();
                        QuestionAnswerPair bestQAPair = eAnswer.getValue().stream()
                            .collect(maxBy((qaPair1, qaPair2) -> Double.compare(qaPair1.getParse().score, qaPair2.getParse().score)))
                            .get();
                        String question = bestQAPair.getQuestion();
                        String answer = bestQAPair.getAnswer();
                        return new QADependenciesSurfaceForm(sentenceId,
                                                             question,
                                                             answer,
                                                             ImmutableList.copyOf(eAnswer.getValue()),
                                                             eQuestion.getKey(),
                                                             eAnswer.getKey());
                        }))
            .collect(toImmutableList());
    }

    /**
     * The input should be all the question-answer pairs given a sentence and its n-best list.
     * This is too crazy...
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForSingleChoiceQA() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(qa -> qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber()))
                .values().stream()
                .map(qaList -> new QAPairAggregatorUtils.QuestionSurfaceFormToStructure<>(
                        QAPairAggregatorUtils.getBestQuestionSurfaceForm(qaList),
                        new QuestionStructure(qaList),
                        qaList))
                .collect(groupingBy(qsts -> qsts.question)) // Group by question surface form.
                .values().stream()
                .flatMap(qsList -> {
                    // All the question structures sharing the same surface form.
                    final ImmutableList<QuestionStructure> questionStructures = qsList.stream()
                            .map(qsts -> qsts.structure)
                            .collect(toImmutableList());
                    // All the QAPairs sharing the same surface form.
                    final ImmutableList<QuestionAnswerPair> qaList = qsList.stream()
                            .flatMap(qs -> qs.qaList.stream())
                            .collect(toImmutableList());
                    final int sentenceId = qaList.get(0).getSentenceId();

                    // Get answer indices list.
                    Table<ImmutableList<Integer>, String, Double> argListToAnswerToScore = HashBasedTable.create();
                    Table<ImmutableList<Integer>, QuestionAnswerPair, Boolean> argListToQAs = HashBasedTable.create();
                    qaList.stream()
                            .collect(groupingBy(QuestionAnswerPair::getParseId))
                            .values().stream()
                            .forEach(qaList2 -> {
                                Map<Integer, String> argIdToSpan = new HashMap<>();
                                qaList2.forEach(qa -> argIdToSpan.put(qa.getArgumentIndex(), qa.getAnswer()));
                                ImmutableList<Integer> argList = argIdToSpan.keySet().stream()
                                        .sorted()
                                        .collect(toImmutableList());
                                String answerString = argList.stream()
                                        .map(argIdToSpan::get)
                                        .collect(Collectors.joining(QAPairAggregatorUtils.answerDelimiter));
                                double score = qaList2.get(0).getParse().score;
                                double s0 = argListToAnswerToScore.contains(argList, answerString) ?
                                        argListToAnswerToScore.get(argList, answerString) : .0;
                                argListToAnswerToScore.put(argList, answerString, score + s0);
                                qaList2.stream().forEach(qa -> argListToQAs.put(argList, qa, Boolean.TRUE));
                            });

                    // Get best answers.
                    return argListToAnswerToScore.rowKeySet().stream()
                            .map(argList -> {
                                String bestAnswerString = argListToAnswerToScore.row(argList).entrySet().stream()
                                        .max(Comparator.comparing(Entry::getValue))
                                        .get().getKey();
                                final Collection<QuestionAnswerPair> qaList2 = argListToQAs.row(argList).keySet();
                                return new QAPairAggregatorUtils.AnswerSurfaceFormToStructure<>(
                                        bestAnswerString,
                                        new AnswerStructure(argList, qaList2),
                                        qaList2);
                            })
                            .collect(groupingBy(asts -> asts.answer))
                            .values().stream()
                            .map(asList -> {
                                final ImmutableList<QuestionAnswerPair> qaList2 = asList.stream()
                                        .flatMap(asts -> asts.qaList.stream())
                                        .collect(toImmutableList());
                                return new QAStructureSurfaceForm(sentenceId,
                                        qsList.get(0).question,
                                        asList.get(0).answer,
                                        qaList2,
                                        questionStructures,
                                        asList.stream().map(asts -> asts.structure).collect(toImmutableList()));
                                });
                })
                .collect(toImmutableList());
    }

    /**
     * The input should be all the question-answer pairs given a sentence and its n-best list.
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForMultipleChoiceQA() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(qa -> qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber()))
                .values().stream()
                .map(qaList -> new QAPairAggregatorUtils.QuestionSurfaceFormToStructure<>(
                        QAPairAggregatorUtils.getBestQuestionSurfaceForm(qaList),
                        new QuestionStructure(qaList),
                        qaList))
                .collect(groupingBy(qsts -> qsts.question)) // Group by question surface form.
                .values().stream()
                .flatMap(qsList -> {
                    // All the question structures sharing the same surface form.
                    final ImmutableList<QuestionStructure> questionStructures = qsList.stream()
                            .map(qs -> qs.structure)
                            .collect(toImmutableList());
                    // All the QAPairs sharing the same surface form.
                    final ImmutableList<QuestionAnswerPair> qaList = qsList.stream()
                            .flatMap(qs -> qs.qaList.stream())
                            .collect(toImmutableList());
                    final int sentenceId = qaList.get(0).getSentenceId();

                    // Get best answer surface forms for each argument head.
                    return qaList.stream()
                            .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                            .entrySet()
                            .stream()
                            .map(argIdGroup -> {
                                final List<QuestionAnswerPair> qaList2 = argIdGroup.getValue();
                                final AnswerStructure answerStructure = new AnswerStructure(
                                        ImmutableList.of(argIdGroup.getKey()),
                                        QAPairAggregatorUtils.getParseIds(qaList2),
                                        QAPairAggregatorUtils.getScore(qaList2));

                                return new QAPairAggregatorUtils.AnswerSurfaceFormToStructure<>(
                                        QAPairAggregatorUtils.getBestAnswerSurfaceForm(qaList2),
                                        answerStructure,
                                        qaList2);
                            })
                            .collect(groupingBy(asts -> asts.answer))
                            .values().stream()
                            .map(asList -> {
                                final ImmutableList<QuestionAnswerPair> qaList2 = asList.stream()
                                        .flatMap(asts -> asts.qaList.stream())
                                        .collect(toImmutableList());
                                return new QAStructureSurfaceForm(sentenceId,
                                        qsList.get(0).question,
                                        asList.get(0).answer,
                                        qaList2,
                                        questionStructures,
                                        asList.stream().map(asts -> asts.structure).collect(toImmutableList()));
                            });
                })
                .collect(toImmutableList());
    }


    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
