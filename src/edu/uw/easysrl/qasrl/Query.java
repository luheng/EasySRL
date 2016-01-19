package edu.uw.easysrl.qasrl;

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
    int predicateIndex;
    int numTotalParses;
    Map<Integer, Set<Integer>> answerToParses;
    TIntDoubleHashMap answerScores;

    /**
     *
     * @param question: question
     * @param predicateIndex: index of the target predicate in sentence
     * @param numTotalParses: total number of parses in the reranker
     */
    public Query(List<String> question, int predicateIndex, int numTotalParses) {
        this.question = question;
        this.predicateIndex = predicateIndex;
        this.numTotalParses = numTotalParses;
        this.questionScore = 1.0;

        answerToParses = new HashMap<>();
        answerScores = new TIntDoubleHashMap();
        Set<Integer> allParses = new HashSet<>();
        for (int i = 0; i < numTotalParses; i++) {
            allParses.add(i);
        }
        answerToParses.put(-1, allParses);
        answerScores.put(-1, 1.0 * numTotalParses);
    }

    public Query(int predicateIndex, int numTotalParses, List<String> question, double questionScore,
                 Map<Integer, Set<Integer>> answerToParses, TIntDoubleHashMap answerScores) {
        this.predicateIndex = predicateIndex;
        this.numTotalParses = numTotalParses;
        this.question = question;
        this.questionScore = questionScore;
        this.answerToParses = answerToParses;
        this.answerScores = answerScores;
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

    public String getQuestionString() {
        return question.stream().collect(Collectors.joining(" "));
    }

    public Set<Integer> getAllParses() {
        return answerToParses.keySet().stream().filter(a -> a >= 0)
                .flatMap(a2 -> answerToParses.get(a2).stream())
                .collect(Collectors.toSet());
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

    public void print(List<String> sentence, Response response) {
        double entropy = QueryGenerator.getAnswerEntropy(this);
        System.out.println(entropy + "\t" + question.stream().collect(Collectors.joining(" ")) + "?");
        answerToParses.keySet().stream().sorted().forEach(id -> {
            String answerStr = (id < 0) ? "N/A" : sentence.get(id);
            boolean match = (id < 0 && response.answerIds.size() == 0) || response.answerIds.contains(id);
            System.out.print("\t" + (match ? "*" : " ") + answerScores.get(id) + "\t" + answerStr);
            List<int[]> idList = getShortList(new ArrayList<>(answerToParses.get(id)));
            idList.stream().forEach(span -> System.out.print("\t" + (span[0] == span[1] ?
                    span[0] : span[0] + "-" + span[1])));
            // answerToParses.get(id).stream().sorted().forEach(parseId -> System.out.print("\t" + parseId));
            System.out.println();
        });
    }

    /**
     * Summarize a list of ids into spans for better printing.
     * @param list: a list of ids, i.e. 1 2 3 5 8
     * @return a summarized list of spans, i.e. 1-3, 5, 8
     */
    private static List<int[]> getShortList(List<Integer> list) {
        Collections.sort(list);
        List<int[]> shortList = new ArrayList<>();
        for (int i = 0; i < list.size(); ) {
            int j = i + 1;
            for ( ; j < list.size() && list.get(j-1) + 1 == list.get(j); j++ ) ;
            shortList.add(new int[] {list.get(i), list.get(j - 1)});
            i = j;
        }
        return shortList;
    }
    // TODO: compute query scores: expected intelligibility score and expected model change score
    // A high quality question need to be:
    // - derived from a confident lexicon.
    // - derived from a parse with confident ``supporting dependencies'' (other than the target dependency)
}
