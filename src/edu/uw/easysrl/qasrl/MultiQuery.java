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
    protected Set<QuestionAnswerPairReduced> allQAPairs;
    private Set<Integer> predIds;
    private Set<QuestionAnswerPairReduced.QuestionType> questionTypes;

    public MultiQuery(int sentenceId, String prompt, Set<String> options,
                      Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs,
                      Table<String, String, Set<Parse>> qaStringsToParses) {
        this.sentenceId = sentenceId;
        this.prompt = prompt;
        this.options = options;
        this.qaStringsToQAPairs = qaStringsToQAPairs;
        this.qaStringsToParses = qaStringsToParses;
        this.predIds = null; // TODO
        this.questionTypes = null;
        this.allQAPairs = new HashSet<QuestionAnswerPairReduced>();

        assert this.options.size() > 0
            : "cannot have a MultiQuery with no answer options";
    }

    public abstract Set<String> getResponse(MultiResponseSimulator responseSimulator);
    public abstract boolean isJeopardyStyle();

    public Set<QuestionAnswerPairReduced.QuestionType> getQuestionTypes() {
        if(questionTypes == null) {
            questionTypes = new HashSet<QuestionAnswerPairReduced.QuestionType>();
            allQAPairs
                .stream()
                .map(qaPair -> qaPair.questionType)
                .forEach(questionTypes::add);
        }
        return questionTypes;
    }

    // prompt is a question, choices are answers
    public static class Forward extends MultiQuery {
        public Forward(int sentenceId, String prompt, Set<String> options,
                       Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs,
                       Table<String, String, Set<Parse>> qaStringsToParses) {
            super(sentenceId, prompt, options, qaStringsToQAPairs, qaStringsToParses);
            for(String answer : options) {
                allQAPairs.addAll(qaStringsToQAPairs.get(prompt, answer));
            }
        }

        public Set<String> getResponse(MultiResponseSimulator responseSimulator) {
            return responseSimulator.answersForQuestion(this);
        }

        public boolean isJeopardyStyle() {
            return false;
        }
    }

    // prompt is an answer, choices are questions
    public static class Backward extends MultiQuery {
        public Backward(int sentenceId, String prompt, Set<String> options,
                       Table<String, String, List<QuestionAnswerPairReduced>> qaStringsToQAPairs,
                       Table<String, String, Set<Parse>> qaStringsToParses) {
            super(sentenceId, prompt, options, qaStringsToQAPairs, qaStringsToParses);
            for(String question : options) {
                allQAPairs.addAll(qaStringsToQAPairs.get(question, prompt));
            }
        }

        public Set<String> getResponse(MultiResponseSimulator responseSimulator) {
            return responseSimulator.questionsForAnswer(this);
        }

        public boolean isJeopardyStyle() {
            return true;
        }
    }

    public String toStringWithResponse(MultiResponseSimulator responseSimulator) {
        Set<String> checks = getResponse(responseSimulator);
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
