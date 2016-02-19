package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Tools for generating text from trees, dependencies, and lists of tokens.
 * Created by luheng on 1/20/16.
 */
public class TextGenerationHelper {

    // here is the punctuation we want to avoid spaces before,
    // and that we don't want at the end of the question or answer.
    // For now, those are the same.
    // Reference: https://www.cis.upenn.edu/~treebank/tokenization.html
    private static final String trimPunctuation = " ,.:;!?";
    private static Set<String> noSpaceBefore = new HashSet<String>();
    private static Set<String> noSpaceAfter = new HashSet<String>();
    static {
        noSpaceBefore.add(".");
        noSpaceBefore.add(",");
        noSpaceBefore.add("!");
        noSpaceBefore.add("?");
        noSpaceBefore.add(";");
        noSpaceBefore.add(":");
        noSpaceBefore.add("\'");
        noSpaceBefore.add("n't");
        noSpaceBefore.add("'s");
        noSpaceBefore.add("'re");
        noSpaceBefore.add("'ve");
        noSpaceBefore.add("'ll");
        noSpaceBefore.add("'m");
        noSpaceBefore.add("'d");
        noSpaceBefore.add("%");
        noSpaceBefore.add(")");
        noSpaceBefore.add("]");
        noSpaceBefore.add("}");

        noSpaceAfter.add("$");
        noSpaceAfter.add("#");
        noSpaceAfter.add("(");
        noSpaceAfter.add("[");
        noSpaceAfter.add("{");
    }

