package edu.uw.easysrl.syntax.training;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;


import java.util.*;

/**
 * Created by luheng on 11/2/15.
 * Given a complete chart for a sentence, and a set of gold-standard QA dependencies,
 * this finds a sub-chart of that maximizes agreement with the QA
 * dependencies
 */

public class QAGoldChartFinder {
    private final CompressedChart completeChart;
    public static boolean verbose = true;

    public QAGoldChartFinder(final CompressedChart completeChart) {
        this.completeChart = completeChart;
    }

    private static class Scored<T> {
        private final T object;
        private final int matchedDependencies;
        public Scored(final T object, final int matchedDependencies) {
            this.object = object;
            this.matchedDependencies = matchedDependencies;
        }
    }

    CompressedChart goldChart(final QASentence sentence, final CutoffsDictionary cutoffs) {
        return goldChart(sentence, getTrainingDependencies(sentence), cutoffs);
    }

    public static Set<QADependency> getTrainingDependencies(final QASentence sentence) {
        final Set<QADependency> goldDeps = new HashSet<>();
        for (final QADependency qa : sentence.getDependencies()) {
            if (qa.getAnswerPositions().size() > 0) {
                // FIXME: get closest answer constituent to the verb?
                /*
                final List<Integer> firstConstituent = new ArrayList<>();
                for (int i = qa.getFirstAnswerPosition(); i <= qa.getLastAnswerPosition() &&
                        qa.getAnswerPositions().contains(i); i++) {
                    firstConstituent.add(i);
                }
                */
                final QADependency newDep = new QADependency(qa.getPredicate(), qa.getPredicateIndex(),
                                                             qa.getQuestion(), qa.getConstituentClosesToPredicate());
                                                             //qa.getFirstConstituent());
                goldDeps.add(newDep);
            }
        }
        return goldDeps;
    }

    private CompressedChart goldChart(final QASentence sentence, final Set<QADependency> goldDeps,
                                      final CutoffsDictionary cutoffs) {
        final Table<CompressedChart.Key, Set<QADependency>, Scored<CompressedChart.Key>> cache = HashBasedTable.create();
        int bestScore = -1;
        Collection<CompressedChart.Key> bestKeys = null;
        for (final CompressedChart.Key root : completeChart.getRoots()) {
            // Choose the value that maximizes the number of dependencies in GoldDeps
            final Scored<CompressedChart.Key> scoredKey = bestKey(root, goldDeps, cache, cutoffs);
            if (scoredKey.matchedDependencies > bestScore) {
                bestKeys = new HashSet<>();
                bestKeys.add(scoredKey.object);
                bestScore = scoredKey.matchedDependencies;
            } else if (scoredKey.matchedDependencies == bestScore) {
                bestKeys.add(scoredKey.object);
            }
        }
        if (bestScore > goldDeps.size()) {
            throw new IllegalStateException();
        }
        if (verbose) {
            synchronized (this) {
                System.out.println("\n" + StringUtils.join(sentence.getWords(), " "));
                System.out.println(bestScore + "/" + goldDeps.size());
            }
        }
        if (bestScore == 0) {
            return null;
        }
        return new CompressedChart(completeChart.getWords(), bestKeys);
    }

    private Scored<CompressedChart.Key> bestKey(
                final CompressedChart.Key key,
                final Set<QADependency> goldDeps,
                final Table<CompressedChart.Key, Set<QADependency>, Scored<CompressedChart.Key>> cache,
                final CutoffsDictionary cutoffs) {
        Scored<CompressedChart.Key> result = cache.get(key, goldDeps);
        if (result == null) {
            // Filter the dependencies to only be ones occurring in this part of the chart.
            final Set<QADependency> possible = getPossibleDeps(key, goldDeps);
            Set<CompressedChart.Value> bestValues = null;
            int bestScore = -1;
            for (final CompressedChart.Value value : key.values) {
                // Choose the value that maximizes the number of dependencies in GoldDeps
                final Scored<CompressedChart.Value> scoredValue = bestValue(value, possible, cache, cutoffs);
                if (scoredValue.matchedDependencies > bestScore) {
                    bestValues = new HashSet<>();
                    bestValues.add(scoredValue.object);
                    bestScore = scoredValue.matchedDependencies;
                } else if (scoredValue.matchedDependencies == bestScore) {
                    bestValues.add(scoredValue.object);
                }
            }
            result = new Scored<>(new CompressedChart.Key(
                        key.category, key.startIndex, key.lastIndex, key.ruleClass, bestValues), bestScore);
            cache.put(key, goldDeps, result);
        }
        return result;
    }

