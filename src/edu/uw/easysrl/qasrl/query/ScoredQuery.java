package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.stanford.nlp.objectbank.IdentityFunction;
import edu.uw.easysrl.qasrl.DebugPrinter;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.qg.QuestionKey;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 3/20/16.
 */
public class ScoredQuery<QA extends QAStructureSurfaceForm> implements Query<QA> {
    public int getSentenceId() {
        return sentenceId;
    }

    public String getPrompt() {
        return prompt;
    }

    public ImmutableList<String> getOptions() {
        return options;
    }

    public ImmutableList<QA> getQAPairSurfaceForms() {
        return qaPairSurfaceForms;
    }

    private final int sentenceId;
    private final ImmutableList<QuestionKey> questionKeys;
    private final String prompt;
    private final ImmutableList<String> options;
    private final ImmutableList<QA> qaPairSurfaceForms;
    private boolean isJeopardyStyle, allowMultipleChoices;

    private double promptScore, optionEntropy;
    private ImmutableList<Double> optionScores;

    public ScoredQuery(int sentenceId,
                       String prompt,
                       ImmutableList<String> options,
                       ImmutableList<QA> qaPairSurfaceForms,
                       boolean isJeopardyStyle,
                       boolean allowMultipleChoices) {
        this.sentenceId = sentenceId;
        this.prompt = prompt;
        this.options = options;
        this.qaPairSurfaceForms = qaPairSurfaceForms;
        this.isJeopardyStyle = isJeopardyStyle;
        this.allowMultipleChoices = allowMultipleChoices;
        if (!isJeopardyStyle) {
            questionKeys = ImmutableList.of(new QuestionKey(qaPairSurfaceForms.get(0).getPredicateIndex(),
                                                            qaPairSurfaceForms.get(0).getCategory(),
                                                            qaPairSurfaceForms.get(0).getArgumentNumber()));
        } else {
            questionKeys = qaPairSurfaceForms.stream()
                    .map(qa -> new QuestionKey(qa.getPredicateIndex(),
                                               qa.getCategory(),
                                               qa.getArgumentNumber()))
                    .collect(GuavaCollectors.toImmutableList());
        }
    }

    public void computeScores(NBestList nbestList) {
        // TODO: handle jeopardy style.
        double totalScore = nbestList.getScores().stream().mapToDouble(s -> s).sum();
        promptScore = qaPairSurfaceForms.stream()
                .flatMap(qa -> qa.getQuestionStructures().stream()
                        .flatMap(q -> q.parseIds.stream()))
                .distinct()
                .mapToDouble(nbestList::getScore)
                .sum() / totalScore;
        optionScores = IntStream.range(0, options.size())
                .boxed()
                .map(i -> {
                    double optionScore = .0;
                    if (i < qaPairSurfaceForms.size()) {
                        optionScore = qaPairSurfaceForms.get(i).getAnswerStructures().stream()
                                .flatMap(a -> a.parseIds.stream())
                                .distinct()
                                .mapToDouble(nbestList::getScore)
                                .sum() / totalScore;
                    } else if (options.get(i).equals(QueryGenerators.kBadQuestionOptionString)) {
                        optionScore = 1.0 - promptScore;
                    }
                    return optionScore;
                }).collect(GuavaCollectors.toImmutableList());

        // TODO: compute entropy.

    }

    public boolean isJeopardyStyle() { return isJeopardyStyle; }
    public boolean allowMultipleChoices() {
        return allowMultipleChoices;
    }

    public String toString(final ImmutableList<String> sentence) {
        // TODO: handle jeopardy style.
        String result = "";
        final int predicateIndex = questionKeys.get(0).predicateIndex;
        final Category category  = questionKeys.get(0).predicateCategory;
        final int argumentNumber = questionKeys.get(0).argumentNumber;

        result += sentence.stream().collect(Collectors.joining(" ")) + "\n";
        result += String.format("%d:%s\t%s\t%d\n", predicateIndex, sentence.get(predicateIndex), category, argumentNumber);

        System.out.println(String.format("%.2f\t%s", promptScore, prompt));

        for (int i = 0; i < options.size(); i++) {
            String optionString = "";
            if (i < qaPairSurfaceForms.size()) {
                final QAStructureSurfaceForm qa = qaPairSurfaceForms.get(i);
                final ImmutableList<Integer> argList = qa.getAnswerStructures().get(0).argumentIndices;
                String argIdsStr = argList.stream().map(String::valueOf).collect(Collectors.joining(","));
                String argHeadsStr = argList.stream().map(sentence::get).collect(Collectors.joining(","));
                String parseIdsStr = DebugPrinter.getShortListString(qa.getAnswerStructures().get(0).parseIds);
                optionString += String.format("%.2f\t%d\t%s\t%s:%s\t%s", optionScores.get(i), i, options.get(i),
                        argIdsStr, argHeadsStr,  parseIdsStr);
            } else {
                optionString += String.format("%.2f\t%d\t%s", optionScores.get(i), i, options.get(i));
            }
            result += optionString + "\n";
        }
        return result;
    }
}