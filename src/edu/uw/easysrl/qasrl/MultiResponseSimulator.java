package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

import java.util.*;
import java.util.stream.Collectors;

public class MultiResponseSimulator {
    private final List<Parse> goldParses;
    private final Map<Integer, List<QuestionAnswerPairReduced>> qaPairsForSentenceId;

    public MultiResponseSimulator(List<Parse> goldParses) {
        this.goldParses = goldParses;
        this.qaPairsForSentenceId = new HashMap<>();
        System.out.println("GOLD QUESTION--ANSWER PAIRS");
        for(int id = 0; id < goldParses.size(); id++) {
            System.out.println("==========");
            Parse parse = goldParses.get(id);
            List<String> words = parse.syntaxTree.getLeaves()
                .stream()
                .map(SyntaxTreeNode::getWord)
                .collect(Collectors.toList());
            System.out.println(TextGenerationHelper.renderString(words));
            for(QuestionAnswerPairReduced qaPair : getQAPairs(id)) {
                System.out.println(String.format("%s %s %s; question main: %s",
                                                 words.get(qaPair.predicateIndex),
                                                 qaPair.predicateCategory,
                                                 qaPair.questionType,
                                                 words.get(qaPair.questionMainIndex)));
                System.out.println(qaPair.renderQuestion());
                System.out.println("  " + qaPair.renderAnswer());
            }
        }
    }

    public Set<String> answersForQuestion(MultiQuery query) {
        final int sentenceId = query.sentenceId;
        Set<String> answers = getQAPairs(sentenceId)
            .stream()
            .filter(qaPair -> qaPair.renderQuestion().equals(query.prompt))
            .map(QuestionAnswerPairReduced::renderAnswer)
            .collect(Collectors.toSet());
        Set<String> checks = query.options
            .stream()
            .filter(answers::contains)
            .collect(Collectors.toSet());
        answers
            .stream()
            .filter(answer -> !checks.contains(answer))
            .forEach(answer -> System.out.println("GG: " + answer));
        return checks;
    }

    public Set<String> questionsForAnswer(MultiQuery query) {
        final int sentenceId = query.sentenceId;
        Set<String> questions = getQAPairs(sentenceId)
            .stream()
            .filter(qaPair -> qaPair.renderAnswer().equals(query.prompt))
            .map(QuestionAnswerPairReduced::renderQuestion)
            .collect(Collectors.toSet());
        Set<String> checks = query.options
            .stream()
            .filter(questions::contains)
            .collect(Collectors.toSet());
        questions
            .stream()
            .filter(question -> !checks.contains(question))
            .forEach(question -> System.out.println("GG: " + question));
        return checks;
    }

    private List<QuestionAnswerPairReduced> getQAPairs(int sentenceId) {
        if(!qaPairsForSentenceId.containsKey(sentenceId)) {
            Parse goldParse = goldParses.get(sentenceId);
            List<String> words = goldParse.syntaxTree.getLeaves().stream().map(SyntaxTreeNode::getWord).collect(Collectors.toList());
            List<Parse> parses = new LinkedList<>(); parses.add(goldParse);
            QueryGeneratorBothWays queryGenerator = new QueryGeneratorBothWays(sentenceId, words, parses);
            qaPairsForSentenceId.put(sentenceId, queryGenerator.allQAPairs);
        }
        return qaPairsForSentenceId.get(sentenceId);
    }
}
