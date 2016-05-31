package edu.uw.easysrl.qasrl.annotation.bioinfer;

/**
 * Created by luheng on 5/30/16.
 */

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.ccgdev.AnnotationFileLoader;
import edu.uw.easysrl.qasrl.annotation.ccgdev.ReparsingConfig;
import edu.uw.easysrl.qasrl.annotation.ccgdev.ReparsingHelper;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.main.ParsePrinter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 5/26/16.
 */
public class BioinferReparsing {
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
    private static final int nBest = 100;

    public static void printTestSetOriginalOneBest(String[] args) {
        BioinferCCGCorpus corpus = BioinferCCGCorpus.readTest().get();
        Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.test.100best.out", 1).get();
        for (int sentenceId : nbestLists.keySet().stream().sorted().collect(Collectors.toList())) {
            System.out.println(ParsePrinter.CCGBANK_PRINTER.print(nbestLists.get(sentenceId).getParse(0).syntaxTree, sentenceId) + "\n");
        }
    }

    public static void printTestSetUpworkReparsed(String[] args) {
        BioinferCCGCorpus corpus = BioinferCCGCorpus.readTest().get();
        Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.test.100best.out", 100).get();

        config = new ReparsingConfig(args);
        System.err.println(config.toString());
        int numMatchedAnnotations = 0;
        // TODO modify config for upwork annotations

        BaseCcgParser.AStarParser baseParser = new BaseCcgParser.AStarParser(BaseCcgParser.longModelFolder, 1,
                                                                             1e-6, 1e-6, 250000, 100);
        baseParser.cacheSupertags(corpus.getInputSentences());
        BaseCcgParser.ConstrainedCcgParser reParser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.longModelFolder, 1);
        reParser.cacheSupertags(corpus.getInputSentences());

        int sentenceCounter = 0, numChangedSentences = 0;
        Results avgChange = new Results();

        StringBuilder parseStringsToPrint = new StringBuilder();

        // TODO decide how to use upwork annotations
        // final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadBioinfer();
    }

    public static void printTestSetCrowdFlowerReparsed(String[] args) {
        BioinferCCGCorpus corpus = BioinferCCGCorpus.readTest().get();
        Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.test.100best.out", nBest).get();
        System.err.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));
        final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadBioinfer();

        config = new ReparsingConfig(args);
        System.err.println(config.toString());
        int numMatchedAnnotations = 0;

        BaseCcgParser.AStarParser baseParser = new BaseCcgParser.AStarParser(BaseCcgParser.longModelFolder, 1,
                1e-6, 1e-6, 250000, 100);
        baseParser.cacheSupertags(corpus.getInputSentences());
        BaseCcgParser.ConstrainedCcgParser reParser = new BaseCcgParser.ConstrainedCcgParser(
                BaseCcgParser.longModelFolder, 1);
        reParser.cacheSupertags(corpus.getInputSentences());

        int sentenceCounter = 0, numChangedSentences = 0;
        Results avgChange = new Results();

        StringBuilder parseStringsToPrint = new StringBuilder();

        for (int sentenceId : nbestLists.keySet().stream().sorted().collect(Collectors.toList())) {
            sentenceCounter++;
            final ImmutableList<String> sentence = corpus.getSentence(sentenceId);
            final NBestList nBestList = nbestLists.get(sentenceId);
            final ImmutableList<InputReader.InputWord> inputSentence = corpus.getInputSentence(sentenceId);
            final Parse baselineParse = baseParser.parse(sentenceId, inputSentence);

            List<ScoredQuery<QAStructureSurfaceForm>> queries = QuestionGenerationPipeline.coreArgQGPipeline
                    .setQueryPruningParameters(queryPruningParameters)
                    .generateAllQueries(sentenceId, nBestList);

            //parser.getAllCoreArgQueriesForSentence(sentenceId);
            // if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
            //     avgChange.add(CcgEvaluation.evaluate(baselineParse.dependencies, baselineParse.dependencies));
            //     continue;
            // }
            Set<Constraint> constraints = new HashSet<>();
            if(annotations.containsKey(sentenceId)) {
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
                    ///// Heuristics and constraints.
                    final int[] newOptionDist = ReparsingHelper.getNewOptionDist(sentence, query, matchedResponses,
                                                                                 nBestList, config);
                    constraints.addAll(ReparsingHelper.getConstraints(query, newOptionDist, nBestList, config));
                }
            }
            final Parse reparsed = constraints.isEmpty() ? baselineParse
                : reParser.parseWithConstraint(sentenceId, inputSentence, constraints);

            parseStringsToPrint.append("\n" + ParsePrinter.CCGBANK_PRINTER.print(reparsed.syntaxTree, sentenceId));

            Results change = CcgEvaluation.evaluate(reparsed.dependencies, baselineParse.dependencies);
            if (change.getF1() < 0.999) {
                numChangedSentences++;
            }
            avgChange.add(change);
            if (sentenceCounter % 100 == 0) {
                System.err.println("Parsed " + sentenceCounter + " sentences ...");
            }
        }
        System.err.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.err.println("Num. changed sentences:\t" + numChangedSentences);
        System.err.println("Avg change:\t" + avgChange);
        System.err.println(config.toString());
        System.err.println();

        // only parses go to stdout
        System.out.println(parseStringsToPrint);
    }

    public static void main(String[] args) {
        printTestSetCrowdFlowerReparsed(args);
    }
}
