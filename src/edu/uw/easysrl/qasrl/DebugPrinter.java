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
public class DebugPrinter {
    // TODO TODO
    public static void printQueryListInfo(int sentIdx, List<String> words, List<Parse> parseList,
                                          List<QueryOld> queryList, List<Response> responseList) {
        System.out.println("\n" + String.format("S[%d]:\t", sentIdx) + words.stream().collect(Collectors.joining(" ")));
        for (int i = 0; i < queryList.size(); i++) {
            QueryOld query = queryList.get(i);
            Response response = responseList.get(i);

            query.print(words, response);
        }
    }

    public static void printQueryListInfo(int sentIdx, List<String> words, List<Parse> parseList,
                                          List<QueryOld> queryList, List<Response> responseList,
                                          List<Response> goldResponseList) {
        System.out.println("\n" + String.format("S[%d]:\t", sentIdx) + words.stream().collect(Collectors.joining(" ")));
        for (int i = 0; i < queryList.size(); i++) {
            QueryOld query = queryList.get(i);
            query.print(words, responseList.get(i), goldResponseList.get(i));
        }
    }
    public static void printQueryListInfo(int sentIdx, List<String> words, List<GroupedQuery> queryList,
                                          List<Integer> responseList) {
        System.out.println("\n" + String.format("S[%d]:\t", sentIdx) + words.stream().collect(Collectors.joining(" ")));
        for (int i = 0; i < queryList.size(); i++) {
            queryList.get(i).print(words, responseList.get(i));
        }
    }


    /**
     * Summarize a list of ids into spans for better printing.
     * @param list: a list of ids, i.e. 1 2 3 5 8
     * @return a summarized list of spans, i.e. 1-3, 5, 8
     */
    public static List<int[]> getShortList(List<Integer> list) {
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
