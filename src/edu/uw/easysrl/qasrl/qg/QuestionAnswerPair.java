package edu.uw.easysrl.qasrl.qg;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.TextGenerationHelper.TextWithDependencies;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

public class QuestionAnswerPair {
    public final int predicateIndex;
    public final Category predicateCategory;
    public final List<ResolvedDependency> questionDeps;
    public final List<String> question;
    public final List<ResolvedDependency> targetDeps;
    public final List<List<String>> answers;
    public final List<Set<ResolvedDependency>> answerDeps;

    public final List<Integer> answerWordIndices;
    public static final String answerDelimiter = " # ";

    public QuestionAnswerPair(int predicateIndex, Category predicateCategory,
                              List<ResolvedDependency> questionDeps, List<String> question,
                              List<ResolvedDependency> targetDeps, List<TextWithDependencies> answers) {
        this.predicateIndex = predicateIndex;
        this.predicateCategory = predicateCategory;
        this.questionDeps = questionDeps;
        this.question = question;
        this.targetDeps = targetDeps;
        this.answers = answers.stream()
                .map(twd -> twd.tokens)
                .collect(Collectors.toList());
        this.answerDeps = answers.stream()
                .map(twd -> twd.dependencies)
                .collect(Collectors.toList());
        this.answerWordIndices = answers.stream()
                .flatMap(twd -> twd.dependencies.stream()
                .map(ResolvedDependency::getArgumentIndex))
                .collect(Collectors.toSet())
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public String renderQuestion() {
        String str = TextGenerationHelper.renderString(question);
        if(!str.isEmpty()) {
            str = Character.toUpperCase(str.charAt(0)) + str.substring(1);
            return str+ "?";
        } else {
            return str;
        }
    }

    public String renderAnswer() {
        if (answers.size() == 1) {
            return TextGenerationHelper.renderString(answers.get(0));
        } else {
            return answers.stream()
                    .map(TextGenerationHelper::renderString)
                    .collect(Collectors.joining(answerDelimiter));
        }
    }
}
