package edu.uw.easysrl.active_learning;

import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.*;

/**
 * Active Learning query
 * Created by luheng on 1/14/16.
 */
public class Query {
    List<String> question;
    double questionScore;
    TIntObjectHashMap<Set<Integer>> answerToParses;
    TIntDoubleHashMap answerScores;

    public Query(List<String> question, double questionScore) {
        this.question = question;
        this.questionScore = questionScore;
        this.answerToParses = new TIntObjectHashMap<>();
        this.answerScores = new TIntDoubleHashMap();
    }

    public void addAnswer(int answerId, int parseId, double answerScore) {
        if (!answerToParses.containsKey(answerId)) {
            answerToParses.put(answerId, new HashSet<>());
        }
        answerToParses.get(answerId).add(parseId);
        answerScores.adjustOrPutValue(answerId, answerScore, answerScore);
    }
}
