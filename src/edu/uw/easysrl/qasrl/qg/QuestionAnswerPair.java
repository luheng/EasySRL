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

import static edu.uw.easysrl.util.GuavaCollectors.*;

import com.google.common.collect.ImmutableSet;

@Deprecated
public class QuestionAnswerPair implements IQuestionAnswerPair {

    public int getSentenceId() {
        return -1; // XXX
    }

    public int getParseId() {
        return -1; // XXX
    }

    public int getArgumentNumber() {
        return -1; // XXX
    }

    public int getPredicateIndex() {
        return predicateIndex;
    }

    public Category getPredicateCategory() {
        return predicateCategory;
    }

    public ImmutableSet<ResolvedDependency> getQuestionDependencies() {
        return ImmutableSet.copyOf(questionDeps);
    }

    public ResolvedDependency getTargetDependency() {
        assert targetDeps.size() > 0
            : "pretty sure we shouldn't have a QA pair with no target deps";
        return targetDeps.get(0);
    }

    public ImmutableSet<ResolvedDependency> getAnswerDependencies() {
        return answerDeps
            .stream()
            .flatMap(x -> x.stream())
            .collect(toImmutableSet());
    }

    public String getQuestion() {
        return renderQuestion();
    }

    public String getAnswer() {
        return renderAnswer();
    }

    public final int predicateIndex;
    public final Category predicateCategory;
    public final List<ResolvedDependency> questionDeps;
    public final List<String> question;
    public final List<ResolvedDependency> targetDeps;
    public final List<List<String>> answers;
    public final List<Set<ResolvedDependency>> answerDeps;
;
    public final List<Integer> answerWordIndices;
    public static final String answerDelimiter = " _AND_ ";

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
