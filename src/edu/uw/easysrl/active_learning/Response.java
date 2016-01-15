package edu.uw.easysrl.active_learning;

import java.util.List;

/**
 * Response from a user.
 * Created by luheng on 1/14/16.
 */
public class Response {
    List<Integer> answerIds;

    public Response(List<Integer> answerIds) {
        this.answerIds = answerIds;
    }
}
