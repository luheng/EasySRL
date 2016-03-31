package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;

import static edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils.*;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
                // plurality vote on queryPrompt and answer
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
     * The input should be all the queryPrompt-answer pairs given a sentence and its n-best list.
     * This is too crazy...
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForSingleChoiceQA() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(qa -> qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber()))
                .values().stream()
                .map(qaList -> new QAPairAggregatorUtils.QuestionSurfaceFormToStructure<>(
                        getBestQuestionSurfaceForm(qaList),
                        new QuestionStructure(qaList),
                        qaList))
                .collect(groupingBy(qsts -> qsts.question)) // Group by queryPrompt surface form.
                .values().stream()
                .flatMap(qsList -> {
                    // All the queryPrompt structures sharing the same surface form.
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
                                        .collect(Collectors.joining(answerDelimiter));
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
                                return new AnswerSurfaceFormToStructure<>(
                                        bestAnswerString,
                                        new AnswerStructure(argList, false /* not single headed */),
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
     * The input should be all the queryPrompt-answer pairs given a sentence and its n-best list.
     * Each aggregated answer is single headed.
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForMultipleChoiceQA() {
        return qaPairs ->  qaPairs
                    .stream()
                    .collect(groupingBy(qa -> qa.getPredicateIndex()
                                                + "\t" + qa.getPredicateCategory()
                                                + "\t" + qa.getArgumentNumber()))
                    .values().stream()
                    .map(questionStrGroup -> new QuestionSurfaceFormToStructure<>(
                            getBestQuestionSurfaceForm(questionStrGroup),
                            new QuestionStructure(questionStrGroup),
                            questionStrGroup))
                    // Group by queryPrompt surface form.
                    .collect(groupingBy(qsts -> qsts.question))
                    .values().stream()
                    .flatMap(questionSurfGroup -> {
                        // All the queryPrompt structures sharing the same surface form.
                        final ImmutableList<QuestionStructure> questionStructures = questionSurfGroup.stream()
                                .map(qs -> qs.structure)
                                .collect(toImmutableList());
                        // All the QAPairs sharing the same surface form.
                        final ImmutableList<QuestionAnswerPair> qaList = questionSurfGroup.stream()
                                .flatMap(qs -> qs.qaList.stream())
                                .collect(toImmutableList());
                        final int sentenceId = qaList.get(0).getSentenceId();

                        // Get best answer surface forms for each argument head.
                        return qaList.stream()
                                .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                                .entrySet()
                                .stream()
                                .map(argStrGroup -> {
                                    final List<QuestionAnswerPair> qaList2 = argStrGroup.getValue();
                                    final AnswerStructure answerStructure = new AnswerStructure(
                                            ImmutableList.of(argStrGroup.getKey()), true /* is single headed */);
                                    return new AnswerSurfaceFormToStructure<>(
                                            getBestAnswerSurfaceForm(qaList2),
                                            answerStructure,
                                            qaList2);
                                })
                                .collect(groupingBy(asts -> asts.answer))
                                .values().stream()
                                .map(answerSurfGroup -> {
                                    final ImmutableList<QuestionAnswerPair> qaList2 = answerSurfGroup.stream()
                                            .flatMap(asts -> asts.qaList.stream())
                                            .collect(toImmutableList());
                                    return new QAStructureSurfaceForm(sentenceId,
                                            questionSurfGroup.get(0).question,
                                            answerSurfGroup.get(0).answer,
                                            qaList2,
                                            questionStructures,
                                            answerSurfGroup.stream().map(asts -> asts.structure).collect(toImmutableList()));
                                });
                    })
                    .collect(toImmutableList());
    }

    /**
     * The input should be all the queryPrompt-answer pairs given a sentence and its n-best list.
     * Each aggregated answer is single headed.
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateWithFullQuestionStructure() {
        return qaPairs -> {
            List<QAStructureSurfaceForm> qaStructureSurfaceFormList = new ArrayList<>();
            qaPairs.stream()
                    .collect(groupingBy(QuestionAnswerPair::getPredicateIndex))
                    .values().stream()
                    .forEach(qaPairs2 -> {
                        // Get global answer surface form to structure map.
                        final ImmutableSet<AnswerSurfaceFormToStructure> allAstsEntries = qaPairs2.stream()
                                .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                                .values().stream()
                                .map(sameAnswerQAs -> {
                                    final AnswerStructure answerStructure = new AnswerStructure(
                                            ImmutableList.of(sameAnswerQAs.get(0).getArgumentIndex()),
                                            true /* single headed */);
                                    return new AnswerSurfaceFormToStructure<>(
                                            getBestAnswerSurfaceForm(sameAnswerQAs),
                                            answerStructure,
                                            sameAnswerQAs);
                                })
                                .collect(toImmutableSet());

                        qaPairs2.stream()
                                // Collect questions of the same structure.
                                .collect(groupingBy(qa -> qa.getPredicateCategory() + "\t"
                                        + qa.getArgumentNumber() + "\t"
                                        + getQuestionDependenciesHashString(qa)))
                                .values().stream()
                                .map(sameQuestionQAs -> new QuestionSurfaceFormToStructure<>(
                                        getBestQuestionSurfaceForm(sameQuestionQAs),
                                        new QuestionStructure(sameQuestionQAs),
                                        sameQuestionQAs))
                                // Combine questions with same surface form.
                                .collect(groupingBy(qsts -> qsts.question))
                                .values().stream()
                                .forEach(qstsEntries -> {
                                    final ImmutableSet<QuestionAnswerPair> questionQAPairs = qstsEntries.stream()
                                            .flatMap(qsts -> qsts.qaList.stream())
                                            .distinct()
                                            .collect(toImmutableSet());
                                    final ImmutableList<QuestionStructure> questionStructures =
                                            qstsEntries.stream().map(qsts -> qsts.structure).collect(toImmutableList());
                                    allAstsEntries.stream()
                                            .collect(groupingBy(asts -> asts.answer))
                                            .values().stream()
                                            .forEach(astsEntries -> {
                                                final ImmutableList<QuestionAnswerPair> commonQAPairs =
                                                        questionQAPairs.stream()
                                                                .filter(qa -> astsEntries.stream()
                                                                        .anyMatch(asts -> asts.qaList.contains(qa)))
                                                        .collect(toImmutableList());
                                                final ImmutableList<AnswerStructure> answerStructures =
                                                        astsEntries.stream()
                                                                .map(asts -> asts.structure)
                                                                .collect(toImmutableList());
                                                if (commonQAPairs.size() > 0) {
                                                    QAStructureSurfaceForm qa = new QAStructureSurfaceForm(
                                                            commonQAPairs.get(0).getSentenceId(),
                                                            qstsEntries.get(0).question,
                                                            astsEntries.get(0).answer,
                                                            commonQAPairs,
                                                            questionStructures,
                                                            answerStructures);
                                                    qaStructureSurfaceFormList.add(qa);
                                                }
                                            });
                                });
                    });
            return ImmutableList.copyOf(qaStructureSurfaceFormList);
        };
    }

    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
