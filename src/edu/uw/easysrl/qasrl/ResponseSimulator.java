package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

public interface ResponseSimulator {
    public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query);
}
