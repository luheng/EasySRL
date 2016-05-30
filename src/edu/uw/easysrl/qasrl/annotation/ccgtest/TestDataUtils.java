package edu.uw.easysrl.qasrl.annotation.ccgtest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataReader;
import scala.Immutable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 5/24/16.
 */
public class TestDataUtils {
    public final static String[] annotatedFiles = {
            "./Crowdflower_ccgtest/annotated/f913659.csv",
            "./Crowdflower_ccgtest/annotated/f914016.csv",
            "./Crowdflower_ccgtest/annotated/f914045.csv",
    };

    public static Map<Integer, List<AlignedAnnotation>> readAllAnnotations() {
        Map<Integer, List<AlignedAnnotation>> sentenceToAnnotations;
        List<AlignedAnnotation> annotationList = new ArrayList<>();
        try {
            for (String fileName : annotatedFiles) {
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


    public static void main(String[] args) {
        try {
            CrowdFlowerDataReader.readAggregatedAnnotationFromFile(annotatedFiles[2]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
