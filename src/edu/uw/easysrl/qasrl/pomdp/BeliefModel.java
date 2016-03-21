package edu.uw.easysrl.qasrl.pomdp;

import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.Response;

import java.util.List;

/**
 * Created by luheng on 2/27/16.
 */
public class BeliefModel {
    // Distribution over parses.
    List<Parse> parses;
    public double[] belief;
    private double[] prior;
    public static final double epsilon = 1e-6;
    public static final double smoothing = 1e-3;

    public BeliefModel(List<Parse> parses) {
        this.parses = parses;
        belief = new double[parses.size()];
        prior = new double[parses.size()];
        double sum = parses.stream().mapToDouble(p -> p.score).sum();
        for (int i = 0; i < parses.size(); i++) {
            prior[i] = parses.get(i).score / sum;
        }
        resetToPrior();
    }

    public void resetToPrior() {
        for (int i = 0; i < parses.size(); i++) {
            belief[i] = prior[i];
        }
        normalize();
    }

    public void normalize() {
        double sum = .0;
        for (int i = 0; i < belief.length; i++) {
            sum += belief[i];
        }
        for (int i = 0; i < belief.length; i++) {
            belief[i] /= sum;
        }
    }

    public void update(ObservationModel obs, ScoredQuery query, Response response) {
        for (int i = 0; i < parses.size(); i++) {
            double p = obs.getObservationProbability(query, response, i, parses.get(i));
            if (Double.isNaN(p)) {
                System.err.println("NAN p");
                p = 0.0;
            } else if (Double.isInfinite(p)) {
                System.err.println("infinite p");
                p = 0.0;
            }
            belief[i] = belief[i] * p + prior[i] * smoothing;
        }
        normalize();
    }

    public int getBestState() {
        int bestState = 0;
        for (int i = 1; i < belief.length; i++) {
            if (belief[i] > belief[bestState] + epsilon) {
                bestState = i;
            }
        }
        return bestState;
    }

    public double getEntropy() {
        // Distribution is normalized.
        double entropy = .0;
        for (int i = 0; i < belief.length; i++) {
            if (belief[i] > 0) {
                entropy -= belief[i] * Math.log(belief[i]);
            }
        }
        return entropy;
    }

    public double getMargin() {
        double first = Math.max(belief[0], belief[1]),
               second = Math.min(belief[0], belief[1]);
        for (int i = 2; i < belief.length; i++) {
            if (belief[i] > first) {
                second = first;
                first = belief[i];
            } else if (belief[i] > second) {
                second = belief[i];
            }
        }
        return first - second;
    }
}
