package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {
    private static final Category somethingVerbal = Category.valueOf("S|NP");
    private static final Category somethingAdjunctive = Category.valueOf("S|S");
    private static final Category intransitiveVerb = Category.valueOf("S\\NP");

    private static final Category[] transitiveVerbs = {
            Category.valueOf("(S\\NP)/NP"),
            Category.valueOf("(S\\NP)/PP"),
            Category.valueOf("((S\\NP)/NP)/PR"),
            Category.valueOf("((S\\NP)/PP)/PR"),
            // T1 said (that) T2
            Category.valueOf("(S[dcl]\\NP)/S"),
            // T1, T2 said, or T1, said T2
            Category.valueOf("(S[dcl]\\S[dcl])|NP"),
            // T1 agreed to do T2
            Category.valueOf("(S\\NP)/(S[to]\\NP)"),

    };

    private static final Category[] ditransitiveVerbs = {
            // T1 made T3 T2
            Category.valueOf("((S\\NP)/NP)/NP"),
            // T1 gave T3 to T2
            Category.valueOf("((S\\NP)/PP)/NP"),
            // T1 promised T3 to do T2
            Category.valueOf("((S\\NP)/(S[to]\\NP))/NP"), // Category.valueOf("((S[dcl]\\NP)/(S[to]\\NP))/NP")
    };

    private static final Category[] adjuncts = {
            Category.valueOf("(S\\NP)|(S\\NP)"),
            Category.valueOf("((S\\NP)\\(S\\NP))/S"),
            Category.valueOf("((S\\NP)\\(S\\NP))/(S[ng]\\NP)"), // ``by'' as in ``by doing something''.
    };

    // TODO: special cases:
    // according (to): ((S\NP)\(S\NP))/PP
    // down (from): ((S\NP)\(S\NP))/PP
    // Possible questions: What is the case, according to someone? What is down from something?
    // What is something according to? What something down from?

    // Categories to skip ..
    private static final Category prepositions = Category.valueOf("((S\\NP)\\(S\\NP))/NP");
    private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
    //private static final Category controlParticles = Category.valueOf("(S[to]\\NP)/(S[b]\\NP)");
    private static final Category controlParticles = Category.valueOf("(S\\NP)/(S[b]\\NP)");
    private static final Category pastParticiples = Category.valueOf("(S[dcl]\\NP)/(S[pt]\\NP)");

    private static final String[] otherFilteredCategories = new String[] {
            "(S/S)/NP",
            "(S\\NP)\\(S\\NP)",
            "S[em]/S[dcl]",
            "(S/S)/(S/S)",
            "(S[b]\\NP)/(S[pt]\\NP)",
            "S[qem]/S[dcl]",
            "(S\\S)/S[dcl]",
            "(S[adj]\\NP)/(S[to]\\NP)",
            "S/S",
            "((S\\NP)/(S\\NP))/((S\\NP)/(S\\NP))", // i.e. more
            "((S\\NP)\\(S\\NP))/((S\\NP)\\(S\\NP))",
            "((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP))",
            "((S\\NP)\\(S\\NP))/(S[b]\\NP)",
            "(((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP)))/(((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP)))",
    };

    private static final Set<String> otherFilteredCategorySet;
    static {
        otherFilteredCategorySet = new HashSet<>();
        Collections.addAll(otherFilteredCategorySet, otherFilteredCategories);
    }

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
                                         List<String> words,
                                         List<Category> categories,
                                         Collection<ResolvedDependency> deps) {
        String word = words.get(targetDependency.getHead());
        Category category = targetDependency.getCategory();
        int predicateIdx = targetDependency.getHead();
        // Filter.
        if (!askQuestionForPredicate(word, category)) {
            return null;
        }
        int[] argNumToPosition = new int[category.getNumberOfArguments() + 1];
        Arrays.fill(argNumToPosition, -1);
        for (ResolvedDependency dep : deps) {
            if (dep.getHead() == predicateIdx && dep.getArgument() != dep.getHead()) {
                argNumToPosition[dep.getArgNumber()] = dep.getArgument();
            }
        }
        QuestionTemplate template = getTemplate(predicateIdx, argNumToPosition, words, categories);
        if (template == null) {
            return null;
        }
        return template.instantiateForArgument(targetDependency.getArgNumber(), verbHelper);
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
                                                   List<String> words,
                                                   List<Category> categories,
                                                   Collection<CCGBankDependency> ccgDeps) {
        String word = words.get(predicateIndex);
        Category category = categories.get(predicateIndex);
        if (!askQuestionForPredicate(word, category)) {
            return null;
        }
        int[] argNumToPosition = new int[category.getNumberOfArguments() + 1];
        Arrays.fill(argNumToPosition, -1);
        for (CCGBankDependency dep : ccgDeps) {
            int predIdx = dep.getSentencePositionOfPredicate();
            int argIdx = dep.getSentencePositionOfArgument();
            if (predIdx == predicateIndex && argIdx != predIdx) {
                argNumToPosition[dep.getArgNumber()] = argIdx;
            }
        }
        return getTemplate(predicateIndex, argNumToPosition, words, categories);
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
                                        List<String> words,
                                        List<Category> categories) {
        Category predicateCategory = categories.get(predicateIndex);
        int numArguments = predicateCategory.getNumberOfArguments();
        if (numArguments == 0 || !askQuestionForPredicate(words.get(predicateIndex), predicateCategory)) {
            return null;
        }
        // Create the verb slot.
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        QuestionSlot verb;
        // If last argument is PR, as in "sweep out"
        if (predicateCategory.getArgument(numArguments).equals(Category.PR)) {
            int particleIndex = argNumToPosition[numArguments];
            verb = new VerbSlot(predicateIndex, particleIndex, auxChain, predicateCategory);
        } else {
            verb = new VerbSlot(predicateIndex, auxChain, predicateCategory);
        }
        // Generate slots. Skipping PR argument.
        QuestionSlot[] arguments = new QuestionSlot[numArguments + 1];
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
            QuestionSlot[] slots = new QuestionSlot[] { arguments[2], verb, arguments[1] };
            return new QuestionTemplate(slots, words, categories);
        }
        // Otherwise it follows this order: T1 verb Tn Tn-1 ... T2
        // The first argument is always the subject. Why is this always true in English?
        List<QuestionSlot> slots = new ArrayList<>();
        slots.add(arguments[1]);
        slots.add(verb);
        for (int i = numArguments; i > 1; i--) {
            if (arguments[i] != null) {
                slots.add(arguments[i]);
            }
        }
        QuestionSlot[] slotList = slots.toArray(new QuestionSlot[slots.size()]);
        return new QuestionTemplate(slotList, words, categories);
    }

    // FIXME: there must be a better way to do this.
    /**
     * Indicates whether we are able to ask questions about a particular verb.
     * @param word      : the verb we're considering asking about
     * @param category  : the syntactic category of the verb
     * @return true iff we're able to generate a question.
     */
    public boolean askQuestionForPredicate(String word, Category category) {
        if (VerbHelper.isCopulaVerb(word)) {
            //System.out.println("skipping because copula");
            return false;
        }
        if (!verbHelper.hasInflectedForms(word) && !category.equals(Category.valueOf("(S[adj]\\NP)/(S[to]\\NP)"))  ) {
            return false;
        }
        if (!category.isFunctionInto(somethingVerbal) &&
                !category.isFunctionInto(somethingAdjunctive)) {
            //System.out.println("skipping because not verb or adjunct");
            return false;
        }
        // Generate question for the target dependency.
        if (category.isFunctionInto(prepositions) ||
                category.isFunctionInto(auxiliaries) ||
                category.isFunctionInto(controlParticles) ||
                category.isFunctionInto(pastParticiples) ||
                otherFilteredCategorySet.contains(category.toString())) {
            // System.out.println("skipping because in other filtered list");
            return false;
        }
        if (category.isFunctionInto(intransitiveVerb) ||
                belongsTo(category, transitiveVerbs) ||
                belongsTo(category, ditransitiveVerbs)) {
            //System.out.println("NOT skipping because is verb");
            return true;
        }
        if (belongsTo(category, adjuncts)) {
            //System.out.println("skipping because is adjunct");
            return false;
        }
        return true;
    }

    public List<String> generateQuestionFromTemplate(QuestionTemplate template, int targetArgNum) {
        return template.instantiateForArgument(targetArgNum, verbHelper);
    }

    private static boolean belongsTo(Category category, Category[] categoryList) {
        for (Category c : categoryList) {
            if (category.isFunctionInto(c)) {
                return true;
            }
        }
        return false;
    }
}

