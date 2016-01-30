package edu.uw.easysrl.qasrl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 1/29/16.
 */
public class RerankerExponentiated extends Reranker {
    Map<Integer, List<Parse>> allParses;
    Map<Integer, double[]> allScores;
    double stepSize = 0.5;

    public RerankerExponentiated(final Map<Integer, List<Parse>> allParses, double stepSize) {
        numQueries = 0;
        numEffectiveQueries = 0;
        allScores = new HashMap<>();
        allParses.forEach((sentId, parses) -> {
            double[] votes = new double[parses.size()];
            Arrays.fill(votes, 1.0 / parses.size());
            // votes[0] = 1.0;
            allScores.put(sentId, votes);
        });
        this.allParses = allParses;
        this.stepSize = stepSize;
    }

    public void rerank(final GroupedQuery query, final Response response) {
        int sentenceId = query.sentenceId;
        List<Parse> parses = allParses.get(sentenceId);
        double[] scores = allScores.get(sentenceId);
        for (int k = 0; k < parses.size(); k++) {
            for (int r : response.chosenOptions) {
                // Multiplicative update: exp(score'(t)) = exp(score(t)) exp(R(q,a,t))
                scores[k] = scores[k] * Math.exp(computeDelta(query, query.answerOptions.get(r), k));
            }
        }
        // Re-normalize
        normalize(scores);
        ++ numQueries;
    }

    public int getRerankedBest(final int sentenceId) {
        double[] votes = allScores.get(sentenceId);
        int bestK = 0;
        for (int k = 1; k < votes.length; k++) {
            if (votes[k] > votes[bestK]) {
                bestK = k;
            }
        }
        return bestK;
    }

    private double computeEntropy(int sentenceId) {
        double[] normalizedScores = allScores.get(sentenceId);
        double entropy = .0;
        for (double s : normalizedScores) {
            entropy -= s * Math.log(s) / Math.log(2.0);
        }
        return entropy;
    }

    public void printVotes() {
        allScores.keySet().stream().sorted().forEach(sentId -> {
            double[] votes = allScores.get(sentId);
            System.out.println(sentId);
            for (double v : votes) {
                System.out.print(String.format("%3f\t", v));
            }
            System.out.println();
        });
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
    private double computeDelta(final GroupedQuery query, GroupedQuery.AnswerOption option, int parseId) {
        return option.parseIds.contains(parseId) ? stepSize : -stepSize;
    }


}