    /**
     * Finds the set of dependencies that are potentially matched at this node
     */
    private Set<QADependency> getPossibleDeps(final CompressedChart.Key key, final Set<QADependency> goldDeps) {
        final Set<QADependency> possible = new HashSet<>(goldDeps.size());
        final int startIndex = key.getStartIndex();
        final int endIndex = key.getLastIndex() + 1;
        for (final QADependency qa : goldDeps) {
            if (qa.getPredicateIndex() >= startIndex && qa.getPredicateIndex() < endIndex) {
                for (final int arg : qa.getAnswerPositions()) {
                    if (arg >= startIndex && arg < endIndex) {
                        possible.add(qa);
                        break;
                    }
                }
            }
        }
        return possible;
    }

    private Scored<CompressedChart.Value> bestValue(
            final CompressedChart.Value value,
            final Set<QADependency> goldDeps,
            final Table<CompressedChart.Key, Set<QADependency>, Scored<CompressedChart.Key>> cache,
            final CutoffsDictionary cutoffs) {
        final List<CompressedChart.Key> children = value.getChildren();
        if (children.size() == 0) {
            return new Scored<>(value, 0);
        }
        final Collection<DependencyStructure.ResolvedDependency> dependencies = value.getDependencies();
        final Set<QADependency> missingDeps;
        int score;
        final Set<DependencyStructure.ResolvedDependency> labelledDeps;
        if (dependencies.size() > 0) {
            labelledDeps = new HashSet<>(dependencies.size());
            final Set<QADependency> matchedDeps = getMatchedDeps(goldDeps, dependencies, cutoffs, labelledDeps);
            score = matchedDeps.size();
            if (matchedDeps.size() > 0) {
                missingDeps = Sets.difference(goldDeps, matchedDeps);
            } else {
                missingDeps = goldDeps;
            }
        } else {
            score = 0;
            missingDeps = goldDeps;
            labelledDeps = Collections.emptySet();
        }
        final List<CompressedChart.Key> newChildren = new ArrayList<>(children.size());
        for (final CompressedChart.Key child : children) {
            final Scored<CompressedChart.Key> key = bestKey(child, missingDeps, cache, cutoffs);
            score += key.matchedDependencies;
            newChildren.add(key.object);
        }
        final CompressedChart.Value object;
        if (newChildren.size() == 1) {
            object = new CompressedChart.TreeValueUnary(newChildren.get(0), value.getRuleID(), labelledDeps);
        } else if (newChildren.size() == 2) {
            object = new CompressedChart.TreeValueBinary(newChildren.get(0), newChildren.get(1), labelledDeps);
        } else {
            throw new IllegalStateException("Shouldn't be here");
        }
        // Avoid potential double-counting
        return new Scored<>(object, Math.min(score, goldDeps.size()));
    }

    /**
     * Finds the set of SRL dependencies that match dependencies resolved at this node
     */
    private Set<QADependency> getMatchedDeps(final Set<QADependency> goldDeps,
                                             final Collection<DependencyStructure.ResolvedDependency> dependencies,
                                             final CutoffsDictionary cutoffs,
                                             final Collection<DependencyStructure.ResolvedDependency> newDeps) {
        final Set<QADependency> matchedDeps = new HashSet<>(dependencies.size());
        for (final DependencyStructure.ResolvedDependency dep : dependencies) {
            final int predicateIndex = dep.getPredicateIndex();
            final int argumentIndex = predicateIndex + dep.getOffset();
            boolean isSRL = false;
            for (final QADependency qa : goldDeps) {
                if (!matchedDeps.contains(qa)
                        && cutoffs.isFrequent(dep.getCategory(), dep.getArgNumber(), qa.getLabel())
                        && cutoffs.getRoles(completeChart.getWords().get(dep.getPredicateIndex()).word,
                                            dep.getCategory(), dep.getPreposition(),
                                            dep.getArgNumber()).contains(qa.getLabel())
                        && qa.match(predicateIndex, argumentIndex, dep.getPreposition())) {
                    matchedDeps.add(qa);
                    newDeps.add(dep.overwriteLabel(qa.getLabel()));
                    isSRL = true;
                    break;
                }
            }
            if (!isSRL) {
                newDeps.add(dep.overwriteLabel(SRLFrame.NONE));
            }

        }
        return matchedDeps;
    }
}