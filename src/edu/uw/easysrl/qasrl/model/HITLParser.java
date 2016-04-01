package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Contains data and convenient interface for HITL experiments.
 * Created by luheng on 3/25/16.
 */
public class HITLParser {
    private int nBest = 100;
    private ParseData parseData;
    private ImmutableList<ImmutableList<String>> sentences;
    private ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences;
    private ImmutableList<Parse> goldParses;
    private Map<Integer, NBestList> nbestLists;

    // Query pruning parameters.
    private QueryPruningParameters queryPruningParameters = new QueryPruningParameters();
    public void setQueryPruningParameters(QueryPruningParameters queryPruningParameters) {
        this.queryPruningParameters = queryPruningParameters;
    }

    private HITLParsingParameters reparsingParameters = new HITLParsingParameters();
    public void setReparsingParameters(HITLParsingParameters reparsingParameters) {
        this.reparsingParameters = reparsingParameters;
    }

    private BaseCcgParser.ConstrainedCcgParser reparser;
    private ResponseSimulatorGold goldSimulator;

    /**
     * Initialize data and re-parser.
     */
    public HITLParser(int nBest) {
        this.nBest = nBest;
        parseData = ParseData.loadFromDevPool().get();
        sentences = parseData.getSentences();
        inputSentences = parseData.getSentenceInputWords();
        goldParses = parseData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));

        String preparsedFile = "parses.100best.out";
        nbestLists = NBestList.loadNBestListsFromFile(preparsedFile, nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));

        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, BaseCcgParser.rootCategories,
                reparsingParameters.maxTagsPerWord, 1 /* nbest */);
        goldSimulator = new ResponseSimulatorGold(parseData);

        // Cache results.
        nbestLists.entrySet().forEach(e -> e.getValue().cacheResults(goldParses.get(e.getKey())));
    }

    public ImmutableList<Integer> getAllSentenceIds() {
        return nbestLists.keySet().stream().sorted().collect(GuavaCollectors.toImmutableList());
    }

    public ImmutableList<String> getSentence(int sentenceId) {
        return sentences.get(sentenceId);
    }

    public ImmutableList<InputReader.InputWord> getInputSentence(int sentenceId) {
        return inputSentences.get(sentenceId);
    }

    public NBestList getNBestList(int sentenceId) {
        return nbestLists.get(sentenceId);
    }

    public Parse getGoldParse(int sentenceId) {
        return goldParses.get(sentenceId);
    }

    public Parse getParse(int sentenceId, int parseId) {
        return parseId < 0 ? getGoldParse(sentenceId) : nbestLists.get(sentenceId).getParse(parseId);
    }

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getAllQueriesForSentence(int sentenceId,
                                                                                       boolean isJeopardyStyle,
                                                                                       boolean isCheckboxStyle,
                                                                                       boolean usePronouns) {
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                ExperimentUtils.generateAllQueries(
                        sentenceId, sentences.get(sentenceId), nbestLists.get(sentenceId),
                        isJeopardyStyle,
                        isCheckboxStyle,
                        usePronouns,
                        queryPruningParameters);
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return queryList;
    }

    /**
     * Pre-set "recipes" for query generation.
     * @param sentenceId
     * @param isCheckboxStyle
     * @return
     */
    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getCoreArgumentQueriesForSentence(int sentenceId,
                                                                                                boolean isCheckboxStyle) {
        QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
        queryPruningParams.skipPPQuestions = true;
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                ExperimentUtils.generateAllQueries(
                        sentenceId, sentences.get(sentenceId), nbestLists.get(sentenceId),
                        false /* isJeopardyStyle */,
                        isCheckboxStyle,
                        false /* usePronouns */,
                        queryPruningParams);
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return queryList;
    }

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getPPAttachmentQueriesForSentence(int sentenceId) {
        QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
        queryPruningParams.skipPPQuestions = false;
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                ExperimentUtils.generateAllQueries(
                        sentenceId, sentences.get(sentenceId), nbestLists.get(sentenceId),
                        true /* isJeopardyStyle */,
                        true /* isCheckboxStyle */,
                        false /* usePronouns */,
                        queryPruningParams);
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return queryList;
    }

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getPronounPPAttachmentQueriesForSentence(int sentenceId) {
        QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
        queryPruningParams.skipPPQuestions = false;
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                ExperimentUtils.generateAllQueries(
                        sentenceId, sentences.get(sentenceId), nbestLists.get(sentenceId),
                        true /* isJeopardyStyle */,
                        true /* isCheckboxStyle */,
                        true /* usePronouns */,
                        queryPruningParams);
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return queryList;
    }

    public Parse getReparsed(int sentenceId, Set<Evidence> evidenceSet) {
        return reparser.parseWithConstraint(inputSentences.get(sentenceId), evidenceSet);
    }

    public int getRerankedParseId(int sentenceId, Set<Evidence> evidenceSet) {
        int rerankedId = 0;
        double bestScore = Double.MIN_VALUE;
        final NBestList nBestList = nbestLists.get(sentenceId);
        for (int i = 0; i < nBestList.getN(); i++) {
            final Parse parse = nBestList.getParse(i);
            final double rerankScore = parse.score + evidenceSet.stream()
                    .filter(ev -> ev.hasEvidence(parse))
                    .mapToDouble(ev -> ev.isPositive() ? ev.getConfidence() : -ev.getConfidence())
                    .sum();
            if (rerankScore > bestScore + 1e-6) {
                rerankedId = i;
                bestScore = rerankScore;
            }
        }
        return rerankedId;
    }

    public ImmutableList<Integer> getGoldOptions(final ScoredQuery<QAStructureSurfaceForm> query) {
        return goldSimulator.respondToQuery(query);
    }

    public ImmutableList<Integer> getOneBestOptions(final ScoredQuery<QAStructureSurfaceForm> query) {
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> query.getOptionToParseIds().get(i).contains(0 /* onebest parse id */))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }

    public ImmutableList<Integer> getOracleOptions(final ScoredQuery<QAStructureSurfaceForm> query) {
        final int oracleParseId = nbestLists.get(query.getSentenceId()).getOracleId();
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> query.getOptionToParseIds().get(i).contains(oracleParseId))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }

    public ImmutableList<Integer> getUserOptions(final ScoredQuery<QAStructureSurfaceForm> query,
                                                 final AlignedAnnotation annotation) {
        final int[] optionDist = QualityControl.getUserResponses(query, annotation);
        final boolean isPPQuestion =  QualityControl.queryIsPrepositional(query);
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> (!isPPQuestion && optionDist[i] >= reparsingParameters.minAgreement) ||
                             (isPPQuestion && optionDist[i] >= reparsingParameters.ppQuestionMinAgreement))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }

    public ImmutableSet<Evidence> getEvidenceSet(final ScoredQuery<QAStructureSurfaceForm> query,
                                                 final ImmutableList<Integer> options) {
        final boolean isPPQuestion =  QualityControl.queryIsPrepositional(query);
        if (isPPQuestion && reparsingParameters.skipPrepositionalQuestions) {
            return ImmutableSet.of();
        }

        final double questionTypeWeight = isPPQuestion ? reparsingParameters.ppQuestionWeight : 1.0;
        final ImmutableSet<Evidence> evidenceSet = Evidence
                .getEvidenceFromQuery(query, options, reparsingParameters.skipPronounEvidence)
                .stream()
                .collect(GuavaCollectors.toImmutableSet());

        evidenceSet.forEach(ev -> ev.setConfidence(
                questionTypeWeight *
                        (Evidence.SupertagEvidence.class.isInstance(ev) ?
                                reparsingParameters.supertagPenaltyWeight :
                                reparsingParameters.attachmentPenaltyWeight)));
        return evidenceSet;
    }

}
