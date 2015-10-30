package edu.uw.easysrl.corpora.qa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luheng on 10/29/15.
 */
public class AnswerAligner {

    public List<Integer> align(List<String[]> answers, List<String> words) {
        int sentenceLength = words.size();
        int[] aligned = new int[sentenceLength];
        Arrays.fill(aligned, 0);
        for (String[] answer : answers) {
            // Try to find the longest match ...
            int answerLength = answer.length;
            int[][] f = new int[answerLength + 1][sentenceLength + 1];
            int[][][] a = new int[answerLength + 1][sentenceLength + 1][2];
            for (int i = 0; i < answerLength; i++) {
                Arrays.fill(f[i], -1);
            }
            f[0][0] = 0;
            for (int i = 0; i < answerLength; i++) {
                for (int j = 0; j < sentenceLength; j++) {
                    if (answer[i].equalsIgnoreCase(words.get(j))) {
                        update(f, a, i, j, i + 1, j + 1, 1);
                    }
                    update(f, a, i, j, i + 1, j, 0);
                    update(f, a, i, j, i, j + 1, 0);
                }
            }
            for (int i = answerLength, j = sentenceLength; i > 0 || j > 0; ) {
                int i0 = a[i][j][0], j0 = a[i][j][1];
                if (i0 < i && j0 < j) {
                    aligned[j0] ++;
                }
                i = i0;
                j = j0;
            }
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < sentenceLength; i++) {
            if (aligned[i] > 0) {
                indices.add(i);
            }
        }
        return indices;
    }

    private void update(int[][] f, int[][][] a, int x1, int y1, int x2, int y2, int delta) {
        if (f[x1][y1] + delta > f[x2][y2]) {
            f[x2][y2] = f[x1][y1] + delta;
            a[x2][y2][0] = x1;
            a[x2][y2][1] = y1;
        }
    }
}
