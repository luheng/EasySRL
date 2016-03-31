package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.model.Evidence;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.concurrent.atomic.AtomicInteger;

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
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipBinaryQueries = true;
    }
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.attachmentPenaltyWeight = 5.0;
        reparsingParameters.supertagPenaltyWeight = 5.0;
        reparsingParameters.skipPrepositionalQuestions = false;
    }

    public static void main(String[] args) {
        myHITLParser = new HITLParser(nBest);
        myHITLParser.setQueryPruningParameters(queryPruningParameters);
        myHITLParser.setReparsingParameters(reparsingParameters);

        myHITLHistory = new ReparsingHistory(myHITLParser);
        AtomicInteger numCoreQueries = new AtomicInteger(0),
                      numPPQueries = new AtomicInteger(0),
                      coreQueryAcc = new AtomicInteger(0),
                      ppQueryAcc = new AtomicInteger(0);

        for (int sentenceId : myHITLParser.getAllSentenceIds()) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> coreQueries = myHITLParser
                    .getCoreArgumentQueriesForSentence(sentenceId, isCheckboxVersion, usePronouns);

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> ppQueries = myHITLParser
                    .getPPAttachmentQueriesForSentence(sentenceId, usePronouns);

            // Get gold results.
            /*
            coreQueries.forEach(query -> {
                ImmutableList<Integer> goldOptions = myHITLParser.getGoldOptions(query);
                ImmutableSet<Evidence> evidences = myHITLParser.getEvidenceSet(query, goldOptions);
                myHITLHistory.addEntry(sentenceId, query, goldOptions, evidences);
                myHITLHistory.printLatestHistory();

                ImmutableList<Integer> onebestOptions = myHITLParser.getOneBestOptions(query);
                if (goldOptions.containsAll(onebestOptions) && onebestOptions.containsAll(goldOptions)) {
                    coreQueryAcc.getAndAdd(1);
                }
                numCoreQueries.getAndAdd(1);
            });
            */

            ppQueries.forEach(query -> {
                ImmutableList<Integer> goldOptions = myHITLParser.getGoldOptions(query);
                ImmutableSet<Evidence> evidences = myHITLParser.getEvidenceSet(query, goldOptions);
                myHITLHistory.addEntry(sentenceId, query, goldOptions, evidences);
                myHITLHistory.printLatestHistory();

                ImmutableList<Integer> onebestOptions = myHITLParser.getOneBestOptions(query);
                if (goldOptions.containsAll(onebestOptions) && onebestOptions.containsAll(goldOptions)) {
                    ppQueryAcc.getAndAdd(1);
                }
                numPPQueries.getAndAdd(1);
                //System.out.println(1.0 * ppQueryAcc.get() / numPPQueries.get());
            });
        }

        myHITLHistory.printSummary();
        System.out.println("Num. core queries:\t" + numCoreQueries + "\tAcc:\t" +
                            1.0 * coreQueryAcc.get() / numCoreQueries.get());
        System.out.println("Num. pp queries:\t" + numPPQueries + "\tAcc:\t" +
                            1.0 * ppQueryAcc.get() / numPPQueries.get());
    }
}
