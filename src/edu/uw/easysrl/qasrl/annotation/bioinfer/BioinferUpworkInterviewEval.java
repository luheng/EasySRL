package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.HashMultiset;
import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.QuestionGenerationPipeline;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.TextGenerationHelper;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static edu.uw.easysrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

/**
 * Create Crowdflower data for pronoun-style core questions.
 *
 * 1. Sample sentences (or reuse previous sentences)
 * 2. Sample test sentences or (reuse previous sentences)
 * 3. Write candidate questions to csv.
 * 4. Write candidate test questions to csv.
 * Created by luheng on 3/25/16.
 */
public class BioinferUpworkInterviewEval {

    // static final int nBest = 100;
    static final int maxNumQueriesPerFile = 500;

    private static final String interviewDataFile = "./Crowdflower_bioinfer/upwork-interview-annotations.csv";
    private static final String fullTestDataFile = "./Crowdflower_bioinfer/upwork-test-annotations.csv";

    private static final ImmutableMap<String, String> annotatorToId = new ImmutableMap.Builder<String, String>()
        .put("Jessica", "37973746")
        .put("Rhonda", "38004555")
        .put("Tamir", "38005470")
        .put("Sean", "38026552")
        .build();

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;  // R5: false // R4: true.
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;   // R4: unspecified.
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static void printAnswersByAnnotator(String annotatorName, String annotationFile) throws IOException {
        final String annotatorId = annotatorToId.get(annotatorName);
        final BioinferCCGCorpus corpus = BioinferCCGCorpus.readDev().get();
        final Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.dev.100best.out", 100).get();
        Map<Integer, List<AlignedAnnotation>> allAnnotations = CrowdFlowerDataUtils
            .loadTestAnnotations(ImmutableList.of(annotationFile));

        final List<String> problemQuestions = new ArrayList<>();
        int numAnnotations = 0, numMatchedAnnotations = 0, numCorrectlyAnswered = 0;
        for (int sid : allAnnotations.keySet()) {
            final NBestList nbestList = nbestLists.get(sid);

            final ImmutableList<String> sentence = corpus.getSentence(sid);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = QuestionGenerationPipeline
                .coreArgQGPipeline
                .setQueryPruningParameters(queryPruningParameters)
                .generateAllQueries(sid, nbestList);
            final List<AlignedAnnotation> annotations = allAnnotations.get(sid);

            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                final int predId = query.getPredicateId().getAsInt();
                final Set<QuestionStructure> qstrs = query.getQAPairSurfaceForms().stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream())
                        .distinct()
                        .collect(toSet());

                for (int annotId = 0; annotId < annotations.size(); annotId++) {
                    final AlignedAnnotation annot = annotations.get(annotId);
                    if (annot.predicateId == predId &&
                        query.getPrompt().equals(annot.queryPrompt)) {
                        String comment = annot.annotatorToComment.get(annotatorId);
                        // these are the person's annotations
                        ImmutableList<Integer> annotatorResponse = Optional
                            .ofNullable(annot.annotatorToAnswerIds.get(annotatorId))
                            .orElse(ImmutableList.of());
                        // ImmutableList<Integer> julianResponse = annot.annotatorToAnswerIds.get("36562414");
                        ImmutableList<Integer> julianResponse = annot.goldOptionIds;

                        // String queryStr = annot.annotatorToAnswerIds.keySet().stream().collect(joining(" "));
                        String queryStr = getAnnotatorFeedbackString(sentence, query, annot,
                                                                     annotatorResponse, comment,
                                                                     julianResponse);
                        numMatchedAnnotations++;

                        if(annotatorResponse.containsAll(julianResponse) && julianResponse.containsAll(annotatorResponse)) {
                            numCorrectlyAnswered++;
                        } else {
                            problemQuestions.add(queryStr);
                        }
                    }
                }
            }
            numAnnotations += annotations.size();
        }
        System.out.println("Num annotations:\t" + numAnnotations);
        System.out.println("Num matched annotations:\t" + numMatchedAnnotations);
        System.out.println(String.format("Accuracy: %.2f",
                                         100.0 * numCorrectlyAnswered / numMatchedAnnotations));

        problemQuestions.forEach(System.out::println);
    }

    private static String getAnnotatorFeedbackString(final ImmutableList<String> sentence,
                                                     final ScoredQuery<QAStructureSurfaceForm> query,
                                                     final AlignedAnnotation annotation,
                                                     final ImmutableList<Integer> response,
                                                     final String reason,
                                                     final ImmutableList<Integer> julianResponse) {
        String prompt = TextGenerationHelper.renderString(sentence) + ".\n" +
            query.getPrompt();
        String options = "";
        for (int i = 0; i < query.getOptions().size(); i++) {
            int spacesNeeded = 3;
            if(julianResponse.contains(i)) {
                options += "!";
                spacesNeeded--;
            }
            if(response.contains(i)) {
                options += "*";
                spacesNeeded--;
            }
            for(int j = 0; j < spacesNeeded; j++) {
                options += " ";
            }
            options += query.getOptions().get(i) + "\n";
        }
        return prompt + "\n" + options + "\n" + "Comment: " + reason + "\n";
    }

    public static void main(String[] args) throws IOException {
        if(args.length == 0 || !annotatorToId.keySet().contains(args[0])) {
            System.err.println("must pass an annotator name (Jessica|Rhonda|Tamir|Sean)");
            System.exit(1);
        }
        printAnswersByAnnotator(args[0], fullTestDataFile);
    }
}
