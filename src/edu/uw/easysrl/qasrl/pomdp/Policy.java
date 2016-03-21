package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.query.Query;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.List;
import java.util.Optional;

/**
 * Created by luheng on 2/27/16.
 */
public class Policy {
    List<ScoredQuery> queryList;
    History history;
    BeliefModel beliefModel;
    int horizon;
    int nextQueryId;

    public Policy(List<ScoredQuery> queryList, History history, BeliefModel beliefModel, int horizon) {
        this.queryList = queryList;
        this.history = history;
        this.beliefModel = beliefModel;
        this.horizon = horizon;
        this.nextQueryId = 0;
    }

    // Play next.
    public Optional<ScoredQuery> getAction() {
        if (history.length() >= horizon - 1 || nextQueryId >= queryList.size()) {
            // Submit.
            return Optional.empty();
        }
        ScoredQuery action = queryList.get(nextQueryId);
        nextQueryId ++;
        return Optional.of(action);
    }
}
