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
import java.util.concurrent.CancellationException;
import java.util.stream.IntStream;

/**
 * Created by luheng on 3/9/16.
 */
public abstract class Evidence {

    // Reward for positive evidence. Penalize for negative evidence. For the A* factored model we can only penalize.
    private boolean isPositive;
    private double confidence;

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
     *   That tag of a predicate is used to generate a queryPrompt, and voted as "Question not valid".
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
}