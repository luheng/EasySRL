package edu.uw.easysrl.dependencies;

import com.google.common.collect.ImmutableSortedSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by luheng on 10/5/15.
 */
public class QADependency {
    private static final long serialVersionUID = 1L;

    private final String predicate;
    private final int predicateIndex;
    private final String[] question;
    private final String preposition; // what's this ???
    private final Integer firstAnswerPosition;
    private final Integer lastAnswerPosition;
    private final Set<Integer> answerIndices;
    private final Set<Integer> firstConstituent;

    public QADependency(final String predicate, final int predicateIndex,
                        final String[] question, final List<Integer> answerIndices) {
        super();
        this.predicate = predicate;
        this.predicateIndex = predicateIndex;
        this.question = question;
        this.answerIndices = ImmutableSortedSet.copyOf(answerIndices);
        firstAnswerPosition = answerIndices.size() == 0 ? null : answerIndices.get(0);
        lastAnswerPosition = answerIndices.size() == 0 ? null : answerIndices.get(answerIndices.size() - 1);

        final List<Integer> firstConstituent = new ArrayList<>();
        for (int i = firstAnswerPosition; i <= lastAnswerPosition && answerIndices.contains(i); i++) {
            firstConstituent.add(i);
        }
        this.firstConstituent = ImmutableSortedSet.copyOf(firstConstituent);
        // FIXME
        this.preposition = "";
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

    public Collection<Integer> getFirstAnswerConstituent() {
        return firstConstituent;
    }

    public String[] getQuestion() {
        return question;
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

}