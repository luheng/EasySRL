package edu.uw.easysrl.corpora.qa;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.QADependency;

import java.io.Serializable;
import java.util.*;

/**
 * Created by luheng on 10/29/15.
 */

public class QASentence implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Collection<QADependency> dependencies = new ArrayList<>();
    private final int sentenceLength;
    private final List<String> words;

    public QASentence(final List<String> words) {
        this.sentenceLength = words.size();
        this.words = words;
    }

    public QASentence(final List<String> words, final Collection<QADependency> qaPairs) {
        this(words);
        add(qaPairs);
    }

    private final Map<Integer, String> indexToFrame = new HashMap<>();
    private final Multimap<Integer, QADependency> indexToDep = HashMultimap.create();

    private void add(final Collection<QADependency> qaPairs) {
        dependencies.addAll(qaPairs);
        for (final QADependency qa : qaPairs) {
            indexToFrame.put(qa.getPredicateIndex(), qa.getPredicate());
            indexToDep.put(qa.getPredicateIndex(), qa);
        }
    }

    public Collection<QADependency> getDependenciesAtPredicateIndex(final int index) {
        return indexToDep.get(index);
    }

    public Collection<QADependency> getDependencies() {
        return dependencies;
    }

    public int getSentenceLength() {
        return sentenceLength;
    }

    public Collection<Integer> getPredicatePositions() {
        return indexToFrame.keySet();
    }

    public List<String> getWords() {
        return words;
    }

    @Override
    public String toString() {
        // FIXME
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            result.append(word);
            result.append(" ");
        }
        for (final QADependency qa : dependencies) {
            result.append("\n");
            result.append(qa.toString(words));
        }
        return result.toString();
    }
}
