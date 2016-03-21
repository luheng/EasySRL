package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.DebugPrinter;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.stream.Collectors;

/**
 * Most basic implementation of the QAPairSurfaceForm interface;
 * contains the bare necessities and nothing else.
 *
 * This class is for DATA, not LOGIC.
 *
 * Created by julianmichael on 3/17/16.
 */
public class BasicQuery<QA extends QAPairSurfaceForm> implements Query<QA> {

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
    private final String prompt;
    private final ImmutableList<String> options;
    private final ImmutableList<QA> qaPairSurfaceForms;
    private boolean isJeopardyStyle;
    private boolean allowMultipleChoices;

    public BasicQuery(int sentenceId,
                      String prompt, ImmutableList<String> options, ImmutableList<QA> qaPairSurfaceForms,
                      boolean isJeopardyStyle,
                      boolean allowMultipleChoices) {
        this.sentenceId = sentenceId;
        this.prompt = prompt;
        this.options = options;
        this.qaPairSurfaceForms = qaPairSurfaceForms;
        this.isJeopardyStyle = isJeopardyStyle;
        this.allowMultipleChoices = allowMultipleChoices;
    }

    public boolean isJeopardyStyle() {
        return isJeopardyStyle;
    }

    public boolean allowMultipleChoices() {
        return allowMultipleChoices;
    }

    public String toString(final ImmutableList<String> sentence) {
        String result = "";
        final int predicateIndex = qaPairSurfaceForms.get(0).getQAPairs().get(0).getPredicateIndex();
        final Category category  = qaPairSurfaceForms.get(0).getQAPairs().get(0).getPredicateCategory();
        final int argumentNumber = qaPairSurfaceForms.get(0).getQAPairs().get(0).getArgumentNumber();

        result += sentence.stream().collect(Collectors.joining(" ")) + "\n";
        result += String.format("%d:%s\t%s\t%d\n", predicateIndex, sentence.get(predicateIndex), category, argumentNumber);
        result += prompt + "\n";

        for (int i = 0; i < options.size(); i++) {
            result += String.format("%d\t%s\n", i, options.get(i));
        }
        return result;
    }
}
