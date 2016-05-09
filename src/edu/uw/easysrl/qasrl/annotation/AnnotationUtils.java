package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.Query;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filtering stuff.
 * Created by luheng on 3/7/16.
 */
public class AnnotationUtils {
    static Set<Category> propositionalCategories = new HashSet<>();
    static {
        Collections.addAll(propositionalCategories,
                Category.valueOf("((S\\NP)\\(S\\NP))/NP"),
                Category.valueOf("(NP\\NP)/NP"),
                Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"));
    }

    static Set<String> badQuestionStrings = new HashSet<>();
    static {
        badQuestionStrings.add(QueryGeneratorUtils.kOldBadQuestionOptionString);
        badQuestionStrings.add(QueryGeneratorUtils.kBadQuestionOptionString);
        badQuestionStrings.add(QueryGeneratorUtils.kNoneApplicableString);
    }

    // FIXME: stream doesn't work here ... why?
    public static boolean queryContainsPronoun(Query query) {
        for (Object o : query.getOptions()) {
            if (optionContainsPronoun((String) o)) {
                return true;
            }
        }
        return false;
    }

    public static boolean optionContainsPronoun(String optionString) {
        if (optionString.equals(QueryGeneratorUtils.kBadQuestionOptionString) ||
                optionString.equals(QueryGeneratorUtils.kUnlistedAnswerOptionString)) {
            return false;
        }
        for (String span : optionString.split(QAPairAggregatorUtils.answerDelimiter)) {
            if (PronounList.englishPronounSet.contains(span.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean queryContainsMultiArg(Query query) {
        for (Object o : query.getOptions()) {
            if (optionContainsMultiArg((String) o)) {
                return true;
            }
        }
        return false;
    }

    public static boolean optionContainsMultiArg(String optionString) {
        return optionString.contains(QAPairAggregatorUtils.answerDelimiter);
    }

    public static boolean queryIsPrepositional(ScoredQuery<QAStructureSurfaceForm> query) {
        return (!query.isJeopardyStyle() && query.getQAPairSurfaceForms().get(0)
                                            .getQuestionStructures().stream()
                                            .anyMatch(qs -> propositionalCategories.contains(qs.category))) ||
                query.isJeopardyStyle();
    }

    public static int[] getUserResponseDistribution(Query query, AlignedAnnotation annotation) {
        int numOptions = query.getOptions().size();
        int[] optionDist = new int[numOptions];
        Arrays.fill(optionDist, 0);
        if (query.getPrompt().equals(annotation.queryPrompt)) {
            for (int i = 0; i < numOptions; i++) {
                for (int j = 0; j < annotation.answerOptions.size(); j++) {
                    String optionStr = (String) query.getOptions().get(i);
                    String annotatedStr = annotation.optionStrings.get(j);
                    if (optionStr.equals(annotatedStr) || (badQuestionStrings.contains(optionStr) &&
                                    badQuestionStrings.contains(annotatedStr))) {
                        optionDist[i] += annotation.answerDist[j];
                        break;
                    }
                }
            }
        }
        return optionDist;
    }

    public static ImmutableList<Integer> getSingleUserResponse(Query query, RecordedAnnotation annotation) {
        int numOptions = query.getOptions().size();
        final Set<Integer> optionIds = new HashSet<>();
        //if (query.getPrompt().equals(annotation.queryPrompt)) {
            for (int i = 0; i < numOptions; i++) {
                String optionStr = (String) query.getOptions().get(i);
                for (String annotatedStr : annotation.userOptions) {
                    if (optionStr.equals(annotatedStr) ||
                            (badQuestionStrings.contains(optionStr) &&
                             badQuestionStrings.contains(annotatedStr))) {
                        optionIds.add(i);
                        break;
                    }
                }
            }
        //}
        return optionIds.stream().distinct().sorted().collect(GuavaCollectors.toImmutableList());
    }
}