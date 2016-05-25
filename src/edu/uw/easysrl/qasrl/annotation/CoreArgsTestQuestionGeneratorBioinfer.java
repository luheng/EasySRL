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
import edu.uw.easysrl.corpora.BioinferCCGCorpus;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.QuestionGenerationPipeline;

import java.io.IOException;
import java.util.*;
import static java.util.stream.Collectors.*;

/**
 * Curated test questions for bioinfer.
 * Created by julianmichael on 5/24/2016.
 */
public class CoreArgsTestQuestionGeneratorBioinfer {

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

    private static final String annotationFile = CrowdFlowerDataUtils.cfBioinferCuratedAnnotations;

    public static void generateTestQuestions() throws IOException {
        BioinferCCGCorpus corpus = BioinferCCGCorpus.readDev().get();
        Map<Integer, NBestList> nbestLists = NBestList.loadNBestListsFromFile("bioinfer.dev.100best.out", 100).get();

        final List<String> autoTestQuestions = new ArrayList<>();

        Map<Integer, List<AlignedAnnotation>> allAnnotations = CrowdFlowerDataUtils
            .loadAnnotations(ImmutableList.of(annotationFile));

        int numAnnotations = 0, numMatchedAnnotations = 0;
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
                    if (annot.predicateId == predId
                            && qstrs.stream().anyMatch(qstr -> qstr.category == annot.predicateCategory
                            && qstr.targetArgNum == annot.argumentNumber)) {
                        HashMultiset<ImmutableList<Integer>> responses =
                                HashMultiset.create(AnnotationUtils.getAllUserResponses(query, annot));
                        String reason = annot.annotatorToComment.get("36562414");
                        // these are Julian's annotations
                        ImmutableList<Integer> goldResponse = annot.annotatorToAnswerIds.get("36562414");

                        // String queryStr = annot.annotatorToAnswerIds.keySet().stream().collect(joining(" "));
                        if(goldResponse != null) {
                            String queryStr = getTestQuestionString(sentence, query, annot, goldResponse, reason);
                            autoTestQuestions.add(queryStr);
                            numMatchedAnnotations++;
                        }
                    }
                }
            }
            numAnnotations += annotations.size();
        }

        System.out.println("Num annotations:\t" + numAnnotations);
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
                .collect(joining("\n")) + "\n";
    }

    public static void main(String[] args) throws IOException {
        generateTestQuestions();
    }
}
