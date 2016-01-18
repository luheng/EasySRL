package edu.uw.easysrl.qasrl;

import gnu.trove.map.hash.TIntDoubleHashMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Active Learning query
 * Created by luheng on 1/14/16.
 */
public class Query {
    List<String> question;
    double questionScore;
    HashMap<Integer, Set<Integer>> answerToParses;
    TIntDoubleHashMap answerScores;

    public Query(List<String> question, double questionScore, int numParses) {
        this.question = question;
        this.questionScore = questionScore;
        answerToParses = new HashMap<>();
        answerScores = new TIntDoubleHashMap();
        Set<Integer> allParses = new HashSet<>();
        for (int i = 0; i < numParses; i++) {
            allParses.add(i);
        }
        answerToParses.put(-1, allParses);
        answerScores.put(-1, 1.0 * numParses);
    }

    public void addAnswer(int answerId, int parseId, double answerScore) {
        if (!answerToParses.containsKey(answerId)) {
            answerToParses.put(answerId, new HashSet<>());
        }
        answerToParses.get(answerId).add(parseId);
        answerScores.adjustOrPutValue(answerId, answerScore, answerScore);
        if (answerToParses.get(-1).remove(parseId)) {
            answerScores.adjustValue(-1, -1.0);
        }
    }

    public void print(List<String> sentence) {
        System.out.println(questionScore + "\t" + question.stream().collect(Collectors.joining(" ")) + "?");
        answerToParses.keySet().stream().sorted().forEach(id -> {
            String answerStr = (id < 0) ? "N/A" : sentence.get(id);
            System.out.print("\t" + answerScores.get(id) + "\t" + answerStr);
            answerToParses.get(id).stream().sorted().forEach(parseId -> System.out.print("\t" + parseId));
            System.out.println();
        });
    }

    // TODO: compute query scores: expected intelligibility score and expected model change score
    // A high quality question need to be:
    // - derived from a confident lexicon.
    // - derived from a parse with confident ``supporting dependencies'' (other than the target dependency)
}
