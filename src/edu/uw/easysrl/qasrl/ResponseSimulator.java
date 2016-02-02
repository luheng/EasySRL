package edu.uw.easysrl.qasrl;

import java.util.List;

/**
 * Simulates user response to a query.
 * Created by luheng on 1/17/16.
 */
public abstract class ResponseSimulator {

    /**
     * interface for answering a query
     * @param query input query
     * @return a single number from 0-(n-1), denoting the option in the query.
     *          return -1 if none of the chosenOptions are chosen.
     */
    public abstract Response answerQuestion(GroupedQuery query);
}
