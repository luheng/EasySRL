//package edu.uw.easysrl.qasrl.annotation;
//
//import com.google.common.collect.ImmutableSet;
//
//import java.io.IOException;
//import java.util.*;
//import java.util.stream.*;
//
///**
// * Compute inter-annotator agreement.
// * Created by luheng on 2/13/16.
// *
// *
// * Input:
// * pilot_annotation/Julian_20160211-2216.txt pilot_annotation/Luke_20160212-1257.txt pilot_annotation/Mike_20160211-2125.txt pilot_annotation/Luheng_20160213-1722.txt
// *
// * pilot_annotation/Zoopdeloop_20160214-1210.txt pilot_annotation/dcarlino_20160214-1242.txt
// *
// * pilot_annotation/Mike_20160211-2125.txt pilot_annotation/Luheng_20160213-1722.txt pilot_annotation/Zoopdeloop_20160214-1210.txt pilot_annotation/dcarlino_20160214-1242.txt
// */
//
//public class InterAnnotatorAgreement {
//
//    public static void computeKappaFor4(Map<String, List<Annotation>> annotations) {
//        double[] kappa4 = computeAgreement(annotations, null);
//        double[] avgKappa2 = new double[3], avgKappa3 = new double[4];
//
//        int[][] acc4 = computeAccuracy(annotations, null);
//        double[][] avgAcc3 = new double[4][2];
//        double[] avgAcc2 = new double[2], avgAcc = new double[2];
//
//        for (String exclude : annotations.keySet()) {
//            Set<String> annotators = new HashSet<>(annotations.keySet());
//            //System.out.println("Exclude:\t" + exclude);
//            annotators.remove(exclude);
//            double[] kappa = computeAgreement(annotations, annotators);
//            int[][] acc = computeAccuracy(annotations, annotators);
//            for (int i = 0; i < 4; i++) {
//                avgKappa3[i] += kappa[i] / 4.0;
//            }
//            // Agreed by at least 2 in 3.
//            avgAcc3[2][0] += acc[2][0] / 4.0;
//            avgAcc3[2][1] += acc[2][1] / 4.0;
//            // Agreed by at least 3 in 3.
//            avgAcc3[3][0] += acc[3][0] / 4.0;
//            avgAcc3[3][1] += acc[3][1] / 4.0;
//            for (String exclude2 : annotators) {
//                Set<String> annotators2 = new HashSet<>(annotators);
//                annotators2.remove(exclude2);
//                kappa = computeAgreement(annotations, annotators2);
//                acc = computeAccuracy(annotations, annotators2);
//                for (int i = 0; i < 3; i++) {
//                    avgKappa2[i] += kappa[i] / 12.0;
//                }
//                // Agreed by at least 2 in 2
//                avgAcc2[0] += acc[2][0] / 12.0;
//                avgAcc2[1] += acc[2][1] / 12.0;
//            }
//
//            acc = computeAccuracy(annotations, ImmutableSet.of(exclude));
//            avgAcc[0] += acc[1][0] / 4.0;
//            avgAcc[1] += acc[1][1] / 4.0;
//        }
//
//        printKappa(kappa4);
//        printKappa(avgKappa3);
//        printKappa(avgKappa2);
//
//        printKappa(computeAgreement(annotations, ImmutableSet.of("Zoopdeloop", "dcarlino")));
//        int[][] acc = computeAccuracy(annotations, ImmutableSet.of("Zoopdeloop", "dcarlino"));
//        System.out.println(acc[2][0] + ", " + acc[2][1] + ", " + 100.0 * acc[2][0] / acc[2][1]);
//
//        System.out.println("4 in 4:\t" + acc4[4][0] + "\t" + acc4[4][1] + "\t" + 100.0 * acc4[4][0] / acc4[4][1]);
//        System.out.println("3 in 4:\t" + acc4[3][0] + "\t" + acc4[3][1] + "\t" + 100.0 * acc4[3][0] / acc4[3][1]);
//        System.out.println("3 in 3:\t" + avgAcc3[3][0] + "\t" + avgAcc3[3][1] + "\t" + 100.0 * avgAcc3[3][0] / avgAcc3[3][1]);
//        System.out.println("2 in 3:\t" + avgAcc3[2][0] + "\t" + avgAcc3[2][1] + "\t" + 100.0 * avgAcc3[2][0] / avgAcc3[2][1]);
//        System.out.println("2 in 2:\t" + avgAcc2[0] + "\t" + avgAcc2[1] + "\t" + 100.0 * avgAcc2[0] / avgAcc2[1]);
//        System.out.println("1 in 1:\t" + avgAcc[0] + "\t" + avgAcc[1] + "\t" + 100.0 * avgAcc[0] / avgAcc[1]);
//    }
//
//    public static void printKappa(double[] kappa) {
//        int numAnnotators = kappa.length - 1;
//        for (int i = 2; i <= numAnnotators; i++) {
//            System.out.println(String.format("%d annotators\t%d-agreement\tKappa: %.3f%%", numAnnotators, i,
//                    100.0 * kappa[i]));
//        }
//        System.out.println();
//    }
//
//    // returns { accuracy, precision, recall, F1 }
//    public static double[] binaryStats(List<Annotation> annotations) {
//        // precision and recall only make sense with two options.
//        // 0 is negative, 1 is positive.
//        assert annotations.stream().allMatch(anno -> anno.getNumAnswers() == 2);
//
//        int total = annotations.size();
//        long tp = annotations
//            .stream()
//            .filter(anno -> anno.getAnswerId() == 1 && anno.getGoldAnswerId() == 1)
//            .collect(Collectors.counting());
//        long fp = annotations
//            .stream()
//            .filter(anno -> anno.getAnswerId() == 1 && anno.getGoldAnswerId() == 0)
//            .collect(Collectors.counting());
//        long tn = annotations
//            .stream()
//            .filter(anno -> anno.getAnswerId() == 0 && anno.getGoldAnswerId() == 0)
//            .collect(Collectors.counting());
//        long fn = annotations
//            .stream()
//            .filter(anno -> anno.getAnswerId() == 0 && anno.getGoldAnswerId() == 1)
//            .collect(Collectors.counting());
//        double accuracy = ((double)tp + tn) / total;
//        double precision = ((double)tp)/(tp + fp);
//        double recall = ((double)tp)/(tp + fn);
//        double f1 = 2.0 * precision * recall / (precision + recall);
//        double[] result = { accuracy, precision, recall, f1 };
//        return result;
//    }
//
//    // this only works for checkboxes because it requires a fixed set of categories.
//    public static double fleissKappa(List<RecordedCheckboxAnnotation> annotations, int numJudgmentsPerItem) {
//        Set<String> annotators = annotations
//            .stream()
//            .map(a -> a.annotatorId)
//            .collect(Collectors.toSet());
//        int numAnnotators = annotators.size();
//        int numCategories = 2;
//
//        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation
//            .getAlignedAnnotations(new ArrayList<Annotation>(annotations))
//            .stream()
//            .filter(anno -> anno.getNumAnnotated() == 5)
//            .collect(Collectors.toList());
//        int numItems = alignedAnnotations.size();
//        System.out.println("Number of items: " + numItems);
//        List<Integer> numAnswersPerCategory = IntStream.range(0, numCategories)
//            .mapToObj(i -> alignedAnnotations
//                 .stream()
//                 .mapToInt(a -> a.answerDist[i])
//                 .sum())
//            .collect(Collectors.toList());
//        int totalJudgments = numAnswersPerCategory.stream().mapToInt(Integer::intValue).sum();
//        List<Double> propAnswersPerCategory = numAnswersPerCategory
//            .stream()
//            .map(i -> ((double) i) / totalJudgments)
//            .collect(Collectors.toList());
//        double meanAgreement = alignedAnnotations
//            .stream()
//            .mapToDouble(anno -> 1.0 / (numJudgmentsPerItem * (numJudgmentsPerItem - 1)) *
//                 Arrays.stream(anno.answerDist).map(i -> i * (i - 1)).sum())
//            .sum() / numItems;
//        double chanceMeanAgreement = propAnswersPerCategory
//            .stream()
//            .mapToDouble(i -> i * i)
//            .sum();
//        double kappa = (meanAgreement - chanceMeanAgreement) / (1.0 - chanceMeanAgreement);
//        return kappa;
//    }
//
//    /**
//     * Compute inter-annotator agreement given (not necessarily aligned) annotation records from different people.
//     * Cohen's Kappa:
//     *      k = 1 - (1 - p_o) / (1 - p_e)
//     *      p_o: Observed agreement among raters. p_o = #times_agreed_questions / #total_aligned_questions
//     *      p_e: Chance agreement. p_e = \sum_q ( 1.0 / #options(q)) / #total_aligned_questions
//     * @param annotations: A map from annotator_identifier to all the annotation records.
//     * @param annotators: a subset of annotators.
//     */
//    public static double[] computeAgreement(Map<String, List<Annotation>> annotations,
//                                            Collection<String> annotators) {
//        if (annotators == null) {
//            annotators = annotations.keySet();
//        } else {
//            Map<String, List<Annotation>> filteredAnnotations = new HashMap<>();
//            for(String annotator : annotators) {
//                filteredAnnotations.put(annotator, annotations.get(annotator));
//            }
//            annotations = filteredAnnotations;
//        }
//
//        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations);
//        int numAnnotators = annotators.size();
//        int[] annotationCount = new int[numAnnotators + 1];
//        double[] agreement = new double[numAnnotators + 1];
//        double[] changeAgreement = new double[numAnnotators + 1];
//        double[] kappa = new double[numAnnotators + 1];
//
//        Arrays.fill(annotationCount, 0);
//        Arrays.fill(agreement, 0.0);
//        Arrays.fill(changeAgreement, 0.0);
//        double norm = .0;
//
//        for (AlignedAnnotation annotation : alignedAnnotations) {
//            for (int i = 2; i <= annotation.getNumAnnotated(); i++) {
//                annotationCount[i]++;
//            }
//            if (annotation.getNumAnnotated() == numAnnotators) {
//                int numOptions = annotation.answerOptions.size();
//                for (int i = 2; i <= numAnnotators; i++) {
//                    changeAgreement[i] += computeAgreementChance(numAnnotators, i /* agreement */, numOptions);
//                }
//                boolean[] agreed = new boolean[numAnnotators + 1];
//                Arrays.fill(agreed, false);
//                for (int d : annotation.answerDist) {
//                    for (int i = 2; i <= d; i++) {
//                        agreed[i] = true;
//                    }
//                }
//                for (int i = 2; i <= numAnnotators; i++) {
//                    if (agreed[i]) {
//                        agreement[i] += 1.0;
//                    }
//                }
//                norm += 1.0;
//            }
//        }
//        for (int i = 2; i < kappa.length; i++) {
//            agreement[i] /= norm;
//            changeAgreement[i] /= norm;
//            kappa[i] = 1.0 - (1.0 - agreement[i]) / (1.0 - changeAgreement[i]);
//            //System.out.println(String.format("%d annotators\t%d-agreement\tKappa: %.3f%%\ton %d questions",
//            //        numAnnotators, i, 100.0 * kappa[i], annotationCount[numAnnotators]));
//        }
//        return kappa;
//    }
//
//    public static int[][] computeAccuracy(Map<String, List<Annotation>> annotations,
//                                         Collection<String> annotators) {
//        if (annotators == null) {
//            annotators = annotations.keySet();
//        } else {
//            Map<String, List<Annotation>> filteredAnnotations = new HashMap<>();
//            for(String annotator : annotators) {
//                filteredAnnotations.put(annotator, annotations.get(annotator));
//            }
//            annotations = filteredAnnotations;
//        }
//        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations);
//        int numAnnotators = annotators.size();
//        int[][] result = new int[numAnnotators + 1][2];
//        for (AlignedAnnotation annotation : alignedAnnotations) {
//            for (int opt = 0; opt < annotation.answerDist.length; opt++) {
//                int d = annotation.answerDist[opt];
//                if (d > numAnnotators / 2) {
//                    for (int i = numAnnotators / 2 + 1; i <= d; i++) {
//                        result[i][1]++;
//                        if (opt == annotation.goldAnswerId) {
//                            result[i][0]++;
//                        }
//                    }
//                }
//            }
//        }
//        return result;
//    }
//
//    public static double computeAgreementChance(int total, int agreement, int options) {
//        if (total < agreement) {
//            return 0.0;
//        }
//        if (agreement == 1) {
//            return 1.0;
//        }
//        double p = 1.0 / options;
//        return  p * computeAgreementChance(total - 1, agreement - 1, options)
//                    + (1.0 - p) * computeAgreementChance(total - 1, agreement, options);
//    }
//
//    public static void main(String[] args) {
//        List<String> filenames = Arrays.asList(args);
//        Map<String, List<Annotation>> annotations = getCheckboxAnnotations(filenames);
//        double[] kappa = computeAgreement(annotations, null);
//        printKappa(kappa);
//    }
//
//    public static Map<String, List<Annotation>> getCheckboxAnnotations(List<String> filenames) {
//        Map<String, List<Annotation>> annotations = new HashMap<>();
//        for (String filename : filenames) {
//            String[] info = filename.split("/");
//            String annotator = info[info.length - 1].split("_")[0];
//            System.out.println(annotator);
//            try {
//                annotations.put(annotator,
//                                new ArrayList<>(RecordedCheckboxAnnotation.loadAnnotationRecordsFromFile(filename)));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        return annotations;
//    }
//
//    public static Map<String, List<Annotation>> getMultipleChoiceAnnotations(List<String> filenames) {
//        Map<String, List<RecordedAnnotation>> annotations = new HashMap<>();
//        for (String filename : filenames) {
//            String[] info = filename.split("/");
//            String annotator = info[info.length - 1].split("_")[0];
//            System.out.println(annotator);
//            try {
//                annotations.put(annotator, new ArrayList<>(RecordedAnnotation.loadAnnotationRecordsFromFile(filename)));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        return annotations;
//    }
//}
//*/
