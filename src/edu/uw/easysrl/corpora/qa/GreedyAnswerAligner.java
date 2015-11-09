package edu.uw.easysrl.corpora.qa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luheng on 11/9/15.
 */
@SuppressWarnings("unused")
public class GreedyAnswerAligner implements AbstractAnswerAligner {
    @Override
    public List<Integer> align(List<String[]> answers, List<String> words) {
        int sentenceLength = words.size();
        int[] matched = new int[sentenceLength];
        Arrays.fill(matched, 0);
        for (String[] answerTokens : answers) {
            int answerLength = answerTokens.length;
            for (int i = 0; i < answerLength; i++) {
                int maxMatchedLength = 0;
                ArrayList<Integer> bestMatches = new ArrayList<>();
                for (int j = 0; j + maxMatchedLength < sentenceLength; j++) {
                    int k = 0;
                    for (; j + k < sentenceLength && i + k < answerLength; k++) {
                        if (!answerTokens[i + k].equalsIgnoreCase(words.get(j + k))) {
                            break;
                        }
                    }
                    if (k > maxMatchedLength) {
                        maxMatchedLength = k;
                        bestMatches.clear();
                        bestMatches.add(j);
                    } else if (k == maxMatchedLength) {
                        bestMatches.add(j);
                    }
                }
                if (maxMatchedLength > 0) {
                    for (int match : bestMatches) {
                        for (int k = 0; k < maxMatchedLength; k++) {
                            matched[match + k] = 1;
                        }
                    }
                    i += maxMatchedLength - 1;
                }
            }
        }
        List<Integer> aligned = new ArrayList<>();
        for (int i = 0; i < sentenceLength; i++) {
            if (matched[i] > 0) {
                aligned.add(i);
            }
        }
        return aligned;
    }
}
