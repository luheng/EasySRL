package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

import java.util.HashSet;
import java.util.List;
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
            parse.dependencies.stream().filter(dep -> dep.getHead() == argumentId)
                    .forEach(dep2 -> answers.add(dependency.getArgument()));
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
            if (i == 0 && depIsCore && child.getCategory().isFunctionInto(Category.PP) && argumentWords.size() > 1) {
                // Simplify arguments by dropping initial prepositions, as in PropBank.
            } else if (i == argumentWords.size() - 1 && child.getCategory().isPunctuation()) {
                // Drop trailing punctuation.
            } else {
                result += " " + child.getWord();
            }
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
}
