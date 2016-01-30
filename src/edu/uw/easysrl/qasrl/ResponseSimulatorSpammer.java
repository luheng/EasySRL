package edu.uw.easysrl.qasrl;

import java.util.List;

/**
 * Created by luheng on 1/20/16.
 */
public class ResponseSimulatorSpammer extends ResponseSimulator {
    public Response answerQuestion(GroupedQuery query, List<String> sentence, Parse unusedSideInfo) {
        // TODO: return a random answer.
        return new Response();
    }
}
