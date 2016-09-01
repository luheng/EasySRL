package edu.uw.easysrl.syntax.model;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/9/16.
 */
public abstract class Constraint {
    // Reward for positive evidence. Penalize for negative evidence. For the A* factored model we can only penalize.
    protected boolean isPositive;
    protected double strength;
    protected String hashString;

    // A hack.
    public double prediction;

    Constraint(boolean isPositive, double strength) {
        this.isPositive = isPositive;
        this.strength = strength;
    }

    public boolean isPositive() {
        return isPositive;
    }

    public double getStrength() {
        return strength;
    }

    public void setStrength(double strength) {
        this.strength = strength;
    }

    public abstract String toString(List<String> sentence);

    public int hashCode() {
        return hashString.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        return hashCode() == other.hashCode();
    }

    /**
     * Supertag evidence.
     * Negative supertag evidence:
     *   That tag of a predicate is used to generate a queryPrompt, and voted as "Question not valid".
     */
    public static class SupertagConstraint extends Constraint {
        int predId;
        Category category;

        public SupertagConstraint(int predId, Category category, boolean isPositive, double confidence) {
            super(isPositive, confidence);
            this.predId = predId;
            this.category = category;
            this.hashString = toString();
        }

        public int getPredId() {
            return predId;
        }

        public Category getCategory() {
            return category;
        }

        public String toString() {
            return (isPositive ? "[+]" : "[-]") + "\t" + predId + "\t" + category;
        }

        public String toString(List<String> sentence) {
            return (isPositive ? "[+]" : "[-]") + "\t" + strength + "\t" + predId + ":" + sentence.get(predId) + "\t"
                    + category;
        }
    }

    /**
     * Unlabeled dependency.
     * Negative attachment evidence:
     *   The (head, arg) dep is not picked by a user, and it's not part of a coordination*.
     */
    public static class AttachmentConstraint extends Constraint {
        int headId, argId;
        public AttachmentConstraint(int headId, int argId, boolean isPositive, double confidence) {
            super(isPositive, confidence);
            this.headId = headId;
            this.argId = argId;
            this.hashString = toString();
        }

        public int getHeadId() {
            return headId;
        }

        public int getArgId() {
            return argId;
        }

        public String toString() {
            return (isPositive ? "[+]" : "[-]") + "\t" + strength + "\t" + headId + "\t" + argId;
        }

        public String toString(List<String> sentence) {
            return (isPositive ? "[+]" : "[-]") + "\t" + strength + "\t" + headId + ":" + sentence.get(headId) + "-->"
                    + argId + ":" + sentence.get(argId);
        }
    }

    /**
     * Disjunctive constraints are always positive? probably not.
     */
    public static class DisjunctiveAttachmentConstraint extends Constraint {
        final int headId;
        final ImmutableList<Integer> argIds;
        public DisjunctiveAttachmentConstraint(int headId, ImmutableList<Integer> argIds, boolean isPositive,
                                               double confidence) {
            super(isPositive, confidence);
            this.headId = headId;
            this.argIds = argIds;
            this.hashString = toString();
        }

        public int getHeadId() {
            return headId;
        }

        public ImmutableList<Integer> getArgIds() {
            return argIds;
        }

        public String toString() {
            return (isPositive ? "[+]" : "[-]") + "\t" + strength + "\t" + headId + "\t" + argIds;
        }

        public String toString(List<String> sentence) {
            return (isPositive ? "[+]" : "[-]") + "\t" + strength + "\t" + headId + ":" + sentence.get(headId) + "-->"
                    + argIds + ":" + argIds.stream().map(sentence::get).collect(Collectors.joining(","));
        }
    }
}