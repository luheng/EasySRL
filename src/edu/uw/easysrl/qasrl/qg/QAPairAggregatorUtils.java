package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.uw.easysrl.util.GuavaCollectors.toImmutableList;
import static edu.uw.easysrl.util.GuavaCollectors.toImmutableSet;
import static java.util.stream.Collectors.*;

/**
 * Helpers.
 * Created by luheng on 3/19/16.
 */
public class QAPairAggregatorUtils {
    public static String answerDelimiter = " _AND_ ";

    static String getQuestionLabelString(final QuestionAnswerPair qa) {
        return qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber() + "\t";
    }

    static String getQuestionDependenciesString(final QuestionAnswerPair qa) {
        // If the QA is a pp-arg question (i.e. What did X sell Y to?), then we need to include all dependencies.
        final boolean isPPArg = qa.getPredicateCategory().getArgument(qa.getArgumentNumber()) == Category.PP;
        return qa.getQuestionDependencies().stream()
                .filter(dep -> dep.getHead() == qa.getPredicateIndex())
                .filter(dep -> isPPArg || dep.getArgNumber() != qa.getArgumentNumber())
                .collect(groupingBy(ResolvedDependency::getArgNumber))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + ":" + e.getValue().stream()
                        .map(ResolvedDependency::getArgument).distinct().sorted()
                        .map(String::valueOf).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\t"));
    }

    static String getFullQuestionStructureString(final QuestionAnswerPair qa) {
        return getQuestionLabelString(qa) + "\t" + getQuestionDependenciesString(qa);
    }

    static QuestionSurfaceFormToStructure getQuestionSurfaceFormToStructure(
            final List<QuestionAnswerPair> qaList) {
        final List<QuestionAnswerPair> bestSurfaceFormQAs = getQAListWithBestQuestionSurfaceForm(qaList);
        return new QuestionSurfaceFormToStructure(
                bestSurfaceFormQAs.get(0).getQuestion(),
                new QuestionStructure(bestSurfaceFormQAs),
                qaList);
    }

    static AnswerSurfaceFormToStructure getAnswerSurfaceFormToSingleHeadedStructure(
            final List<QuestionAnswerPair> qaList) {
        final AnswerStructure answerStructure = new AnswerStructure(
                ImmutableList.of(qaList.get(0).getArgumentIndex()),
                true /* single headed */);
        final List<QuestionAnswerPair> bestSurfaceFormQAs = getQAListWithBestAnswerSurfaceForm(qaList);
        return new AnswerSurfaceFormToStructure(
                bestSurfaceFormQAs.get(0).getAnswer(),
                answerStructure,
                qaList);
    }

    static ImmutableList<AnswerSurfaceFormToStructure> getAnswerSurfaceFormToMultiHeadedStructures(
            final List<QuestionAnswerPair> qaList) {
        // Get answer indices list.
        Table<ImmutableList<Integer>, String, Double> argListToAnswerToScore = HashBasedTable.create();
        Table<ImmutableList<Integer>, QuestionAnswerPair, Boolean> argListToQAs = HashBasedTable.create();
        qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .values().stream()
                .forEach(someParseQAs -> {
                    Map<Integer, String> argIdToSpan = new HashMap<>();
                    someParseQAs.forEach(qa -> argIdToSpan.put(qa.getArgumentIndex(), qa.getAnswer()));
                    ImmutableList<Integer> argList = argIdToSpan.keySet().stream()
                            .sorted()
                            .collect(toImmutableList());
                    String answerString = argList.stream()
                            .map(argIdToSpan::get)
                            .collect(Collectors.joining(answerDelimiter));
                    double score = someParseQAs.get(0).getParse().score;
                    double s0 = argListToAnswerToScore.contains(argList, answerString) ?
                            argListToAnswerToScore.get(argList, answerString) : .0;
                    argListToAnswerToScore.put(argList, answerString, score + s0);
                    someParseQAs.stream().forEach(qa -> argListToQAs.put(argList, qa, Boolean.TRUE));
                });

        return argListToAnswerToScore.rowKeySet().stream()
                .map(argList -> {
                    String bestAnswerString = argListToAnswerToScore.row(argList).entrySet().stream()
                            .max(Comparator.comparing(Map.Entry::getValue))
                            .get().getKey();
                    final Collection<QuestionAnswerPair> qaList2 = argListToQAs.row(argList).keySet();
                    return new AnswerSurfaceFormToStructure(
                            bestAnswerString,
                            new AnswerStructure(argList, false /* not single headed */),
                            qaList2);
                }).collect(GuavaCollectors.toImmutableList());
    }

    static AnswerSurfaceFormToStructure getAnswerSurfaceFormToAdjunctHeadsStructure(
            final List<QuestionAnswerPair> qaList) {
        final QuestionAnswerPair qa = qaList.get(0);
        final int answerHead = qa.getArgumentNumber() == 1 ?
                qa.getPredicateIndex() :
                qa.getTargetDependency().getArgument();
        final ImmutableList<Integer> argIds = Stream.concat(
                    getSalientAnswerDependencies(qaList.get(0)).stream().map(ResolvedDependency::getHead),
                    Stream.of(answerHead))
                .distinct().sorted()
                .collect(toImmutableList());
        return new AnswerSurfaceFormToStructure(
                getQAListWithBestAnswerSurfaceForm(qaList).get(0).getAnswer(),
                new AnswerStructure(argIds, false /* single headed */),
                qaList);
    }

