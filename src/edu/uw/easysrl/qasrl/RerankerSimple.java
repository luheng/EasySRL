package edu.uw.easysrl.qasrl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by luheng on 1/21/16.
 */
public class RerankerSimple extends Reranker {
    Map<Integer, List<Parse>> allParses;
    Map<Integer, double[]> allVotes;
    boolean hasPriorVotes;

    public RerankerSimple(final Map<Integer, List<Parse>> allParses, final List<GroupedQuery> allQueries) {
        numQueries = 0;
        numEffectiveQueries = 0;
        allVotes = new HashMap<>();
        allParses.forEach((sentId, parses) -> {
            double[] votes = new double[parses.size()];
            Arrays.fill(votes, 0.0);
            allVotes.put(sentId, votes);
        });
        this.allParses = allParses;
        if (allQueries == null) {
            hasPriorVotes = false;
            return;
        }
        allQueries.forEach(gq -> {
            double[] votes = allVotes.get(gq.sentenceId);
            gq.answerOptions.forEach(ao -> ao.parseIds.forEach(pid -> votes[pid] += ao.probability));
        });
        hasPriorVotes = true;
    }

    public void rerank(final GroupedQuery query, final Response response) {
        int sentenceId = query.sentenceId;
        int numParses = query.totalNumParses;
        int minK = allParses.get(sentenceId).size();
        double[] votes = allVotes.get(sentenceId);
        for (int r : response.chosenOptions) {
            // Subtract pre-computed votes;
            if (hasPriorVotes) {
                query.answerOptions.forEach(ao -> ao.parseIds.forEach(pid -> votes[pid] -= ao.probability));
            }
            // Add response votes.
            for (int k : query.answerOptions.get(r).parseIds) {
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

    public void printVotes() {
        allVotes.keySet().stream().sorted().forEach(sentId -> {
            double[] votes = allVotes.get(sentId);
            System.out.println(sentId);
            for (double v : votes) {
                System.out.print(String.format("%3f\t", v));
            }
            System.out.println();
        });
    }

}
