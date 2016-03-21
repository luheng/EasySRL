package edu.uw.easysrl.qasrl.experiments;

import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataReader;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 3/21/16.
 */
class ExperimentUtils {

    static class DebugBlock {
        double deltaF1;
        String block;
        DebugBlock(double deltaF1, String block) {
            this.deltaF1 = deltaF1;
            this.block = block;
        }
    }

    static Map<Integer, List<AlignedAnnotation>> loadData(String[] fileNames) {
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
