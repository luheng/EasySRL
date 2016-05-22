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
import edu.uw.easysrl.qasrl.ui.Colors;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
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

    private static final int minAgreement = 5;
    private static List<RecordedAnnotation> reviewedTestQuestions;

    private static final String reviewedTestQuestionsFile =
            "./Crowdflower_unannotated/test_questions/test_question_core_pronoun_r04.tsv";
    private static final String outputTestQuestionsFile =
            "./Crowdflower_data/reviewed_test_questions_checkbox_r01.csv";

    public static void generateAndMergeTestQuestions() throws IOException {
        reviewedTestQuestions = AnnotationReader.readReviewedTestQuestionsFromTSV(reviewedTestQuestionsFile);

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

                        if (responses.count(goldResponse) >= minAgreement) {
                            matchedAnnotations.add(annotId);
                            String reason = String.format(
                                    "Based on high agreement (at least %d out of 5) among annotators.", minAgreement);

                            for (RecordedAnnotation testQuestion : reviewedTestQuestions) {
                                if (testQuestion.sentenceId == sid && testQuestion.predicateId == predId
                                        && testQuestion.queryPrompt.equals(query.getPrompt())) {
                                    reason = testQuestion.comment;
                                }
                            }
                            // Search for reason.

                            //System.out.println(annot);
                            //System.out.println(query.toString(sentence, 'G', goldResponse, '*',
                            //        AnnotationUtils.getUserResponseDistribution(query, annot)));
                            if (!query.getPrompt().equals(annot.queryPrompt)) {
                                System.out.println(Colors.ANSI_RED + "New:\t" + query.getPrompt() + "\n"
                                        + "Old:\t" + annot.queryPrompt + Colors.ANSI_RESET);
                                System.out.println(query.toString(sentence, 'G', goldResponse, 'T', goldResponse)
                                        + String.format("[reason]:\t%s\t\n", reason));
                            }
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

    public static void main(String[] args) throws IOException {
        generateAndMergeTestQuestions();
    }
}
