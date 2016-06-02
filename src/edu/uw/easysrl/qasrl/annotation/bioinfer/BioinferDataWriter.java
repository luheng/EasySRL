package edu.uw.easysrl.qasrl.annotation.ccgdev;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.QuestionGenerationPipeline;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.AnnotationUtils;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataUtils;
import edu.uw.easysrl.qasrl.evaluation.Accuracy;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.evaluation.TicToc;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.model.HeuristicHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.util.GuavaCollectors;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Writes all the annotation on the dev set to a single tsv file. <3
 * Created by luheng on 5/24/16.
 */
public class BioinferDataWriter {
    private static final int nBest = 100;
    private static Map<Integer, List<AlignedAnnotation>> annotations;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f914096.csv",
    };

    private static final String outputFilePath = "bioinfer2.qa.tsv";

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.maxNumOptionsPerQuery = 6;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    public static void main(String[] args) {
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        assert annotations != null;

        try {
            writeAggregatedAnnotation();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeAggregatedAnnotation() throws IOException {
        BioinferCCGCorpus corpus = BioinferCCGCorpus.readTest().get();
        Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.test.100best.out", nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());

        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        int numMatchedAnnotations = 0, numNewQuestions = 0;

       // Output file writer
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFilePath)));

        int counter = 0, numWrittenAnnotations = 0;
        TicToc.tic();
        for (int sentenceId : sentenceIds) {

            if (++counter % 100 == 0) {
                System.out.println(String.format("Processed %d sentences ... in %d seconds", counter, TicToc.toc()));
                TicToc.tic();
            }
            final ImmutableList<String> sentence = corpus.getSentence(sentenceId);
            final NBestList nBestList = nbestLists.get(sentenceId);

            final List<AlignedAnnotation> annotated = annotations.get(sentenceId);
            if (annotated == null || annotated.isEmpty()) {
                continue;
            }

            List<ScoredQuery<QAStructureSurfaceForm>> queryList = QuestionGenerationPipeline.coreArgQGPipeline
                    .setQueryPruningParameters(queryPruningParameters)
                    .generateAllQueries(sentenceId, nBestList);

            for (AlignedAnnotation annotation : annotations.get(sentenceId)) {
                Optional<ScoredQuery<QAStructureSurfaceForm>> queryOpt =
                        ExperimentUtils.getQueryForAlignedAnnotation(annotation, queryList);
                if (!queryOpt.isPresent()) {
                    continue;
                }
                final ScoredQuery<QAStructureSurfaceForm> query = queryOpt.get();
                if (Prepositions.prepositionWords.contains(sentence.get(query.getPredicateId().getAsInt())
                        .toLowerCase())) {
                    continue;
                }
                ImmutableList<ImmutableList<Integer>> responses = AnnotationUtils.getAllUserResponses(query, annotation);
                if (responses.size() != 5) {
                    continue;
                }
                // Write to output file.
                writer.write(query.toAnnotationString(sentence, responses) + "\n");
                numWrittenAnnotations ++;
            }
        }
        writer.close();
        System.out.println(String.format("Wrote %d annotations to file %s.", numWrittenAnnotations, outputFilePath));
        System.out.println(String.format("Covered %d sentences\t", sentenceIds.size()));
    }
}