    /**
     * Turns a list of tokens into a nicely rendered string, spacing everything appropriately.
     * Trims extra punctuation at the end though. (Useful feature for now; might want to change later.)
     */
    public static String renderString(List<String> rawWords) {
        StringBuilder result = new StringBuilder();
        if(rawWords.size() == 0) {
            return "";
        }
        List<String> words = rawWords.stream().map(TextGenerationHelper::translateTreeBankSymbols).collect(Collectors.toList());
        Optional<String> prevWord = Optional.empty();
        for(String word : words) {
            boolean noSpace = !prevWord.isPresent() ||
                (prevWord.isPresent() && noSpaceAfter.contains(prevWord.get())) ||
                noSpaceBefore.contains(word);
            if(!noSpace) {
                result.append(" ");
            }
            result.append(word);
            prevWord = Optional.of(word);
        }
        while(result.length() > 0 &&
              trimPunctuation.indexOf(result.charAt(result.length() - 1)) >= 0) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    public static String renderHTMLString(List<String> rawWords, int predicateIndex) {
        StringBuilder result = new StringBuilder();
        if(rawWords.size() == 0) {
            return "";
        }
        List<String> words = rawWords.stream().map(TextGenerationHelper::translateTreeBankSymbols)
                                              .collect(Collectors.toList());
        Optional<String> prevWord = Optional.empty();
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i);
            boolean noSpace = (prevWord.isPresent() && noSpaceAfter.contains(prevWord.get()))
                                || noSpaceBefore.contains(word);
            if(!noSpace) {
                result.append(" ");
            }
            result.append(i == predicateIndex ? "<mark><strong>" + word + "</mark></strong>" : word);
            prevWord = Optional.of(word);
        }
        result.deleteCharAt(0);
        return result.toString();
    }

    public static String translateTreeBankSymbols(String word) {
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
        } else if (word.equals("\\/")) {
            word = "/";
        } else if (word.equals("``") || word.equals("\'\'")) {
            word = "\"";
        } else if (word.contains("\\/")) {
            word = word.replace("\\/", "/");
        }
        return word;
    }

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

    /**
     * Climbs up the tree, starting at the given node,
     * until we reach a node whose category is a function into (i.e., ends on the left with)
     * the given category. If none is found before getting to the root, returns empty.
     */
    public static Optional<SyntaxTreeNode> getLowestAncestorFunctionIntoCategory(SyntaxTreeNode node, Category category, SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        Optional<Category> curCat = curNode.map(SyntaxTreeNode::getCategory);
        while(curNode.isPresent() && !curCat.get().isFunctionInto(category)) {
            curNode = getParent(curNode.get(), wholeTree);
            curCat = curNode.map(SyntaxTreeNode::getCategory);
        }
        return curNode;
    }

    // Could be improved for PPs and such if necessary.
    public static TextWithDependencies getRepresentativePhraseForUnrealized(Category category) {
        List<String> words = new ArrayList<>();
        Set<ResolvedDependency> deps = new HashSet<>();
        words.add("something");
        return new TextWithDependencies(words, deps);
    }

    public static TextWithDependencies getRepresentativePhrase(Optional<Integer> headIndexOpt, Category neededCategory, Parse parse) {
        return getRepresentativePhrase(headIndexOpt, neededCategory, parse, Optional.empty());
    }

    public static TextWithDependencies getRepresentativePhrase(Optional<Integer> headIndexOpt, Category neededCategory, Parse parse, String replacementWord) {
        return getRepresentativePhrase(headIndexOpt, neededCategory, parse, Optional.of(replacementWord));
    }

    public static TextWithDependencies getRepresentativePhrase(Optional<Integer> headIndexOpt, Category neededCategory, Parse parse, Optional<String> replacementWord) {
        return getRepresentativePhrase(headIndexOpt, neededCategory, parse, headIndexOpt, replacementWord, true);
    }

    /**
     * Constructs a phrase with the desired head and category label.
     * takes an optional (by overloading---see above) argument asking for the head word to be replaced by something else.
     *   the optional argument is used when stripping verbs of their tense. (maybe there's a less hacky way to deal with that...)
     * TODO: does not handle coordination; we might want to include both args in the case of coordination.
     * In particular this would be for the phrases inside questions: consider "What did something do between April 1991?"
     * For multiple answers to the same question, we just call this multiple times. (it should only get one of multiple
     * constituents together in a coordination construction.)
     */
    private static TextWithDependencies getRepresentativePhrase(Optional<Integer> headIndexOpt, Category neededCategory,
                                                        Parse parse, Optional<Integer> replacementIndexOpt,
                                                        Optional<String> replacementWord, boolean lookForOf) {
        SyntaxTreeNode tree = parse.syntaxTree;
        if(!headIndexOpt.isPresent()) {
            return getRepresentativePhraseForUnrealized(neededCategory);
        }
        int headIndex = headIndexOpt.get();
        SyntaxTreeNode headLeaf = tree.getLeaves().get(headIndex);
        Set<ResolvedDependency> touchedDeps = new HashSet<>();

        Optional<SyntaxTreeNode> nodeOpt = getLowestAncestorFunctionIntoCategory(headLeaf, neededCategory, tree);
        if(!nodeOpt.isPresent()) {
            // fall back to just the original leaf. this failure case is very rare.
            List<String> result = new ArrayList<>();
            result.addAll(getNodeWords(headLeaf, replacementIndexOpt, replacementWord));
            return new TextWithDependencies(result, touchedDeps);
        }

        // get all of the dependencies that were (presumably) touched in the course of getting the lowest good ancestor
        SyntaxTreeNode node = nodeOpt.get();
        touchedDeps.addAll(getContainedDependencies(node, parse));

        // here we don't necessarily have the whole phrase. `node` is a function into the phrase.
        // especially common is the case where we get a transitive verb and it doesn't bother including the object.
        // so we need to populate the remaining spots by accessing the arguments of THIS guy,
        // until he exactly matches the category we're looking for.
        // using this method will capture and appropriately rearrange extracted arguments and such.

        Category currentCategory = node.getCategory();

        if(neededCategory.matches(currentCategory)) {
            // if we already have the right kind of phrase, consider adding a trailing "of"-phrase.
            if(lookForOf && // that is, if we're not in the midst of deriving an "of"-phrase already...
               node.getEndIndex() < tree.getEndIndex() &&
               tree.getLeaves().get(node.getEndIndex()).getWord().equals("of") // if the next word is "of",
               ) {
                return getRepresentativePhrase(Optional.of(node.getEndIndex()), neededCategory, parse, replacementIndexOpt, replacementWord, false);
            } else {
                return new TextWithDependencies(getNodeWords(node, replacementIndexOpt, replacementWord), touchedDeps);
            }
        } else {
            List<String> left = new ArrayList<>();
            List<String> center = getNodeWords(node, replacementIndexOpt, replacementWord);
            List<String> right = new ArrayList<>();

            for(int currentArgNum = currentCategory.getNumberOfArguments();
                currentArgNum > neededCategory.getNumberOfArguments();
                currentArgNum--) {
                // otherwise, add arguments on either side until done, according to CCG category.
                Category argCat = currentCategory.getRight();
                // recover arg index using the fact that we know the head leaf and the arg num.
                Set<ResolvedDependency> deps = parse.dependencies;
                final int curArg = currentArgNum; // just so we can use it in the lambda below
                Optional<ResolvedDependency> depOpt = deps.stream().filter(dep -> {
                        return dep.getHead() == headIndex && dep.getArgNumber() == curArg;
                    }).findFirst();
                // if we can't find the argument, we put index -1 so the recursive call considers it "unrealized"
                // and says, e.g., "something"
                depOpt.map(dep -> touchedDeps.add(dep));
                Optional<Integer> argIndexOpt = depOpt.map(dep -> dep.getArgument());
                TextWithDependencies argTextWithDeps = getRepresentativePhrase(argIndexOpt, argCat, parse, replacementIndexOpt, replacementWord, lookForOf);
                List<String> argPhrase = argTextWithDeps.tokens;
                touchedDeps.addAll(argTextWithDeps.dependencies);
                // add the argument on the left or right side, depending on the slash
                Slash slash = currentCategory.getSlash();
                switch(slash) {
                case FWD:
                    right.addAll(argPhrase);
                    break;
                case BWD:
                    argPhrase.addAll(left);
                    left = argPhrase;
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
            return new TextWithDependencies(result, touchedDeps);
        }

    }

    // helper method to make sure we decapitalize the first letter of the sentence
    // and replace a word if necessary.
    public static List<String> getNodeWords(SyntaxTreeNode node, Optional<Integer> replaceIndexOpt, Optional<String> replacementWord) {
        List<String> words = node.getLeaves()
            .stream()
            .map(leaf -> leaf.getWord())
            .collect(Collectors.toList());
        if(node.getStartIndex() == 0) {
            words.set(0, Character.toLowerCase(words.get(0).charAt(0)) + words.get(0).substring(1));
        }
        if(replacementWord.isPresent() && replaceIndexOpt.isPresent()) {
            int indexInWords = replaceIndexOpt.get() - node.getStartIndex();
            if(indexInWords >= 0 && indexInWords < words.size()) {
                words.set(indexInWords, replacementWord.get());
            }
        }
        return words;
    }

    /**
     * Gets all of the dependencies that start and end inside the syntax tree rooted at the given node
     */
    private static Set<ResolvedDependency> getContainedDependencies(SyntaxTreeNode node, Parse parse) {
        final Set<ResolvedDependency> deps = new HashSet<>();
        final int minIndex = node.getStartIndex();
        final int maxIndex = node.getEndIndex();
        for (ResolvedDependency dep : parse.dependencies) {
            if (dep.getHead() >= minIndex && dep.getHead() < maxIndex &&
                dep.getArgument() >= minIndex && dep.getArgument() <= maxIndex) {
                deps.add(dep);
            }
        }
        return deps;
    }

    /**
     * Data structure used to return text generated from a parsed sentence.
     * Indicates which dependencies were used in constructing the text.
     * This is returned by getRepresentativePhrase to indicate what parts of the parse were used.
     */
    public static class TextWithDependencies {
        public final List<String> tokens;
        public final Set<ResolvedDependency> dependencies;

        public TextWithDependencies(List<String> tokens, Set<ResolvedDependency> dependencies) {
            this.tokens = tokens;
            this.dependencies = dependencies;
        }
    }
}
