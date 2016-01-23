package edu.uw.easysrl.qasrl;

import java.util.List;

/**
 * Created by luheng on 1/21/16.
 */
public class Reranker {
    int numQueries;
    int numEffectiveQueries;

    public Reranker() {
        numQueries = 0;
        numEffectiveQueries = 0;
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
