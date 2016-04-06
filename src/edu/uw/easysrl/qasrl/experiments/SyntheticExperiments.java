package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.model.Constraint;
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
public class SyntheticExperiments {
    // Parameters.
    private static int nBest = 100;
    private static int maxNumSentences = 2000;

    // Shared data: nBestList, sentences, etc.
    private static HITLParser myHITLParser;
    private static ReparsingHistory myHITLHistory;

    private static boolean isCheckboxVersion = true;
    private static boolean usePronouns = true;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minPromptConfidence = 0.1;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.skipPPQuestions = false;
    }
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.attachmentPenaltyWeight = 5.0;
        reparsingParameters.supertagPenaltyWeight = 5.0;
        reparsingParameters.skipJeopardyQuestions = false;
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

        AtomicInteger sentenceCounter = new AtomicInteger(0);
        for (int sentenceId : myHITLParser.getAllSentenceIds()) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> coreQueries = myHITLParser
                    .getCoreArgumentQueriesForSentence(sentenceId, isCheckboxVersion);

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> adjunctQueries = myHITLParser
                    .getAdjunctQueriesForSentence(sentenceId, isCheckboxVersion);

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> ppQueries = myHITLParser
                    .getPPAttachmentQueriesForSentence(sentenceId);

            // Get gold results.
            /*
            coreQueries.forEach(query -> {
                ImmutableList<Integer> goldOptions = myHITLParser.getGoldOptions(query);
                ImmutableSet<Evidence> evidences = myHITLParser.getConstraints(query, goldOptions);
                myHITLHistory.addEntry(sentenceId, query, goldOptions, evidences);
                myHITLHistory.printLatestHistory();

                ImmutableList<Integer> onebestOptions = myHITLParser.getOneBestOptions(query);
                if (goldOptions.containsAll(onebestOptions) && onebestOptions.containsAll(goldOptions)) {
                    coreQueryAcc.getAndAdd(1);
                }
                numCoreQueries.getAndAdd(1);
            });
            */

            myHITLHistory.addSentence(sentenceId);
            coreQueries.forEach(query -> {
                ImmutableList<Integer> goldOptions = myHITLParser.getGoldOptions(query);
                ImmutableSet<Constraint> constraints = myHITLParser.getConstraints(query, goldOptions);
                myHITLHistory.addEntry(sentenceId, query, goldOptions, constraints);
                myHITLHistory.printLatestHistory();

                ImmutableList<Integer> onebestOptions = myHITLParser.getOneBestOptions(query);
                if (goldOptions.containsAll(onebestOptions) && onebestOptions.containsAll(goldOptions)) {
                    ppQueryAcc.getAndAdd(1);
                }
                numPPQueries.getAndAdd(1);
                //System.out.println(1.0 * ppQueryAcc.get() / numPPQueries.get());
            });

            if (sentenceCounter.addAndGet(1) >= maxNumSentences) {
                break;
            }
        }

        myHITLHistory.printSummary();
        System.out.println("Num. core queries:\t" + numCoreQueries + "\tAcc:\t" +
                            1.0 * coreQueryAcc.get() / numCoreQueries.get());
        System.out.println("Num. pp queries:\t" + numPPQueries + "\tAcc:\t" +
                            1.0 * ppQueryAcc.get() / numPPQueries.get());
    }
}
