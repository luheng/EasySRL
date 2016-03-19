package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/19/16.
 */
public class AnswerStructure {
    public final ImmutableList<Integer> argumentIndices;

    public AnswerStructure(Collection<Integer> argIds) {
        this.argumentIndices = ImmutableList.copyOf(argIds.stream().sorted().collect(Collectors.toList()));
    }
}
