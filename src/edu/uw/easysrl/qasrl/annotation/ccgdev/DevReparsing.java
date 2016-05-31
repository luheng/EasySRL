package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataUtils;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/26/16.
 */
public class DevReparsing {

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

        for (int sentenceId : CrowdFlowerDataUtils.getNewCoreArgAnnotatedSentenceIds()) {
        //for (int sentenceId : annotations.keySet()) {
            history.addSentence(sentenceId);

            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                   // parser.getAllCoreArgQueriesForSentence(sentenceId);
                    parser.getNewCoreArgQueriesForSentence(sentenceId);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                continue;
            }
            //IntStream.range(0, annotations.get(sentenceId).size())
            //        .forEach(id -> annotations.get(sentenceId).get(id).annotationId = id);
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
                final int[] newOptionDist = ReparsingHelper.getNewOptionDist(sentence, query, matchedResponses, config);
                final ImmutableSet<Constraint> constraints = ReparsingHelper.getConstraints(query, newOptionDist, config);

                history.addEntry(sentenceId, query, parser.getUserOptions(query, newOptionDist), constraints);
                if (history.lastIsWorsened() /*&& !fixType.equals("None") */) {
                    history.printLatestHistory();
                    //System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', optionDist));
                    //System.out.println("Fixed:\t" + fixType);
                    //System.out.println(query.toString(sentence, 'G', parser.getGoldOptions(query), '*', newOptionDist));
                }
            }
        }
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println(config.toString());
        history.printSummary();
    }

    private static boolean hasSpanIssue(final ScoredQuery<QAStructureSurfaceForm> query) {
        for (int opId1 = 0; opId1 < query.getQAPairSurfaceForms().size(); opId1 ++) {
            final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted()
                    .collect(GuavaCollectors.toImmutableList());
            final String opStr = qa.getAnswer().toLowerCase();

            for (int opId2 = 0; opId2 < query.getQAPairSurfaceForms().size(); opId2 ++) {
                if (opId2 != opId1) {
                    final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                    final String opStr2 = qa2.getAnswer().toLowerCase();
                    if (opStr.contains(opStr2) || opStr2.contains(opStr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private static ImmutableList<Integer> getGoldOption(ScoredQuery<QAStructureSurfaceForm> query, Parse goldParse,
                                                        ImmutableList<Integer> referenceOptions) {
        List<Integer> goldOptions = null;
        final Map<String, List<Integer>> allGoldOptions = getAllGoldOptions(query, goldParse);
        final List<QuestionStructure> questionStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream()).collect(Collectors.toList());
        boolean labeledMatch = false;
        for (QuestionStructure questionStructure : questionStructures) {
            String label = questionStructure.category + "." + questionStructure.targetArgNum;
            // Labeled match.
            if (allGoldOptions.containsKey(label)) {
                goldOptions = allGoldOptions.get(label);
                labeledMatch = true;
                break;
            }
        }
        // Unlabeled match.
        if (goldOptions == null) {
            int maxOverlap = 0;
            List<Integer> bestMatch = null;
            for (List<Integer> gold : allGoldOptions.values()) {
                int overlap = (int) gold.stream().filter(referenceOptions::contains).count();
                if (overlap > maxOverlap) {
                    maxOverlap = overlap;
                    bestMatch = gold;
                }
            }
            if (maxOverlap > 0) {
                goldOptions = bestMatch;
            }
        }
        // Other.
        if (goldOptions == null) {
            goldOptions = ImmutableList.of(query.getBadQuestionOptionId().getAsInt());
        }
        return ImmutableList.copyOf(goldOptions);
    }

    private static Map<String, List<Integer>> getAllGoldOptions(ScoredQuery<QAStructureSurfaceForm> query,
                                                                Parse goldParse) {

        final Set<ResolvedDependency> goldDeps = goldParse.dependencies;
        final List<QAStructureSurfaceForm> qaStructures = query.getQAPairSurfaceForms();
        final int headId = query.getPredicateId().getAsInt();
        final OptionalInt prepositionIdOpt = query.getPrepositionIndex();
        Map<String, List<Integer>> allGoldOptions = new HashMap<>();
        if (prepositionIdOpt.isPresent()) {
            //System.out.println("PP id:\t" + ppId);
            final int ppId = prepositionIdOpt.getAsInt();
            for (int id = 0; id < qaStructures.size(); id++) {
                final QAStructureSurfaceForm qa = qaStructures.get(id);
                for (ResolvedDependency goldDep : goldDeps) {
                    final String label = goldDep.getCategory() + "." + goldDep.getArgNumber();
                    if (goldDep.getHead() == ppId && qa.getAnswerStructures().stream()
                            .anyMatch(ans -> ans.argumentIndices.contains(goldDep.getArgument()))) {
                        if (!allGoldOptions.containsKey(label)) {
                            allGoldOptions.put(label, new ArrayList<>());
                        }
                        allGoldOptions.get(label).add(id);
                        break;
                    }
                }
            }
            return allGoldOptions;
        }
        for (int id = 0; id < qaStructures.size(); id++) {
            final QAStructureSurfaceForm qa = qaStructures.get(id);
            for (ResolvedDependency goldDep : goldDeps) {
                if (goldDep.getHead() == headId && qa.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .anyMatch(argId -> goldDep.getArgument() == argId)) {
                    final String label = goldDep.getCategory() + "." + goldDep.getArgNumber();
                    if (!allGoldOptions.containsKey(label)) {
                        allGoldOptions.put(label, new ArrayList<>());
                    }
                    allGoldOptions.get(label).add(id);
                    break;
                }
            }
        }
        return allGoldOptions;
    }

}
