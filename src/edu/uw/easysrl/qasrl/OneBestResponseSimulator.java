package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.stream.IntStream;

public class OneBestResponseSimulator implements ResponseSimulator {
    public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query) {
        return IntStream.range(0, query.getOptions().size())
            .filter(i -> query.getOptionToParseIds().get(i).contains(0 /* one-best parse id */))
            .boxed()
            .collect(toImmutableList());
    }
}
