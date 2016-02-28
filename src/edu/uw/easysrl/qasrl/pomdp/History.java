package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * POMDP History
 * Created by luheng on 2/27/16.
 */
public class History {
    List<GroupedQuery> queries;
    List<Response> responses;

    public History() {
        queries = new ArrayList<>();
        responses = new ArrayList<>();
    }

    public void add(GroupedQuery query, Response response) {
        queries.add(query);
        responses.add(response);
    }

    public int length() {
        return queries.size();
    }
}
