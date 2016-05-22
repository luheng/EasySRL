package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.io.IOException;
import java.util.HashSet;
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
    private final static HITLParser hitlParser = new HITLParser(100 /* nBest */);

    static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipBinaryQueries = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = -1;
    }

    final static String checkboxTestQuestionFilePath = "./Crowdflower_data/reviewed_test_questions_checkbox_r01.csv";

    public static void generateTestQuestions() throws IOException {
        hitlParser.setQueryPruningParameters(queryPruningParameters);
        Map<Integer, List<AlignedAnnotation>> allAnnotations = CrowdFlowerDataUtils.loadCorePronounAnnotations();

        int numAnnotations = 0, numPredMatchedAnnotations = 0, numMatchedAnnotations = 0;
        for (int sid : allAnnotations.keySet()) {
            HashSet<Integer> predicateMatchedAnnotations = new HashSet<>(),
                             matchedAnnotations = new HashSet<>();

            final ImmutableList<String> sentence = hitlParser.getSentence(sid);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    hitlParser.getNewCoreArgQueriesForSentence(sid);
            final List<AlignedAnnotation> annotations = allAnnotations.get(sid);

            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                // Match annotation: 5 overlapping annotations, same predicate.
                final int predId = query.getPredicateId().getAsInt();
                final Category category = query.getPredicateCategory().get();
                final int argNum = query.getArgumentNumber().getAsInt();

                for (int annotId = 0; annotId < annotations.size(); annotId++) {
                    final AlignedAnnotation annot = annotations.get(annotId);
                    if (annot.predicateId == predId
                            && category == annot.predicateCategory
                            && argNum == annot.argumentNumber) {
                        predicateMatchedAnnotations.add(annotId);
                        HashMultiset<ImmutableList<Integer>> responses =
                                HashMultiset.create(AnnotationUtils.getAllUserResponses(query, annot));
                        ImmutableList<Integer> goldResponse = hitlParser.getGoldOptions(query);

                        if (responses.count(goldResponse) >= 5) {
                            matchedAnnotations.add(annotId);
                            //if (!query.getPrompt().equals(annot.queryPrompt)) {
                            //System.out.println(query.getPrompt() + "\n" + annot.queryPrompt);
                            /*
                            System.out.println(annot);
                            System.out.println(query.toString(sentence, 'G', goldResponse, '*',
                                    AnnotationUtils.getUserResponseDistribution(query, annot))); */

                            System.out.println(query.toString(sentence, 'G', goldResponse, 'T', goldResponse)
                                    + String.format("[reason]:\t\t%s\n", "Based on high agreement (at least 5 out of 5) among annotators."));
                            //}
                        }
                    }
                }
            }
            numAnnotations += annotations.size();
            numPredMatchedAnnotations += predicateMatchedAnnotations.size();
            numMatchedAnnotations += matchedAnnotations.size();
        }

        System.out.println("Num annotations:\t" + numAnnotations);
        System.out.println("Num predicate matched annotations:\t" + numPredMatchedAnnotations);
        System.out.println("Num matched annotations:\t" + numMatchedAnnotations);
    }


    // TODO: test question writer "GT" and "reason" fields.


    public static void main(String[] args) throws IOException {
        ImmutableList<RecordedAnnotation> testQuestions = CrowdFlowerDataReader
                .readTestQuestionsFromFile(checkboxTestQuestionFilePath);

        //testQuestions.forEach(System.out::println);
        generateTestQuestions();
    }
}
