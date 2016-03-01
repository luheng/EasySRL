package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

public abstract class MultiQuery {

    public final int sentenceId;
    public final String prompt;
    public final Set<String> options;

    final Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs;
    final Table<String, String, Set<Parse>> qaStringsToParses;

    public MultiQuery(int sentenceId, String prompt, Set<String> options,
                      Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs,
                      Table<String, String, Set<Parse>> qaStringsToParses) {
        this.sentenceId = sentenceId;
        this.prompt = prompt;
        this.options = options;
        this.qaStringsToQAPairs = qaStringsToQAPairs;
        this.qaStringsToParses = qaStringsToParses;
    }

    public abstract String toStringWithResponse(MultiResponseSimulator responseSimulator);

    // prompt is a question, choices are answers
    public static class Forward extends MultiQuery {
        public Forward(int sentenceId, String prompt, Set<String> options,
                       Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs,
                       Table<String, String, Set<Parse>> qaStringsToParses) {
            super(sentenceId, prompt, options, qaStringsToQAPairs, qaStringsToParses);
        }

        public String toStringWithResponse(MultiResponseSimulator responseSimulator) {
            Set<String> answers = responseSimulator.answersForQuestion(this);
            return toStringWithChecks(answers);
        }
    }

    // prompt is an answer, choices are questions
    public static class Backward extends MultiQuery {
        public Backward(int sentenceId, String prompt, Set<String> options,
                       Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs,
                       Table<String, String, Set<Parse>> qaStringsToParses) {
            super(sentenceId, prompt, options, qaStringsToQAPairs, qaStringsToParses);
        }

        public String toStringWithResponse(MultiResponseSimulator responseSimulator) {
            Set<String> questions = responseSimulator.questionsForAnswer(this);
            return toStringWithChecks(questions);
        }
    }

    protected String toStringWithChecks(Set<String> checks) {
        StringBuilder sb = new StringBuilder();
        sb.append(prompt + "\n");
        for(String option : options) {
            if(checks.contains(option)) {
                sb.append(" *");
            } else {
                sb.append("  ");
            }
            sb.append(option);
            sb.append("\n");
        }
        return sb.toString();
    }

}
