package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Print answer spans ..
 * Created by luheng on 1/20/16.
 */
public class AnswerGenerator {

    public static Set<Integer> getArgumentIdsForDependency(final List<String> words, Parse parse,
                                                           ResolvedDependency dependency) {
        Set<Integer> answers = new HashSet<>();
        Category answerCategory = dependency.getCategory().getArgument(dependency.getArgNumber());
        int argumentId = dependency.getArgument();
        if (answerCategory.equals(Category.PP) || words.get(argumentId).equals("to")) {
            parse.dependencies.stream()
                    .filter(d -> d.getHead() == argumentId)
                    .forEach(d2 -> answers.add(d2.getArgument()));
            if (answers.size() == 0) {
                answers.add(argumentId);
            }
        } else {
            answers.add(argumentId);
        }
        return answers;
    }

    public static Set<Integer> getArgumentIds(final List<String> words, final Parse parse, int predicateIndex,
                                              Category category, int argumentNumber) {
        Set<Integer> argumentIds = new HashSet<>();
        parse.dependencies.forEach(dependency -> {
            if (dependency.getHead() == predicateIndex && dependency.getCategory() == category &&
                    dependency.getArgNumber() == argumentNumber) {
                argumentIds.addAll(getArgumentIdsForDependency(words, parse, dependency));
            }
        });
        return argumentIds;
    }

    public static Set<Integer> getArgumentIds(final List<String> words, Parse parse, ResolvedDependency dependency) {
        Set<Integer> answers = new HashSet<>();
        Category answerCategory = dependency.getCategory().getArgument(dependency.getArgNumber());
        int argumentId = dependency.getArgument();
        if (answerCategory.equals(Category.PP) || words.get(argumentId).equals("to")) {
            parse.dependencies.stream().filter(d -> d.getHead() == argumentId)
                    .forEach(d2 -> answers.add(d2.getArgument()));
            if (answers.size() == 0) {
                answers.add(argumentId);
            }
        } else {
            answers.add(argumentId);
        }
        return answers;
    }

    public static Map<ImmutableList<Integer>, String> generateAnswerSpans(int predicateIndex,
            Map<ImmutableList<Integer>, Set<Integer>> answerToParses, List<String> words, List<Parse> parses) {
        Map<ImmutableList<Integer>, String> answerToSpans = new HashMap<>();
        Set<Integer> allIndices = new HashSet<>();
        answerToParses.keySet().forEach(allIndices::addAll);
        allIndices.add(predicateIndex);
        for (ImmutableList<Integer> argumentIds : answerToParses.keySet()) {
            Set<Integer> parseIds = answerToParses.get(argumentIds);
            // use the highest ranked parse to get answer span.
            int bestParseId = parseIds.stream().min(Integer::compare).get();
            SyntaxTreeNode root = parses.get(bestParseId).syntaxTree;
            if (argumentIds.size() == 1) {
                Set<Integer> excludeIndices = new HashSet<>(allIndices);
                excludeIndices.remove(argumentIds.get(0));
                String span = getArgumentConstituent(words, root, argumentIds.get(0), excludeIndices);
                answerToSpans.put(argumentIds, span);
            } else {
                List<String> spans = new ArrayList<>();
                for (int argId : argumentIds) {
                    Set<Integer> excludeIndices = new HashSet<>(allIndices);
                    excludeIndices.remove(argId);
                    spans.add(getArgumentConstituent(words, root, argId, excludeIndices));
                }
                answerToSpans.put(argumentIds, spans.stream().collect(Collectors.joining(", ")));
            }
        }
        return answerToSpans;
    }

