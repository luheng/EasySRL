//package edu.uw.easysrl.qasrl.annotation;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Definition of harmful annotations:
// *     1). Causes a decrease in rerankF1, compared to previous annotation record of the same sentence.
// *     2). Causes the rerankF1 to be lower than onebestF1, if answer is the first record of the sentence.
// * Created by luheng on 2/15/16.
// */
//public class PrintHarmfulAnnotations {
//    /*
//    public static List<RecordedAnnotation> getHarmfulAnnotations(final List<Annotation> annotations) {
//        List<RecordedAnnotation> result = new ArrayList<>();
//        for (int i = 0; i < annotations.size(); i++) {
//            if(!(annotations.get(i) instanceof RecordedAnnotation)) {
//                continue;
//            }
//            final RecordedAnnotation curr = (RecordedAnnotation) annotations.get(i);
//            if (i == 0 || annotations.get(i - 1).sentenceId != curr.sentenceId) {
//                if (curr.rerankF1 < curr.onebestF1) {
//                    result.add(curr);
//                }
//            } else if ((annotations.get(i - 1) instanceof RecordedAnnotation) && curr.rerankF1 < ((RecordedAnnotation)annotations.get(i - 1)).rerankF1) {
//                result.add(curr);
//            }
//        }
//        return result;
//    }
//    */
//
//    public static void main(String[] args) {
//        String fileName = args[0];
//        try {
//            List<Annotation> annotations = new ArrayList<>(RecordedAnnotation.loadAnnotationRecordsFromFile(fileName));
//            List<RecordedAnnotation> harmfulAnnotations = getHarmfulAnnotations(annotations);
//
//            harmfulAnnotations.forEach(System.out::println);
//            System.out.println(String.format("Found %d (%.2f%%) harmful annotations.",
//                    harmfulAnnotations.size(),
//                    100.0 * harmfulAnnotations.size() / annotations.size()));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}
