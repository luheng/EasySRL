package edu.uw.easysrl.syntax.model;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.DependencyGenerator;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.util.GuavaCollectors;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/10/16.
 */
public class ConstrainedSupertagFactoredModel extends SupertagFactoredModel {

    private final List<List<Tagger.ScoredCategory>> tagsForWords;
    private final DependencyGenerator dependencyGenerator;
    private final boolean includeDependencies;

    private Table<Integer, Category, Double> supertagConstraints;
    private Table<Integer, Integer, Double> attachmentConstraints;

    public ConstrainedSupertagFactoredModel(final List<List<Tagger.ScoredCategory>> tagsForWords,
                                            final Set<Constraint> constraints,
                                            final DependencyGenerator dependencyGenerator,
                                            final boolean includeDependencies) {
        super(tagsForWords, includeDependencies);
        this.tagsForWords = tagsForWords;
        this.dependencyGenerator = dependencyGenerator;
        this.includeDependencies = includeDependencies;
        supertagConstraints = HashBasedTable.create();
        attachmentConstraints = HashBasedTable.create();
        constraints.stream()
                .filter(constraint -> !constraint.isPositive())
                .forEach(constraint -> {
                    if (Constraint.SupertagConstraint.class.isInstance(constraint)) {
                        Constraint.SupertagConstraint c = (Constraint.SupertagConstraint) constraint;
                        final int predId = c.getPredId();
                        final Category category = c.getCategory();
                        final double strength = supertagConstraints.contains(predId, category) ?
                                Math.max(supertagConstraints.get(predId, category), c.getStrength()) :
                                c.getStrength();
                        supertagConstraints.put(predId, category, strength);
                    } else if (Constraint.AttachmentConstraint.class.isInstance(constraint)) {
                        Constraint.AttachmentConstraint c = (Constraint.AttachmentConstraint) constraint;
                        final int headId = c.getHeadId();
                        final int argId = c.getArgId();
                        // Undirected attachment constraint.
                        final double strength = attachmentConstraints.contains(headId, argId) ?
                                Math.max(attachmentConstraints.get(headId, argId), c.getStrength()) :
                                c.getStrength();
                        attachmentConstraints.put(headId, argId, strength);
                        attachmentConstraints.put(argId, headId, strength);
                    }
                });
        // Normalize attachment evidence.
        attachmentConstraints.rowKeySet().stream().forEach(head -> {
            int numArgs = attachmentConstraints.row(head).size();
            double norm = 1.0 * numArgs;
            ImmutableSet<Integer> args = attachmentConstraints.row(head).keySet().stream()
                    .collect(GuavaCollectors.toImmutableSet());
            args.forEach(arg -> {
                double weight = attachmentConstraints.get(head, arg);
                attachmentConstraints.put(head, arg, weight / norm);
            });
        });
        computeOutsideProbabilities();
    }

    @Override
    public void buildAgenda(final PriorityQueue<AgendaItem> agenda, final List<InputReader.InputWord> words) {
        for (int i = 0; i < words.size(); i++) {
            final InputReader.InputWord word = words.get(i);
            for (final Tagger.ScoredCategory cat : tagsForWords.get(i)) {
                double supertagPenalty = supertagConstraints.contains(i, cat.getCategory()) ?
                        supertagConstraints.get(i, cat.getCategory()) : 0.0;
                agenda.add(
                        new AgendaItem(
                                new SyntaxTreeNode.SyntaxTreeNodeLeaf(word.word, word.pos, word.ner, cat.getCategory(),
                                        i, includeDependencies),
                                cat.getScore() - supertagPenalty, /* inside score */
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
        double evidencePenalty = 0.0;

        for (UnlabelledDependency dep : node.getResolvedUnlabelledDependencies()) {
            int headId = dep.getHead();
            if (attachmentConstraints.containsRow(headId)) {
                for (int argId : dep.getArguments()) {
                    if (attachmentConstraints.contains(headId, argId)) {
                        evidencePenalty += attachmentConstraints.get(headId, argId);
                    }
                }
            }
        }

        // TODO: equals check
        // TODO: keep track of dependencies
        /*
        Set<UnlabelledDependency> unlabelledDeps = new HashSet<>();
        dependencyGenerator.generateDependencies(node, unlabelledDeps);
        for (UnlabelledDependency dep : unlabelledDeps) {
            int headId = dep.getHead();
            if (!attachmentEvidence.containsRow(headId)) {
                continue;
            }
            for (int argId : dep.getArguments()) {
                boolean headInLeft = leftChild.startOfSpan <= headId && headId < leftChild.startOfSpan + leftChild.spanLength;
                boolean argInLeft =  leftChild.startOfSpan <= argId &&  argId <  leftChild.startOfSpan + leftChild.spanLength;
                if (headInLeft ^ argInLeft && attachmentEvidence.contains(headId, argId)) {
                    evidencePenalty += attachmentEvidence.get(headId, argId);
                }
            }
        }*/

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
                    null,
                    includeDependencies);
        }

        public ConstrainedSupertagFactoredModel make(final InputReader.InputToParser input, Set<Constraint> constraintSet,
                                                     DependencyGenerator dependencyGenerator) {
            return new ConstrainedSupertagFactoredModel(
                    input.isAlreadyTagged() ? input.getInputSupertags() : tagger.tag(input.getInputWords()),
                    constraintSet,
                    dependencyGenerator,
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