    public static String getArgumentConstituent(final List<String> words, final SyntaxTreeNode node,
                                                final int argumentHead, final Set<Integer> excludeIndices) {
        boolean depIsCore = true;
        SyntaxTreeNode constituent = getArgumentConstituent(node, argumentHead, excludeIndices);
        final List<SyntaxTreeNodeLeaf> argumentWords = constituent.getLeaves();
        String result = "";
        int i = 0;
        for (final SyntaxTreeNodeLeaf child : argumentWords) {
            boolean trim = (i == 0 || i == argumentWords.size() - 1);
            boolean trimPP = (i == 0 && depIsCore && child.getCategory().isFunctionInto(Category.PP) && argumentWords.size() > 1);
            boolean trimPos = (trim && child.getCategory().isPunctuation());
            boolean trimOther = (trim && child.getWord().equals("\'s"));
            if (trimPP || trimPos || trimOther) {
                continue;
            }
            result += " " + translateBrackets(child.getWord());
            i++;
        }
        return result.trim();
    }

    public static SyntaxTreeNode getArgumentConstituent(final SyntaxTreeNode node, final int head,
                                                        final Set<Integer> excludeIndices) {
        final boolean exclude = excludeIndices.stream()
                .anyMatch(id -> id >= node.getStartIndex() && id < node.getEndIndex());
        if (!exclude && node.getDependencyStructure().getArbitraryHead() == head) {
            return node;
        }
        for (final SyntaxTreeNode child : node.getChildren()) {
            final SyntaxTreeNode result = getArgumentConstituent(child, head, excludeIndices);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static String translateBrackets(String word) {
        if (word.equalsIgnoreCase("-LRB-")) {
            word = "(";
        } else if (word.equalsIgnoreCase("-RRB-")) {
            word = ")";
        } else if (word.equalsIgnoreCase("-LCB-")) {
            word = "{";
        } else if (word.equalsIgnoreCase("-RCB-")) {
            word = "}";
        } else if (word.equalsIgnoreCase("-LSB-")) {
            word = "[";
        } else if (word.equalsIgnoreCase("-RSB-")) {
            word = "]";
        }
        return word;
    }

    /**
     * Tries to get the parent of a node in a tree.
     * Assumes the given node is in the given tree. If not, it will probably return empty, maybe... but maybe not.
     * Returns empty if the node is just the whole tree.
     */
    public static Optional<SyntaxTreeNode> getParent(SyntaxTreeNode node, SyntaxTreeNode wholeTree) {
        int nodeStart = node.getStartIndex();
        int nodeEnd = node.getEndIndex();
        Optional<SyntaxTreeNode> curCandidate = Optional.of(wholeTree);
        Optional<SyntaxTreeNode> lastCandidate = Optional.empty();
        while(curCandidate.isPresent() && curCandidate.get() != node) {
            lastCandidate = curCandidate;
            curCandidate = curCandidate.get().getChildren().stream().filter(child -> {
                int childStart = child.getStartIndex();
                int childEnd = child.getEndIndex();
                return (childStart <= nodeStart) && (childEnd >= nodeEnd);
            }).findFirst();
        }
        return lastCandidate;
    }

    public static Optional<SyntaxTreeNode> getLowestAncestorInstantiatingCategory(SyntaxTreeNode node,
                                                                                  Category category,
                                                                                  SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        Optional<Category> curCat = curNode.map(n -> n.getCategory());
        // it's important here that we do category.matches(...) because we're looking for the first thing
        // that *is an instance* of the (more general) parameter category, i.e.,
        // it might have features not specified in the parameter.
        while(curNode.isPresent() && !category.matches(curCat.get())) {
            curNode = AnswerGenerator.getParent(curNode.get(), wholeTree);
            curCat = curNode.map(n -> n.getCategory());
        }
        return curNode;
    }

    public static Optional<SyntaxTreeNode> getLowestAncestorFunctionIntoCategory(SyntaxTreeNode node,
                                                                                 Category category,
                                                                                 SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        Optional<Category> curCat = curNode.map(n -> n.getCategory());
        while(curNode.isPresent() && !curCat.get().isFunctionInto(category)) {
            curNode = AnswerGenerator.getParent(curNode.get(), wholeTree);
            curCat = curNode.map(SyntaxTreeNode::getCategory);
        }
        return curNode;
    }
}
