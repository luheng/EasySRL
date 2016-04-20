package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.analysis.DependencyProfiler;
import edu.uw.easysrl.qasrl.analysis.ProfiledDependency;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;
import edu.uw.easysrl.util.Util;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simulation experiments with oracle parsing.
 * Created by luheng on 3/29/16.
 */
public class OracleExperiment {
    // Parameters.
    private static int nBest = 100;
    private static int maxNumSentences = 1000;

    // Shared data: nBestList, sentences, etc.
    private static HITLParser myHITLParser;
    private static ReparsingHistory myHITLHistory;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minPromptConfidence = 0.1;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.skipBinaryQueries = true;
        queryPruningParameters.skipPPQuestions = false;
    }
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.oraclePenaltyWeight = Integer.MAX_VALUE;
        reparsingParameters.skipPronounEvidence = false;
        reparsingParameters.skipJeopardyQuestions = false;
    }

    private static ImmutableList<Integer> skipSentenceIds = ImmutableList.of(604);

    private static Set<Constraint> getAttachmentConstraints(final Stream <ProfiledDependency> dependencyStream) {
        return dependencyStream
                .map(dep -> dep.dependency)
                .map(dep -> new Constraint.AttachmentConstraint(dep.getHead(), dep.getArgument(), true,
                                                                reparsingParameters.oraclePenaltyWeight))
                .collect(Collectors.toSet());
    }

    public static void main(String[] args) {
        myHITLParser = new HITLParser(nBest);
        myHITLParser.setQueryPruningParameters(queryPruningParameters);
        myHITLParser.setReparsingParameters(reparsingParameters);

        myHITLHistory = new ReparsingHistory(myHITLParser);
        AtomicInteger sentenceCounter = new AtomicInteger(0);
        AtomicInteger numConstraints = new AtomicInteger(0);
        Results avgBaseline = new Results(), avgOracle = new Results();

        DependencyProfiler dependencyProfiler = new DependencyProfiler(
                myHITLParser.getParseData(),
                myHITLParser.getAllSentenceIds().stream()
                        .collect(Collectors.toMap(Function.identity(), myHITLParser::getNBestList))
        );
        // TODO: average number of dependencies.
        myHITLParser.getAllSentenceIds().stream()
                .filter(sentenceId -> !skipSentenceIds.contains(sentenceId))
                .limit(maxNumSentences)
                .forEach(sentenceId -> {
                    final ImmutableList<String> sentence = myHITLParser.getSentence(sentenceId);
                    final Parse gold = myHITLParser.getGoldParse(sentenceId);
                    final Set<ProfiledDependency> allDeps = //dependencyProfiler.getAllDependencies(sentenceId);
                            gold.dependencies.stream()
                                    .map(d -> dependencyProfiler.getProfiledDependency(sentenceId, d, 1.0))
                                    .collect(Collectors.toSet());

                    final ImmutableSet<ProfiledDependency> coreDeps = allDeps.stream()
                            .filter(d -> dependencyProfiler.dependencyIsCore(d.dependency, false))
                            .collect(GuavaCollectors.toImmutableSet());

                    final ImmutableSet<ProfiledDependency> uncoveredCoreDeps = allDeps.stream()
                            .filter(d -> !dependencyProfiler.dependencyIsCore(d.dependency, false))
                            .filter(d -> dependencyProfiler.dependencyIsCore(d.dependency, true))
                            .collect(GuavaCollectors.toImmutableSet());

                    final ImmutableSet<ProfiledDependency> adjunctDeps = allDeps.stream()
                            .filter(d -> !dependencyProfiler.dependencyIsCore(d.dependency, true))
                            .filter(d -> dependencyProfiler.dependencyIsAdjunct(d.dependency, false))
                            .filter(d -> !(d.dependency.getCategory() == Category.valueOf("(NP\\NP)/NP") &&
                                            sentence.get(d.dependency.getHead()).equalsIgnoreCase("of")))
                            .collect(GuavaCollectors.toImmutableSet());

                    final ImmutableSet<ProfiledDependency> otherDeps = allDeps.stream()
                            .filter(d -> !dependencyProfiler.dependencyIsCore(d.dependency, true))
                            .filter(d -> !dependencyProfiler.dependencyIsAdjunct(d.dependency, false))
                            .collect(GuavaCollectors.toImmutableSet());

                    // TODO: check constraint violation.

                    final Set<Constraint> constraints = getAttachmentConstraints(Stream.concat(
                            coreDeps.stream(),
                            //uncoveredCoreDeps.stream()
                            //Stream.empty()
                            adjunctDeps.stream()
                            //adjunctDeps.stream().filter(d -> d.dependency.getCategory() == Category.valueOf("(NP\\NP)/NP"))
                    ));


                    //constraints.forEach(c -> System.out.println(c.toString(sentence)));
                    final Parse reparsed = myHITLParser.getReparsed(sentenceId,  constraints);
                    final Results baselineF1 = myHITLParser.getNBestList(sentenceId).getResults(0);
                    final Results oracleF1 = CcgEvaluation.evaluate(reparsed.dependencies, gold.dependencies);

                    if (oracleF1.getF1() < 99.0) {
                        System.out.println(sentenceId + "\t" + TextGenerationHelper.renderString(sentence));
                        otherDeps.forEach(d -> System.out.println(d.toString(sentence)));

                        System.out.println(baselineF1);
                        System.out.println(oracleF1);
                        System.out.println();
                    }

                    avgBaseline.add(baselineF1);
                    avgOracle.add(oracleF1);
                    numConstraints.addAndGet(constraints.size());
                    sentenceCounter.addAndGet(1);


                    /*
                    System.out.println(TextGenerationHelper.renderString(sentence));
                    System.out.println(allDeps.size());
                    System.out.println("[Core]");
                    coreDeps.forEach(d -> System.out.println(d.toString(sentence)));
                    System.out.println("[Uncovered core]");
                    uncoveredCoreDeps.forEach(d -> System.out.println(d.toString(sentence)));
                    System.out.println("[Adjunct]");
                    adjunctDeps.forEach(d -> System.out.println(d.toString(sentence)));
                    System.out.println();
                    */
                });

        System.out.println(avgBaseline);
        System.out.println(avgOracle);
        System.out.println(1.0 * numConstraints.get() / sentenceCounter.get());
        //System.out.println("Num. core queries:\t" + numCoreQueries + "\tAcc:\t" + 1.0 * coreQueryAcc.get() / numCoreQueries.get());
    }
}
