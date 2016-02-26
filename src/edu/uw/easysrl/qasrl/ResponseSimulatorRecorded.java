package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;

import java.util.*;

/**
 * Simulation by recorded annotation.
 * Created by luheng on 2/24/16.
 */
public class ResponseSimulatorRecorded extends ResponseSimulator {
    private boolean allowLabelMatch = true;
    private Map<String, AlignedAnnotation> alignedAnnotations;

    // Evidence propagation switches
    // TODO: debug this ..
    public boolean propagateArgumentAdjunctEvidence = false;

    // TODO: simulate noise level.
    // TODO: partial reward for parses that got part of the answer heads right ..
    public ResponseSimulatorRecorded(final List<AlignedAnnotation> alignedAnnotationList) {
        alignedAnnotations = new HashMap<>();
        alignedAnnotationList.forEach(annotation -> {
            String qkey = annotation.sentenceId + "\t" + annotation.predicateId + "\t" + annotation.predicateCategory
                    + "\t" + annotation.argumentNumber + "\t" + annotation.question;
            System.out.println(qkey);
            alignedAnnotations.put(qkey, annotation);
        });
    }

    public ResponseSimulatorRecorded(final List<AlignedAnnotation> alignedAnnotationList, boolean allowLabelMatch) {
        this(alignedAnnotationList);
        this.allowLabelMatch = allowLabelMatch;
    }

    /**
     * @param query: question
     * @return Answer is represented a list of indices in the sentence.
     */
    public Response answerQuestion(GroupedQuery query) {
        Response response = new Response();
        String qkey = query.sentenceId + "\t" + query.predicateIndex + "\t" + query.category + "\t"
                + query.argumentNumber + "\t" + query.question;
        System.out.println(qkey);
        if (!alignedAnnotations.containsKey(qkey)) {
            System.err.println("No annotation matched.");
            return response;
        }
        AlignedAnnotation annotation = alignedAnnotations.get(qkey);

        String majorityAnswer = "";
        double maxTrust = -1.0;
        for (int i = 0; i < annotation.answerDist.length; i++) {
           // if (annotation.answerDist[i] == 5) {
            // Tie-breaking by worker trust.
            if (annotation.answerTrust[i] > maxTrust) {
                majorityAnswer = annotation.answerOptions.get(i);
                maxTrust = annotation.answerTrust[i];
            }
        }
        for (int i = 0; i < query.answerOptions.size(); i++) {
            GroupedQuery.AnswerOption option = query.answerOptions.get(i);
            if (option.getAnswer().equals(majorityAnswer) && maxTrust > 3.5) {
                response.add(i);
            }
        }
        response.debugInfo = "max trust:\t" + maxTrust;
        response.trust = maxTrust;
        /*
        if (response.chosenOptions.size() == 0) {
            System.err.println(query.getDebuggingInfo(new Response()));
            response.add(-1);
        }
        */
        return response;
    }
}

