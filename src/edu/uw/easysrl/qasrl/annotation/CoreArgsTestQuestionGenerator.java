package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Mostly temporary and unorganized methods for getting test questions ...
 * Created by luheng on 4/6/16.
 */
public class CoreArgsTestQuestionGenerator {
    static final int nBest = 100;
    private final static HITLParser hitlParser = new HITLParser(nBest);

    static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipBinaryQueries = true;
        queryPruningParameters.minOptionConfidence = 0;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = -1;
    }

    // TODO: read annotation files (match predicate & answers spans), find high agreement ones,


    final static String checkboxTestQuestionFilePath = "./Crowdflower_data/reviewed_test_questions_checkbox_r01.csv";

    public static void convertTestQuestions() throws IOException {
        hitlParser.setQueryPruningParameters(queryPruningParameters);
        Map<Integer, List<AlignedAnnotation>> annotations = CrowdFlowerDataUtils.loadCorePronounAnnotations();

        for (int sid : annotations.keySet()) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    hitlParser.getCoreArgumentQueriesForSentence()
        /*
        for (RecordedAnnotation testQuestion : testQuestions) {
            final int sentId = testQuestion.sentenceId;
            if (sentId < 0) {
                continue;
            }
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    hitlParser.getPronounCoreArgQueriesForSentence(sentId)
                            //hitlParser.get(sentId)
                            .stream()
                            .filter(query -> query.getPredicateId().getAsInt() == testQuestion.predicateId
                                    && query.getQAPairSurfaceForms().stream()
                                    .flatMap(qa -> qa.getQuestionStructures().stream())
                                    .anyMatch(q -> q.category == testQuestion.predicateCategory &&
                                            q.targetArgNum == testQuestion.argumentNumber))
                            .collect(GuavaCollectors.toImmutableList());

            // TODO: re-align answer options.
            queryList.forEach(query -> {
                final ImmutableList<Integer> realignedOptionIds = AnnotationUtils.getSingleUserResponse(query, testQuestion);
                System.out.println(query.toString(hitlParser.getSentence(sentId),
                        'G', hitlParser.getGoldOptions(query),
                        'T', realignedOptionIds) +
                        String.format("[reason]:\t\t%s\n", testQuestion.comment));
            });
            */
            //queryList.forEach(query -> {
            //final ImmutableList<Integer> realignedOptionIds = AnnotationUtils.getSingleUserResponse(query, testQuestion);
            //    System.out.println(query.toString(hitlParser.getSentence(sentId), 'G', hitlParser.getGoldOptions(query)));
            //});

            //if (queryList.size() == 0) {
            //    System.out.println("------\n" + testQuestion);
            //}
        }
    }

    public static void main(String[] args) throws IOException {
        ImmutableList<RecordedAnnotation> testQuestions = CrowdFlowerDataReader
                .readTestQuestionsFromFile(checkboxTestQuestionFilePath);

        //testQuestions.forEach(System.out::println);
        convertTestQuestionsToPronounQuestions(testQuestions);
    }
}
