package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.*;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.qg.util.Pronoun;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import jdk.nashorn.internal.ir.annotations.Immutable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 5/26/16.
 */
public class PronounAnalyzer {
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

    private static final ParseData dev = ParseDataLoader.loadFromDevPool().get();
    private static final Map<Integer, NBestList> nbestLists = NBestList
            .loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
    private static final HITLParser parser = new HITLParser(dev, nbestLists);
    private static final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadDev();

    public static void main(String[] args) {
        parser.setQueryPruningParameters(queryPruningParameters);
        int numGoldIsPronoun = 0, numOne = 0, numThree = 0, numOnebestIsRight = 0;
        for (int sentenceId : parser.getAllSentenceIds()) {
            final ImmutableList<String> sentence = parser.getSentence(sentenceId);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    parser.getAllCoreArgQueriesForSentence(sentenceId);
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

                ///// Heuristics
                final int[] optionDist = new int[query.getOptions().size()];
                int[] newOptionDist = new int[optionDist.length];
                Arrays.fill(optionDist, 0);
                Arrays.fill(newOptionDist, 0);
                matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));

                final Multiset<Integer> votes = HashMultiset.create(matchedResponses.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
                final ImmutableList<Integer> chosenOptions = votes.entrySet().stream()
                        .filter(e -> e.getCount() >= 1)
                        .map(e -> e.getElement()).distinct().sorted()
                        .collect(GuavaCollectors.toImmutableList());
                final ImmutableList<Integer> agreedOptions = votes.entrySet().stream()
                        .filter(e -> e.getCount() >= 3)
                        .map(e -> e.getElement()).distinct().sorted()
                        .collect(GuavaCollectors.toImmutableList());
                final ImmutableList<Integer> onebestOptions = parser.getOneBestOptions(query);

                int goldOption = goldIsPronoun(query, parser.getGoldParse(sentenceId));
                if (goldOption < 0) {
                    continue;
                }
                numGoldIsPronoun ++;
                if (chosenOptions.contains(goldOption)) {
                    numOne++;
                }
                if (agreedOptions.contains(goldOption)) {
                    numThree ++;
                }
                if (onebestOptions.contains(goldOption)) {
                    numOnebestIsRight ++;
                }
                System.out.println(query.toString(sentence,
                        'G', parser.getGoldOptions(query),
                        'B', onebestOptions,
                        '*', optionDist));
            }
        }
        System.out.println(numGoldIsPronoun);
        System.out.println(numOne);
        System.out.println(numThree);
        System.out.println(numOnebestIsRight);
    }

    private static int goldIsPronoun(final ScoredQuery<QAStructureSurfaceForm> query, Parse goldParse) {
        for (int opId1 = 0; opId1 < query.getQAPairSurfaceForms().size(); opId1 ++) {
            final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> argIds = qa.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted()
                    .collect(GuavaCollectors.toImmutableList());
            final int headId = query.getPrepositionIndex().isPresent() ?
                    query.getPrepositionIndex().getAsInt() :
                    query.getPredicateId().getAsInt();
            final String opStr = qa.getAnswer().toLowerCase();
            boolean inGold = goldParse.dependencies.stream()
                    .anyMatch(d -> d.getHead() == headId && argIds.contains(d.getArgument()));
            if (inGold && PronounList.nonPossessivePronouns.contains(opStr)) {
                return opId1;
            }
        }
        return -1;
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
