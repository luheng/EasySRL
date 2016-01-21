package edu.uw.easysrl.qasrl;

import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Queries with multiple question strings.
 * Created by luheng on 1/18/16.
 */
public class GroupedQueryOld {
    int predicateIndex;
    int numTotalParses;
    Map<String, Set<Integer>> questionToParses;
    Map<Integer, Set<Integer>> answerToParses;
    TObjectDoubleHashMap<String> questionScores;
    TIntDoubleHashMap answerScores;

    public GroupedQueryOld(int predicateIndex, int numTotalParses, final Set<Integer> answerIds) {
        this.predicateIndex = predicateIndex;
        this.numTotalParses = numTotalParses;
        questionToParses = new HashMap<>();
        questionScores = new TObjectDoubleHashMap<>();

        answerToParses = new HashMap<>();
        answerScores = new TIntDoubleHashMap();
        answerIds.forEach(a -> answerToParses.put(a, new HashSet<>()));

        Set<Integer> allParses = IntStream.range(0, numTotalParses).boxed().collect(Collectors.toSet());
        answerToParses.put(-1, allParses);
        answerScores.put(-1, 1.0 * allParses.size());
    }

    public void addQuery(QueryOld query) {
        String qstr = query.getQuestionString();
        double questionScore = query.getAllParses().size();
        if (!questionToParses.containsKey(qstr)) {
            questionToParses.put(qstr, new HashSet<>());
        }
        questionToParses.get(qstr).addAll(query.getAllParses());
        questionScores.adjustOrPutValue(qstr, questionScore, questionScore);
        query.answerToParses.forEach((answerId, parseIds) -> {
            if (answerId >= 0) {
                double answerScore = query.answerScores.get(answerId);
                answerToParses.get(answerId).addAll(parseIds);
                answerToParses.get(-1).removeAll(parseIds);
                answerScores.adjustOrPutValue(answerId, answerScore, answerScore);
                answerScores.put(-1, answerToParses.get(-1).size());
            }
        });
    }

    public QueryOld getQuery() {
        // Pick the surface form with highest score.
        String bestQuestion = "";
        double bestScore = -1.0;
        for (String qstr : questionScores.keySet()) {
            double score = questionScores.get(qstr);
            if (score > bestScore) {
                bestScore = score;
                bestQuestion = qstr;
            }
        }
        return new QueryOld(predicateIndex, numTotalParses, Arrays.asList(bestQuestion.split("\\s+")), bestScore,
                         answerToParses, answerScores);
    }

    public void print(List<String> sentence) {
        double entropy = QueryGenerator.getAnswerEntropy(this);
        questionToParses.keySet().stream().forEach(qstr -> {
            System.out.print(String.format("%.3f, %.3f\t%s", questionScores.get(qstr), entropy, qstr + "?"));
            List<int[]> idList = getShortList(new ArrayList<>(questionToParses.get(qstr)));
            idList.stream().forEach(span -> System.out.print("\t" + (span[0] == span[1] ?
                    span[0] : span[0] + "-" + span[1])));
            System.out.println();
        });
        answerToParses.keySet().stream().sorted().forEach(id -> {
            String answerStr = (id < 0) ? "N/A" : sentence.get(id);
            System.out.print(String.format("\t%.3f\t%s", answerScores.get(id), answerStr));
            List<int[]> idList = getShortList(new ArrayList<>(answerToParses.get(id)));
            idList.stream().forEach(span -> System.out.print("\t" + (span[0] == span[1] ?
                    span[0] : span[0] + "-" + span[1])));
            System.out.println();
        });
    }

    public void print(List<String> sentence, Response response) {
        double entropy = QueryGenerator.getAnswerEntropy(this);
        questionToParses.keySet().stream().forEach(qstr -> {
            System.out.print(String.format("%.3f, %.3f\t%s", questionScores.get(qstr), entropy, qstr + "?"));
            List<int[]> idList = getShortList(new ArrayList<>(questionToParses.get(qstr)));
            idList.stream().forEach(span -> System.out.print("\t" + (span[0] == span[1] ?
                    span[0] : span[0] + "-" + span[1])));
            System.out.println();
        });
        answerToParses.keySet().stream().sorted().forEach(id -> {
            String answerStr = (id < 0) ? "N/A" : sentence.get(id);
            boolean match = (id < 0 && response.answerIds.size() == 0) || response.answerIds.contains(id);
            System.out.print(String.format("\t%s%.3f\t%s", (match ? "*" : " "), answerScores.get(id), answerStr));
            List<int[]> idList = getShortList(new ArrayList<>(answerToParses.get(id)));
            idList.stream().forEach(span -> System.out.print("\t" + (span[0] == span[1] ?
                    span[0] : span[0] + "-" + span[1])));
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
