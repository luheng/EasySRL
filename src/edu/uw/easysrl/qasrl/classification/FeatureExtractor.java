package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.corpora.CountDictionary;
import edu.uw.easysrl.qasrl.model.HeuristicHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 4/21/16.
 */
public class FeatureExtractor {
    CountDictionary featureMap;
    private boolean acceptNewFeatures = true;

    boolean addCategoryFeatures = false;
    boolean addNBestPriorFeatures = false;
    boolean addTemplateBasedFeatures = false;
    boolean addAnnotationFeatures = true;
    boolean addNAOptionFeature = false;
    boolean addAnswerLexicalFeatures = false;
    boolean addArgumentPositionFeatures = false;

    FeatureExtractor() {
        featureMap = new CountDictionary();
        acceptNewFeatures = true;
    }

    void freeze() {
        acceptNewFeatures = false;
    }

    /*
    void freeze(final int minFeatureFreq) {
        featureMap = new CountDictionary(featureMap, minFeatureFreq);
        acceptNewFeatures = false;
    }*/

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
    ImmutableMap<Integer, Double> getCoreArgFeatures(final int headId, final int argId,
                                                     final DependencyInstanceType instanceType,
                                                     final ScoredQuery<QAStructureSurfaceForm> query,
                                                     final ImmutableList<ImmutableList<Integer>> annotation,
                                                     final ImmutableList<String> sentence,
                                                     final NBestList nBestList) {
        Map<Integer, Double> features = new HashMap<>();

        final ImmutableList<QuestionStructure> questionStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<AnswerStructure> answerStructures = query.getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getAnswerStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> candidateHeads = Stream.concat(
                    Stream.of(argId),
                    answerStructures.stream().flatMap(astr -> astr.argumentIndices.stream()))
                .distinct()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());

