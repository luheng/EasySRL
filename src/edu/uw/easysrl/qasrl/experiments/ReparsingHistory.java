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
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
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
        if (!queries.containsKey(sentenceId)) {
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

    public void addEntry(int sentenceId, final ScoredQuery<QAStructureSurfaceForm> query,
                         final ImmutableList<Integer> options, final ImmutableSet<Evidence> evidenceSet) {
        if (!queries.containsKey(sentenceId)) {
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
        final ImmutableSet<Evidence> allEvidence = getAllEvidence(sentenceId);
        final Parse reparsed = hitlParser.getReparsed(sentenceId, allEvidence);
        final int reranked = hitlParser.getRerankedParseId(sentenceId, allEvidence);
        reparses.get(sentenceId).add(reparsed);
        rerankedParseIds.get(sentenceId).add(reranked);
        reparsingResults.get(sentenceId).add(CcgEvaluation.evaluate(reparsed.dependencies,
                hitlParser.getGoldParse(sentenceId).dependencies));
        rerankingResults.get(sentenceId).add(hitlParser.getNBestList(sentenceId).getResults(reranked));
    }

    public void printLatestHistory() {
        final int sentId = getLast(sentenceIds);
        final ImmutableList<String> words = hitlParser.getSentence(sentId);
        final ScoredQuery<QAStructureSurfaceForm> query = getLast(queries.get(sentId));
        final Results reparsedF1 = getLast(reparsingResults.get(sentId));
        final Results rerankedF1 = hitlParser.getNBestList(sentId).getResults(getLast(rerankedParseIds.get(sentId)));
        final Results currentF1  = reparsingResults.get(sentId).size() < 2 ?
                hitlParser.getNBestList(sentId).getResults(0) :
                reparsingResults.get(sentId).get(reparsingResults.get(sentId).size() - 2);

        System.out.println(query.toString(words,
                'G', hitlParser.getGoldOptions(query),
                'O', hitlParser.getOracleOptions(query),
                'B', hitlParser.getOneBestOptions(query),
                'U', getLast(userOptions.get(sentId))));
        getLast(evidenceSets.get(sentId)).forEach(ev -> System.out.println(ev.toString(words)));
        String f1Impv = reparsedF1.getF1() < currentF1.getF1() - 1e-8 ? "[-]" :
                (reparsedF1.getF1() > currentF1.getF1() + 1e-8 ? "[+]" : " ");

        System.out.println(String.format("F1: %.3f%% -> %.3f%% %s", 100.0 * currentF1.getF1(),
                                                                    100.0 * reparsedF1.getF1(), f1Impv));
        System.out.println(String.format("Reranked F1: %.3f%%", 100.0 * rerankedF1.getF1()));
        System.out.println(String.format("Reparsed F1: %.3f%%", 100.0 * reparsedF1.getF1()));
        System.out.println();
    }

    public void printSummary() {
        System.out.println(
                "Processed:\t" + queries.keySet().size() + " sentences.\n" +
                "Processed:\t" + queries.values().stream().mapToInt(List::size).sum() + " queries.\n");

        System.out.println(
                "Baseline:\n" + getAvgBaseline() + "\n" +
                "Reranked:\n" + getAvgReranked() + "\n" +
                "Reparsed:\n" + getAvgReparsed() + "\n" +
                "Oracle  :\n" + getAvgOracle() + "\n");

        System.out.println(
                "Baseline-changed:\n" + getAvgBaselineOnModifiedSentences() + "\n" +
                "Reranked-changed:\n" + getAvgRerankedOnModifiedSentences() + "\n" +
                "Reparsed-changed:\n" + getAvgReparsedOnModifiedSentences() + "\n" +
                "Oracle-changed  :\n" + getAvgOracleOnModifiedSentences());

        System.out.println(
                "Num modified: " + getNumModifiedSentences() + "\n" +
                "Num improved: " + getNumImprovedSentences() + "\n" +
                "Num worsened: " + getNumWorsenedSentences() + "\n");
    }

    private <O extends Object> O getLast(final List<O> history) {
        return history.get(history.size() - 1);
    }

    public ImmutableSet<Evidence> getAllEvidence(int sentenceId) {
        return evidenceSets.containsKey(sentenceId) ?
                evidenceSets.get(sentenceId).stream()
                        .flatMap(ImmutableSet::stream)
                        .collect(GuavaCollectors.toImmutableSet()) : ImmutableSet.of();
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
