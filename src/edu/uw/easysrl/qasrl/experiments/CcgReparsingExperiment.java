package edu.uw.easysrl.qasrl.experiments;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.*;
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
        parser.setQueryPruningParameters(queryPruningParameters);

        int numMatchedAnnotations = 0;
        int numChangedSentence = 0;
        Results avgChange = new Results();

        BaseCcgParser.AStarParser baseParser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 1, /* onebest*/
                1e-6, 1e-6, 1000000, 70);
        baseParser.cacheSupertags(parser.getParseData());
        BaseCcgParser.ConstrainedCcgParser reParser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder,
                1 /* one best */);

        reParser.cacheSupertags(parser.getParseData());
        Results avgBaseline = new Results(),
                avgReranked = new Results(),
                avgReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReranked = new Results(),
                avgUnlabeledReparsed = new Results(),
                avgBaselineOnChanged = new Results(),
                avgRerankedOnChanged = new Results(),
                avgReparsedOnChanged = new Results();

        int sentenceCounter = 0;
        //for (int sentenceId = 0; sentenceId < corpus.getSentences().size(); sentenceId++) {
        for  (int sentenceId : parser.getAllSentenceIds()) {
            if (!config.runTest && !nbestLists.containsKey(sentenceId)) {
                continue;
            }
            if (++sentenceCounter % 100 == 0) {
                System.out.println("Parsed " + sentenceCounter + " sentences ...");
            }
            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = null;
            if (annotations.containsKey(sentenceId)) {
                queries = !config.runTest ?
                        parser.getAllCoreArgQueriesForSentence(sentenceId) :
                        parser.getNewCoreArgQueriesForSentence(sentenceId);
            }

            //// Sanity check.
            final Parse goldParse = parser.getGoldParse(sentenceId);
            final Parse baselineParse = baseParser.parse(sentenceId, parser.getInputSentence(sentenceId));
            Preconditions.checkArgument(baselineParse != null);
            final Results baselineF1 = CcgEvaluation.evaluate(baselineParse.dependencies, goldParse.dependencies);
            final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(baselineParse.dependencies,
                    goldParse.dependencies);
            avgBaseline.add(baselineF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                avgReparsed.add(baselineF1);
                avgReranked.add(baselineF1);
                avgUnlabeledReranked.add(unlabeledBaselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
                avgChange.add(CcgEvaluation.evaluate(baselineParse.dependencies, baselineParse.dependencies));
                continue;
            }

            final Set<Constraint> allConstraintsForSentence = new HashSet<>();
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                final Optional<ScoredQuery<QAStructureSurfaceForm>> matchQueryOpt =
                        ExperimentUtils.getBestAlignedQuery(annotation, queries);
                if (!matchQueryOpt.isPresent()) {
                    continue;
                }
                final ScoredQuery<QAStructureSurfaceForm> query = matchQueryOpt.get();
                final ImmutableList<ImmutableList<Integer>> matchedResponses = annotation.getResponses(query);
                Preconditions.checkArgument(matchedResponses.stream().filter(r -> r.size() > 0).count() == 5);

                // Filter ditransitives.
                if (query.getQAPairSurfaceForms().stream().flatMap(qa -> qa.getQuestionStructures().stream())
                        .anyMatch(q -> (q.category == Category.valueOf("((S\\NP)/NP)/NP") && q.targetArgNum > 1))) {
                    continue;
                }

                numMatchedAnnotations ++;
                // Get constraints.
                final ImmutableSet<Constraint> constraints = ReparsingHelper.getConstraints(sentenceId, sentence,
                        query, matchedResponses, config);
                allConstraintsForSentence.addAll(constraints);
            }
            if (allConstraintsForSentence.isEmpty()) {
                avgReparsed.add(baselineF1);
                avgReranked.add(baselineF1);
                avgUnlabeledReranked.add(unlabeledBaselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
            } else {
                final NBestList nBestList = parser.getNBestList(sentenceId);
                final Parse reparsed = parser.getReparsed(sentenceId, allConstraintsForSentence);
                final int rerankedId = parser.getRerankedParseId(sentenceId, allConstraintsForSentence);
                Results rerankedF1 = nBestList.getResults(rerankedId);
                Results unlabeledRerankedF1 = CcgEvaluation.evaluateUnlabeled(
                        nBestList.getParse(rerankedId).dependencies,
                        goldParse.dependencies);
                Results reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies, goldParse.dependencies);
                Results unlabeledReparsedF1 = CcgEvaluation.evaluateUnlabeled(reparsed.dependencies,
                        goldParse.dependencies);
                boolean parseChanged = CcgEvaluation.evaluate(reparsed.dependencies, baselineParse.dependencies)
                        .getF1() < 0.999;
                avgReparsed.add(reparsedF1);
                avgReranked.add(rerankedF1);
                avgUnlabeledReranked.add(unlabeledRerankedF1);
                avgUnlabeledReparsed.add(unlabeledReparsedF1);
                if (parseChanged) {
                    numChangedSentence++;
                    avgBaselineOnChanged.add(baselineF1);
                    avgRerankedOnChanged.add(rerankedF1);
                    avgReparsedOnChanged.add(reparsedF1);
                }
            }
        }
        System.out.println("Sentence count:\t" + sentenceCounter);
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println(config.toString());
        System.out.println("Num. changed sentences:\t" + numChangedSentence);

        System.out.println("On changed baseline:\n" + avgBaselineOnChanged);
        System.out.println("On changed reranked:\n" + avgRerankedOnChanged);
        System.out.println("On changed reparsed:\n" + avgReparsedOnChanged);

        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println("Labeled baseline:\n" + avgBaseline);
        System.out.println("Labeled reranked:\n" + avgReranked);
        System.out.println("Labeled reparsed:\n" + avgReparsed);
        System.out.println("Unlabeled baseline:\n" + avgUnlabeledBaseline);
        System.out.println("Unlabeled reranked:\n" + avgUnlabeledReranked);
        System.out.println("Unlabeled reparsed:\n" + avgUnlabeledReparsed);



        System.out.println("Sanity check:\t" + avgBaseline.getFrequency() + "\t"
                + avgReranked.getFrequency() + "\t" + avgReparsed.getFrequency());
    }
}
