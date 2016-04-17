package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataReader;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregator;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.*;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 3/21/16.
 */
public class ExperimentUtils {

    static class DebugBlock {
        double deltaF1;
        String block;
        DebugBlock(double deltaF1, String block) {
            this.deltaF1 = deltaF1;
            this.block = block;
        }
    }

    @Deprecated
    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllRadioButtonQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final QueryPruningParameters queryPruningParameters) {
        QuestionGenerator.setIndefinitesOnly(false);
        final ImmutableList<QuestionAnswerPair> rawQAPairs = QuestionGenerator
                .generateAllQAPairs(sentenceId, sentence, nBestList);
        return QueryFilters.scoredQueryFilter()
                .filter(QueryGenerators.radioButtonQueryGenerator()
                        .generate(QAPairAggregators.aggregateForSingleChoiceQA()
                                .aggregate(rawQAPairs)),
                        nBestList, queryPruningParameters);
    }

    @Deprecated
    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllCheckboxQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final QueryPruningParameters queryPruningParameters) {
        QuestionGenerator.setIndefinitesOnly(false);
        final ImmutableList<QuestionAnswerPair> rawQAPairs = QuestionGenerator
                .generateAllQAPairs(sentenceId, sentence, nBestList);
        return QueryFilters.scoredQueryFilter()
                .filter(QueryGenerators.checkboxQueryGenerator()
                        .generate(QAPairAggregators.aggregateForMultipleChoiceQA()
                                .aggregate(rawQAPairs)),
                nBestList, queryPruningParameters);
    }

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final boolean isJeopardyStyle,
            final boolean isCheckbox,
            final boolean usePronouns,
            final QueryPruningParameters queryPruningParameters) {
        return generateAllQueries(
                // Choose QAPair aggregator.
                isCheckbox ?
                        //QAPairAggregators.aggregateWithFullQuestionStructure() :
                        QAPairAggregators.aggregateForMultipleChoiceQA() :
                        QAPairAggregators.aggregateForSingleChoiceQA(),
                // Choose Query generator.
                isJeopardyStyle ?
                        QueryGenerators.jeopardyCheckboxQueryGenerator() :
                        (isCheckbox ?
                                QueryGenerators.checkboxQueryGenerator() :
                                QueryGenerators.radioButtonQueryGenerator()),
                // Choose Query filter.
                isJeopardyStyle ?
                        QueryFilters.jeopardyPPQueryFilter() :
                        QueryFilters.scoredQueryFilter(),
                // Other parameters.
                sentenceId, sentence, nBestList, usePronouns, queryPruningParameters);
    }

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateCleftedQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final boolean usePronouns,
            final QueryPruningParameters queryPruningParameters) {
        QuestionGenerator.setAskPPAttachmentQuestions(true);
        QuestionGenerator.setIndefinitesOnly(usePronouns);
        return QueryFilters.cleftedQueryFilter().filter(
                QueryGenerators.cleftedQueryGenerator().generate(
                        QAPairAggregators.aggregateWithAnswerAdjunctDependencies().aggregate(
                                QuestionGenerator.generateAllQAPairs(sentenceId, sentence, nBestList))),
                nBestList, queryPruningParameters);
    }

    private static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(
            QAPairAggregator<QAStructureSurfaceForm> qaPairAggregator,
            QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> queryGenerator,
            QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> queryFilter,
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final boolean usePronouns,
            final QueryPruningParameters queryPruningParameters) {
        QuestionGenerator.setAskPPAttachmentQuestions(false);
        QuestionGenerator.setIndefinitesOnly(usePronouns);
        return queryFilter.filter(
                    queryGenerator.generate(
                            qaPairAggregator.aggregate(
                                    QuestionGenerator.generateAllQAPairs(sentenceId, sentence, nBestList))),
                nBestList, queryPruningParameters);
    }

    static Map<Integer, List<AlignedAnnotation>> loadCrowdflowerAnnotation(String[] fileNames) {
        Map<Integer, List<AlignedAnnotation>> sentenceToAnnotations;
        List<AlignedAnnotation> annotationList = new ArrayList<>();
        try {
            for (String fileName : fileNames) {
                annotationList.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(fileName));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        sentenceToAnnotations = new HashMap<>();
        annotationList.forEach(annotation -> {
            int sentId = annotation.sentenceId;
            if (!sentenceToAnnotations.containsKey(sentId)) {
                sentenceToAnnotations.put(sentId, new ArrayList<>());
            }
            sentenceToAnnotations.get(sentId).add(annotation);
        });
        return sentenceToAnnotations;
    }

    static AlignedAnnotation getAlignedAnnotation(ScoredQuery<QAStructureSurfaceForm> query,
                                                  List<AlignedAnnotation> annotations) {
        AlignedAnnotation bestAligned = null;
        int maxNumOverlappingOptions = 1;
        for (AlignedAnnotation annotation : annotations) {
            if (query.getPrompt().equals(annotation.queryPrompt)) {
                int numOverlappingOptions = (int) annotation.answerOptions.stream()
                        .filter(op -> query.getOptions().contains(op))
                        .count();
                if (numOverlappingOptions > maxNumOverlappingOptions) {
                    bestAligned = annotation;
                }
            }
        }
        return bestAligned;
    }
}
