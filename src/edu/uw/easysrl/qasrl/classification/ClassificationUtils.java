package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/21/16.
 */
public class ClassificationUtils {

    /**
     * Split a list of objects into n parts.
     * @param objectList
     * @param ratio: list of size n.
     * @param randomSeed
     * @return
     */
    public static <O extends Object> ImmutableList<ImmutableList<O>> jackknife(final List<O> objectList,
                                                                               final List<Double> ratio,
                                                                               final int randomSeed) {
        final int totalNum = objectList.size();
        final List<Integer> shuffledIds = IntStream.range(0, totalNum).boxed().collect(Collectors.toList());
        Collections.shuffle(shuffledIds, new Random(randomSeed));

        assert ratio.stream().mapToDouble(r -> r).sum() <= 1.0;

        final ImmutableList<Integer> splits = IntStream.range(0, ratio.size() + 1)
                .boxed()
                .map(i -> (int) Math.floor(totalNum * IntStream.range(0, i).mapToDouble(ratio::get).sum()))
                .collect(GuavaCollectors.toImmutableList());

        return IntStream.range(0, ratio.size())
                .boxed()
                .map(i -> shuffledIds.subList(splits.get(i), splits.get(i+1))
                        .stream()
                        .map(objectList::get)
                        .collect(GuavaCollectors.toImmutableList()))
                .collect(GuavaCollectors.toImmutableList());
    }
}
