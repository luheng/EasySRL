package edu.uw.easysrl.qasrl.model;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.corpora.PronounList;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

/**
 * Created by luheng on 3/9/16.
 */
public abstract class Evidence {

    // Reward for positive evidence. Penalize for negative evidence. For the A* factored model we can only penalize.
    boolean isPositive;
    double confidence;

    Evidence(boolean isPositive, double confidence) {
        this.isPositive = isPositive;
        this.confidence = confidence;
    }

    public boolean isPositive() {
        return isPositive;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public abstract boolean hasEvidence(Parse parse);

    public abstract String toString(List<String> sentence);

    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Supertag evidence.
     * Negative supertag evidence:
     *   That tag of a predicate is used to generate a question, and voted as "Question not valid".
     */
    public static class SupertagEvidence extends Evidence {
        int predId;
        Category category;
        public SupertagEvidence(int predId, Category category, boolean isPositive, double confidence) {
            super(isPositive, confidence);
            this.predId = predId;
            this.category = category;
        }

        public int getPredId() {
            return predId;
        }

        public Category getCategory() {
            return category;
        }

        public boolean hasEvidence(Parse parse) {
            return parse.categories.get(predId) == category;
        }

        public String toString() {
            return predId + "\t" + category;
        }

        public String toString(List<String> sentence) {
            return predId + ":" + sentence.get(predId) + "\t" + category;
        }
    }

    /**
     * Unlabeled dependency.
     * Negative attachment evidence:
     *   The (head, arg) dep is not picked by a user, and it's not part of a coordination*.
     */
    public static class AttachmentEvidence extends Evidence {
        int headId, argId;
        public AttachmentEvidence(int headId, int argId, boolean isPositive, double confidence) {
            super(isPositive, confidence);
            this.headId = headId;
            this.argId = argId;
        }

        public int getHeadId() {
            return headId;
        }

        public int getArgId() {
            return argId;
        }

        public boolean hasEvidence(Parse parse) {
            return parse.dependencies.stream().anyMatch(dep -> dep.getHead() == headId && dep.getArgument() == argId);
        }

        public String toString() {
            return headId + "\t" + argId;
        }

        public String toString(List<String> sentence) {
            return headId + ":" + sentence.get(headId) + "-->" + argId + ":" + sentence.get(argId);
        }
    }

    public static Set<Evidence> getEvidenceFromQuery(ScoredQuery<QAStructureSurfaceForm> query,
                                                     Collection<Integer> chosenOptions,
                                                     boolean doNotPenalizePronouns) {
        Set<Evidence> evidenceList = new HashSet<>();

        boolean questionIsNA = false;
        Set<Integer> chosenArgIds = new HashSet<>(),
                     listedArgIds = new HashSet<>();
        Map<Integer, String> argIdToSpan = new HashMap<>();
        for (int i = 0; i < query.getOptions().size(); i++) {
            final String option = query.getOptions().get(i);
            if (chosenOptions.contains(i) && option.equals(QueryGeneratorUtils.kBadQuestionOptionString)) {
                questionIsNA = true;
            }
            if (i < query.getQAPairSurfaceForms().size()) {
                final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(i);
                final ImmutableList<Integer> argIds = qa.getArgumentIndices();
                if (chosenOptions.contains(i)) {
                    chosenArgIds.addAll(argIds);
                }
                listedArgIds.addAll(argIds);
                if (doNotPenalizePronouns) {
                    final String[] answerSpans = option.split(QAPairAggregatorUtils.answerDelimiter);
                    for (int j = 0; j < argIds.size(); j++) {
                        argIdToSpan.put(argIds.get(j), answerSpans[j]);
                    }
                }
            }
        }
        final int predId = query.getQAPairSurfaceForms().get(0).getPredicateIndex();
        final Category category = query.getQAPairSurfaceForms().get(0).getCategory();
        if (questionIsNA) {
            evidenceList.add(new SupertagEvidence(predId, category, false, 1.0));
        }
        // TODO: take into account co-ordinations.
        else {
            for (int argId : listedArgIds) {
                if (!chosenArgIds.contains(argId)) {
                    if (doNotPenalizePronouns) {
                        String argSpan = argIdToSpan.get(argId).toLowerCase();
                        if (PronounList.englishPronounSet.contains(argSpan)) {
                            continue;
                        }
                    }
                    evidenceList.add(new AttachmentEvidence(predId, argId, false, 1.0));
                }
            }
        }
        return evidenceList;
    }
}