        final int numAnnotators = annotation.size();
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final ImmutableList<Integer> options = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> DependencyInstanceHelper.containsDependency(sentence, query.getQAPairSurfaceForms().get(i),
                        headId, argId))
                .collect(GuavaCollectors.toImmutableList());

        final double nBestPriorNorm = nBestList.getParses().stream()
                .filter(parse -> candidateHeads.stream()
                        .anyMatch(a -> parse.dependencies.stream()
                                .anyMatch(d -> d.getHead() == headId && d.getArgument() == a)))
                .mapToDouble(parse -> parse.score)
                .sum();
        final ImmutableMap<Integer, Double> nBestPriors = candidateHeads.stream()
                .collect(GuavaCollectors.toImmutableMap(
                        a -> a,
                        a -> nBestList.getParses().stream()
                                .filter(parse -> parse.dependencies.stream()
                                        .anyMatch(d -> d.getHead() == headId && d.getArgument() == a))
                        .mapToDouble(parse -> parse.score)
                        .sum()
                ));

        ImmutableList<ImmutableList<Integer>> fixedAnnotation = HeuristicHelper.adjustVotes(sentence, query, annotation);
        final ImmutableList<Integer> allVotes = IntStream.range(0, numQAOptions).boxed()
                .map(i -> (int) fixedAnnotation.stream().filter(ops -> ops.contains(i)).count())
                .collect(GuavaCollectors.toImmutableList());

        //addFeature(features, "DependencyType=" + instanceType, 1.0);
        //addFeature(features, "BIAS", 1.0);

        if (addCategoryFeatures) {
            questionStructures.stream()
                    .map(qstr -> qstr.category)
                    .distinct()
                    .forEach(cat -> addFeature(features, "PredicateCategory=" + cat, 1));
            questionStructures.stream()
                    .map(qstr -> qstr.category.getArgument(qstr.targetArgNum))
                    .distinct()
                    .forEach(argCat -> addFeature(features, "ArgumentCategory=" + argCat, 1));
            questionStructures.stream()
                    .map(qstr -> qstr.targetArgNum == 1 && !qstr.category.isFunctionInto(Category.valueOf("S[pss]")) ? "Agent" : "Patient")
                    .distinct()
                    .forEach(argType -> addFeature(features, "ArgumentType=" + argType, 1));
        }

        final double prior = nBestPriors.get(argId) / nBestPriorNorm;
        if (addNBestPriorFeatures) {
            addFeature(features, "NBestPrior", prior);
        }

        final double votes = 1.0 * options.stream().mapToInt(allVotes::get).max().orElse(0);
        if (addAnnotationFeatures) {
            //addFeature(features, "NumReceivedVotes", votes); // / numAnnotators);
            if (votes > 4.001) {
                addFeature(features, "NumReceivedVotes>4", 1.0);
            }
            if (votes > 3.001) {
                addFeature(features, "NumReceivedVotes>3", 1.0);
            }
            if (votes < 1.999) {
                addFeature(features, "NumReceivedVotes<2", 1.0);
            }
            if (votes < 0.999) {
                addFeature(features, "NumReceivedVotes<1", 1.0);
            }
            /*
            int numSingleVotes = options.stream()
                    .mapToInt(i -> (int) annotation.stream().filter(ops -> ops.contains(i) && ops.size() == 1).count())
                    .max().orElse(0);
            addFeature(features, "NumReceivedSingleVotes", 1.0 * numSingleVotes); // / numAnnotators);

            int numSkipped = options.stream()
                    .mapToInt(i -> (int) annotation.stream().filter(ops -> !ops.contains(i)).count())
                    .max().orElse(0);
            addFeature(features, "NumSkippedVotes", 1.0 * numSkipped);// / numAnnotators);
            */
        }

        if (addNAOptionFeature) {
            addFeature(features, "QueryConfidence", query.getPromptScore());
            addFeature(features, "NAOptionReceivedVotes", 1.0 * annotation.stream()
                    .filter(ops -> ops.contains(naOptionId)).count() / numAnnotators);
        }

        if (addAnswerLexicalFeatures) {
            if (options.stream()
                    .map(op -> query.getOptions().get(op).toLowerCase())
                    .anyMatch(op -> Stream.of("a ", "an ", "the ").anyMatch(op::startsWith))) {
                addFeature(features, "StartsWith=a/an/the", 1);
            }
            final String sentenceStr = sentence.stream().collect(Collectors.joining(" "));
            if (options.stream()
                    .map(op -> query.getOptions().get(op).toLowerCase())
                    .anyMatch(op -> Stream.of(" who ", " that ", " which ", " , who ", " , which ")
                            .anyMatch(tok -> sentenceStr.contains(op + tok)))) {
                addFeature(features, "FollowedBy=who/that/which", 1);
            }
        }

        if (addArgumentPositionFeatures) {
            addFeature(features, argId < headId ? "ArgOnLeft" : "ArgOnRight", 1);
            addFeature(features, "ArgHeadDistance", 1.0 * Math.abs(argId - headId));
        }

        if (addTemplateBasedFeatures) {
            for (int i = 0; i < numQAOptions; i++) {
                for (int j = 0; j < numQAOptions; j++) {
                    String template = TemplateHelper.identifyTemplate(sentence, query, i, j);
                    if (i == j || template.isEmpty()) {
                        continue;
                    }
                    if (options.contains(i)) {
                        final int otherHead = query.getQAPairSurfaceForms().get(j).getAnswerStructures().get(0).argumentIndices.get(0);
                        final double otherVotes = 1.0 * allVotes.get(j);
                        final double otherPrior = nBestPriors.get(otherHead) / nBestPriorNorm;
                        addFeature(features, template + ".1." + "votes", otherVotes);
                        addFeature(features, template + ".1." + "prior", otherPrior);
                        //addFeature(features, template + ".1." + "votes+", Math.min(votes + otherVotes, numAnnotators));
                    } else if (options.contains(j)) {
                        final int otherHead = query.getQAPairSurfaceForms().get(i).getAnswerStructures().get(0).argumentIndices.get(0);
                        final double otherVotes = 1.0 * allVotes.get(i);
                        final double otherPrior = nBestPriors.get(otherHead) / nBestPriorNorm;
                        addFeature(features, template + ".2." + "votes", otherVotes);
                        addFeature(features, template + ".2." + "prior", otherPrior);
                        //addFeature(features, template + ".2." + "votes+", Math.min(votes + otherVotes, numAnnotators));
                    }
                }
            }
        }


        // TODO: sentence length features
        final int sentenceLength = sentence.size();
        if (sentenceLength < 15) {
            addFeature(features, "SLEN<15", 1.0);
        } else {
            addFeature(features, "SLEN>15", 1.0);
        }
        if (sentenceLength < 30) {
            addFeature(features, "SLEN<30", 1.0);
        } else {
            addFeature(features, "SLEN>30", 1.0);
        }
        if (sentenceLength < 45) {
            addFeature(features, "SLEN<45", 1.0);
        } else {
            addFeature(features, "SLEN>45", 1.0);
        }

        return ImmutableMap.copyOf(features);
    }

    ImmutableMap<Integer, Double> getNPCleftingFeatures(final int headId, final int argId,
                                                        final DependencyInstanceType instanceType,
                                                        final ScoredQuery<QAStructureSurfaceForm> query,
                                                        final ImmutableList<ImmutableList<Integer>> annotation,
                                                        final ImmutableList<String> sentence,
                                                        final NBestList nBestList) {
        Map<Integer, Double> features = new HashMap<>();
        final int numAnnotators = annotation.size();
        final int naOptionId = query.getBadQuestionOptionId().getAsInt();
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final ImmutableList<Integer> options = IntStream.range(0, numQAOptions)
                .boxed()
                .filter(i -> DependencyInstanceHelper.containsDependency(sentence, query.getQAPairSurfaceForms().get(i),
                        headId, argId))
                .collect(GuavaCollectors.toImmutableList());

        final double nBestPriorNorm = nBestList.getParses().stream()
                .mapToDouble(parse -> parse.score)
                .sum();


        ImmutableList<ImmutableList<Integer>> fixedAnnotation = HeuristicHelper.adjustVotes(sentence, query, annotation);
        final ImmutableList<Integer> allVotes = IntStream.range(0, numQAOptions).boxed()
                .map(i -> (int) fixedAnnotation.stream().filter(ops -> ops.contains(i)).count())
                .collect(GuavaCollectors.toImmutableList());

        if (addAnswerLexicalFeatures) {
            if (instanceType == DependencyInstanceType.PPGovernor || instanceType == DependencyInstanceType.PPObject) {
                if (headId > 0 && sentence.get(headId - 1).equalsIgnoreCase("such") && sentence.get(headId).equals("as")) {
                    addFeature(features, "Preposition=" + "such as", 1.0);
                } else {
                    addFeature(features, "Preposition=" + sentence.get(headId).toLowerCase(), 1.0);
                }
            }
        }

        if (addNAOptionFeature) {
            addFeature(features, "QueryConfidence", query.getPromptScore());
            addFeature(features, "NAOptionReceivedVotes", 1.0 * annotation.stream()
                    .filter(ops -> ops.contains(naOptionId)).count() / numAnnotators);
        }

        addFeature(features, "DependencyType=" + instanceType, 1.0);

        final double nBestPrior = nBestList.getParses().stream()
                .filter(parse -> parse.dependencies.stream()
                        .anyMatch(d -> d.getHead() == headId && d.getArgument() == argId))
                .mapToDouble(parse -> parse.score)
                .sum();
        final double prior = nBestPrior / nBestPriorNorm;
        if (addNBestPriorFeatures) {
            addFeature(features, "NBestPrior", prior);
        }

        final double maxVotes = 1.0 * options.stream().mapToInt(allVotes::get).max().orElse(0);
        final double minVotes = 1.0 * options.stream().mapToInt(allVotes::get).min().orElse(0);
        if (addAnnotationFeatures) {
            //addFeature(features, "MaxReceivedVotes", maxVotes);
            final double votes = 1.0 * options.stream().mapToInt(allVotes::get).max().orElse(0);
            if (votes > 4.001) {
                addFeature(features, "NumReceivedVotes>5", 1.0);
            }
            if (votes > 3.001) {
                addFeature(features, "NumReceivedVotes>3", 1.0);
            }
            if (votes < 1.999) {
                addFeature(features, "NumReceivedVotes<2", 1.0);
            }
            if (votes < 0.999) {
                addFeature(features, "NumReceivedVotes<1", 1.0);
            }
        }

        if (addArgumentPositionFeatures) {
            // Head arg distance.
            addFeature(features, "HeadArgDist", Math.abs(headId - argId));

            // Comma and other stuff in between.
            for (int i = Math.min(headId, argId) + 1; i < Math.max(headId, argId); i++) {
                final String token = sentence.get(i).toLowerCase();
                if (ImmutableSet.of(",", "and", "of").contains(token)) {
                    addFeature(features, "TokenBetweenHeadAndArg=" + token, 1.0);
                }
            }
        }

        return ImmutableMap.copyOf(features);
    }


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
