package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;


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
    private static final String trimPunctuation = " ,.:;!?-";
    private static final String vowels = "aeiou";
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

    // the result here is ALL POSSIBLE EXPANSIONS
    public static List<String> expandContraction(String contraction) {
        List<String> res = new LinkedList<>();
        if(contraction.equals("'s")) {
            res.add("has"); res.add("is");
        } else if(contraction.equals("'ve")) {
            res.add("have");
        } else if(contraction.equals("n't")) {
            res.add("not");
        } else if(contraction.equals("'re")) {
            res.add("are");
        } else if(contraction.equals("'ll")) {
            res.add("will");
        } else if(contraction.equals("'m")) {
            res.add("am");
        } else if(contraction.equals("'d")) {
            res.add("would"); res.add("had");
        }
        return res;
    }

    public static boolean startsWithVowel(String word) {
        if(word.length() == 0) return false;
        char firstLetter = word.toLowerCase().charAt(0);
        return vowels.indexOf(firstLetter) > -1;
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
        List<String> words = rawWords
            .stream()
            .map(TextGenerationHelper::translateTreeBankSymbols)
            .collect(Collectors.toList());
        Iterator<String> prevIterator = words.iterator();
        Optional<String> prevWord = Optional.empty();
        Iterator<String> nextIterator = words.iterator(); nextIterator.next();
        Optional<String> nextWord = Optional.empty(); if(nextIterator.hasNext()) nextWord = Optional.of(nextIterator.next());
        for(String word : words) {
            boolean noSpace = !prevWord.isPresent() ||
                (prevWord.isPresent() && noSpaceAfter.contains(prevWord.get())) ||
                noSpaceBefore.contains(word);
            if(!noSpace) {
                result.append(" ");
            }
            if(word.equalsIgnoreCase("a") && nextWord.isPresent() && startsWithVowel(nextWord.get())) {
                result.append("an");
            } else if(word.equalsIgnoreCase("an") && nextWord.isPresent() && !startsWithVowel(nextWord.get())) {
                result.append("a");
            } else {
                result.append(word);
            }
            prevWord = Optional.of(prevIterator.next());
            if(nextIterator.hasNext()) {
                nextWord = Optional.of(nextIterator.next());
            } else {
                nextWord = Optional.empty();
            }
        }
        while(result.length() > 0 &&
              trimPunctuation.indexOf(result.charAt(result.length() - 1)) >= 0) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    public static String renderHTMLSentenceString(List<String> rawWords, int predicateIndex,
                                                  boolean highlightPredicate) {
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
            if (i == predicateIndex && highlightPredicate) {
                result.append("<mark><strong>" + word + "</mark></strong>");
            } else {
                result.append(word);
            }
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
    public static List<TextWithDependencies> getRepresentativePhrasesForUnrealized(Category category) {
        List<String> words = new ArrayList<>();
        Set<ResolvedDependency> deps = new HashSet<>();
        words.add("something");
        List<TextWithDependencies> result = new LinkedList<>();
        result.add(new TextWithDependencies(words, deps));
        return result;
    }

    public static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt, Category neededCategory, Parse parse) {
        return getRepresentativePhrases(headIndexOpt, neededCategory, parse, Optional.empty());
    }

    public static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt, Category neededCategory, Parse parse, String replacementWord) {
        return getRepresentativePhrases(headIndexOpt, neededCategory, parse, Optional.of(replacementWord));
    }

    public static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt, Category neededCategory, Parse parse, Optional<String> replacementWord) {
        return getRepresentativePhrases(headIndexOpt, neededCategory, parse, headIndexOpt, replacementWord, true);
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
    private static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt, Category neededCategory,
                                                        Parse parse, Optional<Integer> replacementIndexOpt,
                                                        Optional<String> replacementWord, boolean lookForOf) {
        SyntaxTreeNode tree = parse.syntaxTree;
        if(!headIndexOpt.isPresent()) {
            return getRepresentativePhrasesForUnrealized(neededCategory);
        }
        int headIndex = headIndexOpt.get();
        SyntaxTreeNode headLeaf = tree.getLeaves().get(headIndex);
        Set<ResolvedDependency> touchedDeps = new HashSet<>();

        Optional<SyntaxTreeNode> nodeOpt = getLowestAncestorFunctionIntoCategory(headLeaf, neededCategory, tree);
        if(!nodeOpt.isPresent()) {
            // fall back to just the original leaf. this failure case is very rare.
            List<String> resultWords = new ArrayList<>();
            resultWords.addAll(getNodeWords(headLeaf, replacementIndexOpt, replacementWord));
            List<TextWithDependencies> result = new LinkedList<>();
            result.add(new TextWithDependencies(resultWords, touchedDeps));
            return result;
        }
        SyntaxTreeNode node = nodeOpt.get();

        // TODO figure out if this is better: optional fix for reducing the size of noun phrases
        // if asking for an NP, just take the determiner and the noun itself.
        // Buts then when aking for a noun, get one modifier if present.
        // if(Category.valueOf("N").matches(node.getCategory())) {
        //     Optional<SyntaxTreeNode> parentOpt = getParent(node, tree);
        //     if(parentOpt.isPresent()) {
        //         SyntaxTreeNode parent = parentOpt.get();
        //         if(Category.valueOf("N").matches(parent.getCategory())) {
        //             List<TextWithDependencies> result = new LinkedList<>();
        //             result.add(new TextWithDependencies(getNodeWords(parent, replacementIndexOpt, replacementWord),
        //                                                 getContainedDependencies(parent, parse)));
        //             return result;
        //         }
        //     }
        // } else if((node instanceof SyntaxTreeNodeBinary) &&
        //           Category.valueOf("NP").matches(node.getCategory()) &&
        //           Category.valueOf("NP/N").matches(((SyntaxTreeNodeBinary) node).getLeftChild().getHead().getCategory()) &&
        //           !(lookForOf && // don't do the NP shortcut when there's an of-phrase later that we need to include.
        //             node.getEndIndex() < tree.getEndIndex() &&
        //             tree.getLeaves().get(node.getEndIndex()).getWord().equals("of"))) {
        //     SyntaxTreeNodeLeaf detHead = ((SyntaxTreeNodeBinary) node).getLeftChild().getHead();
        //     List<TextWithDependencies> phrases = getRepresentativePhrases(headIndexOpt, Category.valueOf("N"), parse, replacementIndexOpt, replacementWord, lookForOf);
        //     // System.err.println("Reworking headedness of node: category " + node.getCategory() + ", word " + node.getWord());
        //     // System.err.println("Head node: category " + detHead.getCategory() + ", word " + detHead.getWord());
        //     // System.err.println("Sub phrase: " + renderString(phrases.get(0).tokens));
        //     return phrases
        //         .stream()
        //         .map(twd -> {
        //                 List<String> tokens = new LinkedList<>();
        //                 tokens.addAll(getNodeWords(detHead, replacementIndexOpt, replacementWord));
        //                 tokens.addAll(twd.tokens);
        //                 return new TextWithDependencies(tokens, twd.dependencies);
        //                     })
        //         .collect(Collectors.toList());
        // }

        // get all of the dependencies that were (presumably) touched in the course of getting the lowest good ancestor
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
                List<TextWithDependencies> phrasesWithOf =
                    getRepresentativePhrases(Optional.of(node.getEndIndex()),
                                            neededCategory,
                                            parse,
                                            replacementIndexOpt,
                                            replacementWord,
                                            false);
                List<TextWithDependencies> goodOfPhrases = phrasesWithOf
                    .stream()
                    .filter(phrase -> phrase.tokens
                            .stream()
                            .collect(Collectors.joining(" "))
                            .toLowerCase()
                            .contains(headLeaf.getWord().toLowerCase()))
                    .collect(Collectors.toList());
                if(goodOfPhrases.size() > 0) {
                    return goodOfPhrases;
                }
            }
            List<TextWithDependencies> result = new LinkedList<>();
            result.add(new TextWithDependencies(getNodeWords(node, replacementIndexOpt, replacementWord), touchedDeps));
            return result;
        } else {
            assert currentCategory.isFunctionInto(neededCategory)
                : "Current category should be a function into the needed category";
            assert neededCategory.getNumberOfArguments() < currentCategory.getNumberOfArguments()
                : "Current category should have fewer args than needed category, since they don't match";
            List<String> center = getNodeWords(node, replacementIndexOpt, replacementWord);

            // choose argument list
            // Map<Integer, Set<ResolvedDependency>> argDeps = new HashMap<>();
            // for (int i = 1; i <= currentCategory.getNumberOfArguments(); i++) {
            //     argDeps.put(i, new HashSet<Integer>());
            // }
            List<Set<Integer>> argIndices = new LinkedList<Set<Integer>>();
            argIndices.add(new HashSet<Integer>());
            for (int i = 1; i <= currentCategory.getNumberOfArguments(); i++) {
                argIndices.add(new HashSet<Integer>());
            }
            assert argIndices.size() == currentCategory.getNumberOfArguments() + 1
                : "Arg indices should be indexed properly";
            // System.err.println("needed category: " + neededCategory);
            // System.err.println("current category: " + currentCategory);
            final Category curCat = currentCategory; // for lambda below
            parse.dependencies
                .stream()
                .filter(dep -> dep.getHead() == headIndex)
                .filter(dep -> dep.getArgNumber() > neededCategory.getNumberOfArguments())
                .filter(dep -> dep.getArgNumber() <= curCat.getNumberOfArguments())
                .forEach(dep -> argIndices.get(dep.getArgNumber()).add(dep.getArgument()));
            List<List<Optional<Integer>>> argumentChoicePaths = getAllArgumentChoicePaths(argIndices);

            List<TextWithDependencies> phrases = new LinkedList<>();

            for(List<Optional<Integer>> chosenArgs : argumentChoicePaths) {
                List<String> left = new ArrayList<>();
                List<String> right = new ArrayList<>();
                Set<ResolvedDependency> localDeps = new HashSet<>();
                for(int currentArgNum = currentCategory.getNumberOfArguments();
                    currentArgNum > neededCategory.getNumberOfArguments();
                    currentArgNum--) {
                    // otherwise, add arguments on either side until done, according to CCG category.
                    Category argCat = currentCategory.getRight();
                    Optional<Integer> argIndexOpt = chosenArgs.get(currentArgNum);
                    // recover dep using the fact that we know the head leaf, arg num, and arg index.
                    final int curArg = currentArgNum; // just so we can use it in the lambda below
                    Optional<ResolvedDependency> depOpt = argIndexOpt
                        .flatMap(argIndex -> parse.dependencies
                                 .stream()
                                 .filter(dep -> (dep.getHead() == headIndex) &&
                                         (dep.getArgNumber() == curArg) &&
                                         (dep.getArgument() == argIndex))
                                 .findFirst());
                    depOpt.map(localDeps::add);
                    // XXX TODO do it for all arrangements of sub-phrases
                    TextWithDependencies argTextWithDeps =
                        getRepresentativePhrases(argIndexOpt,
                                                argCat,
                                                parse,
                                                replacementIndexOpt,
                                                replacementWord,
                                                lookForOf).get(0);
                    List<String> argPhrase = argTextWithDeps.tokens;
                    localDeps.addAll(argTextWithDeps.dependencies);
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
                localDeps.addAll(touchedDeps);
                phrases.add(new TextWithDependencies(result, localDeps));
            }
            return phrases;
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
            SyntaxTreeNodeLeaf firstLeaf = node.getLeaves().stream().findFirst().get(); // this should always be present
            String firstWord = firstLeaf.getWord();
            String firstPos = firstLeaf.getPos();
            boolean isProper = firstPos.equals("NNP") || firstPos.equals("NNPS");
            boolean isAllCaps = firstWord.matches("[A-Z]{2,}");
            if(!isProper && !isAllCaps) {
                words.set(0, Character.toLowerCase(words.get(0).charAt(0)) + words.get(0).substring(1));
            }
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

    // this is so stupid.
    public static List<List<Optional<Integer>>> getAllArgumentChoicePaths(List<Set<Integer>> choices) {
        List<List<Optional<Integer>>> pastPaths = new LinkedList<>();
        pastPaths.add(new LinkedList<Optional<Integer>>());
        for(Set<Integer> choiceList : choices) {
            List<List<Optional<Integer>>> currentPaths;
            if(choiceList.isEmpty()) {
                currentPaths = pastPaths
                    .stream()
                    .map(path -> {
                            List<Optional<Integer>> newPath = new LinkedList<>();
                            newPath.addAll(path);
                            newPath.add(Optional.empty());
                            return newPath;
                        })
                    .collect(Collectors.toList());
            } else {
                currentPaths = pastPaths
                    .stream()
                    .flatMap(path -> choiceList
                             .stream()
                             .map(choice -> {
                                     List<Optional<Integer>> newPath = new LinkedList<>();
                                     newPath.addAll(path);
                                     newPath.add(Optional.of(choice));
                                     return newPath;
                                 }))
                    .collect(Collectors.toList());
            }
            pastPaths = currentPaths;
        }
        return pastPaths;
    }

    // this is also stupid.
    public static List<Map<Integer, Optional<ResolvedDependency>>> getAllArgumentChoicePaths(Map<Integer, Set<ResolvedDependency>> choices) {
        List<Map<Integer, Optional<ResolvedDependency>>> pastPaths = new LinkedList<>();
        pastPaths.add(new TreeMap<Integer, Optional<ResolvedDependency>>());
        List<Map.Entry<Integer, Set<ResolvedDependency>>> choicesList = choices
            .entrySet()
            .stream()
            .sorted(Comparator.comparing(e -> e.getKey()))
            .collect(Collectors.toList());
        for(Map.Entry<Integer, Set<ResolvedDependency>> argAndChoiceSet : choicesList) {
            final int argNum = argAndChoiceSet.getKey();
            final Set<ResolvedDependency> choiceSet = argAndChoiceSet.getValue();
            final List<Map<Integer, Optional<ResolvedDependency>>> currentPaths;
            if(choicesList.isEmpty()) {
                currentPaths = pastPaths
                    .stream()
                    .map(path -> {
                            Map<Integer, Optional<ResolvedDependency>> newPath = new HashMap<>();
                            newPath.putAll(path);
                            newPath.put(argNum, Optional.empty());
                            return newPath;
                        })
                    .collect(Collectors.toList());
            } else {
                currentPaths = pastPaths
                    .stream()
                    .flatMap(path -> choiceSet
                             .stream()
                             .map(choice -> {
                                     Map<Integer, Optional<ResolvedDependency>> newPath = new HashMap<>();
                                     newPath.putAll(path);
                                     newPath.put(argNum, Optional.of(choice));
                                     return newPath;
                                 }))
                    .collect(Collectors.toList());
            }
            pastPaths = currentPaths;
        }
        return pastPaths;
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
