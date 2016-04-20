package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.qg.MultiQuestionTemplate;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.Util.Scored;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Profile dependency type, etc.
 * Created by luheng on 4/19/16.
 */
public class DependencyProfiler {
    final ParseData parseData;
    final Map<Integer, NBestList> nBestLists;

    public DependencyProfiler(final ParseData parseData, final Map<Integer, NBestList> nBestLists) {
        this.parseData = parseData;
        this.nBestLists = nBestLists;
    }

    public Set<ProfiledDependency> getAllDependencies(final int sentenceId) {
        final Parse goldParse = parseData.getGoldParses().get(sentenceId);
        final NBestList nBestList = nBestLists.get(sentenceId);
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
                    final ProfiledDependency pDep = new ProfiledDependency(scoredDeps.get(0).getObject(), score);
                    pDep.inGold = dependencyInGold(pDep.dependency, goldParse);
                    pDep.unlabeledInGold = unlabeledDependencyInGold(pDep.dependency, goldParse);
                    pDep.undirectedInGold = undirectedDependencyInGold(pDep.dependency, goldParse);
                    return pDep;
                })
                .collect(Collectors.toSet());
    }

    public ProfiledDependency getProfiledDependency(final int sentenceId, final ResolvedDependency dependency,
                                                    final double score) {
        final Parse goldParse = parseData.getGoldParses().get(sentenceId);
        final ProfiledDependency pDep = new ProfiledDependency(dependency, score);
        pDep.inGold = dependencyInGold(pDep.dependency, goldParse);
        pDep.unlabeledInGold = unlabeledDependencyInGold(pDep.dependency, goldParse);
        pDep.undirectedInGold = undirectedDependencyInGold(pDep.dependency, goldParse);
        return pDep;
    }

    public boolean dependencyInGold(final ResolvedDependency dependency, final Parse goldParse) {
        return goldParse.dependencies.contains(dependency);
    }

    public boolean unlabeledDependencyInGold(final ResolvedDependency dependency, final Parse goldParse) {
        final int headId = dependency.getHead(),
                  argId = dependency.getArgument();
        return goldParse.dependencies.stream()
                .anyMatch(dep -> dep.getHead() == headId && dep.getArgument() == argId);
    }

    public boolean undirectedDependencyInGold(final ResolvedDependency dependency, final Parse goldParse) {
        final int headId = dependency.getHead(),
                  argId = dependency.getArgument();
        return goldParse.dependencies.stream()
                .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                 (dep.getHead() == argId && dep.getArgument() == headId));
    }

    public boolean dependencyIsCore(final ResolvedDependency dependency, boolean relaxedType) {
        if (!relaxedType) {
            return MultiQuestionTemplate.QuestionType.getTypeFor(dependency.getCategory()) ==
                    MultiQuestionTemplate.QuestionType.VERB;
        }
        return RelaxedQuestionType.getTypeFor(dependency.getCategory()) == RelaxedQuestionType.VERB;
    }

    public boolean dependencyIsAdjunct(final ResolvedDependency dependency, boolean relaxedType) {
        return dependency.getCategory() == Category.valueOf("PP/NP") ||
                ImmutableList.of(RelaxedQuestionType.VERB_ADJUNCT, RelaxedQuestionType.NOUN_ADJUNCT)
                .contains(RelaxedQuestionType.getTypeFor(dependency.getCategory()));
    }

    //TODO: get all unlabeled dependencies.

}
