package edu.uw.easysrl.qasrl.annotation.ccgtest;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.ccgdev.AnnotationFileLoader;
import edu.uw.easysrl.qasrl.annotation.ccgdev.DevReparsing;
import edu.uw.easysrl.qasrl.annotation.ccgdev.FixerNew;
import edu.uw.easysrl.qasrl.annotation.ccgdev.ReparsingConfig;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private static final ParseData test = ParseDataLoader.loadFromTestPool(true).get();
    private static final Map<Integer, NBestList> nbestLists = NBestList
            .loadNBestListsFromFile("parses.tagged.test.gold.100best.new.out", 100).get();
    private static final HITLParser parser = new HITLParser(test, nbestLists);
    private static final ReparsingHistory history =  new ReparsingHistory(parser);
    private static final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadTest();

    public static void main(String[] args) {
        parser.setQueryPruningParameters(queryPruningParameters);
        config = new ReparsingConfig(args);
        System.out.println(config.toString());
        int numMatchedAnnotations = 0;
        for (int sentenceId : parser.getAllSentenceIds()) {
            history.addSentence(sentenceId);
            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    parser.getNewCoreArgQueriesForSentence(sentenceId);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                continue;
            }
            IntStream.range(0, annotations.get(sentenceId).size())
                    .forEach(id -> annotations.get(sentenceId).get(id).annotationId = id);
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
                ///// Heuristics and constraints.
                final int[] optionDist = new int[query.getOptions().size()];
                int[] newOptionDist = new int[optionDist.length];
                Arrays.fill(optionDist, 0);
                Arrays.fill(newOptionDist, 0);
                matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));
                final Multiset<Integer> votes = HashMultiset.create(matchedResponses.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
                final ImmutableList<Integer> agreedOptions = votes.entrySet().stream()
                        .filter(e -> e.getCount() >= config.positiveConstraintMinAgreement)
                        .map(e -> e.getElement()).distinct().sorted()
                        .collect(GuavaCollectors.toImmutableList());
                final ImmutableList<Integer> pronounFix = FixerNew.pronounFixer(query, agreedOptions, optionDist);
                final ImmutableList<Integer> subspanFix = FixerNew.subspanFixer(query, agreedOptions, optionDist);
                final ImmutableList<Integer> appositiveFix = FixerNew.appositiveFixer(sentence, query, agreedOptions, optionDist);
                final ImmutableList<Integer> relativeFix = FixerNew.relativeFixer(sentence, query, agreedOptions, optionDist);
                List<Integer> fixedResopnse = null;
                if (config.fixPronouns && !pronounFix.isEmpty()) {
                    fixedResopnse = pronounFix;
                } else if (config.fixSubspans && !subspanFix.isEmpty()) {
                    fixedResopnse = subspanFix;
                } else if (config.fixRelatives && !relativeFix.isEmpty()) {
                    fixedResopnse = relativeFix;
                } else if (config.fixAppositves && !appositiveFix.isEmpty()) {
                    fixedResopnse = appositiveFix;
                }
                if (fixedResopnse != null) {
                    fixedResopnse.stream().forEach(op -> newOptionDist[op] += 5);
                } else {
                    for (ImmutableList<Integer> response : matchedResponses) {
                        response.stream().forEach(op -> newOptionDist[op] ++);
                    }
                }
                final ImmutableSet<Constraint> constraints = DevReparsing.getConstraints(query, newOptionDist);
                history.addEntry(sentenceId, query, parser.getUserOptions(query, newOptionDist), constraints);
            }
        }
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println(config.toString());
        history.printSummary();
    }
}
