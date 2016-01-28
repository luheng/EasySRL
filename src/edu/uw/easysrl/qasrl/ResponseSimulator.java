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
     * @param sentence the raw sentence
     * @param goldParse gold ccg parse of the sentence, for simulating correct response
     * @return a single number from 0-(n-1), denoting the option in the query.
     *          return -1 if none of the options are chosen.
     */
    public abstract int answerQuestion(GroupedQuery query, List<String> sentence, Parse goldParse);
}
