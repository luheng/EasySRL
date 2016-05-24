package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

/**
 * Convenience class to hold our QueryAggregator instances,
 * which are often polymorphic (so they appear as polymorphic static methods).
 *
 * This class encodes LOGIC, NOT DATA.
 *
 * Created by julianmichael on 3/17/16.
 */
public class QueryGenerators {

    @Deprecated
    public static <QA extends QAPairSurfaceForm> QueryGenerator<QA, Query<QA>> maximalForwardGenerator() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(QA::getQuestion))
            .entrySet()
            .stream()
            .map(e -> {
                assert e.getValue().size() > 0
                        : "grouped list should always be nonempty";
                int sentenceId = e.getValue().get(0).getSentenceId();
                String question = e.getKey();
                ImmutableList<String> answers = e.getValue()
                        .stream()
                        .map(QA::getAnswer)
                        .distinct()
                        .collect(toImmutableList());
                ImmutableList<QA> surfaceForms = ImmutableList.copyOf(e.getValue());
                return new BasicQuery<>(sentenceId, question, answers, surfaceForms,
                                        false /* is jeopardy style */,
                                        true /* allow multiple */);
            })
            .collect(toImmutableList());
    }

    public static QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> checkboxQueryGenerator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QueryGeneratorUtils::getQueryKey))
                .values()
                .stream()
                .map(qaList -> {
                    ImmutableList<QAStructureSurfaceForm> sortedQAList = qaList.stream()
                            .sorted((qa1, qa2) -> Integer.compare(qa1.getArgumentIndices().get(0),
                                                                  qa2.getArgumentIndices().get(0)))
                            .collect(GuavaCollectors.toImmutableList());
                    List<String> options = sortedQAList.stream().map(QAStructureSurfaceForm::getAnswer).collect(toList());
                    options.add(QueryGeneratorUtils.kNoneApplicableString);
                    return new ScoredQuery<>(qaList.get(0).getSentenceId(),
                                             qaList.get(0).getQuestion(),
                                             ImmutableList.copyOf(options),
                                             sortedQAList,
                                             QueryType.Forward,
                                             true /* allow multiple */);
                }).collect(toImmutableList());
    }

    public static QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> cleftedQueryGenerator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QueryGeneratorUtils::getCleftedQueryKey))
                .values()
                .stream()
                .map(qaList -> {
                    final ImmutableList<QAStructureSurfaceForm> sortedQAList = qaList.stream()
                            //.sorted((qa1, qa2) -> StringComparable))
                            .sorted(Comparator.comparing(QAStructureSurfaceForm::getAnswer))
                            .collect(GuavaCollectors.toImmutableList());
                    final List<String> options = sortedQAList.stream()
                            .map(QAStructureSurfaceForm::getAnswer)
                            .collect(toList());
                    options.add(QueryGeneratorUtils.kNoneApplicableString);
                    return new ScoredQuery<>(qaList.get(0).getSentenceId(),
                            qaList.get(0).getQuestion(),
                            ImmutableList.copyOf(options),
                            sortedQAList,
                            QueryType.Clefted,
                            true /* allow multiple */);
                }).collect(toImmutableList());
    }

    public static QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> radioButtonQueryGenerator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QueryGeneratorUtils::getQueryKey))
                .values()
                .stream()
                .map(qaList -> {
                    List<String> options = qaList.stream().map(QAStructureSurfaceForm::getAnswer).collect(toList());
                    options.add(QueryGeneratorUtils.kUnlistedAnswerOptionString);
                    options.add(QueryGeneratorUtils.kBadQuestionOptionString);
                    return new ScoredQuery<>(
                            qaList.get(0).getSentenceId(),
                            qaList.get(0).getQuestion(),
                            ImmutableList.copyOf(options),
                            ImmutableList.copyOf(qaList),
                            QueryType.Forward,
                            false /* allow multiple */);
                }).collect(toImmutableList());
    }

    /**
     * Generate all jeopardy-style checkbox queries.
     */
    public static QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> jeopardyCheckboxQueryGenerator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QAStructureSurfaceForm::getAnswer))
                .values()
                .stream()
                .map(qaList -> {
                    List<String> options = qaList.stream().map(QAStructureSurfaceForm::getQuestion).collect(toList());
                    options.add(QueryGeneratorUtils.kNoneApplicableString);
                    return new ScoredQuery<>(
                            qaList.get(0).getSentenceId(),
                            qaList.get(0).getAnswer(),
                            ImmutableList.copyOf(options),
                            ImmutableList.copyOf(qaList),
                            QueryType.Jeopardy,
                            true /* allow multiple */);
                }).collect(toImmutableList());
    }

    /**
     * For each question string, we want to come up with several queries:
     * one with only the arguments that have no adjuncts,
     * and for EACH ADJUNCT, one with all the no-adjunct answers and all the answers with that adjunct.
     * TODO: consider: what if we have the same string answer, but not the same deps? should we solve this at surface form level?
     */
    public static <QA extends QADependenciesSurfaceForm> QueryGenerator<QA, Query<QA>> answerAdjunctPartitioningQueryGenerator() {
        return qaPairs -> qaPairs.stream()
                .collect(groupingBy(QA::getSentenceId))
                .entrySet().stream()
                .flatMap(e1 -> {
                    int sentenceId = e1.getKey();
                    return e1.getValue().stream()
                            .collect(groupingBy(QA::getQuestion))
                            .entrySet().stream()
                            .flatMap(e2 -> {
                                String question = e2.getKey();
                                ImmutableList<QA> surfaceForms = ImmutableList.copyOf(e2.getValue());
                                ImmutableList<QA> noAdjunctSurfaceForms = surfaceForms.stream()
                                        .filter(sf -> sf.getAnswerDependencySets().stream()
                                                .anyMatch(depSet -> !depSet.stream()
                                                .anyMatch(dep -> dep.getCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) ||
                                                          dep.getCategory().isFunctionInto(Category.valueOf("NP\\NP")))))
                                        .collect(toImmutableList());

                                ImmutableSet<Integer> adjunctIndices = surfaceForms.stream()
                                        .flatMap(sf -> sf.getAllPossibleAnswerDependencies().stream()
                                                .filter(dep -> dep.getCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) ||
                                                        dep.getCategory().isFunctionInto(Category.valueOf("NP\\NP")))
                                                .map(dep -> dep.getHead()))
                                        .collect(toImmutableSet());
                                ImmutableList<ImmutableList<QA>> necessarilyAdjunctGroupedSurfaceForms = adjunctIndices.stream()
                                    .map(adjunctIndex -> surfaceForms.stream()
                                         .filter(sf -> sf.getAllPossibleAnswerDependencies().stream()
                                                 .anyMatch(dep -> dep.getHead() == adjunctIndex))
                                         .filter(sf -> !sf.getAnswerDependencySets().stream()
                                                 .anyMatch(depSet -> !depSet.stream()
                                                 .anyMatch(dep -> dep.getCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) ||
                                                           dep.getCategory().isFunctionInto(Category.valueOf("NP\\NP")))))
                                         .collect(toImmutableList()))
                                    .filter(list -> list.size() > 0)
                                        .collect(toImmutableList());

                                final ImmutableList<ImmutableList<QA>> surfaceFormsForQueries;
                                if(necessarilyAdjunctGroupedSurfaceForms.size() == 0) {
                                    surfaceFormsForQueries = ImmutableList.of(noAdjunctSurfaceForms);
                                } else {
                                    surfaceFormsForQueries = necessarilyAdjunctGroupedSurfaceForms.stream()
                                        .map(chosenAdjunctSurfaceForms -> Stream.concat(noAdjunctSurfaceForms.stream(), chosenAdjunctSurfaceForms.stream())
                                             .collect(toImmutableList()))
                                        .collect(toImmutableList());
                                }

                                // each of these is a list of surface forms that will be in one query.
                                return surfaceFormsForQueries.stream()
                                    .map(finalSurfaceForms -> {
                                            ImmutableList<String> answers = finalSurfaceForms.stream()
                                                .map(QA::getAnswer)
                                                .distinct()
                                                .collect(toImmutableList());
                                            return new BasicQuery<>(sentenceId, question, answers, finalSurfaceForms,
                                                                    false /* is jeopardy style */,
                                                                    true /* allow multiple */);
                                        });
                                });
                    }).collect(toImmutableList());
    }

    private QueryGenerators() {
        throw new AssertionError("no instances");
    }
}
