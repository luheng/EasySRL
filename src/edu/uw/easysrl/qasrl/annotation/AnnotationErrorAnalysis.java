package edu.uw.easysrl.qasrl.annotation;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mostly a printer for aligned annotation, for convenience of manual error analysis...
 * Created by luheng on 2/15/16.
 */
public class AnnotationErrorAnalysis {

    public static void main(String[] args) {
        Map<String, List<RecordedAnnotation>> annotations = new HashMap<>();
        for (String fileName : args) {
            String[] info = fileName.split("/");
            String annotator = info[info.length - 1].split("_")[0];
            System.out.println(annotator);
            try {
                annotations.put(annotator, RecordedAnnotation.loadAnnotationRecordsFromFile(fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations, null);
        for (AlignedAnnotation alignedAnnotation : alignedAnnotations) {
            // TODO: pretty print
        }
    }
}
