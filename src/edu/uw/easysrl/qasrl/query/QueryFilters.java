package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

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

    private static boolean canBeSplitted(final String option, final ImmutableList<String> allOptions) {
        for (String op1 : allOptions) {
            for (String op2 : allOptions) {
                if (option.equalsIgnoreCase(op1 + ", " + op2) ||
                        option.equalsIgnoreCase(op1 + " and " + op2) ||
                        option.equalsIgnoreCase(op1 + " or " + op2)) {
                    System.err.println(option);
                    return true;
                }
            }
        }
        return false;
    }

    // TODO: filter answers that is a superspan of two non-overlapping answers. i.e.
    //   Barack Obama, president vs. Obama vs. president
    public static QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> scoredQueryFilter() {
        return (queries, nBestList, queryPruningParameters) -> queries.stream()
                .filter(query -> {
                    final ImmutableList<QuestionStructure> qstrs = query.getQAPairSurfaceForms().stream()
                            .flatMap(qa -> qa.getQuestionStructures().stream())
                            .distinct()
                            .collect(GuavaCollectors.toImmutableList());
                    return (!queryPruningParameters.skipSAdjQuestions ||
                                !qstrs.stream().anyMatch(q -> q.category.isFunctionInto(Category.valueOf("S[adj]")))) &&
                            (!queryPruningParameters.skipPPQuestions || qstrs.stream()
                                    .anyMatch(q -> !propositionalCategories.contains(q.category)));
                })
                .map(query -> {
                    query.computeScores(nBestList);
                    final ImmutableList<String> options = query.getOptions().stream()
                            .filter(op -> !op.isEmpty())
                            .collect(GuavaCollectors.toImmutableList());
                    // Prune answer options.
                    final int numQAOptions = query.getQAPairSurfaceForms().size();
                    final List<Integer> filteredOptionIds =
                            IntStream.range(0, numQAOptions).boxed()
                                    .filter(i -> !query.getOptions().get(i).isEmpty())
                                    .filter(i -> query.getOptionScores().get(i) > queryPruningParameters.minOptionConfidence)
                                    .filter(i -> !canBeSplitted(query.getOptions().get(i), options))
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
                .collect(toImmutableList());
    }

    // FIXME
    public static QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> jeopardyPPQueryFilter() {
        return (queries, nBestList, queryPruningParameters) -> queries.stream()
                .map(query -> {
                    query.computeScores(nBestList);
                    // Prune answer options.
                    final int numQAOptions = query.getQAPairSurfaceForms().size();

                    final ImmutableList<Integer> filteredQAOptions = IntStream.range(0, numQAOptions)
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
                            .filter(i -> i >= numQAOptions || filteredQAOptions.contains(i))
                            .collect(toImmutableList());

                    return new ScoredQuery<>(
                            query.getSentenceId(),
                            query.getPrompt(),
                            filteredOptions.stream().map(query.getOptions()::get).collect(toImmutableList()),
                            filteredQAOptions.stream().map(query.getQAPairSurfaceForms()::get).collect(toImmutableList()),
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
