package edu.uw.easysrl.qasrl;

import java.util.*;

@Deprecated
public class RerankerNew {
    Map<Integer, List<Parse>> allParses;
    Map<Integer, double[]> allScores; // all scores, in exponential.

    public RerankerNew(Map<Integer, List<Parse>> allParses) {
        this.allParses = allParses;
        allScores = new HashMap<>();
        allParses.forEach((sentIdx, parses) -> {
            //List<Double> scores = parses.stream().map(parse -> parse.score).collect(Collectors.toList());
            double[] scores = new double[parses.size()];
            Arrays.fill(scores, 1.0 / parses.size());
            allScores.put(sentIdx, scores);
        });
    }

    public void update(final GroupedQuery query, final Response response) {
        // Update all parse scores
        int sentenceId = query.sentenceId;
        List<Parse> parses = allParses.get(sentenceId);
        double[] scores = allScores.get(sentenceId);
        for (int k = 0; k < parses.size(); k++) {
            for (int r : response.chosenOptions) {
                // Multiplicative update: exp(score'(t)) = exp(score(t)) exp(R(q,a,t))
                scores[k] = scores[k] * Math.exp(computeDelta(query, r, k));
            }
        }
        // Re-normalize
        normalize(scores);
    }

    /**
     *
     * @param query query
     * @param answerDist answer distribution. null if we don't know, treated as uniform.
     */
    // TODO: set GroupedQuery utility
    public void computeUtility(final GroupedQuery query, final List<Double> answerDist) {
        /*
        int sentenceId = query.sentenceId;
        final double[] scores = allScores.get(sentenceId);
        int numOptions = query.answerOptions.size();
        int numParses = scores.length;
        double avgEntropy = .0;
        for (int r = 0; r < numOptions; r++) {
            double[] newScores = scores.clone();
            for (int k = 0; k < numParses; k++) {
                newScores[k] = scores[k] * Math.exp(computeDelta(query, r, k));
            }
            normalize(newScores);
            double ent = entropy(newScores);
            avgEntropy += (answerDist == null) ? ent : answerDist.get(r) * ent;
        }
        //System.out.println(avgEntropy / numOptions);
        */
        //query.setUtility(avgEntropy / numOptions);
        query.setUtility(query.answerEntropy);
    }

    public int getRerankedBest(final int sentenceId) {
        double[] scores = allScores.get(sentenceId);
        int bestK = 0;
        for (int k = 1; k < scores.length; k++) {
            if (scores[k] > scores[bestK]) {
                bestK = k;
            }
        }
        return bestK;
    }

    private static double entropy(double[] normalizedScores) {
        double entropy = .0;
        for (double s : normalizedScores) {
            entropy -= s * Math.log(s) / Math.log(2.0);
        }
        return entropy;
    }

    private static void normalize(double[] scores) {
        double norm = .0;
        for (double s : scores) {
            norm += s;
        }
        for (int i = 0; i < scores.length; i++) {
            scores[i] /= norm;
        }
    }

    // R(q, a, t)
    private double computeDelta(final GroupedQuery query, final int response, int parseId) {
        if (response < 0) {
            return .0;
        }
        GroupedQuery.AnswerOption option = query.answerOptions.get(response);
        // TODO: tune step-size
        return option.parseIds.contains(parseId) ? 1.0 : -1.0;
    }
}
