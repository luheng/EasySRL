package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

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

    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
