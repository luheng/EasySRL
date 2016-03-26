package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 3/22/16.
 */
public class QueryFilter {
    private static Set<Category> propositionalCategories = new HashSet<>();
    static {
        Collections.addAll(propositionalCategories,
                Category.valueOf("((S\\NP)\\(S\\NP))/NP"),
                Category.valueOf("(NP\\NP)/NP"),
                Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"));
    }

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> filter(
                        final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries,
                        final NBestList nBestList,
                        final QueryPruningParameters queryPruningParameters) {
        return queries.stream()
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
                .collect(GuavaCollectors.toImmutableList());
    }

    /**
     * Each option should be a pp question.
     * @param queries
     * @param nBestList
     * @param queryPruningParameters
     * @return
     */
    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> filterForJeopardyPPQuestions(
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries,
            final NBestList nBestList,
            final QueryPruningParameters queryPruningParameters) {
        return queries.stream()
                .map(query -> {
                    query.computeScores(nBestList);
                    // Prune answer options.
                    final int numQAOptions = query.getQAPairSurfaceForms().size();
                    final List<Integer> filteredOptionIds =
                            IntStream.range(0, numQAOptions).boxed()
                                    .filter(i -> {
                                            if (i < numQAOptions) {
                                                if (!query.getQAPairSurfaceForms().get(i).getQuestionStructures().stream()
                                                        .anyMatch(qs -> propositionalCategories.contains(qs.category))) {
                                                    return false;
                                                }
                                            }
                                            return query.getOptionScores().get(i) > queryPruningParameters.minOptionConfidence;
                                    })
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
                            && (!queryPruningParameters.skipBinaryQueries || query.getQAPairSurfaceForms().size() > 1);
                })
                .collect(GuavaCollectors.toImmutableList());
    }
}
