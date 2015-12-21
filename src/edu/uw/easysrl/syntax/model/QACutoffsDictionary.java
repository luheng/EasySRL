package edu.uw.easysrl.syntax.model;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.qa.QACorpusReader;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.training.*;
import edu.uw.easysrl.util.Util;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
            makeAfterConstruction(new CCGHelper(dataParameters, true /* backoff */));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void make(List<ParallelCorpusReader.Sentence> sentences) throws IOException {
        // do nothing
    }

    // Really hakcy ...
    private void makeAfterConstruction(CCGHelper ccgHelper) throws IOException {
        final Map<String, Multiset<SRLFrame.SRLLabel>> keyToRole = new HashMap<>();
        for (final SRLFrame.SRLLabel label : SRLFrame.getAllSrlLabels()) {
            srlToOffset.put(label, HashMultiset.create());
        }
        final Iterator<QASentence> sentences = QACorpusReader.getReader(QATraining.trainingDomain).readTrainingCorpus();
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
                        if (ccgDep.getHead() != wordIndex ||
                                ccgDep.getArgNumber() > category.getNumberOfArguments()) {
                            // If CCG dependency corresponds to the category.
                            continue;
                        }
                        Collection<QADependency> matchedQADependencies = sentence.getDependencies().stream()
                                .filter(qa -> qa.unlabeledMatch(ccgDep))
                                .collect(Collectors.toSet());
                        if (matchedQADependencies == null || matchedQADependencies.size() == 0) {
                            continue;
                        }
                        for (QADependency qaDep : matchedQADependencies) {
                            final int offset = ccgDep.getArgumentIndex() - ccgDep.getHead();
                            for (int i = Math.min(offset, 0); i <= Math.max(offset, 0); i++) {
                                if (i != 0 && Math.abs(offset) <= maxDependencyLength) {
                                    // For a word at -5, also at -4,-3,-2,-1
                                    Util.add(srlToOffset, qaDep.getLabel(), i);
                                }
                            }
                            Util.add(categoryToArgumentToSRLs, category, ccgDep.getArgNumber(), qaDep.getLabel());
                            final Preposition preposition = Preposition.fromString(qaDep.getPreposition());
                            final String key = makeKey(words.get(wordIndex).word, category, preposition,
                                    ccgDep.getArgNumber());
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
        }
        for (final Map.Entry<String, Multiset<SRLFrame.SRLLabel>> entry : keyToRole.entrySet()) {
            this.keyToRole.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }
}
