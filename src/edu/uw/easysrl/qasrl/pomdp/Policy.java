package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.GroupedQuery;

import java.util.List;
import java.util.Optional;

/**
 * Created by luheng on 2/27/16.
 */
public class Policy {
    List<GroupedQuery> queryList;
    History history;
    BeliefModel beliefModel;
    int nextQueryId;

    public Policy(List<GroupedQuery> queryList, History history, BeliefModel beliefModel) {
        this.queryList = queryList;
        this.history = history;
        this.beliefModel = beliefModel;
        this.nextQueryId = 0;
    }

    // Play next.
    public Optional<GroupedQuery> getAction() {
        if (history.length() > 10 || nextQueryId >= queryList.size()) {
            // Submit.
            return Optional.empty();
        }
        GroupedQuery action = queryList.get(nextQueryId);
        nextQueryId ++;
        return Optional.of(action);
    }
}
