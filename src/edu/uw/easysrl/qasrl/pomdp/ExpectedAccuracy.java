package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.CcgEvaluation;
import edu.uw.easysrl.qasrl.Parse;

import java.util.List;

/**
 * Created by luheng on 3/9/16.
 */
public class ExpectedAccuracy {
    public static double compute(double[] dist, List<Parse> parses) {
        int best = 0;
        for (int i = 1; i < dist.length; i++) {
            if (dist[i] > dist[best]) {
                best = i;
            }
        }
        double expAcc = .0;
        for (int i = 0; i < dist.length; i++) {
            double F1 = CcgEvaluation.evaluate(parses.get(i).dependencies, parses.get(best).dependencies).getF1();
            if (!Double.isNaN(F1)) {
                expAcc += dist[i] * F1;
            }
        }
        return expAcc;
    }
}
