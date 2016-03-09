package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.analysis.PPAttachment;
import edu.uw.easysrl.qasrl.corpora.PronounList;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filtering stuff.
 * Created by luheng on 3/7/16.
 */
public class QualityControl {
    static Set<Category> propositionalCategories = new HashSet<>();
    static {
        Collections.addAll(propositionalCategories,
                PPAttachment.nounAdjunct,
                PPAttachment.verbAdjunct,
                Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"));
    }

    public static boolean queryContainsPronoun(GroupedQuery query) {
        return query.getAnswerOptions().stream().anyMatch(QualityControl::optionContainsPronoun);
    }

    public static boolean optionContainsPronoun(GroupedQuery.AnswerOption option) {
        if (option.isNAOption()) {
            return false;
        }
        for (String span : option.getAnswer().split(QuestionAnswerPair.answerDelimiter)) {
            if (PronounList.englishPronounSet.contains(span.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean queryContainsMultiArg(GroupedQuery query) {
        return query.getAnswerOptions().stream().anyMatch(QualityControl::optionContainsMultiArg);
    }

    public static boolean optionContainsMultiArg(GroupedQuery.AnswerOption option) {
        return option.getAnswer().contains(QuestionAnswerPair.answerDelimiter);
    }

    public static boolean queryIsPrepositional(GroupedQuery query) {
        return propositionalCategories.contains(query.getCategory());
    }

    public static int getAgreementNumber(GroupedQuery query, AlignedAnnotation annotation) {
        int[] optionDist = getUserResponses(query, annotation);
        int agreement = 1;
        for (int i = 0; i < optionDist.length; i++) {
            agreement = Math.max(agreement, optionDist[i]);
        }
        return agreement;
    }


    public static int[] getUserResponses(GroupedQuery query, AlignedAnnotation annotation) {
        String qkey =query.getPredicateIndex() + "\t" + query.getQuestion();
        int numOptions = query.getAnswerOptions().size();
        int[] optionDist = new int[numOptions];
        Arrays.fill(optionDist, 0);
        String qkey2 = annotation.predicateId + "\t" + annotation.question;
        if (qkey.equals(qkey2)) {
            for (int i = 0; i < numOptions; i++) {
                for (int j = 0; j < annotation.answerOptions.size(); j++) {
                    if (query.getAnswerOptions().get(i).getAnswer().equals(annotation.answerStrings.get(j))) {
                        optionDist[i] += annotation.answerDist[j];
                        break;
                    }
                }
            }
        }
        return optionDist;
    }

}
