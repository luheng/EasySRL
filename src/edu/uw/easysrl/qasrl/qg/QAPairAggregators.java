package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import static edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils.*;
import edu.uw.easysrl.syntax.grammar.Category;

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
                .collect(groupingBy(qsts -> qsts.question))
                .values().stream()
                .flatMap(qs2sEntries -> {
                    final ImmutableList<QuestionAnswerPair> questionQAList = qs2sEntries.stream()
                            .flatMap(qs -> qs.qaList.stream())
                            .collect(toImmutableList());
                    return QAPairAggregatorUtils.getAnswerSurfaceFormToMultiHeadedStructures(questionQAList)
                            .stream()
                            .collect(groupingBy(asts -> asts.answer))
                            .values().stream()
                            .map(as2sEntries -> new QAStructureSurfaceForm(
                                    questionQAList.get(0).getSentenceId(),
                                    qs2sEntries.get(0).question,
                                    as2sEntries.get(0).answer,
                                    as2sEntries.stream().flatMap(asts -> asts.qaList.stream()).collect(toImmutableList()),
                                    qs2sEntries.stream().map(qsts -> qsts.structure).distinct().collect(toImmutableList()),
                                    as2sEntries.stream().map(asts -> asts.structure).distinct().collect(toImmutableList()))
                            );
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
                .collect(groupingBy(qsts -> qsts.question))
                .values().stream()
                .flatMap(qs2sEntries -> {
                    final ImmutableList<QuestionAnswerPair> questionQAPairs = qs2sEntries.stream()
                            .flatMap(qs -> qs.qaList.stream())
                            .collect(toImmutableList());

                    return questionQAPairs.stream()
                            .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                            .values().stream()
                            .map(QAPairAggregatorUtils::getAnswerSurfaceFormToSingleHeadedStructure)
                            .collect(groupingBy(asts -> asts.answer))
                            .values().stream()
                            .map(as2sEntries -> new QAStructureSurfaceForm(
                                    questionQAPairs.get(0).getSentenceId(),
                                    qs2sEntries.get(0).question,
                                    as2sEntries.get(0).answer,
                                    as2sEntries.stream().flatMap(asts -> asts.qaList.stream()).collect(toImmutableList()),
                                    qs2sEntries.stream().map(qsts -> qsts.structure).distinct().collect(toImmutableList()),
                                    as2sEntries.stream().map(asts -> asts.structure).distinct().collect(toImmutableList())));
                })
                .collect(toImmutableList());
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
