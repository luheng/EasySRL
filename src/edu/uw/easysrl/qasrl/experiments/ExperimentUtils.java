package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataReader;
import edu.uw.easysrl.qasrl.qg.IQuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregator;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.util.Util;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

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

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllRadioButtonQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final QueryPruningParameters queryPruningParameters) {
        final ImmutableList<IQuestionAnswerPair> rawQAPairs = QuestionGenerator
                .generateAllQAPairs(sentenceId, sentence, nBestList);
        return QueryFilter.filter(QueryGenerators.radioButtonQueryGenerator()
                                    .generate(QAPairAggregators.aggregateForSingleChoiceQA().aggregate(rawQAPairs)),
                                  nBestList,
                                  queryPruningParameters);
    }

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllCheckboxQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final QueryPruningParameters queryPruningParameters) {
        final ImmutableList<IQuestionAnswerPair> rawQAPairs = QuestionGenerator
                .generateAllQAPairs(sentenceId, sentence, nBestList);
        return QueryFilter.filter(QueryGenerators.checkboxQueryGenerator().generate(
                                        QAPairAggregators.aggregateForMultipleChoiceQA().aggregate(rawQAPairs)),
                                  nBestList,
                                  queryPruningParameters);
    }

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final boolean isJeopardyStyle,
            final boolean isCheckbox,
            final QueryPruningParameters queryPruningParameters) {
        return generateAllQueries(
                isCheckbox ?
                        QAPairAggregators.aggregateForMultipleChoiceQA() :
                        QAPairAggregators.aggregateForSingleChoiceQA(),
                isJeopardyStyle ?
                        QueryGenerators.jeopardyCheckboxQueryGenerator() :
                        (isCheckbox ?
                                QueryGenerators.checkboxQueryGenerator() :
                                QueryGenerators.radioButtonQueryGenerator()),
                sentenceId, sentence, nBestList, queryPruningParameters);
    }

    public static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(
            QAPairAggregator<QAStructureSurfaceForm> qaPairAggregator,
            QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> queryGenerator,
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final QueryPruningParameters queryPruningParameters) {
        return QueryFilter.filter(
                    queryGenerator.generate(
                            qaPairAggregator.aggregate(
                                    QuestionGenerator.generateAllQAPairs(sentenceId, sentence, nBestList))),
                nBestList, queryPruningParameters);
    }

    public static NBestList getNBestList(final BaseCcgParser parser, int sentenceId,
                                         final List<InputReader.InputWord> inputSentence) {
        List<Parse> nbestParses = parser.parseNBest(sentenceId, inputSentence);
        if (nbestParses == null) {
            return null;
        }
        return new NBestList(ImmutableList.copyOf(nbestParses));
    }

    public static Map<Integer, NBestList> getAllNBestLists(
            final BaseCcgParser parser,
            final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentence) {
        Map<Integer, NBestList> allParses = new HashMap<>();
        IntStream.range(0, inputSentence.size()).boxed()
                .forEach(sentenceId -> {
                    List<Parse> nbestParses = parser.parseNBest(sentenceId, inputSentence.get(sentenceId));
                    if (nbestParses != null) {
                        allParses.put(sentenceId, new NBestList(ImmutableList.copyOf(nbestParses)));
                    }
                });
        return allParses;
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
        String qkey = query.getQAPairSurfaceForms().get(0).getPredicateIndex() + "\t" + query.getPrompt();
        for (AlignedAnnotation annotation : annotations) {
            String qkey2 = annotation.predicateId + "\t" + annotation.question;
            if (qkey.equals(qkey2)) {
                return annotation;
            }
        }
        return null;
    }
}
