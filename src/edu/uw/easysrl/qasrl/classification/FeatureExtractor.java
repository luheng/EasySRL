package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/21/16.
 */
public class FeatureExtractor {
    CountDictionary featureMap, predicateTypeMap, argTypeMap;
    private boolean acceptNewFeatures = true;

    FeatureExtractor() {
        featureMap = new CountDictionary();
        predicateTypeMap = new CountDictionary();
        argTypeMap = new CountDictionary();
        acceptNewFeatures = true;
    }

    void freeze() {
        acceptNewFeatures = false;
    }

    /**
     *
     * @param headId
     * @param argId
     * @param query
     * @param annotation: a list of option ids, for each annotator.
     * @param sentence
     * @param nBestList
     * @return
     */
    ImmutableMap<Integer, Double> getDependencyInstanceFeatures(final int headId, final int argId,
                                                                final ScoredQuery<QAStructureSurfaceForm> query,
                                                                final ImmutableList<ImmutableList<Integer>> annotation,
                                                                final ImmutableList<String> sentence,
                                                                final NBestList nBestList) {
        Map<Integer, Double> features = new HashMap<>();

        final String argWord = sentence.get(argId);
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();

        // Question Type
        addFeature(features, "QueryType=", query.getQueryType() == QueryType.Forward ? 1 : 2);

        // Dependency types.
        query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .map(qstr -> String.format("%s.%d", qstr.category.getArgument(qstr.targetArgNum), qstr.targetArgNum))
                .distinct()
                .forEach(catArgNum -> addFeature(features, "ArgType", argTypeMap.addString(catArgNum)));

        // Predicate argNum
        query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream().map(q -> q.category.toString()))
                .distinct()
                .forEach(cat -> addFeature(features, "PredicateType", predicateTypeMap.addString(cat)));

        // Dependency contain type
        addFeature(features, "DepContainedIn", DependencyInstanceHelper.getDependencyContainsType(query, headId, argId));

        // NBest score.
        final double nBestScoreNorm = nBestList.getParses().stream().mapToDouble(p -> p.score).sum();
        final double nBestScore = nBestList.getParses().stream()
                .filter(p -> p.dependencies.stream()
                        .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                (dep.getHead() == argId && dep.getArgument() == headId)))
                .mapToDouble(p -> p.score)
                .sum();
        addFeature(features, "NBestScore", 10.0 * nBestScore / nBestScoreNorm);

        addFeature(features, "QueryConfidence", 10.0 * query.getPromptScore());

        // User votes.
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final ImmutableList<Integer> options = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> DependencyInstanceHelper.containsDependency(query.getQAPairSurfaceForms().get(i),
                        headId, argId))
                .collect(GuavaCollectors.toImmutableList());

        addFeature(features, "ContainedByNumOptions", options.size());
        int maxVotes = options.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().getAsInt();
        addFeature(features, "MaxReceivedVotes", maxVotes);
        int superspanVotes = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> options.stream().map(query.getOptions()::get)
                        .anyMatch(op -> query.getOptions().get(i).toLowerCase().contains(op.toLowerCase())))
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().getAsInt();
        addFeature(features, "SuperspanReceivedVotes", superspanVotes);

        addFeature(features, "NAOptionReceivedVotes", (int) annotation.stream()
                .filter(ops -> ops.contains(naOptionId)).count());

        // Pronoun stuff.
        if (PronounList.englishPronounSet.contains(argWord.toLowerCase())) {
            addFeature(features, "ArgIsPronoun", 1);
        }
        if (query.getOptions().stream().anyMatch(op -> PronounList.englishPronounSet.contains(op.toLowerCase()))) {
            addFeature(features, "OptionsContainPronoun", 1);
        }

        addFeature(features, "ArgRelativePosition", argId < headId ? 1 : (argId > headId ? 3 : 2));

        //addFeature(features, "PrecedingComma", );
        /*
        singleVotes = new AtomicInteger(0); // Number of annotators that picked this option only.
        IntStream.range(0, query.getQAPairSurfaceForms().size())
                .filter(i -> DependencyInstanceHelper.containsDependency(query.getQAPairSurfaceForms().get(i), headId, argId))
                .forEach(i -> {
                    final int numVotes = (int) annotation.stream().filter(ops -> ops.contains(i)).count();
                    final int numSingleVotes = (int) annotation.stream()
                            .filter(ops -> ops.contains(i) && ops.size() == 1).count();
                    votes.addAndGet(numVotes);
                    singleVotes.addAndGet(numSingleVotes);
                });

        if (query.getPredicateId().isPresent() && headId == query.getPredicateId().getAsInt()) {
            IntStream.range(0, query.getQAPairSurfaceForms().size())
                    .filter(i -> query.getOptions().get(i).toLowerCase().contains(sentence.get(argId).toLowerCase()))
                    .forEach(i -> subspanVotes.addAndGet((int) annotation.stream()
                            .filter(ops -> ops.contains(i)).count()));
        }
        addFeature(features, "ChosenByNumAnnotatorsOnlyThis", singleVotes.intValue());
        */

        return ImmutableMap.copyOf(features);
        //boolean inGold = gold.dependencies.stream().anyMatch(d -> d.getHead() == headId && d.getArgument() == argId);
        //return new DependencyInstance(sentenceId, headId, argId, inGold, ImmutableMap.copyOf(features));
    }

    // TODO: return dependency types.
    // TODO: separate label extraction and feature extraction.

    private boolean addFeature(final Map<Integer, Double> features, String featureName, double featureValue) {
        int featureId = featureMap.addString(featureName, acceptNewFeatures);
        if (featureId < 0) {
            return false;
        }
        features.put(featureId, featureValue);
        return true;
    }

    public void printFeature(final Map<Integer, Double> features) {
        for (int fid : features.keySet()) {
            System.out.println(featureMap.getString(fid) + "\t=\t" + features.get(fid));
        }
    }
}
