package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {

    public VerbHelper verbHelper;

    public QuestionGenerator() {
        // FIXME: build from unlabeled corpora.
        verbHelper = new VerbHelper(VerbInflectionDictionary.buildFromPropBankTraining());
    }

    /**
     * Generates a question given a supertagged sentence, a dependency in question,
     * and all of the dependencies sharing their predicate with that dependency.
     * First constructs a QuestionTemplate by locating the target dep's arguments in the sentence.
     * Then passes to generateQuestionFromTemplate.
     * @param targetDependency    : the dependency to ask about
     * @param words               : the sentence
     * @param categories          : supertags for the sentence
     * @param deps                : dependencies sharing their predicate with the target
     * @return the question as a list of non-empty strings
     */
    public List<String> generateQuestion(ResolvedDependency targetDependency,
                                         SyntaxTreeNode tree,
                                         List<String> words,
                                         List<Category> categories,
                                         Collection<ResolvedDependency> deps) {
        String word = words.get(targetDependency.getHead());
        Category category = targetDependency.getCategory();
        int predicateIdx = targetDependency.getHead();
        int[] argNumToPosition = new int[category.getNumberOfArguments() + 1];
        Arrays.fill(argNumToPosition, -1);
        for (ResolvedDependency dep : deps) {
            if (dep.getHead() == predicateIdx && dep.getArgument() != dep.getHead()) {
                argNumToPosition[dep.getArgNumber()] = dep.getArgument();
            }
        }
        QuestionTemplate template = getTemplate(predicateIdx, argNumToPosition, tree, words, categories);
        if (template == null) {
            return null;
        }
        return template.instantiateForArgument(targetDependency.getArgNumber());
    }

    /**
     * Given a set of CCG dependencies and a target predicate, generate a QuestionTemplate object.
     * @param predicateIndex   : target predicate
     * @param words            : words in the sentence
     * @param categories       : categories for each word
     * @param ccgDeps          : the CCG dependencies in the sentence
     * @return questionTemplate
     */
    public QuestionTemplate getTemplateFromCCGBank(int predicateIndex,
                                                   SyntaxTreeNode tree,
                                                   List<String> words,
                                                   List<Category> categories,
                                                   Collection<CCGBankDependency> ccgDeps) {
        String word = words.get(predicateIndex);
        Category category = categories.get(predicateIndex);
        int[] argNumToPosition = new int[category.getNumberOfArguments() + 1];
        Arrays.fill(argNumToPosition, -1);
        for (CCGBankDependency dep : ccgDeps) {
            int predIdx = dep.getSentencePositionOfPredicate();
            int argIdx = dep.getSentencePositionOfArgument();
            if (predIdx == predicateIndex && argIdx != predIdx) {
                argNumToPosition[dep.getArgNumber()] = argIdx;
            }
        }
        return getTemplate(predicateIndex, argNumToPosition, tree, words, categories);
    }

    /**
     * Given a target predicate and the locations of its arguments in a sentence,
     * generate a QuestionTemplate object.
     * @param predicateIndex   : target predicate
     * @param argNumToPosition : argument position in sentence for each argNum. -1 for unrealized arguments.
     * @param words            : words in the sentence
     * @param categories       : categories for each word
     * @return questionTemplate
     */
    public QuestionTemplate getTemplate(int predicateIndex,
                                        int[] argNumToPosition,
                                        SyntaxTreeNode tree,
                                        List<String> words,
                                        List<Category> categories) {
        Category predicateCategory = categories.get(predicateIndex);
        int numArguments = predicateCategory.getNumberOfArguments();
        if (numArguments == 0) {
            return null;
        }
        // a question template should roughly match up with the supertag of the predicate.
        // Create the pred slot.
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        PredicateSlot pred;
        // If last argument is PR, as in "sweep out"
        if (predicateCategory.getArgument(numArguments).equals(Category.PR)) {
            int particleIndex = argNumToPosition[numArguments];
            pred = new PredicateSlot(predicateIndex, particleIndex, auxChain, predicateCategory);
        } else {
            pred = new PredicateSlot(predicateIndex, auxChain, predicateCategory);
        }
        // Generate slots. Skipping PR argument.
        ArgumentSlot[] arguments = new ArgumentSlot[numArguments + 1];
        for (int argNum = 1; argNum <= numArguments; argNum++) {
            int argIdx = argNumToPosition[argNum];
            Category argumentCategory = predicateCategory.getArgument(argNum);
            ArgumentSlot slot;
            if (argIdx < 0) {
                slot = new UnrealizedArgumentSlot(argNum, argumentCategory);
            } else if (argumentCategory.equals(Category.PR)) {
                slot = null;
            } else {
                // TODO: maybe we should use the identified PP? Add later.
                String ppStr = argumentCategory.isFunctionInto(Category.PP) ?
                        PrepositionHelper.getPreposition(words, categories, argIdx) : "";
                slot = new ArgumentSlot(argIdx, argNum, argumentCategory, ppStr);
            }
            arguments[argNum] = slot;
        }
        // Special case: T1, T2 said, or T2, said T1
        if (numArguments == 2 && predicateCategory.getArgument(1).equals(Category.Sdcl)) {
            ArgumentSlot[] slots = new ArgumentSlot[] { arguments[2], arguments[1] };
            return new QuestionTemplate(pred, slots, tree, words, categories, verbHelper);
        }
        // Otherwise it follows this order: T1 verb Tn Tn-1 ... T2
        // The first argument is always the subject. Why is this always true in English?
        List<ArgumentSlot> slots = new ArrayList<>();
        slots.add(arguments[1]);
        for (int i = numArguments; i > 1; i--) {
            if (arguments[i] != null) {
                slots.add(arguments[i]);
            }
        }
        ArgumentSlot[] slotList = slots.toArray(new ArgumentSlot[slots.size()]);
        return new QuestionTemplate(pred, slotList, tree, words, categories, verbHelper);
    }

    public List<String> generateQuestionFromTemplate(QuestionTemplate template, int targetArgNum) {
        return template.instantiateForArgument(targetArgNum);
    }
}

