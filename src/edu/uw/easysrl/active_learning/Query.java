package edu.uw.easysrl.active_learning;

import gnu.trove.map.hash.TIntDoubleHashMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Active Learning query
 * Created by luheng on 1/14/16.
 */
public class Query {
    List<String> question;
    double questionScore;
    HashMap<Integer, Set<Integer>> answerToParses;
    TIntDoubleHashMap answerScores;

    public Query(List<String> question, double questionScore) {
        this.question = question;
        this.questionScore = questionScore;
        this.answerToParses = new HashMap<>();
        this.answerScores = new TIntDoubleHashMap();
    }

    public void addAnswer(int answerId, int parseId, double answerScore) {
        if (!answerToParses.containsKey(answerId)) {
            answerToParses.put(answerId, new HashSet<>());
        }
        answerToParses.get(answerId).add(parseId);
        answerScores.adjustOrPutValue(answerId, answerScore, answerScore);
    }

    public void print(List<String> sentence) {
        System.out.println(questionScore + "\t" + question.stream().collect(Collectors.joining(" ")) + "?");
        answerToParses.keySet().stream().sorted().forEach(id -> {
            System.out.print("\t" + answerScores.get(id) + "\t" + sentence.get(id));
            answerToParses.get(id).stream().sorted().forEach(parseId -> System.out.print("\t" + parseId));
            System.out.println();
        });
    }
}
