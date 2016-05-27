package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;

import java.util.stream.Collectors;

/**
 * New cleaned up class for annotation.
 * Created by luheng on 5/26/16.
 */
public class AnnotatedQuery {
    public int sentenceId, predicateId;
    public String sentenceString, questionString;
    public ImmutableList<String> optionStrings;
    public ImmutableList<ImmutableList<Integer>> responses;

    public AnnotatedQuery() {}

    public String toString() {
        return String.format("%d\t%s\n%d\t%s\n%s\n%s", sentenceId, sentenceString, predicateId, questionString,
                optionStrings.stream().collect(Collectors.joining("\n")),
                responses);
    }
}
