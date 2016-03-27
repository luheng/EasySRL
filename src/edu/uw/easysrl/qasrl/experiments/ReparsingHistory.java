package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.annotation.RecordedAnnotation;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.model.Evidence;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.util.GuavaCollectors;
import edu.uw.easysrl.util.Util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 3/26/16.
 */
public class ReparsingHistory {
    final HITLParser hitlParser;

    final List<Integer> sentenceIds;
    final Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> queries;
    final Map<Integer, List<ImmutableSet<Evidence>>> evidenceSets;
    final Map<Integer, List<ImmutableList<Integer>>> userOptions;
    final Map<Integer, List<Parse>> reparses;
    final Map<Integer, List<Integer>> rerankedParseIds;
    final Map<Integer, List<Results>> rerankingResults, reparsingResults;

    public ReparsingHistory(HITLParser parser) {
        hitlParser = parser;

        sentenceIds = new ArrayList<>();
        queries = new HashMap<>();
        userOptions = new HashMap<>();
        evidenceSets = new HashMap<>();
        reparses = new HashMap<>();
        rerankedParseIds = new HashMap<>();
        reparsingResults = new HashMap<>();
        rerankingResults = new HashMap<>();
    }

    public void addEntry(int sentenceId, final ScoredQuery<QAStructureSurfaceForm> query,
                         final ImmutableList<Integer> options, final ImmutableSet<Evidence> evidenceSet,
                         final Parse reparsed, int reranked, final Results reparsedResult,
                         final Results rerankedResult) {
        if (sentenceIds.size() == 0 || sentenceIds.get(0) != sentenceId) {
            sentenceIds.add(sentenceId);
            queries.put(sentenceId, new ArrayList<>());
            userOptions.put(sentenceId, new ArrayList<>());
            evidenceSets.put(sentenceId, new ArrayList<>());
            reparses.put(sentenceId, new ArrayList<>());
            rerankedParseIds.put(sentenceId, new ArrayList<>());
            reparsingResults.put(sentenceId, new ArrayList<>());
            rerankingResults.put(sentenceId, new ArrayList<>());
        }
        queries.get(sentenceId).add(query);
        userOptions.get(sentenceId).add(options);
        evidenceSets.get(sentenceId).add(evidenceSet);
        reparses.get(sentenceId).add(reparsed);
        rerankedParseIds.get(sentenceId).add(reranked);
        reparsingResults.get(sentenceId).add(reparsedResult);
        rerankingResults.get(sentenceId).add(rerankedResult);
    }

    private <O extends Object> O getLast(final List<O> history) {
        return history.get(history.size() - 1);
    }

    public Optional<Results> getLastReparsingResult(int sentenceId) {
        return reparsingResults.containsKey(sentenceId) ?
                Optional.of(getLast(reparsingResults.get(sentenceId))) : Optional.empty();
    }

    public Results getAvgBaseline() {
        Results avg = new Results();
        sentenceIds.stream()
                .map(sid -> hitlParser.getNBestList(sid).getResults(0))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgOracle() {
        Results avg = new Results();
        sentenceIds.stream()
                .map(sid -> hitlParser.getNBestList(sid).getResults(hitlParser.getNBestList(sid).getOracleId()))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgReranked() {
        Results avg = new Results();
        sentenceIds.stream()
                .map(sid -> getLast(rerankingResults.get(sid)))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgReparsed() {
        Results avg = new Results();
        sentenceIds.stream()
                .map(sid -> getLast(reparsingResults.get(sid)))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgBaselineOnModifiedSentences() {
        Results avg = new Results();
        getModifiedSentences().stream()
                .map(sid -> hitlParser.getNBestList(sid).getResults(0))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgRerankedOnModifiedSentences() {
        Results avg = new Results();
        getModifiedSentences().stream()
                .map(sid -> getLast(rerankingResults.get(sid)))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgReparsedOnModifiedSentences() {
        Results avg = new Results();
        getModifiedSentences().stream()
                .map(sid -> getLast(reparsingResults.get(sid)))
                .forEach(avg::add);
        return avg;
    }

    public Results getAvgOracleOnModifiedSentences() {
        Results avg = new Results();
        getModifiedSentences().stream()
                .map(sid -> hitlParser.getNBestList(sid).getResults(hitlParser.getNBestList(sid).getOracleId()))
                .forEach(avg::add);
        return avg;
    }

    public ImmutableList<Integer> getModifiedSentences() {
        return sentenceIds.stream()
                .filter(sid -> CcgEvaluation.evaluate(getLast(reparses.get(sid)).dependencies,
                        hitlParser.getNBestList(sid).getParse(0).dependencies).getF1() < 1.0 - 1e-6)
                .collect(GuavaCollectors.toImmutableList());
    }

    public int getNumWorsenedSentences() {
        return (int) sentenceIds.stream()
                .filter(sid -> getLast(reparsingResults.get(sid)).getF1() + 1e-6 <
                        hitlParser.getNBestList(sid).getResults(0).getF1())
                .count();
    }

    public int getNumImprovedSentences() {
        return (int) sentenceIds.stream()
                .filter(sid -> getLast(reparsingResults.get(sid)).getF1() - 1e-6 >
                        hitlParser.getNBestList(sid).getResults(0).getF1())
                .count();
    }

    public int getNumModifiedSentences() {
        return getModifiedSentences().size();
    }
}
