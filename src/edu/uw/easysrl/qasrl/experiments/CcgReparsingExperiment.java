package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.Annotation;
import edu.uw.easysrl.qasrl.annotation.ccgdev.AnnotationFileLoader;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingConfig;
import edu.uw.easysrl.qasrl.experiments.ReparsingHelper;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.IntStream;

public class CcgReparsingExperiment {

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.maxNumOptionsPerQuery = 6;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    public static void main(String[] args) {
        ReparsingConfig config = new ReparsingConfig(args);
        System.out.println(config.toString());

        ParseData corpus;
        Map<Integer, NBestList> nbestLists;
        Map<Integer, List<AnnotatedQuery>> annotations;
        if (!config.runTest) {
            corpus = ParseDataLoader.loadFromDevPool().get();
            nbestLists = NBestList.loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
            annotations = AnnotationFileLoader.loadDev();
        } else {
            corpus = ParseDataLoader.loadFromTestPool(true).get();
            nbestLists = NBestList.loadNBestListsFromFile("parses.tagged.test.gold.100best.out", 100).get();
            annotations = AnnotationFileLoader.loadTest();
        }
        final HITLParser parser = new HITLParser(corpus, nbestLists);
        final ReparsingHistory history =  new ReparsingHistory(parser);

        parser.setQueryPruningParameters(queryPruningParameters);

        int numMatchedAnnotations = 0;
        int numChangedSentence = 0;
        Results avgChange = new Results();

        for (int sentenceId = 0; sentenceId < corpus.getSentences().size(); sentenceId++) {
            if (!config.runTest && !nbestLists.containsKey(sentenceId)) {
                continue;
            }
            history.addSentence(sentenceId);
            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = null;
            if (annotations.containsKey(sentenceId)) {
                queries = !config.runTest ? parser.getAllCoreArgQueriesForSentence(sentenceId) :
                        parser.getNewCoreArgQueriesForSentence(sentenceId);
            }
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                Parse baselineParse = parser.getNBestList(sentenceId).getParse(0);
                avgChange.add(CcgEvaluation.evaluate(baselineParse.dependencies, baselineParse.dependencies));
                continue;
            }
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                final Optional<ScoredQuery<QAStructureSurfaceForm>> matchQueryOpt =
                        ExperimentUtils.getBestAlignedQuery(annotation, queries);
                if (!matchQueryOpt.isPresent()) {
                    continue;
                }
                final ScoredQuery<QAStructureSurfaceForm> query = matchQueryOpt.get();
                final ImmutableList<ImmutableList<Integer>> matchedResponses = annotation.getResponses(query);
                if (matchedResponses.stream().filter(r -> r.size() > 0).count() < 5) {
                    continue;
                }
                // Filter ditransitives.
                if (query.getQAPairSurfaceForms().stream().flatMap(qa -> qa.getQuestionStructures().stream())
                        .allMatch(q -> (q.category == Category.valueOf("((S[dcl]\\NP)/NP)/NP") && q.targetArgNum > 1)
                                || (q.category == Category.valueOf("((S[b]\\NP)/NP)/NP") && q.targetArgNum > 1))) {
                    continue;
                }
                numMatchedAnnotations ++;
                // Get constraints.
                final ImmutableSet<Constraint> constraints = ReparsingHelper.getConstraints(sentenceId, sentence,
                        query, matchedResponses, config);
                final int[] optionDist = new int[query.getOptions().size()];
                matchedResponses.forEach(response -> response.stream().forEach(r -> optionDist[r] ++));
                history.addEntry(sentenceId, query, parser.getUserOptions(query, optionDist), constraints);
                // Debug for dev.
                if (!config.runTest && history.lastIsWorsened()) {
                    history.printLatestHistory();
                    System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', optionDist));
                    constraints.forEach(c -> System.out.println(c.toString(sentence)));
                    System.out.println();
                }
            }
            Parse baselineParse = parser.getNBestList(sentenceId).getParse(0);
            Parse lastReparsed = history.getLastReparsed(sentenceId).orElse(baselineParse);
            Results change = CcgEvaluation.evaluate(lastReparsed.dependencies, baselineParse.dependencies);
            if (change.getF1() < 0.999) {
                ++ numChangedSentence;
            }
            avgChange.add(change);
        }
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println(config.toString());
        System.out.println("Num. changed sentences:\t" + numChangedSentence);
        System.out.println("Avg. change:\t" + avgChange);
        history.printSummary();
    }
}
