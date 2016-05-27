package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.ParseDataLoader;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 5/26/16.
 */
public class ErrorAnalysis {
    final static int minAgreement = 5;

    private static final ParseData dev = ParseDataLoader.loadFromDevPool().get();
    private static final Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("parses.tagged.dev.100best.out", 100).get();
    private static final HITLParser parser = new HITLParser(dev, nbestLists);
    private static final Map<Integer, List<AnnotatedQuery>> annotations = AnnotationFileLoader.loadDev();

    public static void main(String[] args) {
        int numUnmatchedAnnotations = 0, numMatchedAnnotations = 0, numHighAgreementAnnotations = 0,
                numWrongAnnotations = 0;
        for (int sentenceId : parser.getAllSentenceIds()) {
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
                    parser.getAllCoreArgQueriesForSentence(sentenceId);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                continue;
            }
            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final int predicateId = query.getPredicateId().getAsInt();
                final Optional<AnnotatedQuery> matchAnnotation = annotations.get(sentenceId).stream()
                        .filter(annotation -> annotation.predicateId == predicateId
                                && annotation.questionString.equalsIgnoreCase(query.getPrompt()))
                        .findFirst();
                if (!matchAnnotation.isPresent()) {
                    numUnmatchedAnnotations ++;
                    continue;
                }
                numMatchedAnnotations ++;
                final Multiset<ImmutableList<Integer>> responses = HashMultiset.create(matchAnnotation.get().responses);
                final Optional<ImmutableList<Integer>> agreedOptionsOpt = responses.stream()
                        .filter(op -> responses.count(op) >= minAgreement)
                        .findFirst();
                if (!agreedOptionsOpt.isPresent()) {
                    continue;
                }
                final ImmutableList<Integer> agreedOptions = agreedOptionsOpt.get();
                numHighAgreementAnnotations ++;
                //final ImmutableList<Integer> goldOptions = getGoldOptions(query, parser.getGoldParse(sentenceId));
                final Map<String, List<Integer>> allGoldOptions = getAllGoldOptions(query, parser.getGoldParse(sentenceId));
                final List<QuestionStructure> questionStructures = query.getQAPairSurfaceForms().stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream()).collect(Collectors.toList());
                List<Integer> goldOptions = null;
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
                        int overlap = (int) gold.stream().filter(agreedOptions::contains).count();
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
                if (!agreedOptions.equals(goldOptions)) {
                    System.out.print(query.toString(parser.getSentence(sentenceId),
                            'G', ImmutableList.copyOf(goldOptions),
                            'U', agreedOptions));
                    if (!labeledMatch) {
                        for (String label : allGoldOptions.keySet()) {
                            System.out.println("[gold]:\t" + label + "\t" + ImmutableList.of(allGoldOptions.get(label)));
                        }
                    }
                    System.out.println();
                    ++numWrongAnnotations;
                }
            }
        }

        System.out.println("Num. unmatched annotation:\t" + numUnmatchedAnnotations);
        System.out.println("Num. matched annotation:\t" + numMatchedAnnotations);
        System.out.println("Num. high-agreement annotation:\t" + numHighAgreementAnnotations);
        System.out.println("Num. high-agreement wrong annotation:\t" + numWrongAnnotations);
    }

    private static ImmutableList<Integer> getGoldOptions(ScoredQuery<QAStructureSurfaceForm> query,
                                                         Parse goldParse) {
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final ImmutableList<Integer> labeledGoldOptions = parser.getGoldOptions(query);
        if (!labeledGoldOptions.contains(naOptionId)) {
            return labeledGoldOptions;
        }
        final Set<ResolvedDependency> goldDeps = goldParse.dependencies;
        final List<QAStructureSurfaceForm> qaStructures = query.getQAPairSurfaceForms();
        final int predicateId = query.getPredicateId().getAsInt();
        ImmutableList<Integer> goldOptions = IntStream.range(0, qaStructures.size())
                .boxed()
                .filter(id -> goldDeps.stream()
                        .filter(goldDep -> goldDep.getHead() == predicateId)
                        .anyMatch(goldDep -> {
                            final QAStructureSurfaceForm qa = qaStructures.get(id);
                            return qa.getAnswerStructures().stream().flatMap(ans -> ans.argumentIndices.stream())
                                    .anyMatch(argId -> goldDep.getArgument() == argId);
                        })
                ).collect(GuavaCollectors.toImmutableList());
        return goldOptions.isEmpty() ? ImmutableList.of(naOptionId) : goldOptions;
    }

    private static Map<String, List<Integer>> getAllGoldOptions(ScoredQuery<QAStructureSurfaceForm> query,
                                                                Parse goldParse) {

        final Set<ResolvedDependency> goldDeps = goldParse.dependencies;
        final List<QAStructureSurfaceForm> qaStructures = query.getQAPairSurfaceForms();
        final int predicateId = query.getPredicateId().getAsInt();

        Map<String, List<Integer>> allGoldOptions = new HashMap<>();
        for (int id = 0; id < qaStructures.size(); id++) {
            final QAStructureSurfaceForm qa = qaStructures.get(id);
            for (ResolvedDependency goldDep : goldDeps) {
                if (goldDep.getHead() == predicateId && qa.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .anyMatch(argId -> goldDep.getArgument() == argId)) {
                    final String label = goldDep.getCategory() + "." + goldDep.getArgNumber();
                    if (!allGoldOptions.containsKey(label)) {
                        allGoldOptions.put(label, new ArrayList<>());
                    }
                    allGoldOptions.get(label).add(id);
                }
            }
        }
        return allGoldOptions;
    }
}
