package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.HashMap;
import java.util.Map;
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
        //final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final ImmutableList<QuestionStructure> questionStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());

        // Query Type
        // addFeature(features, "QueryType=" + query.getQueryType(), 1);

        // Argument category.
        /*
        questionStructures.stream()
                .map(qstr -> qstr.category.getArgument(qstr.targetArgNum))
                .distinct()
                .forEach(argCat -> addFeature(features, "ArgumentCategory=" + argCat, 1));
        */

        // Patient-agent
        questionStructures.stream()
                .map(qstr -> qstr.targetArgNum == 1 ? "Agent" : "Patient")
                .distinct()
                .forEach(argType -> addFeature(features, "ArgumentType=" + argType, 1));

        // Predicate type
        //if (questionStructures.stream().anyMatch(qstr -> qstr.category.isFunctionInto(Category.valueOf("S[pss]")))) {
        //    addFeature(features, "PredicateIsPassive", 1);
        //}

        // Dependency contain type
        // final String depQATApe = DependencyInstanceHelper.getDependencyContainsType(query, headId, argId);
        // addFeature(features, "DepContainedType=" + depQATApe, 1);

        // 1best score
        /*
        final boolean oneBestContains = nBestList.getParse(0).dependencies.stream()
                .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                 (dep.getHead() == argId && dep.getArgument() == headId));
        if (oneBestContains) {
            addFeature(features, "1BestContains", 1);
        }
        */

        final int numAnnotators = annotation.size();
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final ImmutableList<Integer> options = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> DependencyInstanceHelper.containsDependency(query.getQAPairSurfaceForms().get(i),
                        headId, argId))
                .collect(GuavaCollectors.toImmutableList());


        // NBest score.
        /*
        final double nBestScoreNorm = nBestList.getParses().stream().mapToDouble(p -> p.score).sum();
        final double nBestScore = nBestList.getParses().stream()
                .filter(p -> p.dependencies.stream()
                        .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                         (dep.getHead() == argId && dep.getArgument() == headId)))
                .mapToDouble(p -> p.score)
                .sum();
                */
        final double nBestScore = options.stream()
                .mapToDouble(i -> query.getOptionScores().get(i))
                .sum() / query.getPromptScore();
        addFeature(features, "NBestScore", nBestScore);

        final int firstEncounterInNBest = IntStream.range(0, nBestList.getN())
                .filter(k -> nBestList.getParse(k).dependencies.stream()
                        .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                         (dep.getHead() == argId && dep.getArgument() == headId)))
                .findFirst().orElse(nBestList.getN());
        addFeature(features, "firstEncounterInNBest", 1.0 - firstEncounterInNBest / nBestList.getN());

        // User votes.
        int minVotes = options.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .min().orElse(0);
        addFeature(features, "MinReceivedVotes", 1.0 * minVotes / numAnnotators);

        int maxVotes = options.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().orElse(0);
        addFeature(features, "MaxReceivedVotes", 1.0 * maxVotes / numAnnotators);

        int otherVotes = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> !options.contains(i))
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().orElse(0);
        addFeature(features, "OtherReceivedVotes", 1.0 * otherVotes / numAnnotators);

        ImmutableList<Integer> superSpanOptions = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> options.stream().map(query.getOptions()::get)
                        .anyMatch(op -> query.getOptions().get(i).toLowerCase().contains(op.toLowerCase()) &&
                               // !op.equals(query.getOptions().get(i)) &&
                                !query.getOptions().get(i).contains("_AND_") ))
                .collect(GuavaCollectors.toImmutableList());
        ImmutableList<Integer> subSpanOptions = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> options.stream().map(query.getOptions()::get)
                        .anyMatch(op -> op.toLowerCase().contains(query.getOptions().get(i).toLowerCase()) &&
                              //  !op.equals(query.getOptions().get(i)) &&
                                !op.contains("_AND_") ))
                .collect(GuavaCollectors.toImmutableList());

        addFeature(features, "SuperSpanScores", superSpanOptions.stream()
                .mapToDouble(query.getOptionScores()::get)
                .sum());
        addFeature(features, "SuperSpanReceivedVotes", 1.0 / numAnnotators * superSpanOptions.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().orElse(0));

        addFeature(features, "SubSpanScores", subSpanOptions.stream()
                .mapToDouble(query.getOptionScores()::get)
                .sum());
        addFeature(features, "SubSpanReceivedVotes", 1.0 / numAnnotators * subSpanOptions.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().orElse(0));

        /*
        addFeature(features, "QueryConfidence", query.getPromptScore());
        addFeature(features, "NAOptionReceivedVotes", 1.0 * annotation.stream()
                .filter(ops -> ops.contains(naOptionId)).count() / numAnnotators);*/

        // Pronoun stuff.
        if (PronounList.englishPronounSet.contains(argWord.toLowerCase())) {
            addFeature(features, "ArgIsPronoun", 1);
        }
        ImmutableList<Integer> pronounOptions = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> PronounList.englishPronounSet.contains(query.getOptions().get(i).toLowerCase()))
                .collect(GuavaCollectors.toImmutableList());
        double pronounScores = pronounOptions.stream()
                .mapToDouble(query.getOptionScores()::get)
                .sum();
        addFeature(features, "PronounScores", pronounScores);

        int pronounVotes = pronounOptions.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .max().orElse(0);
        addFeature(features, "PronounVotes", 1.0 * pronounVotes / numAnnotators);

        // Trailing tokens.
        if (argId + 1 < sentence.size()) {
            final String tok = sentence.get(argId + 1);
            if (ImmutableSet.of("of", "who", "that", "which").contains(tok)) {
                addFeature(features, "TrailingToken=" + tok, 1);
            }
        }

        // Preceding comma.
        for (int i = argId - 1; i >= 0; i--) {
            if (sentence.get(i).equals(",")) {
                addFeature(features, "PrecedingComma", 1.0 - 1.0 * (argId - i - 1) / sentence.size());
                break;
            }
        }

        // Trailing comma.
        for (int i = argId + 1; i < sentence.size(); i++) {
            if (sentence.get(i).equals(",")) {
                addFeature(features, "TrailingComma", 1.0 - 1.0 * (i - argId - 1) / sentence.size());
                break;
            }
        }

        //addFeature(features, argId < headId ? "ArgOnLeft" : "ArgOnRight", 1);

        return ImmutableMap.copyOf(features);
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
