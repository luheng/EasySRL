package edu.uw.easysrl.qasrl.annotation.ccgtest;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.ccgdev.*;
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

/**
 * Created by luheng on 5/26/16.
 */
public class TestReparsing {
    private static ReparsingConfig config = new ReparsingConfig();
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

    private static final ParseData data = ParseDataLoader.loadFromTestPool(true).get();
                                        //ParseDataLoader.loadFromDevPool().get();
    private static final Map<Integer, NBestList> nbestLists = NBestList
            .loadNBestListsFromFile("parses.tagged.test.gold.100best.new.out", 100).get();
            //.loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
    private static final HITLParser parser = new HITLParser(data, nbestLists);
    private static final ReparsingHistory history =  new ReparsingHistory(parser);
    private static final Map<Integer, List<AnnotatedQuery>> annotations =
            AnnotationFileLoader.loadTest();
            //AnnotationFileLoader.loadDev();

    public static void main(String[] args) {
        parser.setQueryPruningParameters(queryPruningParameters);
        config = new ReparsingConfig(args);
        System.out.println(config.toString());
        int numMatchedAnnotations = 0;

        BaseCcgParser.AStarParser baseParser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 1);
        baseParser.cacheSupertags(parser.getParseData());
        BaseCcgParser.ConstrainedCcgParser reParser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, 1);
        reParser.cacheSupertags(parser.getParseData());
        Results avgBaseline = new Results(),
                avgReranked = new Results(),
                avgReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReranked = new Results(),
                avgUnlabeledReparsed = new Results();

        int sentenceCounter = 0;
        for (int sentenceId : parser.getAllSentenceIds()) {
            history.addSentence(sentenceId);
            sentenceCounter ++;
            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final NBestList nBestList = parser.getNBestList(sentenceId);
            final Parse goldParse = parser.getGoldParse(sentenceId);
            final Parse baselineParse = baseParser.parse(sentenceId, parser.getInputSentence(sentenceId));

            final Results baselineF1 = CcgEvaluation.evaluate(baselineParse.dependencies, goldParse.dependencies);
            final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(baselineParse.dependencies, goldParse.dependencies);
            avgBaseline.add(baselineF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                        parser.getNewCoreArgQueriesForSentence(sentenceId);
                        //parser.getAllCoreArgQueriesForSentence(sentenceId);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                avgReparsed.add(baselineF1);
                avgReranked.add(baselineF1);
                avgUnlabeledReranked.add(unlabeledBaselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
                continue;
            }
            Set<Constraint> constraints = new HashSet<>();
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
                        .allMatch(q -> (q.category == Category.valueOf("((S\\NP)/NP)/NP") && q.targetArgNum > 1))) {
                                //|| (q.category == Category.valueOf("((S[b]\\NP)/NP)/NP") && q.targetArgNum > 1))) {
                    continue;
                }
                ///// Heuristics and constraints.
                final int[] newOptionDist = ReparsingHelper.getNewOptionDist(sentence, query, matchedResponses,
                        nBestList, config);
                constraints.addAll(ReparsingHelper.getConstraintsOld(query, newOptionDist, nBestList, config));
            }
            if (constraints.isEmpty()) {
                avgReparsed.add(baselineF1);
                avgReranked.add(baselineF1);
                avgUnlabeledReranked.add(unlabeledBaselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
            } else {
                final Parse reparsed = parser.getReparsed(sentenceId, constraints);
                final int rerankedId = parser.getRerankedParseId(sentenceId, constraints);
                Results rerankedF1 = nBestList.getResults(rerankedId);
                Results unlabeledRerankedF1 = CcgEvaluation.evaluateUnlabeled(nBestList.getParse(rerankedId).dependencies,
                        goldParse.dependencies);
                Results reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies, goldParse.dependencies);
                Results unlabeledReparsedF1 = CcgEvaluation.evaluateUnlabeled(reparsed.dependencies, goldParse.dependencies);
                avgReparsed.add(reparsedF1);
                avgReranked.add(rerankedF1);
                avgUnlabeledReranked.add(unlabeledRerankedF1);
                avgUnlabeledReparsed.add(unlabeledReparsedF1);
            }
            if (sentenceCounter % 100 == 0) {
                System.out.println("Parsed " + sentenceCounter + " sentences ...");
            }
        }
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println("Labeled baseline:\n" + avgBaseline);
        System.out.println("Labeled reranked:\n" + avgReranked);
        System.out.println("Labeled reparsed:\n" + avgReparsed);
        System.out.println("Unlabeled baseline:\n" + avgUnlabeledBaseline);
        System.out.println("Unlabeled reranked:\n" + avgUnlabeledReranked);
        System.out.println("Unlabeled reparsed:\n" + avgUnlabeledReparsed);
        System.out.println(config.toString());
        history.printSummary();
    }
}
