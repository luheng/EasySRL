package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.classification.DependencyInstanceHelper;
import edu.uw.easysrl.qasrl.classification.DependencyInstanceType;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
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
    public QueryPruningParameters getQueryPruningParameters() {
        return queryPruningParameters;
    }

    private HITLParsingParameters reparsingParameters = new HITLParsingParameters();
    public void setReparsingParameters(HITLParsingParameters reparsingParameters) {
        this.reparsingParameters = reparsingParameters;
    }
    public HITLParsingParameters getReparsingParameters() {
        return reparsingParameters;
    }

    private BaseCcgParser.ConstrainedCcgParser reparser;
    private ResponseSimulatorGold goldSimulator;

    /**
     * Initialize data and re-parser.
     */
    public HITLParser(int nBest) {
        this(nBest, false /* load from test */);
    }

    public HITLParser(int nBest, boolean getTestSet) {
        this.nBest = nBest;
        parseData = getTestSet ?
                ParseDataLoader.loadFromTestPool(false /* include gold */).get() :
                ParseDataLoader.loadFromDevPool().get();
        sentences = parseData.getSentences();
        inputSentences = parseData.getSentenceInputWords();
        goldParses = parseData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));

        String preparsedFile = getTestSet ? "parses.tagged.test.100best.out" : "parses.tagged.dev.100best.out";
        nbestLists = NBestList.loadNBestListsFromFile(preparsedFile, nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences from %s.",
                nBest, nbestLists.size(), preparsedFile));

        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, 1 /* nbest */);
        reparser.cacheSupertags(parseData);
        goldSimulator = new ResponseSimulatorGold(parseData);

        // Cache results.
        nbestLists.entrySet().forEach(e -> e.getValue().cacheResults(goldParses.get(e.getKey())));

        // Print nbest stats
        /* Print stats */
        System.out.println(String.format("Read nBest lists for %d sentences", nbestLists.size()));
        System.out.println(String.format("Average-N:\t%.3f", nbestLists.values().stream()
                .mapToDouble(NBestList::getN).sum() / nbestLists.size()));
        Results baseline = new Results(), oracle = new Results();
        nbestLists.values().forEach(nb -> {
            baseline.add(nb.getResults(0));
            oracle.add(nb.getResults(nb.getOracleId()));
        });
        System.out.println(String.format("Baseline F1:\t%.5f%%\tOracle F1:\t%.5f%%", 100.0 * baseline.getF1(),
                100.0 * oracle.getF1()));
    }

    public HITLParser(ParseData parseData, Map<Integer, NBestList> nbestLists) {
        this.parseData = parseData;
        this.nbestLists = nbestLists;
        sentences = parseData.getSentences();
        inputSentences = parseData.getSentenceInputWords();
        goldParses = parseData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));

        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, 1 /* nbest */);
        reparser.cacheSupertags(parseData);
        goldSimulator = new ResponseSimulatorGold(parseData);

        // Cache results, is gold parses are given.
        nbestLists.entrySet().forEach(e -> e.getValue().cacheResults(goldParses.get(e.getKey())));

        // Print nbest stats
        /* Print stats */
        System.out.println(String.format("Read nBest lists for %d sentences", nbestLists.size()));
        System.out.println(String.format("Average-N:\t%.3f", nbestLists.values().stream()
                .mapToDouble(NBestList::getN).sum() / nbestLists.size()));
        Results baseline = new Results(), oracle = new Results();
        nbestLists.values().forEach(nb -> {
            baseline.add(nb.getResults(0));
            oracle.add(nb.getResults(nb.getOracleId()));
        });
        System.out.println(String.format("Baseline F1:\t%.5f%%\tOracle F1:\t%.5f%%", 100.0 * baseline.getF1(),
                100.0 * oracle.getF1()));
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

    public ParseData getParseData() { return parseData; }

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

    /****************************** Pre-set "recipes" for query generation. *************************/

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getCleftedQuestionsForSentence(int sentenceId) {
        QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
        queryPruningParams.skipPPQuestions = false;
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                ExperimentUtils.generateCleftedQueries(
                        sentenceId, sentences.get(sentenceId), nbestLists.get(sentenceId),
                        true /* usePronouns */,
                        queryPruningParams)
                        .stream()
                        .filter(query -> !query.getQAPairSurfaceForms().get(0).getAnswerStructures().get(0).headIsVP)
                        .collect(GuavaCollectors.toImmutableList());
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return queryList;
    }

    @Deprecated
    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getPronounCoreArgQueriesForSentence(int sentenceId) {
        final QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
       queryPruningParams.skipPPQuestions = true;
        final ImmutableList<String> sentence = sentences.get(sentenceId);

        if(nbestLists.get(sentenceId) == null) {
            return ImmutableList.of();
        }

        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> copulaQueries = ExperimentUtils.generateAllQueries(
                    sentenceId, sentence, nbestLists.get(sentenceId),
                    false,  // isJeopardyStyle
                    true,   // isCheckboxStyle
                    false,  // usePronouns
                    queryPruningParams)
                .stream().filter(query -> {
                    final int predicateId = query.getPredicateId().getAsInt();
                    return VerbHelper.isCopulaVerb(sentence.get(predicateId));
                }).collect(GuavaCollectors.toImmutableList());

        List<ScoredQuery<QAStructureSurfaceForm>> queryList = ExperimentUtils.generateAllQueries(
                        sentenceId, sentence, nbestLists.get(sentenceId),
                        false, // isJeopardyStyle,
                        true,  // isCheckboxStyle,
                        true,  // usePronouns,
                        queryPruningParams)
                .stream().filter(query -> {
                    final int predicateId = query.getPredicateId().getAsInt();
                    return !VerbHelper.isCopulaVerb(sentence.get(predicateId));
                }).collect(Collectors.toList());

        queryList.addAll(copulaQueries);

        /*
        if(nbestLists.get(sentenceId) == null) {
            return ImmutableList.of();
        }
        List<ScoredQuery<QAStructureSurfaceForm>> queryList = QuestionGenerationPipeline.coreArgQGPipeline
                .generateAllQueries(sentenceId, nbestLists.get(sentenceId));
                */

        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return ImmutableList.copyOf(queryList);
    }

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getNewCoreArgQueriesForSentence(int sentenceId) {
        if(nbestLists.get(sentenceId) == null) {
            return ImmutableList.of();
        }
        // Without verb-adjunct questions yet.
        List<ScoredQuery<QAStructureSurfaceForm>> queryList = QuestionGenerationPipeline.coreArgQGPipeline
                .setQueryPruningParameters(queryPruningParameters)
                .generateAllQueries(sentenceId, nbestLists.get(sentenceId));
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return ImmutableList.copyOf(queryList);
    }

    @Deprecated
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

    /**
     * Old non-jeopardy pp queries.
     * @param sentenceId
     * @return
     */
    @Deprecated
    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getAdjunctQueriesForSentence(int sentenceId,
                                                                                           boolean isCheckboxStyle) {
        QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
        queryPruningParams.skipPPQuestions = false;
        queryPruningParams.skipBinaryQueries = false;
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                ExperimentUtils.generateAllQueries(
                        sentenceId, sentences.get(sentenceId), nbestLists.get(sentenceId),
                        false /* isJeopardyStyle */,
                        isCheckboxStyle,
                        false /* usePronouns */,
                        queryPruningParams)
                .stream().filter(AnnotationUtils::queryIsPrepositional)
                .collect(GuavaCollectors.toImmutableList());
        // Assign query ids.
        IntStream.range(0, queryList.size())
                .forEach(i -> queryList.get(i).setQueryId(i));
        return queryList;
    }

    @Deprecated
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

    @Deprecated
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

    public Parse getReparsed(int sentenceId, Set<Constraint> constraintSet) {
        if (constraintSet == null || constraintSet.isEmpty()) {
            return nbestLists.get(sentenceId).getParse(0);
        }
        final Parse reparsed = reparser.parseWithConstraint(sentenceId, inputSentences.get(sentenceId), constraintSet);
        if (reparsed == null) {
            System.err.println(String.format("Unable to parse sentence %d with constraints: %s", sentenceId,
                    constraintSet.stream()
                            .map(c -> c.toString(getSentence(sentenceId)))
                            .collect(Collectors.joining("\n"))));
            return nbestLists.get(sentenceId).getParse(0);
        }
        return reparsed;
    }

    public int getRerankedParseId(int sentenceId, Set<Constraint> constraintSet) {
        int rerankedId = 0;
        double bestScore = Double.MIN_VALUE;
        final NBestList nBestList = nbestLists.get(sentenceId);
        for (int i = 0; i < nBestList.getN(); i++) {
            final Parse parse = nBestList.getParse(i);
            final double rerankScore = parse.score + constraintSet.stream()
                    .filter(ev -> ev.isSatisfiedBy(parse))
                    .mapToDouble(ev -> ev.isPositive() ? ev.getStrength() : -ev.getStrength())
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
        final int[] optionDist = AnnotationUtils.getUserResponseDistribution(query, annotation);
        return getUserOptions(query, optionDist);
    }

    public ImmutableList<Integer> getUserOptions(final ScoredQuery<QAStructureSurfaceForm> query,
                                                 final int[] optionDist) {
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> (!query.isJeopardyStyle()
                        && optionDist[i] > reparsingParameters.negativeConstraintMaxAgreement) ||
                        (query.isJeopardyStyle()
                                && optionDist[i] >= reparsingParameters.jeopardyQuestionMinAgreement))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }

    /**
     * Directly get constraints from annotation.
     * @param query
     * @param annotation
     * @return
     */
    public ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                   final AlignedAnnotation annotation) {
        final int[] optionDist = AnnotationUtils.getUserResponseDistribution(query, annotation);
        return getConstraints(query, optionDist);
    }

    public ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                   final int[] optionDist) {
        final Set<Constraint> constraints = new HashSet<>();
        final Set<Integer> hitOptions = IntStream.range(0, optionDist.length).boxed()
                .filter(i -> optionDist[i] >= reparsingParameters.positiveConstraintMinAgreement)
                .collect(Collectors.toSet());
        final Set<Integer> chosenOptions = IntStream.range(0, optionDist.length).boxed()
                .filter(i -> optionDist[i] > reparsingParameters.negativeConstraintMaxAgreement)
                .collect(Collectors.toSet());
        ConstraintExtractor.extractPositiveConstraints(query, hitOptions)
                .forEach(constraints::add);
        ConstraintExtractor.extractNegativeConstraints(query, chosenOptions, reparsingParameters.skipPronounEvidence)
                .forEach(constraints::add);
        constraints.forEach(c -> c.setStrength(
                (Constraint.SupertagConstraint.class.isInstance(c) ?
                        reparsingParameters.supertagPenaltyWeight :
                        reparsingParameters.attachmentPenaltyWeight)));
        return ImmutableSet.copyOf(constraints);
    }

    public ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                   final ImmutableList<Integer> options) {
        if (query.isJeopardyStyle() && reparsingParameters.skipJeopardyQuestions) {
            return ImmutableSet.of();
        }
        final double questionTypeWeight = query.isJeopardyStyle() ? reparsingParameters.jeopardyQuestionWeight : 1.0;
        final ImmutableSet<Constraint> constraintSet = ConstraintExtractor
                .extractNegativeConstraints(query, options, reparsingParameters.skipPronounEvidence)
                .stream()
                .collect(GuavaCollectors.toImmutableSet());
        constraintSet.forEach(ev -> ev.setStrength(
                questionTypeWeight * (Constraint.SupertagConstraint.class.isInstance(ev) ?
                        reparsingParameters.supertagPenaltyWeight :
                        reparsingParameters.attachmentPenaltyWeight)));
        return constraintSet;
    }

    public ImmutableSet<Constraint> getOracleConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                         final ImmutableList<Integer> options) {
        // Extract positive constraints.
        final Set<Constraint> constraints = new HashSet<>();
        constraints.addAll(ConstraintExtractor.extractPositiveConstraints(query, options));
        constraints.addAll(ConstraintExtractor.extractNegativeConstraints(query, options, false /* dont skip pronouns */));
        constraints.forEach(c -> c.setStrength(reparsingParameters.oraclePenaltyWeight));
        return constraints.stream()
                .distinct()
                .collect(GuavaCollectors.toImmutableSet());
    }

    public ImmutableSet<Constraint> getOracleConstraints(final ScoredQuery<QAStructureSurfaceForm> query) {
        final Set<Constraint> constraints = new HashSet<>();
        final Parse gold = getGoldParse(query.getSentenceId());
        final ImmutableList<String> sentence = sentences.get(query.getSentenceId());

        for (int i = 0; i < sentence.size(); i++) {
            final int headId = i;
            for (int j = 0; j < sentence.size(); j++) {
                final int argId = j;
                if (headId == argId || DependencyInstanceHelper.getDependencyType(query, headId, argId) ==
                        DependencyInstanceType.NONE) {
                    continue;
                }
                //final DependencyInstanceType dtype = DependencyInstanceHelper.getDependencyType(query, headId, argId);
                final boolean inGold = gold.dependencies.stream()
                        .anyMatch(dep -> dep.getHead() == headId && dep.getArgument() == argId);
                constraints.add(inGold ?
                        new Constraint.AttachmentConstraint(headId, argId, true, reparsingParameters.oraclePenaltyWeight) :
                        new Constraint.AttachmentConstraint(headId, argId, false, reparsingParameters.oraclePenaltyWeight));
            }
        }
        return constraints.stream()
                .distinct()
                .collect(GuavaCollectors.toImmutableSet());
    }
}
