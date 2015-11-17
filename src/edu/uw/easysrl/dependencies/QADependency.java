package edu.uw.easysrl.dependencies;

import edu.uw.easysrl.corpora.qa.QASlots;
import edu.uw.easysrl.corpora.qa.QuestionEncoder;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;

import com.google.common.collect.ImmutableSortedSet;
import edu.uw.easysrl.syntax.grammar.Preposition;

import java.io.Serializable;
import java.util.*;

/**
 * Created by luheng on 10/5/15.
 */
public class QADependency implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String predicate;
    private final int predicateIndex;
    private final String[] question;
    private final String preposition; // what's this ???
    private final Integer firstAnswerPosition;
    private final Integer lastAnswerPosition;
    private final Set<Integer> answerIndices;
    private final List<List<Integer>> constituents;
    private final int closestConstituent;
    private final SRLLabel label;

    public static boolean directedMatch;

    public QADependency(final String predicate, final int predicateIndex,
                        final String[] question, final List<Integer> answerIndices) {
        super();
        this.predicate = predicate;
        this.predicateIndex = predicateIndex;
        this.question = question;
        this.answerIndices = ImmutableSortedSet.copyOf(answerIndices);
        firstAnswerPosition = answerIndices.size() == 0 ? null : answerIndices.get(0);
        lastAnswerPosition = answerIndices.size() == 0 ? null : answerIndices.get(answerIndices.size() - 1);

        // Extract continuous spans from aligned answers.
        constituents = new ArrayList<>();
        int lastIndex = Integer.MIN_VALUE, closestConstituent = -1, minDistanceToPredicate = Integer.MAX_VALUE;
        for (int index : answerIndices) {
            if (lastIndex + 1 < index) {
                constituents.add(new ArrayList<>());
                if (minDistanceToPredicate > Math.abs(predicateIndex - index)) {
                    minDistanceToPredicate = Math.abs(predicateIndex - index);
                    closestConstituent = constituents.size() - 1;
                }
            }
            constituents.get(constituents.size() - 1).add(index);
            lastIndex = index;
        }
        this.closestConstituent = closestConstituent;
        String pp = question[QASlots.PPSlotId];
        this.preposition = pp.equals("_") ? null : pp;
        this.label = makeLabel(question);
    }

    private static SRLLabel makeLabel(String[] question) {
        String label = QuestionEncoder.getHeuristicSrlLabel(question);
        return SRLLabel.make(label, QALabels.isCore(label));
    }

    public String getPredicate() {
        return predicate;
    }

    public String getPreposition() {
        return preposition;
    }

    public int getPredicateIndex() {
        return predicateIndex;
    }

    public Collection<Integer> getAnswerPositions() {
        return answerIndices;
    }

    public List<List<Integer>> getConstituents() { return constituents; }

    public List<Integer> getFirstConstituent() {
        return constituents.get(0);
    }

    public List<Integer> getConstituentClosesToPredicate() {
        return constituents.get(closestConstituent);
    }

    public String[] getQuestion() {
        return question;
    }

    public SRLLabel getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return predicate + ":" + question + "-->" + answerIndices;
    }

    public String toString(List<String> words) {
        StringBuilder result = new StringBuilder();
        result.append(words.get(predicateIndex) + "\t:\t");
        for (String qw : question) {
            result.append(qw + " ");
        }
        result.append("?");
        for (final int i : answerIndices) {
            result.append(" " + words.get(i));
        }
        return result.toString();
    }

    public Integer getLastAnswerPosition() {
        return lastAnswerPosition;
    }

    public Integer getFirstAnswerPosition() {
        return firstAnswerPosition;
    }

    /** Below: various matching functions ... **/

    public boolean labeledMatch(DependencyStructure.ResolvedDependency ccgDep) {
        return label == ccgDep.getSemanticRole() && unlabeledMatch(ccgDep);
    }

    public boolean unlabeledMatch(DependencyStructure.ResolvedDependency ccgDep) {
        return unlabeledMatch(ccgDep.getPredicateIndex(), ccgDep.getArgumentIndex());
    }

    public boolean match(final int otherPredicate, final int otherArgument, final Preposition otherPreposition) {
        return unlabeledMatch(otherPredicate, otherArgument) &&
                Preposition.fromString(preposition) == otherPreposition;
    }

    public boolean forwardUnlabeledMatch(DependencyStructure.ResolvedDependency ccgDep) {
        return predicateIndex == ccgDep.getPredicateIndex() && answerIndices.contains(ccgDep.getArgumentIndex());
    }

    private boolean unlabeledMatch(final int otherPredicate, final int otherArgument) {
        boolean forwardMatch  = (predicateIndex == otherPredicate && answerIndices.contains(otherArgument)),
                reversedMatch = (predicateIndex == otherArgument && answerIndices.contains(otherPredicate));
        if (directedMatch) {
            return (label.isCoreArgument() && forwardMatch) || (!label.isCoreArgument() && reversedMatch);
        } else {
            return forwardMatch || reversedMatch;
        }
    }
}