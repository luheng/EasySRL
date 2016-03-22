package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.qasrl.query.Query;

/**
 * Simulates user response to a query.
 * Created by luheng on 1/17/16.
 */
public abstract class ResponseSimulator {

    public abstract ImmutableList<Integer> respondToQuery(Query query);
}
