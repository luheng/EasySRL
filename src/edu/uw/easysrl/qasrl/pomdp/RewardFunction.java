package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.evaluation.Results;

import java.util.List;
import java.util.Optional;

/**
 * Created by luheng on 2/28/16.
 */
public class RewardFunction {

    List<Parse> parses;
    List<Results> result;
    double moneyPenalty;

    public RewardFunction(List<Parse> parses, List<Results> results, double moneyPenalty) {
        this.parses = parses;
        this.result = results;
        this.moneyPenalty = moneyPenalty;
    }

    public double getReward(Optional<ScoredQuery> action, BeliefModel beliefModel, History history) {
        return action.isPresent() ? -moneyPenalty : result.get(beliefModel.getBestState()).getF1();
    }
}
