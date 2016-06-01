package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

/**
 * Created by luheng on 5/26/16.
 */
public class DevReparsingExp {

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
        final ParseData dev = ParseDataLoader.loadFromDevPool().get();
        final Map<Integer, NBestList> nbestLists = NBestList
                .loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
        final HITLParser parser = new HITLParser(dev, nbestLists);
        final ReparsingHistory history =  new ReparsingHistory(parser);
        final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadDev();
        parser.setQueryPruningParameters(queryPruningParameters);
        ReparsingConfig config = new ReparsingConfig(args);
        System.out.println(config.toString());
        int numMatchedAnnotations = 0;
        int numChangedSentence = 0;
        Results avgChange = new Results();

        //for (int sentenceId : CrowdFlowerDataUtils.getNewCoreArgAnnotatedSentenceIds()) {
        for (int sentenceId : annotations.keySet()) {
            history.addSentence(sentenceId);

            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = parser.getAllCoreArgQueriesForSentence(sentenceId);
            //parser.getNewCoreArgQueriesForSentence(sentenceId);

            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                Parse baselineParse = parser.getNBestList(sentenceId).getParse(0);
                avgChange.add(CcgEvaluation.evaluate(baselineParse.dependencies, baselineParse.dependencies));
                continue;
            }
            System.out.println(sentenceId);
            final Set<Constraint> allConstraints = new HashSet<>();

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
                numMatchedAnnotations ++;

                // Filter ditransitives.
                if (query.getQAPairSurfaceForms().stream().flatMap(qa -> qa.getQuestionStructures().stream())
                        .allMatch(q -> (q.category == Category.valueOf("((S[dcl]\\NP)/NP)/NP") && q.targetArgNum > 1)
                                || (q.category == Category.valueOf("((S[b]\\NP)/NP)/NP") && q.targetArgNum > 1))) {
                    continue;
                }

                ///// Heuristics
                final int[] optionDist = new int[query.getOptions().size()];
                matchedResponses.forEach(response -> response.stream().forEach(r -> optionDist[r] ++));
                //final int[] newOptionDist = ReparsingHelper.getNewOptionDist2(sentenceId, sentence, query, matchedResponses,
                 //       nbestLists.get(sentenceId), config);
                //final ImmutableSet<Constraint> constraints = ReparsingHelper.getConstraints(query, newOptionDist,
                //        nbestLists.get(sentenceId), config);
                final ImmutableSet<Constraint> constraints = ReparsingHelper.getConstraints2(sentenceId, sentence,
                        query, matchedResponses, nbestLists.get(sentenceId), config);
                allConstraints.addAll(constraints);

                history.addEntry(sentenceId, query, parser.getUserOptions(query, optionDist), constraints);
                if (history.lastIsWorsened()) {
                    history.printLatestHistory();
                    System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', optionDist));
                    allConstraints.forEach(c -> System.out.println(c.toString(sentence)));
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
