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
import java.util.concurrent.atomic.AtomicInteger;
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

    public static void generateTestQuestions() throws IOException {
        hitlParser.setQueryPruningParameters(queryPruningParameters);
        Map<Integer, List<AlignedAnnotation>> allAnnotations = CrowdFlowerDataUtils.loadCorePronounAnnotations();

        AtomicInteger numAnnotations = new AtomicInteger(0),
                      numMatchedAnnotations = new AtomicInteger(0),
                      numPredMatchedAnnotations = new AtomicInteger(0);

        for (int sid : allAnnotations.keySet()) {
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    hitlParser.getNewCoreArgQueriesForSentence(sid);
            final List<AlignedAnnotation> annotations = allAnnotations.get(sid);
            numAnnotations.addAndGet(annotations.size());
            queryList.forEach(query -> {
                // Match annotation: 5 overlapping annotations, same predicate.
                final int predId = query.getPredicateId().getAsInt();
                annotations.stream()
                        .filter(annot -> annot.predicateId == predId
                                && query.getPredicateCategory().get() == annot.predicateCategory
                                && query.getArgumentNumber().getAsInt() == annot.argumentNumber)
                        .map(annot -> {
                            numPredMatchedAnnotations.getAndAdd(1);
                            return annot;
                        })
                        .filter(annot -> {
                            // TODO: 5 annotaiton match
                            int nmatch = IntStream.range(0, annot.answerOptions.size())
                                    .filter(op -> query.getOptions().contains(annot.answerOptions.get(op)))
                                    .map(op -> annot.answerDist[op])
                                    .sum();
                            return nmatch >= 5;
                        })
                        .forEach(annot -> {
                            if (!query.getPrompt().equals(annot.queryPrompt)) {
                                System.out.println(query.getPrompt() + "\n" + annot.queryPrompt);
                                System.out.println(annot);
                            }
                            numMatchedAnnotations.addAndGet(1);
                        });
            });
        }

        System.out.println("Num annotations:\t" + numAnnotations);
        System.out.println("Num predicate matched annotations:\t" + numPredMatchedAnnotations);
        System.out.println("Num matched annotations:\t" + numMatchedAnnotations);
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

    public static void main(String[] args) throws IOException {
        ImmutableList<RecordedAnnotation> testQuestions = CrowdFlowerDataReader
                .readTestQuestionsFromFile(checkboxTestQuestionFilePath);

        //testQuestions.forEach(System.out::println);
        generateTestQuestions();
    }
}
