package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicDouble;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
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
     * @param nBestList
     * @return
     */
    ImmutableMap<Integer, Double> getDependencyInstanceFeatures(final int headId, final int argId,
                                                                final ScoredQuery<QAStructureSurfaceForm> query,
                                                                final ImmutableList<ImmutableList<Integer>> annotation,
                                                                final int sentenceId,
                                                                final ImmutableList<String> sentence,
                                                                final NBestList nBestList) {
        Map<Integer, Double> features = new HashMap<>();

        final String argWord = sentence.get(argId);
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();

        if (PronounList.englishPronounSet.contains(argWord.toLowerCase())) {
            addFeature(features, "ArgIsPronoun", 1);
        }
        if (query.getOptions().stream().anyMatch(op -> PronounList.englishPronounSet.contains(op.toLowerCase()))) {
            addFeature(features, "OptionsContainPronoun", 1);
        }
        if (query.getQueryType() == QueryType.Clefted) {
            addFeature(features, "CleftingQuestion", 1);
        }
        // Dependency types.
        query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream()
                        .map(qstr -> qstr.category.getArgument(qstr.targetArgNum)))
                .distinct()
                .forEach(cat -> addFeature(features, String.format("QueryArgType=%s", cat), 1));

        //addFeature(features, "PrecedingComma", );

        AtomicInteger annotatorVotes = new AtomicInteger(0),
                      annotatorSingleVotes = new AtomicInteger(0); // Number of annotators that picked this option only.
        AtomicDouble nBestScore = new AtomicDouble(0);

        IntStream.range(0, query.getQAPairSurfaceForms().size())
                .filter(i -> DependencyInstanceHelper.containsDependency(query.getQAPairSurfaceForms().get(i), headId, argId))
                .forEach(i -> {
                    //final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(i);
                    final int numVotes = (int) annotation.stream().filter(ops -> ops.contains(i)).count();
                    final int numSingleVotes = (int)annotation.stream()
                            .filter(ops -> ops.contains(i) && ops.size() == 1).count();
                    annotatorVotes.addAndGet(numVotes);
                    annotatorSingleVotes.addAndGet(numSingleVotes);
                    nBestScore.addAndGet(query.getOptionScores().get(i));
                });

        addFeature(features, "NBestScore", nBestScore.doubleValue());
        addFeature(features, "ChosenByNumAnnotators", annotatorVotes.intValue());
        addFeature(features, "ChosenByNumAnnotatorsOnlyThis", annotatorSingleVotes.intValue());
        addFeature(features, "NAChosenByNumAnnotators", (int) annotation.stream()
                .filter(ops -> ops.contains(naOptionId)).count());

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
}
