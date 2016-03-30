package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.model.Evidence;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

/**
 * Simulation experiments for PP Attachment questions (jeopardy style)
 * Created by luheng on 3/29/16.
 */
public class SimulatedExperiments {
    // Parameters.
    private static int nBest = 100;

    // Shared data: nBestList, sentences, etc.
    private static HITLParser myHITLParser;
    private static ReparsingHistory myHITLHistory;

    private static boolean isCheckboxVersion = true;
    private static boolean usePronouns = false;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minPromptConfidence = 0.1;
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipBinaryQueries = true;
    }
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.supertagPenaltyWeight = 1.0;
        reparsingParameters.skipPrepositionalQuestions = false;
    }

    public static void main(String[] args) {
        myHITLParser = new HITLParser(nBest);
        myHITLParser.setQueryPruningParameters(queryPruningParameters);
        myHITLParser.setReparsingParameters(reparsingParameters);

        myHITLHistory = new ReparsingHistory(myHITLParser);

        for (int sentenceId : myHITLParser.getAllSentenceIds()) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = myHITLParser.getCoreArgumentQueriesForSentence(
                    sentenceId, isCheckboxVersion, usePronouns);

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> ppQueries = myHITLParser.getPPAttachmentQueriesForSentence(
                    sentenceId, usePronouns);

            // Get gold results.
            queries.forEach(query -> {
                ImmutableList<Integer> goldOptions = myHITLParser.getGoldOptions(query);
                ImmutableSet<Evidence> evidences = myHITLParser.getEvidenceSet(query, goldOptions);
                myHITLHistory.addEntry(sentenceId, query, goldOptions, evidences);
                myHITLHistory.printLatestHistory();
            });
            ppQueries.forEach(query -> {
                ImmutableList<Integer> goldOptions = myHITLParser.getGoldOptions(query);
                ImmutableSet<Evidence> evidences = myHITLParser.getEvidenceSet(query, goldOptions);
                myHITLHistory.addEntry(sentenceId, query, goldOptions, evidences);
                myHITLHistory.printLatestHistory();
            });
        }

        myHITLHistory.printSummary();
    }
}
