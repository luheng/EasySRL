package edu.uw.easysrl.corpora.qa;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

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
    public String sentenceId;

    private List<InputWord> inputWords;

    private final Map<Integer, String> indexToFrame = new HashMap<>();
    private final Multimap<Integer, QADependency> indexToDep = HashMultimap.create();

    private static final AnswerAligner defaultAligner = new AnswerAligner();

    public QASentence(final String[] words) {
        this.words = new ArrayList<>();
        for (String w : words) {
            this.words.add(w);
        }
        this.sentenceLength = this.words.size();
    }

    public QASentence(final List<String> words) {
        this.sentenceLength = words.size();
        this.words = words;
    }

    public QASentence(final List<String> words, final Collection<QADependency> qaPairs) {
        this(words);
        add(qaPairs);
    }

    public void addDependencyFromLine(int predIdx, String line) {
        String[] info = line.split("\\?");
        String[] question = info[0].split("\\s+");
        List<String[]> answers = new ArrayList<>();
        for (String answerStr : info[1].split("###")) {
            answers.add(answerStr.trim().split("\\s+"));
        }
        List<Integer> answerIndices = defaultAligner.align(answers, words);
        add(new QADependency(words.get(predIdx), predIdx, question, answerIndices));
    }

    private void add(QADependency qaPair) {
        dependencies.add(qaPair);
        indexToFrame.put(qaPair.getPredicateIndex(), qaPair.getPredicate());
        indexToDep.put(qaPair.getPredicateIndex(), qaPair);
    }

    private void add(final Collection<QADependency> qaPairs) {
        qaPairs.forEach(qa -> add(qa));
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


    public List<InputReader.InputWord> getInputWords() {
        if (inputWords == null) {
            inputWords = new ArrayList<>(words.size());
            for (String word : words) {
                inputWords.add(new InputWord(word, "", ""));
            }
        }
        return inputWords;
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
