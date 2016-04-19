package edu.uw.easysrl.syntax.model;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.model.Constraint;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.IntStream;

/**
 * A more general constrained parsing model compared to ConstrainedSupertagFactoredModel.
 * This takes into account both "must-have" and "cannot-have" constraints.
 * Created by luheng on 4/18/16.
 */
public class ConstrainedParsingModel extends SupertagFactoredModel {
    private static final boolean kIncludeDependencies = true;
    private final List<List<Tagger.ScoredCategory>> tagsForWords;
    private Table<Integer, Integer, Double> mustLinks, cannotLinks;
    private Table<Integer, Category, Double> mustSupertags, cannotSupertags;
    //private ImmutableSet<Constraint.AttachmentConstraint> positiveConstraints, negativeConstraints;

    public ConstrainedParsingModel(final List<List<Tagger.ScoredCategory>> tagsForWords,
                                   final Set<Constraint> constraints) {
        super(tagsForWords, kIncludeDependencies);
        this.tagsForWords = tagsForWords;
        initializeConstraints(constraints);
        computeOutsideProbabilities();
    }

    private void initializeConstraints(final Set<Constraint> constraints) {
        mustLinks = HashBasedTable.create();
        cannotLinks = HashBasedTable.create();
        mustSupertags = HashBasedTable.create();
        cannotSupertags = HashBasedTable.create();
        /*
        positiveConstraints = constraints.stream()
                .filter(c -> !Constraint.SupertagConstraint.class.isInstance(c) && c.isPositive())
                .map(c -> (Constraint.AttachmentConstraint) c)
                .collect(GuavaCollectors.toImmutableSet());
        negativeConstraints = constraints.stream()
                .filter(c -> !Constraint.SupertagConstraint.class.isInstance(c) && !c.isPositive())
                .map(c -> (Constraint.AttachmentConstraint) c)
                .collect(GuavaCollectors.toImmutableSet());
        */
        constraints.stream()
                .filter(Constraint.AttachmentConstraint.class::isInstance)
                .map(c -> (Constraint.AttachmentConstraint) c)
                .forEach(constraint -> {
                    final int headId = constraint.getHeadId();
                    final int argId = constraint.getArgId();
                    final double penalty = constraint.getStrength();
                    if (constraint.isPositive()) {
                        mustLinks.put(headId, argId, mustLinks.contains(headId, argId) ?
                                Math.max(mustLinks.get(headId, argId), penalty) : penalty);
                    } else {
                        cannotLinks.put(headId, argId, cannotLinks.contains(headId, argId) ?
                                Math.max(cannotLinks.get(headId, argId), penalty) : penalty);
                    }
                });

        constraints.stream()
                .filter(Constraint.SupertagConstraint.class::isInstance)
                .map(c -> (Constraint.SupertagConstraint) c)
                .forEach(constraint -> {
                    final int headId = constraint.getPredId();
                    final Category category = constraint.getCategory();
                    final double penalty = constraint.getStrength();
                    if (constraint.isPositive()) {
                        mustSupertags.put(headId, category, mustLinks.contains(headId, category) ?
                                Math.max(mustLinks.get(headId, category), penalty) : penalty);
                    } else {
                        cannotSupertags.put(headId, category, cannotLinks.contains(headId, category) ?
                                Math.max(cannotLinks.get(headId, category), penalty) : penalty);
                    }
                });
        //TODO: Normalize supertag and attachment evidence.
        /*
        mustLinks.rowKeySet().stream().forEach(head -> {
            int numArgs = mustLinks.row(head).size();
            double norm = 1.0 * numArgs;
            ImmutableSet<Integer> args = mustLinks.row(head).keySet().stream()
                    .collect(GuavaCollectors.toImmutableSet());
            args.forEach(arg -> {
                double weight = mustLinks.get(head, arg);
                mustLinks.put(head, arg, weight / norm);
            });
        }); */
    }

