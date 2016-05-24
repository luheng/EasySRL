package edu.uw.easysrl.qasrl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A printer for debugging information, what else do you expect?
 * Created by luheng on 1/17/16.
 */
public class DebugHelper {
    public static void printQueryInfo(List<String> words, GroupedQuery query, int response, final Parse goldParse) {
        System.out.println(String.format("S[%d]:\t", query.sentenceId) + words.stream()
                .collect(Collectors.joining(" ")));
        query.printWithGoldDependency(words, response, goldParse);
    }

    public static void printQueryInfo(List<String> words, GroupedQuery query, Response response, Response goldResponse) {
        System.out.println(String.format("S[%d]:\t", query.sentenceId) + words.stream()
                .collect(Collectors.joining(" ")));
        query.print(words, response);
        System.out.println("[gold]");
        query.print(words, goldResponse);
    }

    /**
     * Summarize a list of ids into spans for better printing.
     * @param inputList: a list of ids, i.e. 1 2 3 5 8
     * @return a summarized list of spans, i.e. 1-3, 5, 8
     */
    public static String getShortListString(final Collection<Integer> inputList) {
        List<Integer> list = new ArrayList<>(inputList);
        Collections.sort(list);
        List<int[]> shortList = new ArrayList<>();
        for (int i = 0; i < list.size(); ) {
            int j = i + 1;
            for ( ; j < list.size() && list.get(j-1) + 1 == list.get(j); j++ ) ;
            shortList.add(new int[] {list.get(i), list.get(j - 1)});
            i = j;
        }
        return shortList.stream().map(r -> (r[0] == r[1] ? String.valueOf(r[0]) : String.valueOf(r[0]) + "-" + r[1]))
                .collect(Collectors.joining(","));
    }

}
