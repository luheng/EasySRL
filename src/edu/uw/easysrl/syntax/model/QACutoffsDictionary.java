package edu.uw.easysrl.syntax.model;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.training.*;
import edu.uw.easysrl.util.Util;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 11/6/15.
 */
// FIXME
public class QACutoffsDictionary extends CutoffsDictionary {
    public QACutoffsDictionary(final Collection<Category> lexicalCategories,
                               final Map<String, Collection<Category>> tagDict,
                               final int maxDependencyLength,
                               TrainingDataParameters dataParameters) {
        super(lexicalCategories, tagDict, maxDependencyLength);
        try {
            makeAfterContruction(new CCGHelper(dataParameters, true /* backoff */));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void make() throws IOException {
        // do nothing
    }

    // Really hakcy ...
    private void makeAfterContruction(CCGHelper ccgHelper) throws IOException {
        final Map<String, Multiset<SRLFrame.SRLLabel>> keyToRole = new HashMap<>();
        for (final SRLFrame.SRLLabel label : SRLFrame.getAllSrlLabels()) {
            srlToOffset.put(label, HashMultiset.create());
        }
        final Iterator<QASentence> sentences = QACorpusReader.READER.readCorpus(false /* is not dev */);
        while (sentences.hasNext()) {
            final QASentence sentence = sentences.next();
            final List<InputReader.InputWord> words = sentence.getInputWords();
            final CompressedChart smallChart = ccgHelper.parseSentence(sentence.getWords());
            if (smallChart == null) {
                continue;
            }
            final List<Set<Category>> allCategories = ccgHelper.getAllCategories(sentence, smallChart);
            for (int wordIndex = 0; wordIndex < sentence.getSentenceLength(); wordIndex++) {
                for (Category category : allCategories.get(wordIndex)) {
                    for (ResolvedDependency ccgDep : smallChart.getAllDependencies()) {
                        if (ccgDep.getPredicateIndex() != wordIndex ||
                                ccgDep.getArgNumber() >= category.getNumberOfArguments()) {
                            // If CCG dependency corresponds to the category.
                            continue;
                        }
                        QADependency qaDep = null;
                        for (QADependency qa : sentence.getDependencies()) {
                            if (qa.unlabeledMatch(ccgDep)) {
                                // If CCG dependency agrees with the Q/A pair.
                                qaDep = qa;
                                break;
                            }
                        }
                        if (qaDep == null) {
                            continue;
                        }
                        final int offset = ccgDep.getArgumentIndex() - ccgDep.getPredicateIndex();
                        for (int i = Math.min(offset, 0); i <= Math.max(offset, 0); i++) {
                            if (i != 0 && Math.abs(offset) <= maxDependencyLength) {
                                // For a word at -5, also at -4,-3,-2,-1
                                Util.add(srlToOffset, qaDep.getLabel(), i);
                            }
                        }
                        Util.add(categoryToArgumentToSRLs, category, ccgDep.getArgNumber(), qaDep.getLabel());
                        final Preposition preposition = Preposition.fromString(qaDep.getPreposition());
                        final String key = makeKey(words.get(wordIndex).word, category, preposition, ccgDep.getArgNumber());
                        Multiset<SRLFrame.SRLLabel> roles = keyToRole.get(key);
                        if (roles == null) {
                            roles = HashMultiset.create();
                            roles.add(SRLFrame.NONE);
                            keyToRole.put(key, roles);
                        }
                        roles.add(qaDep.getLabel());
                    }
                }
            }
        }
        for (final Map.Entry<String, Multiset<SRLFrame.SRLLabel>> entry : keyToRole.entrySet()) {
            this.keyToRole.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }
}
