package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.Response;

/**
 * Created by luheng on 2/27/16.
 */
public class ObservationModel {

    public ObservationModel() {

    }

    public double getObservationProbability(GroupedQuery query, Response response, int parseId, Parse parse) {
        // Noiseless.
        return query.getAnswerOptions().get(response.chosenOptions.get(0)).getParseIds().contains(parseId) ? 1.0 : 0.0;
    }
}
