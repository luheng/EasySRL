package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/21/16.
 */
public class FeatureExtractor {
    CountDictionary featureMap;
    private boolean acceptNewFeatures = true;

    FeatureExtractor() {
        featureMap = new CountDictionary();
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
     * @param sentenceId
     * @param sentence
     * @param gold
     * @param nBestList
     * @return
     */
    DependencyInstance getDependencyInstance(final int headId, final int argId,
                                             final ScoredQuery<QAStructureSurfaceForm> query,
                                             final ImmutableList<ImmutableList<Integer>> annotation,
                                             final int sentenceId,
                                             final ImmutableList<String> sentence,
                                             final Parse gold,
                                             final NBestList nBestList) {
        Map<Integer, Double> features = new HashMap<>();

        final String argWord = sentence.get(argId);
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();

        addFeature(features, "ArgIsPronoun", PronounList.englishPronounSet.contains(argWord.toLowerCase()) ? 1 : 0);
        addFeature(features, "CleftingQuestion", query.getQueryType() == QueryType.Clefted ? 1 : 0);

        //addFeature(features, "PrecedingComma", );

        AtomicInteger annotatorVotes = new AtomicInteger(0);
        AtomicDouble nBestScore = new AtomicDouble(0);

        IntStream.range(0, query.getQAPairSurfaceForms().size())
                .filter(i -> containsDependency(query.getQAPairSurfaceForms().get(i), headId, argId))
                .forEach(i -> {
                    final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(i);
                    final int numAnnotators = (int) annotation.stream().filter(ops -> ops.contains(i)).count();
                    annotatorVotes.addAndGet(numAnnotators);
                    nBestScore.addAndGet(query.getOptionScores().get(i));
                });

        addFeature(features, "NBestScore", nBestScore.doubleValue());
        addFeature(features, "ChosenByNumAnnotators", annotatorVotes.intValue());
        addFeature(features, "NAChosenByNumAnnotators", (int) annotation.stream()
                .filter(ops -> ops.contains(naOptionId)).count());

        boolean inGold = gold.dependencies.stream().anyMatch(d -> d.getHead() == headId && d.getArgument() == argId);
        return new DependencyInstance(sentenceId, headId, argId, inGold, ImmutableMap.copyOf(features));
    }

    // FIXME: handle pp args etc.
    private boolean containsDependency(QAStructureSurfaceForm qa, int headId, int argId) {
        return qa.getAnswerStructures().stream().anyMatch(
                a -> a.argumentIndices.contains(argId) || a.adjunctDependencies.stream()
                        .anyMatch(dep -> dep.getHead() == headId && dep.getArgument() == argId));
    }

    private boolean addFeature(final Map<Integer, Double> features, String featureName, double featureValue) {
        int featureId = featureMap.addString(featureName, acceptNewFeatures);
        if (featureId < 0) {
            return false;
        }
        features.put(featureId, featureValue);
        return true;
    }
}
