package edu.uw.easysrl.qasrl;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A printer for debugging information, what else do you expect?
 * Created by luheng on 1/17/16.
 */
public class DebugPrinter {
    // TODO TODO
    public static void printQueryListInfo(int sentIdx, List<String> words, List<Parse> parseList, List<Query> queryList,
                                          List<Response> responseList) {
        System.out.println("\n" + String.format("S[%d]:\t", sentIdx) +
                words.stream().collect(Collectors.joining(" ")));
        for (int i = 0; i < queryList.size(); i++) {
            Query query = queryList.get(i);
            Response response = responseList.get(i);

            query.print(words);
        }
    }
}
