package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
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
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final ImmutableList<QuestionStructure> questionStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<AnswerStructure> answerStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getAnswerStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> candidateHeads = answerStructures.stream()
                .flatMap(astr -> astr.argumentIndices.stream())
                .distinct()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());

        // Argument category.
        questionStructures.stream()
                .map(qstr -> qstr.category.getArgument(qstr.targetArgNum))
                .distinct()
                .forEach(argCat -> addFeature(features, "ArgumentCategory=" + argCat, 1));

        // Patient-agent
        questionStructures.stream()
                .map(qstr -> qstr.targetArgNum == 1 ? "Agent" : "Patient")
                .distinct()
                .forEach(argType -> addFeature(features, "ArgumentType=" + argType, 1));

        // Predicate type
        if (questionStructures.stream().anyMatch(qstr -> qstr.category.isFunctionInto(Category.valueOf("S[pss]")))) {
            addFeature(features, "PredicateIsPassive", 1);
        }

        // Dependency contain type
        final String depQATApe = DependencyInstanceHelper.getDependencyContainsType(query, headId, argId);
        addFeature(features, "DepContainedType=" + depQATApe, 1);

        // 1best score
        final boolean oneBestContains = nBestList.getParse(0).dependencies.stream()
                .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                 (dep.getHead() == argId && dep.getArgument() == headId));
        if (oneBestContains) {
            addFeature(features, "1BestContains", 1);
        }

        final int numAnnotators = annotation.size();
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final ImmutableList<Integer> options = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> DependencyInstanceHelper.containsDependency(sentence, query.getQAPairSurfaceForms().get(i),
                                                                         headId, argId))
                .collect(GuavaCollectors.toImmutableList());

        final double nBestScoreNorm = nBestList.getParses().stream()
                .filter(parse -> candidateHeads.stream()
                        .anyMatch(a -> parse.dependencies.stream()
                                .anyMatch(d -> d.getHead() == headId && d.getArgument() == a)))
                .mapToDouble(parse -> parse.score)
                .sum();
        final double nBestScore = nBestList.getParses().stream()
                .filter(parse -> parse.dependencies.stream()
                        .anyMatch(d -> d.getHead() == headId && d.getArgument() == argId))
                .mapToDouble(parse -> parse.score)
                .sum();
        addFeature(features, "NBestScore", nBestScore / nBestScoreNorm);

        final int firstEncounterInNBest = IntStream.range(0, nBestList.getN())
                .filter(k -> nBestList.getParse(k).dependencies.stream()
                        .anyMatch(dep -> (dep.getHead() == headId && dep.getArgument() == argId) ||
                                         (dep.getHead() == argId && dep.getArgument() == headId)))
                .findFirst().orElse(nBestList.getN());
        addFeature(features, "firstEncounterInNBest", 1.0 - firstEncounterInNBest / nBestList.getN());

        // User votes.
        ImmutableList<Integer> votes = IntStream.range(0, numQAOptions).boxed()
                .map(i -> (int) annotation.stream().filter(ops -> ops.contains(i)).count())
                .collect(GuavaCollectors.toImmutableList());
        addFeature(features, "NumReceivedVotes", 1.0 * options.stream().mapToInt(votes::get).max().orElse(0) / numAnnotators);

        int numSingleVotes = options.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i) && ops.size() == 1).count())
                .max().orElse(0);
        addFeature(features, "NumReceivedSingleVotes", 1.0 * numSingleVotes / numAnnotators);

        int numSkipped = options.stream()
                .mapToInt(i -> (int) annotation.stream().filter(ops -> !ops.contains(i)).count())
                .max().orElse(0);
        addFeature(features, "NumSkippedVotes", 1.0 * numSkipped / numAnnotators);

        addFeature(features, "QueryConfidence", query.getPromptScore());
        addFeature(features, "NAOptionReceivedVotes", 1.0 * annotation.stream()
                .filter(ops -> ops.contains(naOptionId)).count() / numAnnotators);

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

        if (options.stream().map(query.getOptions()::get)
                .anyMatch(op -> op.toLowerCase().startsWith("a "))) {
            addFeature(features, "StartsWith=a", 1);
        }
        if (options.stream().map(query.getOptions()::get)
                .anyMatch(op -> op.toLowerCase().startsWith("the "))) {
            addFeature(features, "StartsWith=the", 1);
        }
        //addFeature(features, argId < headId ? "ArgOnLeft" : "ArgOnRight", 1);

        // Template features.
        for (int i = 0; i < numQAOptions; i++) {
            for (int j = 0; j < numQAOptions; j++) {
                String template = TemplateHelper.identifyTemplate(sentence, query, i, j);
                if (i == j || template.isEmpty()) {
                    continue;
                }
                if (options.contains(i)) {
                    addFeature(features, "Template=" + template + ".1", 1);
                    addFeature(features, "TemplateOtherVotes", 1.0 * votes.get(j) / numAnnotators);
                } else if (options.contains(j)) {
                    addFeature(features, "Template=" + template + ".2", 1);
                    addFeature(features, "TemplateOtherVotes", 1.0 * votes.get(i) / numAnnotators);
                }
            }
        }

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
