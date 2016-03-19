package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Contains information about the answer spans.
 * Created by luheng on 3/19/16.
 */
public class AnswerStructure {
    public final ImmutableList<Integer> argumentIndices;

    public AnswerStructure(Collection<Integer> argIds) {
        this.argumentIndices = ImmutableList.copyOf(argIds.stream().sorted().collect(Collectors.toList()));
    }
}
