package edu.uw.easysrl.qasrl.classification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import scala.tools.cmd.gen.AnyVals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 4/30/16.
 */
public class TemplateHelper {


    public static String identifyTemplate(final ImmutableList<String> sentence,
                                          final ScoredQuery<QAStructureSurfaceForm> query,
                                          final int opId1, final int opId2) {
        final int predicateId = query.getPredicateId().getAsInt();
        final String op1 = query.getOptions().get(opId1).toLowerCase();
        final String op2 = query.getOptions().get(opId2).toLowerCase();
        final int argId1 = query.getQAPairSurfaceForms().get(opId1).getAnswerStructures().get(0).argumentIndices.get(0);
        final int argId2 = query.getQAPairSurfaceForms().get(opId2).getAnswerStructures().get(0).argumentIndices.get(0);

        // op1 is superspan of op2
        if (op1.equals("some of " + op2) || op1.equals("many of " + op2)) {
            return "[op1] := some/many of [op2]";
        }
        if (op1.contains(" of " + op2)) {
            return "[op1] := X of [op2]";
        }
        // op1 is subspan of op2
        for (String pp : ImmutableList.copyOf(Prepositions.prepositionWords)) {
            if (!pp.equals("of") && op2.startsWith(op1 + " " + pp + " ")) {
                return "[op2] := [op1] [pp] X";
            }
        }
        if (op2.startsWith(op1 + " ")) {
            return "[op2] := [op1] X";
        }
        // Appositive templates.
        int commaBetweenArgs = 0, commaBetweenPredArg1 = 0, commaBetweenArg2Pred = 0;
        for (int i = argId1 + 1; i < argId2; i++) {
            if (sentence.get(i).equals(",")) {
                commaBetweenArgs ++;
            }
        }
        for (int i = predicateId + 1; i < argId1; i++) {
            if (sentence.get(i).equals(",")) {
                commaBetweenPredArg1 ++;
            }
        }
        for (int i = argId2 + 1; i < predicateId; i++) {
            if (sentence.get(i).equals(",")) {
                commaBetweenArg2Pred ++;
            }
        }
        if (argId1 < argId2 && argId2 < predicateId && commaBetweenArgs == 1) {
            if (commaBetweenArg2Pred == 0) {
                return "[op1] , [op2] [pred]";
            }
            if (commaBetweenArg2Pred == 1) {
                return "[op1] , [op2] , [pred]";
            }
        }
        if (predicateId < argId1 && argId1 < argId2 && commaBetweenArgs == 1) {
            if (commaBetweenPredArg1 == 0) {
                return "[pred] [op1] , [op2]";
            }
            if (commaBetweenPredArg1 == 1) {
                return "[pred] , [op1] , [op2]";
            }
        }
        // Pronoun template.
        if (PronounList.englishPronounSet.contains(op2)) {
            if (argId1 < argId2 && argId2 < predicateId) {
                return "[op1] [pron] [pred]";
            }
            if (predicateId < argId2 && argId2 < argId1) {
                return "[pred] [pron] [op1]";
            }
        }
        return "";
    }

    private static final int nBest = 100;
    private static final ImmutableList<Double> split = ImmutableList.of(0.6, 0.4, 0.0);
    private static final int randomSeed = 12345;
    private static HITLParser myParser;
    private static Map<Integer, List<AlignedAnnotation>> annotations;
    private static FeatureExtractor featureExtractor;

    private static Map<Integer, List<ScoredQuery<QAStructureSurfaceForm>>> alignedQueries;
    private static Map<Integer, List<ImmutableList<ImmutableList<Integer>>>> alignedAnnotations;
    private static Map<Integer, List<AlignedAnnotation>> alignedOldAnnotations;

    private static ImmutableList<Integer> trainSents;

    private static final int numFolds = 5;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f893900.csv",                   // Round3-pronouns: checkbox, core only, pronouns.
            "./Crowdflower_data/f902142.csv"                   // Round4: checkbox, pronouns, core only, 300 sentences.
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipSAdjQuestions = true;
    }

    public static void main(String[] args) {
        myParser = new HITLParser(nBest);
        myParser.setQueryPruningParameters(queryPruningParameters);
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        alignedQueries = new HashMap<>();
        alignedAnnotations = new HashMap<>();
        alignedOldAnnotations = new HashMap<>();
        featureExtractor = new FeatureExtractor();
        assert annotations != null;
        ImmutableList<Integer> sentenceIds = annotations.keySet().stream()
                .sorted()
                .collect(GuavaCollectors.toImmutableList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());
        ImmutableList<ImmutableList<Integer>> splitSents = ClassificationUtils.jackknife(sentenceIds, split, randomSeed);
        trainSents = splitSents.get(0);
        ClassificationUtils.getInstances(trainSents, myParser, featureExtractor, annotations, alignedQueries,
                                         alignedAnnotations, alignedOldAnnotations);

        trainSents.forEach(sid -> {
            final ImmutableList<String> sentence = myParser.getSentence(sid);
            alignedQueries.get(sid).forEach(query -> {
                final int numQAs = query.getQAPairSurfaceForms().size();
                IntStream.range(0, numQAs).boxed().forEach(op1 -> {
                    IntStream.range(0, numQAs).boxed()
                            .filter(op2 -> op2.intValue() != op1.intValue())
                            .forEach(op2 -> {
                                final String template = identifyTemplate(sentence, query, op1, op2);
                                if (!template.isEmpty()) {
                                    System.out.println(TextGenerationHelper.renderString(sentence));
                                    System.out.println(query.getPrompt());
                                    System.out.println(query.getOptions().get(op1));
                                    System.out.println(query.getOptions().get(op2));
                                    System.out.println(template);
                                    System.out.println();
                                }
                            });
                });
            });
        });
    }
}
