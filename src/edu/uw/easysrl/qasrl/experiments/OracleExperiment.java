package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.analysis.DependencyProfiler;
import edu.uw.easysrl.qasrl.analysis.ProfiledDependency;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.util.Util;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulation experiments with oracle parsing.
 * Created by luheng on 3/29/16.
 */
public class OracleExperiment {
    // Parameters.
    private static int nBest = 100;
    private static int maxNumSentences = 100;

    // Shared data: nBestList, sentences, etc.
    private static HITLParser myHITLParser;
    private static ReparsingHistory myHITLHistory;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minPromptConfidence = 0.1;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.skipBinaryQueries = true;
        queryPruningParameters.skipPPQuestions = false;
    }
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.attachmentPenaltyWeight = 10.0;
        reparsingParameters.supertagPenaltyWeight = 10.0;
        reparsingParameters.skipJeopardyQuestions = false;
    }

    public static void main(String[] args) {
        myHITLParser = new HITLParser(nBest);
        myHITLParser.setQueryPruningParameters(queryPruningParameters);
        myHITLParser.setReparsingParameters(reparsingParameters);

        myHITLHistory = new ReparsingHistory(myHITLParser);
        AtomicInteger numCoreQueries = new AtomicInteger(0), coreQueryAcc = new AtomicInteger(0);
        AtomicInteger sentenceCounter = new AtomicInteger(0);

        // TODO: average number of dependencies.
        for (int sentenceId : myHITLParser.getAllSentenceIds()) {
            final ImmutableList<String> sentence = myHITLParser.getSentence(sentenceId);
            final NBestList nBestList = myHITLParser.getNBestList(sentenceId);
            final Set<ProfiledDependency> allDeps = DependencyProfiler.getAllDependencies(nBestList);

            System.out.println(TextGenerationHelper.renderString(sentence));
            System.out.println(allDeps.size());
            allDeps.forEach(dep -> {
                System.out.println(dep.toString(sentence));
            });
            System.out.println();

            if (sentenceCounter.addAndGet(1) >= maxNumSentences) {
                break;
            }
        }

        myHITLHistory.printSummary();
        System.out.println("Num. core queries:\t" + numCoreQueries + "\tAcc:\t" + 1.0 * coreQueryAcc.get() / numCoreQueries.get());
    }
}
