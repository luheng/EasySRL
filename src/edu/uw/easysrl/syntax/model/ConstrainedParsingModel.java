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
import edu.uw.easysrl.syntax.parser.Agenda;
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
    private static final boolean kNormalizeConstraints = false;
    private final List<List<Tagger.ScoredCategory>> tagsForWords;
    private Table<Integer, Integer, Double> mustLinks, cannotLinks;
    private Table<Integer, Category, Double> mustSupertags, cannotSupertags;

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

        constraints.stream()
                .filter(Constraint::isPositive)
                .filter(Constraint.AttachmentConstraint.class::isInstance)
                .map(c -> (Constraint.AttachmentConstraint) c)
                .forEach(c -> mustLinks.put(c.getHeadId(), c.getArgId(), c.getStrength()));
        constraints.stream()
                .filter(Constraint::isPositive)
                .filter(Constraint.SupertagConstraint.class::isInstance)
                .map(c -> (Constraint.SupertagConstraint) c)
                .forEach(c -> mustSupertags.put(c.getPredId(), c.getCategory(), c.getStrength()));

        // Positive evidence can override negative evidence.
        constraints.stream()
                .filter(c -> !c.isPositive())
                .filter(Constraint.AttachmentConstraint.class::isInstance)
                .map(c -> (Constraint.AttachmentConstraint) c)
                .filter(c -> !mustLinks.contains(c.getHeadId(), c.getArgId()))
                           //&& !mustLinks.contains(c.getArgId(), c.getHeadId()))
                .forEach(c -> cannotLinks.put(c.getHeadId(), c.getArgId(), c.getStrength()));

        constraints.stream()
                .filter(c -> !c.isPositive())
                .filter(Constraint.SupertagConstraint.class::isInstance)
                 .map(c -> (Constraint.SupertagConstraint) c)
                .filter(c -> !mustSupertags.contains(c.getPredId(), c.getCategory()))
                .forEach(c -> cannotSupertags.put(c.getPredId(), c.getCategory(), c.getStrength()));

        if (kNormalizeConstraints) {
            mustLinks.rowKeySet().stream().forEach(head -> {
                int numArgs = mustLinks.row(head).size();
                double norm = 1.0 * numArgs;
                ImmutableSet<Integer> args = mustLinks.row(head).keySet().stream()
                        .collect(GuavaCollectors.toImmutableSet());
                args.forEach(arg -> {
                    double weight = mustLinks.get(head, arg);
                    mustLinks.put(head, arg, weight / norm);
                });
            });
            cannotLinks.rowKeySet().stream().forEach(head -> {
                int numArgs = cannotLinks.row(head).size();
                double norm = 1.0 * numArgs;
                ImmutableSet<Integer> args = cannotLinks.row(head).keySet().stream()
                        .collect(GuavaCollectors.toImmutableSet());
                args.forEach(arg -> {
                    double weight = cannotLinks.get(head, arg);
                    cannotLinks.put(head, arg, weight / norm);
                });
            });
        }
    }

    @Override
    public void buildAgenda(final Agenda agenda, final List<InputReader.InputWord> words) {
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

        double constraintsPenalty = 0.0;
        final List<UnlabelledDependency> dependencies = node.getResolvedUnlabelledDependencies();

        // Penalize cannot-links.
        constraintsPenalty += dependencies.stream()
                .mapToDouble(dep -> dep.getArguments().stream()
                        // Directed match.
                        .filter(argId -> cannotLinks.contains(dep.getHead(), argId))
                        .mapToDouble(argId -> cannotLinks.get(dep.getHead(), argId))
                        .sum())
                .sum();

        // Penalize missed must-links.
        constraintsPenalty += mustLinks.cellSet().stream()
                .filter(c -> {
                    final int cHead = c.getRowKey(), cArg = c.getColumnKey();
                    return (indexInSpan(cHead, leftChild) && indexInSpan(cArg, rightChild)) ||
                            (indexInSpan(cArg, leftChild) && indexInSpan(cHead, rightChild));
                })
                .filter(c -> {
                    final int cHead = c.getRowKey(), cArg = c.getColumnKey();
                    return !dependencies.stream()
                            // Undirected match.
                            .anyMatch(dep -> (dep.getHead() == cHead && dep.getArguments().contains(cArg)));
                                    //|| (dep.getHead() == cArg && dep.getArguments().contains(cHead))));
                })
                .mapToDouble(Table.Cell::getValue)
                .sum();

        return new AgendaItem(node,
            leftChild.getInsideScore() + rightChild.getInsideScore() - lengthPenalty - constraintsPenalty, /* inside */
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
        return new AgendaItem(result, child.getInsideScore() - 0.1, child.outsideScoreUpperbound, child.startOfSpan,
                child.spanLength, kIncludeDependencies);
    }

    public static class ConstrainedParsingModelFactory extends Model.ModelFactory {
        private final Tagger tagger;
        private final Collection<Category> lexicalCategories;

        public ConstrainedParsingModelFactory(final Tagger tagger, final Collection<Category> lexicalCategories) {
            super();
            this.tagger = tagger;
            this.lexicalCategories = lexicalCategories;
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
            return lexicalCategories;
        }

        @Override
        public boolean isUsingDependencies() {
            return true;
        }
    }
}
