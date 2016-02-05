package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luheng on 2/5/16.
 */
public class ActiveLearningHistory {

    List<GroupedQuery> queries;
    List<Response> responses, goldResponses;
    List<Results> results;

    public ActiveLearningHistory() {
        queries = new ArrayList<>();
        responses = new ArrayList<>();
        goldResponses = new ArrayList<>();
        results = new ArrayList<>();
    }

    public void add(final GroupedQuery query, final Response response, final Response goldResponse,
                    final Results result) {
        queries.add(query);
        responses.add(response);
        goldResponses.add(goldResponse);
        results.add(result);
    }

    public GroupedQuery getQuery(int id) {
        return queries.get(id);
    }

    public Response getResponse(int id) {
        return responses.get(id);
    }

    public Response getGoldResponse(int id) {
        return goldResponses.get(id);
    }

    public Results getResult(int id) {
        return results.get(id);
    }

    public int size() {
        return queries.size();
    }
}
