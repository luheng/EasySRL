package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Print answer spans ..
 * Created by luheng on 1/20/16.
 */
public class AnswerGenerator {

    public static Set<Integer> getArgumentIds(final List<String> words, Parse parse, ResolvedDependency dependency) {
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

    public static Optional<SyntaxTreeNode> getLowestAncestorWithCategory(SyntaxTreeNode node, Category category, SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        Optional<Category> curCat = curNode.map(n -> n.getCategory());
        while(curNode.isPresent() && !curCat.get().matches(category)) {
            curNode = AnswerGenerator.getParent(curNode.get(), wholeTree);
            curCat = curNode.map(n -> n.getCategory());
        }
        return curNode;
    }
}
