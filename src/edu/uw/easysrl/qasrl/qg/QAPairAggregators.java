package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.*;
import edu.stanford.nlp.util.Triple;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TObjectDoubleHashMap;

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
            .collect(groupingBy(IQuestionAnswerPair::getQuestion))
            .entrySet()
            .stream()
            .flatMap(eQuestion -> eQuestion.getValue()
                    .stream()
                    .collect(groupingBy(IQuestionAnswerPair::getAnswer))
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
            .collect(groupingBy(IQuestionAnswerPair::getTargetDependency))
            .entrySet()
            .stream()
            .map(e -> {
                final ResolvedDependency targetDep = e.getKey();
                final Collection<IQuestionAnswerPair> qaList = e.getValue();
                assert qaList.size() > 0
                        : "list in group should always be nonempty";
                int sentenceId = e.getValue().get(0).getSentenceId();
                // plurality vote on question and answer
                String pluralityQuestion = HashMultiset
                        .create(qaList.stream()
                                .map(IQuestionAnswerPair::getQuestion)
                                .collect(toList()))
                        .entrySet()
                        .stream()
                        .max(Comparator.comparing(Multiset.Entry::getCount))
                        .map(Multiset.Entry::getElement)
                        .get(); // there should always be one because our list is nonempty
                String pluralityAnswer = HashMultiset
                        .create(qaList.stream()
                                .map(IQuestionAnswerPair::getAnswer)
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
     * The input should be all the question-answer pairs given a sentence and its n-best list.
     * This is too crazy...
     * @return
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForSingleChoiceQA() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(qa -> qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber()))
                .entrySet()
                .stream()
                .flatMap(questionStructureGroup -> {
                    final List<IQuestionAnswerPair> qaList1 = questionStructureGroup.getValue();
                    assert qaList1.size() > 0 : "list in group should always be nonempty";

                    final String[] qkey = questionStructureGroup.getKey().split("\\t");
                    int predId = Integer.valueOf(qkey[0]);
                    final Category category = Category.valueOf(qkey[1]);
                    final int argNum = Integer.valueOf(qkey[2]);
                    final int sentenceId = qaList1.get(0).getSentenceId();
                    QuestionStructure questionStructure = new QuestionStructure(predId, category, argNum,
                            qaList1.get(0).getQuestionDependencies());

                    // Get best question surface form.
                    String bestQuestionString = qaList1.stream()
                            .collect(groupingBy(IQuestionAnswerPair::getQuestion))
                            .entrySet()
                            .stream()
                            .map(questionStringGroup -> {
                                double score = questionStringGroup.getValue()
                                        .stream()
                                        .mapToDouble(qa -> qa.getParse().score).sum();
                                return new AbstractMap.SimpleEntry<>(questionStringGroup.getKey(), score);
                            })
                            .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                            .get().getKey();

                    // Get answer indices list.
                    Table<ImmutableList<Integer>, String, Double> argListToAnswerToScore = HashBasedTable.create();
                    qaList1.stream()
                            .collect(groupingBy(IQuestionAnswerPair::getParseId))
                            .entrySet()
                            .stream()
                            .forEach(parseIdGroup -> {
                                final List<IQuestionAnswerPair> qaList2 = parseIdGroup.getValue();
                                ImmutableMap<Integer, String> argIdToSpan = qaList2.stream()
                                        .collect(toImmutableMap(IQuestionAnswerPair::getArgumentIndex,
                                                IQuestionAnswerPair::getAnswer));
                                ImmutableList<Integer> argList = qaList2.stream()
                                        .map(IQuestionAnswerPair::getArgumentIndex)
                                        .distinct()
                                        .sorted()
                                        .collect(toImmutableList());
                                String answerString = argList.stream()
                                        .map(argIdToSpan::get)
                                        .collect(Collectors.joining(QAPairAggregatorUtils.answerDelimiter));
                                double score = qaList2.get(0).getParse().score;
                                if (argListToAnswerToScore.contains(argList, answerString)) {
                                    double s = argListToAnswerToScore.get(argList, answerString);
                                    argListToAnswerToScore.put(argList, answerString, s + score);
                                } else {
                                    argListToAnswerToScore.put(argList, answerString, score);
                                }
                            });

                    // Get best answers.
                    return argListToAnswerToScore.rowKeySet().stream().map(argList -> {
                        String bestAnswerString = argListToAnswerToScore.row(argList).entrySet().stream()
                                .max(Comparator.comparing(Entry::getValue)).get().getKey();
                        return new QAStructureSurfaceForm(sentenceId,
                                bestQuestionString,
                                bestAnswerString,
                                ImmutableList.copyOf(qaList1),
                                ImmutableList.of(questionStructure),
                                ImmutableList.of(new AnswerStructure(argList)));
                    });
                })
                .collect(toImmutableList());
    }

    /**
     * The input should be all the question-answer pairs given a sentence and its n-best list.
     * @return
     */
    public static QAPairAggregator<QAStructureSurfaceForm> aggregateForMultipleChoiceQA() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(qa -> qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber()))
                .entrySet()
                .stream()
                .flatMap(questionStructureGroup -> {
                    final List<IQuestionAnswerPair> qaList1 = questionStructureGroup.getValue();
                    assert qaList1.size() > 0 : "list in group should always be nonempty";

                    final String[] qkey = questionStructureGroup.getKey().split("\\t");
                    int predId = Integer.valueOf(qkey[0]);
                    final Category category = Category.valueOf(qkey[1]);
                    final int argNum = Integer.valueOf(qkey[2]);
                    final int sentenceId = qaList1.get(0).getSentenceId();
                    QuestionStructure questionStructure = new QuestionStructure(predId, category, argNum,
                            qaList1.get(0).getQuestionDependencies());

                    // Get best question surface form.
                    String bestQuestionString = qaList1.stream()
                            .collect(groupingBy(IQuestionAnswerPair::getQuestion))
                            .entrySet()
                            .stream()
                            .map(questionStringGroup -> {
                                double score = questionStringGroup.getValue().stream()
                                        .mapToDouble(qa -> qa.getParse().score).sum();
                                return new AbstractMap.SimpleEntry<>(questionStringGroup.getKey(), score);
                            })
                            .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                            .get().getKey();

                    return qaList1.stream()
                            .collect(groupingBy(IQuestionAnswerPair::getArgumentIndex))
                            .entrySet()
                            .stream()
                            .map(argIdGroup -> {
                                final List<IQuestionAnswerPair> qaList2 = argIdGroup.getValue();
                                String bestAnswerString = qaList2.stream()
                                        .collect(groupingBy(IQuestionAnswerPair::getAnswer))
                                        .entrySet()
                                        .stream()
                                        .map(answerStringGroup -> {
                                            double score = answerStringGroup.getValue().stream()
                                                    .mapToDouble(qa -> qa.getParse().score).sum();
                                            return new AbstractMap.SimpleEntry<>(answerStringGroup.getKey(), score);
                                        })
                                        .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                                        .get().getKey();

                                return new QAStructureSurfaceForm(sentenceId,
                                            bestQuestionString,
                                            bestAnswerString,
                                            ImmutableList.copyOf(qaList1),
                                            ImmutableList.of(questionStructure),
                                            ImmutableList.of(new AnswerStructure(ImmutableList.of(argIdGroup.getKey()))));
                            });
                })
                .collect(toImmutableList());
    }



    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
