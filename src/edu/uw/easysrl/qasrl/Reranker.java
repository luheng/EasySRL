package edu.uw.easysrl.qasrl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 1/21/16.
 */
public class Reranker {
    int numQueries;
    int numEffectiveQueries;
    Map<Integer, List<Parse>> allParses;
    Map<Integer, double[]> allVotes;

    public Reranker(final Map<Integer, List<Parse>> allParses) {
        numQueries = 0;
        numEffectiveQueries = 0;
        allVotes = new HashMap<>();
        allParses.forEach((sentId, parses) -> {
            double[] votes = new double[parses.size()];
            Arrays.fill(votes, 0.0);
            allVotes.put(sentId, votes);
        });
        this.allParses = allParses;
    }

    public void rerank(final GroupedQuery query, final int response) {
        int sentenceId = query.sentenceId;
        int numParses = query.totalNumParses;
        int minK = allParses.get(sentenceId).size();
        double[] votes = allVotes.get(sentenceId);
        if (0 <= response && response < query.answerOptions.size()) {
            for (int k : query.answerOptions.get(response).parseIds) {
                if (0 <= k && k < numParses) {
                    votes[k] += 1.0;
                    if (k < minK) {
                        minK = k;
                    }
                }
            }
        }
        ++ numQueries;
        if (minK > 0 && minK < numParses) {
            ++ numEffectiveQueries;
        }
    }

    public void rerank(final List<GroupedQuery> queries, final List<Integer> responses) {
        for (int i = 0; i < queries.size(); i++) {
            GroupedQuery query = queries.get(i);
            int response = responses.get(i);
            int sentenceId = query.sentenceId;
            int numParses = query.totalNumParses;
            int minK = allParses.get(sentenceId).size();
            double[] votes = allVotes.get(sentenceId);
            if (0 <= response && response < query.answerOptions.size()) {
                for (int k : query.answerOptions.get(response).parseIds) {
                    if (0 <= k && k < numParses) {
                        votes[k] += 1.0;
                        if (k < minK) {
                            minK = k;
                        }
                    }
                }
            }
            ++ numQueries;
            if (minK > 0 && minK < numParses) {
                ++ numEffectiveQueries;
            }
        }
    }

    public int getRerankedBest(final int sentenceId) {
        double[] votes = allVotes.get(sentenceId);
        int bestK = 0;
        for (int k = 1; k < votes.length; k++) {
            if (votes[k] > votes[bestK]) {
                bestK = k;
            }
        }
        return bestK;
    }

    public int getRerankedBest(final List<Parse> parses, final List<GroupedQuery> queries,
                               final List<Integer> responses) {
        double[] votes = rerank(parses, queries, responses);
        int bestK = 0;
        for (int k = 1; k < parses.size(); k++) {
            if (votes[k] > votes[bestK]) {
                bestK = k;
            }
        }
        return bestK;
    }

    public double[] rerank(final List<Parse> parses, final List<GroupedQuery> queries, final List<Integer> responses) {
        double[] votes = parses.stream().mapToDouble(p->0.0).toArray();
        for (int i = 0; i < queries.size(); i++) {
            GroupedQuery query = queries.get(i);
            int response = responses.get(i);
            int minK = parses.size();
            if (0 <= response && response < query.answerOptions.size()) {
                for (int k : query.answerOptions.get(response).parseIds) {
                    if (0 <= k && k < parses.size()) {
                        votes[k] += 1.0;
                        if (k < minK) {
                            minK = k;
                        }
                    }
                }
            }
            ++ numQueries;
            if (minK > 0 && minK < parses.size()) {
                ++ numEffectiveQueries;
            }
        }
        return votes;
    }
}
