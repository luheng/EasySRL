package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Response from a user.
 * Created by luheng on 1/14/16.
 */
@Deprecated
public class Response {
    ImmutableList<Integer> answerIds;

    public Response(final List<Integer> answerIds) {
        // TODO: make sure the answer ids are sorted.
        this.answerIds = ImmutableList.copyOf(answerIds);
    }
}