    @Override
    public void buildAgenda(final PriorityQueue<AgendaItem> agenda, final List<InputReader.InputWord> words) {
        for (int i = 0; i < words.size(); i++) {
            final InputReader.InputWord word = words.get(i);
            for (final Tagger.ScoredCategory cat : tagsForWords.get(i)) {
                final Category category = cat.getCategory();
                double supertagPenalty = .0;
                if (mustSupertags.containsRow(i) && !mustSupertags.row(i).containsKey(category)) {
                    // Get arbitrary penalty value.
                    supertagPenalty += mustSupertags.row(i).values().iterator().next();
                }
                if (cannotSupertags.contains(i, cat.getCategory())) {
                    supertagPenalty += cannotSupertags.get(i, category);
                }
                agenda.add(
                        new AgendaItem(
                                new SyntaxTreeNode.SyntaxTreeNodeLeaf(word.word, word.pos, word.ner, category, i,
                                        kIncludeDependencies),
                                cat.getScore() - supertagPenalty, /* inside score */
                                getOutsideUpperBound(i, i + 1),   /* outside score upperbound */
                                i, /* start index */
                                1, /* length */
                                kIncludeDependencies));
            }
        }
    }

    @Override
    public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {
        final int length = leftChild.spanLength + rightChild.spanLength;

        // Attach low heuristic.
        final int depLength = Math.abs(leftChild.getParse().getHeadIndex() - rightChild.getParse().getHeadIndex());
        double lengthPenalty = 0.00001 * depLength;
        if (rightChild.getSpanLength() == 1 && rightChild.getParse().getWord().startsWith("'")) {
            lengthPenalty = lengthPenalty * 10;
        }

        double evidencePenalty = 0.0;
        final List<UnlabelledDependency> dependencies = node.getResolvedUnlabelledDependencies();

        // Penalize cannot-links.
        evidencePenalty += dependencies.stream()
                .mapToDouble(dep -> dep.getArguments().stream()
                        .filter(argId -> cannotLinks.contains(dep.getHead(), argId))
                        .mapToDouble(argId -> cannotLinks.get(dep.getHead(), argId))
                        .sum())
                .sum();

        // Penalize missed must-links.
        evidencePenalty += mustLinks.cellSet().stream()
                .filter(c -> (indexInSpan(c.getRowKey(), leftChild) && indexInSpan(c.getColumnKey(), rightChild)) ||
                             (indexInSpan(c.getColumnKey(), leftChild) && indexInSpan(c.getRowKey(), rightChild)))
                .filter(c -> !dependencies.stream().anyMatch(dep -> dep.getHead() == c.getRowKey() &&
                                                                    dep.getArguments().contains(c.getColumnKey())))
                .mapToDouble(Table.Cell::getValue)
                .sum();

        return new AgendaItem(node,
            leftChild.getInsideScore() + rightChild.getInsideScore() - lengthPenalty - evidencePenalty, /* inside */
            getOutsideUpperBound(leftChild.startOfSpan, leftChild.startOfSpan + length),                /* outside */
            leftChild.startOfSpan,
            length,
            kIncludeDependencies);
    }

    private static boolean indexInSpan(final int id, final AgendaItem span) {
        return span.getStartOfSpan() <= id && id < span.getStartOfSpan() + span.getSpanLength();
    }

    @Override
    public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode result, final AbstractParser.UnaryRule rule) {
        return new AgendaItem(result,
                child.getInsideScore(),
                child.outsideScoreUpperbound,
                child.startOfSpan,
                child.spanLength,
                kIncludeDependencies);
    }

    public static class ConstrainedParsingModelFactory extends Model.ModelFactory {
        private final Tagger tagger;

        public ConstrainedParsingModelFactory(final Tagger tagger) {
            super();
            this.tagger = tagger;
        }

        @Override
        public ConstrainedParsingModel make(final InputReader.InputToParser input) {
            return new ConstrainedParsingModel(
                    input.isAlreadyTagged() ? input.getInputSupertags() : tagger.tag(input.getInputWords()),
                    new HashSet<>());
        }

        public ConstrainedParsingModel make(final InputReader.InputToParser input, Set<Constraint> constraintSet) {
            return new ConstrainedParsingModel(
                    input.isAlreadyTagged() ? input.getInputSupertags() : tagger.tag(input.getInputWords()),
                    constraintSet);
        }

        @Override
        public Collection<Category> getLexicalCategories() {
            return tagger.getLexicalCategories();
        }

        @Override
        public boolean isUsingDependencies() {
            return true;
        }
    }
}
