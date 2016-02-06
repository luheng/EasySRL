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
    int numCorrectAnswers;

    public ActiveLearningHistory() {
        queries = new ArrayList<>();
        responses = new ArrayList<>();
        goldResponses = new ArrayList<>();
        results = new ArrayList<>();
        numCorrectAnswers = 0;
    }

    public void add(final GroupedQuery query, final Response response, final Response goldResponse,
                    final Results result) {
        queries.add(query);
        responses.add(response);
        goldResponses.add(goldResponse);
        results.add(result);
        if (response.chosenOptions.get(0).equals(goldResponse.chosenOptions.get(0))) {
            numCorrectAnswers ++;
        }
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

    public String printLatestHistory() {
        if (size() == 0) {
            return "";
        }
        final int last = size() - 1;
        String str = String.format("ITER=%d\n", last);
        str += queries.get(last).getDebuggingInfo(responses.get(last), goldResponses.get(last)) + "\n";
        str += String.format("USER_ACC=%.2f%%\n", 100.0 * numCorrectAnswers / size());
        str += "[ReRanked]:\n" + results.get(last).toString() + "\n";
        return str;
    }
}
