package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.*;

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

    /**
     *
     * @param parse
     * @param words
     * @param predId
     * @param predCategory
     * @param argId
     * @param argCategory
     * @return
     */
    public static String getAnswerSpan(Parse parse, List<String> words, int predId, Category predCategory,
                                       int argId, Category argCategory) {
        List<String> phrase = getRepresentativePhrase(argId, argCategory, parse);
        return phrase.stream().collect(Collectors.joining(" "));
    }

    public static String getAnswerSpan(Parse parse, List<String> words, int predId, Category predCategory,
                                       Collection<Integer> argIds, Category argCategory) {
        return argIds.stream()
                .map(id -> getAnswerSpan(parse, words, predId, predCategory, id, argCategory))
                .collect(Collectors.joining(", "));
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

    public static Optional<SyntaxTreeNode> getLowestAncestorFunctionIntoCategory(SyntaxTreeNode node, Category category,
                                                                                 SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        Optional<Category> curCat = curNode.map(n -> n.getCategory());
        while(curNode.isPresent() && !curCat.get().isFunctionInto(category)) {
            curNode = AnswerGenerator.getParent(curNode.get(), wholeTree);
            curCat = curNode.map(SyntaxTreeNode::getCategory);
        }
        return curNode;
    }

    // Could be improved for PPs and such if necessary.
    public static List<String> getRepresentativePhraseForUnrealized(Category category) {
        List<String> result = new ArrayList<>();
        result.add("something");
        return result;
    }

    public static List<String> getRepresentativePhrase(int headIndex, Category neededCategory, Parse parse) {
        return getRepresentativePhrase(headIndex, neededCategory, parse, Optional.empty());
    }

    public static List<String> getRepresentativePhrase(int headIndex, Category neededCategory, Parse parse,
                                                       String replacementWord) {
        return getRepresentativePhrase(headIndex, neededCategory, parse, Optional.of(replacementWord));
    }

    /**
     * Constructs a phrase with the desired head and category label.
     * takes an optional (by overloading---see above) argument asking for the head word to be replaced by something else.
     *   the optional argument is used when stripping verbs of their tense. (maybe there's a less hacky way to deal with that...)
     * TODO: does not handle coordination; we might want to include both args in the case of coordination.
     * In particular this would be for the phrases inside questions: consider "What did something do between April 1991?"
     */
    public static List<String> getRepresentativePhrase(int headIndex, Category neededCategory, Parse parse,
                                                       Optional<String> replacementWord) {
        SyntaxTreeNode tree = parse.syntaxTree;
        if(headIndex == -1) {
            return getRepresentativePhraseForUnrealized(neededCategory);
        }
        SyntaxTreeNode headLeaf = tree.getLeaves().get(headIndex);
        Optional<SyntaxTreeNode> nodeOpt = AnswerGenerator
            .getLowestAncestorFunctionIntoCategory(headLeaf, neededCategory, tree);
        if(!nodeOpt.isPresent()) {
            // fall back to just the original leaf. this failure case is very rare.
            List<String> result = new ArrayList<>();
            result.addAll(getNodeWords(headLeaf));
            return result;
        }
        // here we don't necessarily have the whole phrase. `node` is a function into the phrase.
        // especially common is the case where we get a transitive verb and it doesn't bother including the object.
        // so we need to populate the remaining spots by accessing the arguments of THIS guy,
        // until he exactly matches the category we're looking for.
        // using this method will capture and appropriately rearrange extracted arguments and such.

        SyntaxTreeNode node = nodeOpt.get();
        List<String> center = getNodeWords(node);
        if(replacementWord.isPresent()) {
            int indexInCenter = headIndex - node.getStartIndex();
            center.set(indexInCenter, replacementWord.get());
        }
        List<String> left = new ArrayList<>();
        List<String> right = new ArrayList<>();

        // add arguments on either side until done, according to CCG category.
        Category currentCategory = node.getCategory();
        for(int currentArgNum = currentCategory.getNumberOfArguments();
            currentArgNum > neededCategory.getNumberOfArguments();
            currentArgNum--) {
            Category argCat = currentCategory.getRight();
            // recover arg index using the fact that we know the head leaf and the arg num.
            Set<ResolvedDependency> deps = parse.dependencies;
            int curArg = currentArgNum; // just so we can use it in the lambda below
            Optional<ResolvedDependency> depOpt = deps.stream().filter(dep -> {
                    return dep.getHead() == headIndex && dep.getArgNumber() == curArg;
                }).findFirst();
            int argIndex = depOpt.map(dep -> dep.getArgument()).orElse(-1);
            List<String> argPhrase = getRepresentativePhrase(argIndex, argCat, parse);
            // add the argument on the left or right side, depending on the slash
            Slash slash = currentCategory.getSlash();
            switch(slash) {
            case FWD:
                right = Stream.concat(right.stream(), argPhrase.stream()).collect(Collectors.toList());
                break;
            case BWD:
                left = Stream.concat(argPhrase.stream(), left.stream()).collect(Collectors.toList());
                break;
            case EITHER:
                System.err.println("Undirected slash appeared in supertagged data :(");
                break;
            }
            // proceed to the next argument
            currentCategory = currentCategory.getLeft();
        }

        List<String> result = new ArrayList<>();
        result.addAll(left);
        result.addAll(center);
        result.addAll(right);
        return result;
    }

    // helper method to make sure we decapitalize the first letter of the sentence
    private static List<String> getNodeWords(SyntaxTreeNode node) {
        List<String> words = node.getLeaves()
            .stream()
            .map(leaf -> leaf.getWord())
            .collect(Collectors.toList());
        if(node.getStartIndex() == 0) {
            words.set(0, Character.toLowerCase(words.get(0).charAt(0)) + words.get(0).substring(1));
        }
        return words;
    }
}
