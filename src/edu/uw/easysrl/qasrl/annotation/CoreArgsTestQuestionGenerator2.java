package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.ui.Colors;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mostly temporary and unorganized methods for getting test questions ...
 * Created by luheng on 4/6/16.
 */
public class CoreArgsTestQuestionGenerator2 {
    private final static HITLParser hitlParser = new HITLParser(100 /* nBest */);

    static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipBinaryQueries = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static final int minAgreement = 5;

    private static final String reviewedTestQuestionsFile =
            "./Crowdflower_unannotated/test_questions/test_question_core_pronoun_r04.tsv";

    public static void generateAndMergeTestQuestions() throws IOException {
        final List<RecordedAnnotation> reviewedTestQuestions =
                AnnotationReader.readReviewedTestQuestionsFromTSV(reviewedTestQuestionsFile);
        final Set<Integer> matchedTestQuestionIds = new HashSet<>();
        final List<String> autoTestQuestions = new ArrayList<>();

        hitlParser.setQueryPruningParameters(queryPruningParameters);
        Map<Integer, List<AlignedAnnotation>> allAnnotations = CrowdFlowerDataUtils.loadAnnotations(ImmutableList.of(
                CrowdFlowerDataUtils.cfRound1And2CoreArgsRerun,
                CrowdFlowerDataUtils.cfRound6CoreArgsAnnotationFile
        ));

        int numAnnotations = 0, numPredMatchedAnnotations = 0, numMatchedAnnotations = 0;
        for (int sid : allAnnotations.keySet()) {
            HashSet<Integer> predicateMatchedAnnotations = new HashSet<>(),
                    matchedAnnotations = new HashSet<>();

            final ImmutableList<String> sentence = hitlParser.getSentence(sid);
            final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    hitlParser.getNewCoreArgQueriesForSentence(sid);
            final List<AlignedAnnotation> annotations = allAnnotations.get(sid);

            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                final int predId = query.getPredicateId().getAsInt();
                final Set<QuestionStructure> qstrs = query.getQAPairSurfaceForms().stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream())
                        .distinct()
                        .collect(Collectors.toSet());

                for (int annotId = 0; annotId < annotations.size(); annotId++) {
                    final AlignedAnnotation annot = annotations.get(annotId);
                    if (annot.predicateId == predId
                            && qstrs.stream().anyMatch(qstr -> qstr.category == annot.predicateCategory
                            && qstr.targetArgNum == annot.argumentNumber)) {
                        predicateMatchedAnnotations.add(annotId);
                        HashMultiset<ImmutableList<Integer>> responses =
                                HashMultiset.create(AnnotationUtils.getAllUserResponses(query, annot));
                        ImmutableList<Integer> goldResponse = hitlParser.getGoldOptions(query);

                        if (responses.count(goldResponse) >= minAgreement) {
                            matchedAnnotations.add(annotId);
                            String reason = String.format(
                                    "Based on high agreement (at least %d out of 5) among annotators.", minAgreement);

                            for (int tid = 0; tid < reviewedTestQuestions.size(); tid++) {
                                final RecordedAnnotation testQuestion = reviewedTestQuestions.get(tid);
                                if (testQuestion.sentenceId == sid && testQuestion.predicateId == predId
                                        && testQuestion.queryPrompt.equals(query.getPrompt())) {
                                    reason = testQuestion.comment;
                                    matchedTestQuestionIds.add(tid);
                                }
                            }
                            //System.out.println(annot);
                            //System.out.println(query.toString(sentence, 'G', goldResponse, '*',
                            //        AnnotationUtils.getUserResponseDistribution(query, annot)));
                            if (!query.getPrompt().equals(annot.queryPrompt)) {
                                System.out.println(Colors.ANSI_RED + "New:\t" + query.getPrompt() + "\n"
                                        + "Old:\t" + annot.queryPrompt + Colors.ANSI_RESET);
                            }
                            String queryStr = getTestQuestionString(sentence, query, annot, goldResponse, reason);
                            autoTestQuestions.add(queryStr);
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

        autoTestQuestions.forEach(System.out::println);
    }

    private static String getTestQuestionString(final ImmutableList<String> sentence,
                                                final ScoredQuery<QAStructureSurfaceForm> query,
                                                final AlignedAnnotation annotation,
                                                final ImmutableList<Integer> response,
                                                final String reason) {
        String status = query.getPrompt().equals(annotation.queryPrompt) ? "R" : "M";
        String queryStr = status + "\t"
                + query.toTestQuestionString(sentence, 'G', response, 'T', response)
                + String.format("[reason]:\t%s\t\n", reason);
        return ImmutableList.copyOf(queryStr.split("\\n")).stream()
                .map(s -> s.startsWith(status + "\t") ? s : " \t" + s)
                .collect(Collectors.joining("\n")) + "\n";
    }

    public static void main(String[] args) throws IOException {
        generateAndMergeTestQuestions();
    }
}
