package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import static edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils.*;

import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

import java.util.*;

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

    /**
     * Aggregates by question and answer deps connected to verbs or noun/verb adjuncts.
     */
    public static QAPairAggregator<QADependenciesSurfaceForm> aggregateBySalientDependencies() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QAPairAggregatorUtils::getSalientQuestionDependencies))
                .values().stream()
                .map(QAPairAggregatorUtils::getQuestionSurfaceFormToDependencies)
                .collect(groupingBy(q2d -> q2d.surfaceForm))
                .values().stream()
                .flatMap(q2dEntries -> {
                    final ImmutableList<QuestionAnswerPair> questionQAPairs = q2dEntries.stream()
                            .flatMap(qs -> qs.qaList.stream())
                            .collect(toImmutableList());
                    return questionQAPairs.stream()
                            .collect(groupingBy(QAPairAggregatorUtils::getSalientAnswerDependencies))
                            .values().stream()
                            .map(QAPairAggregatorUtils::getAnswerSurfaceFormToDependencies)
                            .collect(groupingBy(a2d -> a2d.surfaceForm))
                            .values().stream()
                            .map(a2dEntries -> new QADependenciesSurfaceForm(
                                    questionQAPairs.get(0).getSentenceId(),
                                    q2dEntries.get(0).surfaceForm,
                                    a2dEntries.get(0).surfaceForm,
                                    a2dEntries.stream().flatMap(a2d -> a2d.qaList.stream()).collect(toImmutableList()),
                                    q2dEntries.get(0).dependencies,
                                    a2dEntries.stream().map(a2d -> a2d.dependencies).collect(toImmutableSet())));
                }).collect(toImmutableList());
    }

    public static QAPairAggregator<QAStructureSurfaceForm> aggregateWithAnswerAdjunctDependencies() {
        return qaPairs ->  qaPairs
                .stream()
                .collect(groupingBy(QuestionAnswerPair::getPredicateIndex))
                .values().stream()
                .flatMap(samePredicateQAs -> samePredicateQAs
                        .stream()
                        .collect(groupingBy(QAPairAggregatorUtils::getQuestionLabelString))
                        .values().stream()
                        .map(QAPairAggregatorUtils::getQuestionSurfaceFormToStructure)
                        .collect(groupingBy(qs2s -> qs2s.question))
                        .values().stream()
                        .flatMap(qs2sEntries -> {
                            final ImmutableList<QuestionAnswerPair> questionQAPairs = qs2sEntries.stream()
                                    .flatMap(qs -> qs.qaList.stream())
                                    .collect(toImmutableList());
                            return questionQAPairs.stream()
                                    .collect(groupingBy(QAPairAggregatorUtils::getAnswerDependenciesStructureString))
                                    .values().stream()
                                    .map(QAPairAggregatorUtils::getAnswerSurfaceFormToAdjunctHeadsStructure)
                                    .collect(groupingBy(as2s -> as2s.answer))
                                    .values().stream()
                                    .map(as2sEntries -> getQAStructureSurfaceForm(qs2sEntries, as2sEntries));
                        })
                ).collect(toImmutableList());
    }

    /**
     * The input should be all the queryPrompt-answer pairs given a sentence and its n-best list.
     * This is too crazy...
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForSingleChoiceQA() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QAPairAggregatorUtils::getQuestionLabelString))
                .values().stream()
                .map(QAPairAggregatorUtils::getQuestionSurfaceFormToStructure)
                .collect(groupingBy(qs2s -> qs2s.question))
                .values().stream()
                .flatMap(qs2sEntries -> {
                    final ImmutableList<QuestionAnswerPair> questionQAList = qs2sEntries.stream()
                            .flatMap(qs -> qs.qaList.stream())
                            .collect(toImmutableList());
                    return QAPairAggregatorUtils.getAnswerSurfaceFormToMultiHeadedStructures(questionQAList)
                            .stream()
                            .collect(groupingBy(as2s -> as2s.answer))
                            .values().stream()
                            .map(as2sEntries -> getQAStructureSurfaceForm(qs2sEntries, as2sEntries));
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
                .collect(groupingBy(QAPairAggregatorUtils::getQuestionLabelString))
                .values().stream()
                .map(QAPairAggregatorUtils::getQuestionSurfaceFormToStructure)
                .collect(groupingBy(qs2s -> qs2s.question))
                .values().stream()
                .flatMap(qs2sEntries -> qs2sEntries
                        .stream()
                        .flatMap(qs -> qs.qaList.stream())
                        .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                        .values().stream()
                        .map(QAPairAggregatorUtils::getAnswerSurfaceFormToSingleHeadedStructure)
                        .collect(groupingBy(as2s -> as2s.answer))
                        .values().stream()
                        .map(as2sEntries -> getQAStructureSurfaceForm(qs2sEntries, as2sEntries))
                ).collect(toImmutableList());
    }

    /**
     * The input should be all the queryPrompt-answer pairs given a sentence and its n-best list.
     * Each aggregated answer is single headed.
     * Questions are aggregated according to their full dependency structure.
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateWithFullQuestionStructure() {
        return qaPairs -> {
            List<QAStructureSurfaceForm> qaStructureSurfaceFormList = new ArrayList<>();
            qaPairs.stream()
                    .collect(groupingBy(QuestionAnswerPair::getPredicateIndex))
                    .values().stream()
                    .forEach(somePredicateQAs -> {
                        final Map<String, List<AnswerSurfaceFormToStructure>> allAS2SEntries = somePredicateQAs.stream()
                                .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                                .values().stream()
                                .map(QAPairAggregatorUtils::getAnswerSurfaceFormToSingleHeadedStructure)
                                .collect(groupingBy(as2s -> as2s.answer));

                        somePredicateQAs.stream()
                                .collect(groupingBy(QAPairAggregatorUtils::getFullQuestionStructureString))
                                .values().stream()
                                .map(QAPairAggregatorUtils::getQuestionSurfaceFormToStructure)
                                .collect(groupingBy(qs2s -> qs2s.question))
                                .values().stream()
                                .forEach(qs2sEntries -> allAS2SEntries.values().stream()
                                        .forEach(as2sEntries -> {
                                            Set<QuestionSurfaceFormToStructure> commonQS2S = new HashSet<>();
                                            Set<AnswerSurfaceFormToStructure> commonAS2S = new HashSet<>();
                                            Set<QuestionAnswerPair> commonQAPairs = new HashSet<>();

                                            qs2sEntries.forEach(qs2s ->
                                                    as2sEntries.forEach(as2s -> {
                                                        ImmutableSet<QuestionAnswerPair> qaSet = qs2s.qaList.stream()
                                                                .filter(as2s.qaList::contains)
                                                                .collect(toImmutableSet());
                                                        if (qaSet.size() > 0) {
                                                            commonQS2S.add(qs2s);
                                                            commonAS2S.add(as2s);
                                                            commonQAPairs.addAll(qaSet);
                                                        }
                                                    }));
                                                if (commonQS2S.size() > 0) {
                                                    QAStructureSurfaceForm qa = new QAStructureSurfaceForm(
                                                            commonQAPairs.iterator().next().getSentenceId(),
                                                            commonQS2S.iterator().next().question,
                                                            commonAS2S.iterator().next().answer,
                                                            commonQAPairs.stream().collect(toImmutableList()),
                                                            commonQS2S.stream().map(qs2s -> qs2s.structure).collect(toImmutableList()),
                                                            commonAS2S.stream().map(as2s -> as2s.structure).collect(toImmutableList()));
                                                    qaStructureSurfaceFormList.add(qa);

                                                    // Debug.
                                                    /*
                                                    System.err.println(
                                                            qa.getSentenceId() + "\t" + qa.getQuestion() + "\t" + qa.getAnswer() + "\t" +
                                                                    DebugPrinter.getShortListString(
                                                                            qa.getQAPairs().stream().map(QuestionAnswerPair::getParseId)
                                                                                    .distinct().collect(Collectors.toList())));
                                                    */
                                                }
                                            })
                                );
                    });
            return ImmutableList.copyOf(qaStructureSurfaceFormList);
        };
    }

    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
