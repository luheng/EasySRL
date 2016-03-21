package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.Response;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * POMDP History
 * Created by luheng on 2/27/16.
 */
public class History {
    List<ScoredQuery> queries;
    List<Response> responses;
    Set<Integer> playedQueryIds;

    public History() {
        queries = new ArrayList<>();
        responses = new ArrayList<>();
        playedQueryIds = new HashSet<>();
    }

    public void addAction(ScoredQuery query) {
        queries.add(query);
        playedQueryIds.add(query.getQueryId());
    }

    public void addObservation(Response response) {
        responses.add(response);
    }

    public ScoredQuery getLastAction() {
        return queries.get(length() - 1);
    }

    public int length() {
        return queries.size();
    }
}
