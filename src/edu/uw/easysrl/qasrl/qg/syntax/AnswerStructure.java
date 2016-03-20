package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Contains information about the answer spans.
 * Created by luheng on 3/19/16.
 */
public class AnswerStructure {
    public final ImmutableList<Integer> argumentIndices;
    public final ImmutableSet<Integer> parseIds;
    public final double score;

    public AnswerStructure(Collection<Integer> argIds, ImmutableSet<Integer> parseIds, double score) {
        this.argumentIndices = ImmutableList.copyOf(argIds.stream().sorted().collect(Collectors.toList()));
        this.parseIds = parseIds;
        this.score = score;
    }
}