    static SurfaceFormToDependencies getQuestionSurfaceFormToDependencies(final List<QuestionAnswerPair> qaList) {
        final List<QuestionAnswerPair> bestSurfaceFormQAs = getQAListWithBestQuestionSurfaceForm(qaList);
        return new SurfaceFormToDependencies(
                bestSurfaceFormQAs.get(0).getQuestion(),
                getSalientQuestionDependencies(qaList.get(0)),
                qaList);
    }

    static SurfaceFormToDependencies getAnswerSurfaceFormToDependencies(final List<QuestionAnswerPair> qaList) {
        final List<QuestionAnswerPair> bestSurfaceFormQAs = getQAListWithBestAnswerSurfaceForm(qaList);
        return new SurfaceFormToDependencies(
                bestSurfaceFormQAs.get(0).getAnswer(),
                getSalientAnswerDependencies(qaList.get(0)),
                qaList);
    }

    static ImmutableSet<ResolvedDependency> getSalientQuestionDependencies(final QuestionAnswerPair qa) {
        return Stream.concat(qa.getQuestionDependencies().stream(), Stream.of(qa.getTargetDependency()))
                .filter(dep -> isDependencySalient(dep, qa))
                .collect(GuavaCollectors.toImmutableSet());
    }

    static ImmutableSet<ResolvedDependency> getSalientAnswerDependencies(final QuestionAnswerPair qa) {
        return qa.getAnswerDependencies().stream()
                .filter(dep -> isDependencySalient(dep, qa))
                .collect(GuavaCollectors.toImmutableSet());
    }

    /**
     * Tells us whether we want to group based on a dependency.
     */
    private static boolean isDependencySalient(ResolvedDependency dep, QuestionAnswerPair qaPair) {
        ImmutableList<Category> categories = ImmutableList.copyOf(qaPair.getParse().categories);
        ImmutableList<String> words = qaPair.getParse().syntaxTree.getLeaves().stream()
                .map(SyntaxTreeNode::getWord)
                .collect(toImmutableList());
        int index = dep.getHead();
        Category cat = categories.get(index);
        return (qaPair.getTargetDependency() != null && dep.equals(qaPair.getTargetDependency())) ||
                (cat.isFunctionInto(Category.valueOf("S\\NP")) && !cat.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"))) ||
                (cat.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) && dep.getArgNumber() == 2) ||
                (cat.isFunctionInto(Category.valueOf("NP\\NP")) && dep.getArgNumber() == 1 && !words.get(index).equalsIgnoreCase("of"));
        // (Category.valueOf("((S\\NP)\\(S\\NP))/NP").matches(cat) && dep.getArgNumber() == 2) ||
        // (Category.valueOf("(NP\\NP)/NP").matches(cat) && dep.getArgNumber() == 1 && !words.get(index).equalsIgnoreCase("of"));
    }

    private static List<QuestionAnswerPair> getQAListWithBestQuestionSurfaceForm(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getQuestion))
                .entrySet().stream()
                .max(Comparator.comparing(e -> QAPairAggregatorUtils.getScore(e.getValue())))
                .get().getValue();
    }

    private static List<QuestionAnswerPair> getQAListWithBestAnswerSurfaceForm(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getAnswer))
                .entrySet().stream()
                .max(Comparator.comparing(e -> QAPairAggregatorUtils.getScore(e.getValue())))
                .get().getValue();
    }

    private static double getScore(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .entrySet().stream()
                .mapToDouble(e -> e.getValue().get(0).getParse().score)
                .sum();
    }

    static class QuestionSurfaceFormToStructure {
        public final String question;
        public final QuestionStructure structure;
        public final Collection<QuestionAnswerPair> qaList;

         QuestionSurfaceFormToStructure(String question, QuestionStructure structure, Collection<QuestionAnswerPair> qaList) {
            this.question = question;
            this.structure = structure;
            this.qaList = qaList;
        }
    }

    static class AnswerSurfaceFormToStructure {
        public final String answer;
        public final AnswerStructure structure;
        public final Collection<QuestionAnswerPair> qaList;

        AnswerSurfaceFormToStructure(String answer, AnswerStructure structure, Collection<QuestionAnswerPair> qaList) {
            this.answer = answer;
            this.structure = structure;
            this.qaList = qaList;
        }
    }

    static class SurfaceFormToDependencies {
        public final String surfaceForm;
        public final ImmutableSet<ResolvedDependency> dependencies;
        public final Collection<QuestionAnswerPair> qaList;

        SurfaceFormToDependencies(String surfaceForm, final ImmutableSet<ResolvedDependency> dependencies,
                                 final Collection<QuestionAnswerPair> qaList) {
            this.surfaceForm = surfaceForm;
            this.dependencies = dependencies;
            this.qaList = qaList;
        }
    }
}
