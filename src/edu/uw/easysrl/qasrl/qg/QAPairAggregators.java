package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.HashMultiset;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

import java.util.Comparator;

/**
 * Helper class where we put all of our useful QAPairAggregator instances
 * (which in general may be polymorphic over subtypes of QAPairSurfaceForm).
 *
 * This class is for LOGIC, NOT DATA.
 *
 * Created by julianmichael on 3/17/2016.
 */
public final class QAPairAggregators {

    public static QAPairAggregator<BasicQAPairSurfaceForm> aggregateByString() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(qa -> qa.getQuestion()))
            .entrySet()
            .stream()
            .flatMap(eQuestion -> eQuestion.getValue()
                     .stream()
                     .collect(groupingBy(qa -> qa.getAnswer()))
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
            .collect(groupingBy(qa -> qa.getTargetDependency()))
            .entrySet()
            .stream()
            .map(e -> {
                    ResolvedDependency targetDep = e.getKey();
                    assert e.getValue().size() > 0
                        : "list in group should always be nonempty";
                    int sentenceId = e.getValue().get(0).getSentenceId();
                    // plurality vote on question and answer
                    String pluralityQuestion = HashMultiset
                        .create(e.getValue()
                                .stream()
                                .map(IQuestionAnswerPair::getQuestion)
                                .collect(toList()))
                        .entrySet()
                        .stream()
                        .max(Comparator.comparing(Multiset.Entry::getCount))
                        .map(Multiset.Entry::getElement)
                        .get(); // there should always be one because our list is nonempty
                    String pluralityAnswer = HashMultiset
                        .create(e.getValue()
                                .stream()
                                .map(IQuestionAnswerPair::getAnswer)
                                .collect(toList()))
                        .entrySet()
                        .stream()
                        .max(Comparator.comparing(Multiset.Entry::getCount))
                        .map(Multiset.Entry::getElement)
                        .get(); // there should always be one because our list is nonempty
                    return new TargetDependencySurfaceForm(sentenceId, pluralityQuestion, pluralityAnswer,
                                                           ImmutableList.copyOf(e.getValue()),
                                                           targetDep);
                })
            .collect(toImmutableList());
    }


    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
