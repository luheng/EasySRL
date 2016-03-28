package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.uw.easysrl.util.GuavaCollectors.toImmutableList;

/**
 * Created by luheng on 3/22/16.
 */
public class QueryFilters {
    private static Set<Category> propositionalCategories = new HashSet<>();
    static {
        Collections.addAll(propositionalCategories,
                Category.valueOf("((S\\NP)\\(S\\NP))/NP"),
                Category.valueOf("(NP\\NP)/NP"),
                Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"));
    }

    public static QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> scoredQueryFilter() {
        return (queries, nBestList, queryPruningParameters) -> queries.stream()
                .map(query -> {
                    query.computeScores(nBestList);
                    // Prune answer options.
                    final int numQAOptions = query.getQAPairSurfaceForms().size();
                    final List<Integer> filteredOptionIds =
                            IntStream.range(0, numQAOptions).boxed()
                                    .filter(i -> query.getOptionScores().get(i) > queryPruningParameters.minOptionConfidence)
                                    .collect(Collectors.toList());
                    // TODO: handle max number of options
                    final List<QAStructureSurfaceForm> filteredQAList = filteredOptionIds.stream()
                                    .map(query.getQAPairSurfaceForms()::get)
                                    .collect(Collectors.toList());
                    final List<String> filteredOptions = IntStream.range(0, query.getOptions().size()).boxed()
                                    .filter(i -> i >= numQAOptions || filteredOptionIds.contains(i))
                                    .map(query.getOptions()::get)
                                    .collect(Collectors.toList());
                    return new ScoredQuery<>(
                            query.getSentenceId(),
                            query.getPrompt(),
                            ImmutableList.copyOf(filteredOptions),
                            ImmutableList.copyOf(filteredQAList),
                            query.isJeopardyStyle(),
                            query.allowMultipleChoices());
                })
                .filter(query -> {
                    query.computeScores(nBestList);
                    return query.getPromptScore() > queryPruningParameters.minPromptConfidence
                            && query.getOptionEntropy() > queryPruningParameters.minOptionEntropy
                            && (!queryPruningParameters.skipBinaryQueries || query.getQAPairSurfaceForms().size() > 1)
                            && (!queryPruningParameters.skipPPQuestions || !query.getPredicateCategory().isPresent()
                                || !propositionalCategories.contains(query.getPredicateCategory().get()));
                })
                .collect(toImmutableList());
    }

    public static QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> jeopardyPPQueryFilter() {
        return (queries, nBestList, queryPruningParameters) -> queries.stream()
                .map(query -> {
                    query.computeScores(nBestList);
                    // Prune answer options.
                    final int numQAOptions = query.getQAPairSurfaceForms().size();
                    final ImmutableList<Integer> filteredNAOptions = IntStream.range(0, numQAOptions)
                            .boxed()
                            .filter(i -> query.getOptionScores().get(i) > queryPruningParameters.minOptionConfidence)
                            .filter(i -> query.getQAPairSurfaceForms().get(i).getQuestionStructures().stream()
                                    .anyMatch(qs ->
                                        (qs.targetArgNum == 2 && qs.category == Category.valueOf("(NP\\NP)/NP")) ||
                                        (qs.targetArgNum == 3 && qs.category.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) ||
                                         qs.category.getArgument(qs.targetArgNum) == Category.PP)))
                        .collect(toImmutableList());

                    final ImmutableList<Integer> filteredOptions = IntStream.range(0, query.getOptions().size())
                            .boxed()
                            .filter(i -> i >= numQAOptions || filteredNAOptions.contains(i))
                            .collect(toImmutableList());

                    return new ScoredQuery<>(
                            query.getSentenceId(),
                            query.getPrompt(),
                            filteredOptions.stream().map(query.getOptions()::get).collect(toImmutableList()),
                            filteredNAOptions.stream().map(query.getQAPairSurfaceForms()::get).collect(toImmutableList()),
                            query.isJeopardyStyle(),
                            query.allowMultipleChoices());
                })
                .filter(query -> {
                    /*
                    boolean hasVerbAttachment = false, hasNounAttachment = false;
                    for (QAStructureSurfaceForm qa : query.getQAPairSurfaceForms()) {
                        for (QuestionStructure qs : qa.getQuestionStructures()) {
                            hasNounAttachment |= (qs.category.isFunctionInto(Category.valueOf("NP")));
                            hasVerbAttachment |= (qs.category.isFunctionInto(Category.valueOf("S\\NP")));
                        }
                    }
                    if (!hasNounAttachment || !hasVerbAttachment) {
                        return false;
                    }
                    */
                    query.computeScores(nBestList);
                    return query.getPromptScore() > queryPruningParameters.minPromptConfidence
                            && query.getOptionEntropy() > queryPruningParameters.minOptionEntropy
                            && (!queryPruningParameters.skipBinaryQueries || query.getQAPairSurfaceForms().size() > 1);
                })
                .collect(toImmutableList());
    }
}