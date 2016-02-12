package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.syntax.evaluation.Results;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.*;

/**
 * Created by luheng on 2/5/16.
 */
public class ActiveLearningHistory {
    final public List<GroupedQuery> queries;
    final public List<Response> responses, goldResponses;
    final public List<Results> corpusResults, sentenceResults;
    final public List<Integer> sentenceIds;
    public int numCorrectAnswers;

    // Sentence history.
    final public Map<Integer, Results> oracleResults, oneBestResults, rerankedResults;
    final public TIntIntHashMap numQueriesPerSentence, numCorrectPerSentence;

    public ActiveLearningHistory() {
        queries = new ArrayList<>();
        responses = new ArrayList<>();
        goldResponses = new ArrayList<>();
        corpusResults = new ArrayList<>();
        sentenceIds = new ArrayList<>();
        sentenceResults = new ArrayList<>();
        numCorrectAnswers = 0;

        oracleResults = new HashMap<>();
        oneBestResults = new HashMap<>();
        rerankedResults = new HashMap<>();
        numQueriesPerSentence = new TIntIntHashMap();
        numCorrectPerSentence = new TIntIntHashMap();
    }

    public void add(final GroupedQuery query, final Response response, final Response goldResponse,
                    final Results corpusRerank, final Results sentenceRerank, final Results oneBest,
                    final Results oracle) {
        int sentenceId = query.sentenceId;
        queries.add(query);
        responses.add(response);
        goldResponses.add(goldResponse);
        corpusResults.add(corpusRerank);
        sentenceResults.add(sentenceRerank);

        if (!numQueriesPerSentence.containsKey(sentenceId)) {
            sentenceIds.add(sentenceId);
            oneBestResults.put(sentenceId, oneBest);
            oracleResults.put(sentenceId, oracle);
        }
        rerankedResults.put(sentenceId, sentenceRerank);
        numQueriesPerSentence.adjustOrPutValue(sentenceId, 1, 1);
        if (response.chosenOptions.get(0).equals(goldResponse.chosenOptions.get(0))) {
            numCorrectAnswers ++;
            numCorrectPerSentence.adjustOrPutValue(sentenceId, 1, 1);
        }
    }

    public void addSkipSentence(int sentenceId, final Results oneBest, final Results oracle) {
        if (!numQueriesPerSentence.containsKey(sentenceId)) {
            sentenceIds.add(sentenceId);
            numQueriesPerSentence.put(sentenceId, 0);
            numCorrectPerSentence.put(sentenceId, 0);
            oracleResults.put(sentenceId, oracle);
            oneBestResults.put(sentenceId, oneBest);
            rerankedResults.put(sentenceId, oneBest);
        }
    }

    public GroupedQuery getQuery(int id) {
        return queries.get(id);
    }

    public Response getResponse(int id) {
        return responses.get(id);
    }

    public Response getGoldResponse(int id) {
        return goldResponses.get(id);
    }

    public Results getCorpusResult(int id) {
        return corpusResults.get(id);
    }

    public int size() {
        return queries.size();
    }

    public String printLatestHistory() {
        if (size() == 0) {
            return "";
        }
        final int last = size() - 1;
        String str = String.format("ITER=%d\n", last);
        str += queries.get(last).getDebuggingInfo(responses.get(last), goldResponses.get(last)) + "\n";
        str += String.format("USER_ACC:\t%.2f%%\n", 100.0 * numCorrectAnswers / size());
        int sentenceId = queries.get(last).sentenceId;
        str += "[ReRank-sentence]:\t"  + sentenceResults.get(last).toString().replace("\n", "\t") + "\n";
        str += "[OneBest-sentence]:\t" + oneBestResults.get(sentenceId).toString().replace("\n", "\t") + "\n";
        str += "[Oracle-sentence]:\t"  + oracleResults.get(sentenceId).toString().replace("\n", "\t") + "\n";

        Results rerankAvg = new Results(), oneBestAvg = new Results(), oracleAvg = new Results();
        rerankedResults.values().forEach(rerankAvg::add);
        oracleResults.values().forEach(oracleAvg::add);
        oneBestResults.values().forEach(oneBestAvg::add);
        str += "[ReRank-sentence-avg]:\t" + rerankAvg.toString().replace("\n", "\t") + "\n";
        str += "[OneBest-sentence-avg]:\t" + oneBestAvg.toString().replace("\n", "\t") + "\n";
        str += "[Oracle-sentence-avg]:\t" + oracleAvg.toString().replace("\n", "\t") + "\n";
        str += "[ReRank-corpus]:\t"    + corpusResults.get(last).toString().replace("\n", "\t") + "\n";
        return str;
    }
}
