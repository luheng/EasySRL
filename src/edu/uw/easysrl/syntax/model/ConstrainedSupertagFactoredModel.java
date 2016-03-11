package edu.uw.easysrl.syntax.model;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.pomdp.Evidence;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.tagger.Tagger;


import java.util.*;

/**
 * Created by luheng on 3/10/16.
 */
public class ConstrainedSupertagFactoredModel extends SupertagFactoredModel {

    private final List<List<Tagger.ScoredCategory>> tagsForWords;
    private final boolean includeDependencies;

    private Table<Integer, Category, Double> supertagEvidence;
    private Table<Integer, Integer, Double> attachmentEvidence;

    public ConstrainedSupertagFactoredModel(final List<List<Tagger.ScoredCategory>> tagsForWords,
                                            final Set<Evidence> evidenceSet,
                                            final boolean includeDependencies) {
        super(tagsForWords, includeDependencies);
        this.includeDependencies = includeDependencies;
        this.tagsForWords = tagsForWords;
        supertagEvidence = HashBasedTable.create();
        attachmentEvidence = HashBasedTable.create();
        evidenceSet.stream()
                .filter(ev -> !ev.isPositive())
                .forEach(ev -> {
                    if (Evidence.SupertagEvidence.class.isInstance(ev)) {
                        Evidence.SupertagEvidence ev1 = (Evidence.SupertagEvidence) ev;
                        supertagEvidence.put(ev1.getPredId(), ev1.getCategory(), ev1.getConfidence());
                    } else if (Evidence.AttachmentEvidence.class.isInstance(ev)) {
                        Evidence.AttachmentEvidence ev1 = (Evidence.AttachmentEvidence) ev;
                        attachmentEvidence.put(ev1.getHeadId(), ev1.getArgId(), ev1.getConfidence());
                    }
                });
        computeOutsideProbabilities();
    }

    @Override
    public void buildAgenda(final PriorityQueue<AgendaItem> agenda, final List<InputReader.InputWord> words) {
        for (int i = 0; i < words.size(); i++) {
            final InputReader.InputWord word = words.get(i);
            for (final Tagger.ScoredCategory cat : tagsForWords.get(i)) {
                double evidencePenalty = supertagEvidence.contains(i, cat.getCategory()) ?
                        supertagEvidence.get(i, cat.getCategory()) : 0.0;
                agenda.add(
                        new AgendaItem(
                                new SyntaxTreeNode.SyntaxTreeNodeLeaf(word.word, word.pos, word.ner, cat.getCategory(),
                                                                      i, includeDependencies),
                                cat.getScore() - evidencePenalty, /* inside score */
                                getOutsideUpperBound(i, i + 1),   /* outside score upperbound */
                                i, /* start index */
                                1, /* length */
                                includeDependencies));
            }
        }
    }

    @Override
    public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {
        final int length = leftChild.spanLength + rightChild.spanLength;

        // Add a penalty based on length of distance between the heads of the two children.
        // This implements the 'attach low' heuristic.
        final int depLength = Math.abs(leftChild.getParse().getHeadIndex() - rightChild.getParse().getHeadIndex());
        double lengthPenalty = 0.00001 * depLength;

        // Extra penalty for clitics, to really make sure they attach locally.
        if (rightChild.getSpanLength() == 1 && rightChild.getParse().getWord().startsWith("'")) {
            lengthPenalty = lengthPenalty * 10;
        }

        double evidencePenalty = .0;
        int h1 = leftChild.getParse().getHeadIndex(), h2 = rightChild.getParse().getHeadIndex();
        if (attachmentEvidence.contains(h1, h2)) {
            evidencePenalty = attachmentEvidence.get(h1, h2);
        } else if (attachmentEvidence.contains(h2, h1)) {
            evidencePenalty = attachmentEvidence.get(h2, h1);
        }

        return new AgendaItem(node,
                leftChild.getInsideScore() + rightChild.getInsideScore() - lengthPenalty - evidencePenalty, /* inside */
                getOutsideUpperBound(leftChild.startOfSpan, leftChild.startOfSpan + length),                /* outside */
                leftChild.startOfSpan,
                length,
                includeDependencies);
    }

    @Override
    public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode result, final AbstractParser.UnaryRule rule) {
        return new AgendaItem(result,
                              child.getInsideScore(),
                              child.outsideScoreUpperbound,
                              child.startOfSpan,
                              child.spanLength,
                              includeDependencies);
    }

    public static class ConstrainedSupertagModelFactory extends ModelFactory {
        private final Tagger tagger;
        private final boolean includeDependencies;

        public ConstrainedSupertagModelFactory(final Tagger tagger, final boolean includeDependencies) {
            super();
            this.tagger = tagger;
            this.includeDependencies = includeDependencies;
        }

        @Override
        public ConstrainedSupertagFactoredModel make(final InputReader.InputToParser input) {
            return new ConstrainedSupertagFactoredModel(
                    input.isAlreadyTagged() ? input.getInputSupertags() : tagger.tag(input.getInputWords()),
                    new HashSet<>(),
                    includeDependencies);
        }

        public ConstrainedSupertagFactoredModel make(final InputReader.InputToParser input, Set<Evidence> evidenceSet) {
            return new ConstrainedSupertagFactoredModel(
                    input.isAlreadyTagged() ? input.getInputSupertags() : tagger.tag(input.getInputWords()),
                    evidenceSet,
                    includeDependencies);
        }

        @Override
        public Collection<Category> getLexicalCategories() {
            return tagger.getLexicalCategories();
        }

        @Override
        public boolean isUsingDependencies() {
            return includeDependencies;
        }
    }
}

