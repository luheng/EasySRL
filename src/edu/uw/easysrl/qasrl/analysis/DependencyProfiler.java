package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.util.Util.Scored;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luheng on 4/19/16.
 */
public class DependencyProfiler {

    public static Set<ProfiledDependency> getAllDependencies(final NBestList nBestList) {
        final double scoreNorm = nBestList.getParses().stream()
                .mapToDouble(p -> p.score)
                .sum();
        return nBestList.getParses().stream()
                .flatMap(parse -> parse.dependencies.stream()
                        .map(dep -> new Scored<>(dep, parse.score)))
                .collect(Collectors.groupingBy(Scored::getObject))
                .values().stream()
                .map(scoredDeps -> {
                    final double score = scoredDeps.stream().mapToDouble(Scored::getScore).sum() / scoreNorm;
                    return new ProfiledDependency(scoredDeps.get(0).getObject(), score);
                })
                .collect(Collectors.toSet());
    }

    //TODO: get all unlabeled dependencies.

}
