package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

import java.io.*;
import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGeneration {
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
            Category.valueOf("(S\\NP)/(S[to]\\NP)")
    };

    private static final Category[] ditransitiveVerbs = {
            Category.valueOf("((S\\NP)/NP)/NP"),
            Category.valueOf("((S\\NP)/PP)/NP")
    };

    private static final Category[] adjuncts = {
            Category.valueOf("(S\\NP)|(S\\NP)"),
            Category.valueOf("((S\\NP)\\(S\\NP))/S"),
            Category.valueOf("((S\\NP)\\(S\\NP))/(S[ng]\\NP)"), // ``by'' as in ``by doing something''.
    };

    // Categories to skip ..
    private static final Category prepositions = Category.valueOf("((S\\NP)\\(S\\NP))/NP");
    private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
    //private static final Category controlParticles = Category.valueOf("(S[to]\\NP)/(S[b]\\NP)");
    private static final Category controlParticles = Category.valueOf("(S\\NP)/(S[b]\\NP)");
    private static final Category pastParticiples = Category.valueOf("(S[dcl]\\NP)/(S[pt]\\NP)");

    private static final String[] otherFilteredCategories = new String[] {
            "(S/S)/NP", "(S\\NP)\\(S\\NP)",
            "S[em]/S[dcl]", "(S/S)/(S/S)",
            "(S[b]\\NP)/(S[pt]\\NP)", "S[qem]/S[dcl]",
            "(S\\S)/S[dcl]", "(S[adj]\\NP)/(S[to]\\NP)",
            "S/S",
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

    private static VerbHelper verbHelper;

    private static List<String> generateQuestionFromTemplate(ResolvedDependency targetDependency,
                                                             List<String> words,
                                                             List<Category> categories,
                                                             Collection<ResolvedDependency> ccgDeps) {
        Category category = targetDependency.getCategory();
        int predicateIdx = targetDependency.getHead();
        int argumentIdx = targetDependency.getArgument();
        // Filter.
        if (verbHelper.isCopula(words, predicateIdx) ||
                category.isFunctionInto(prepositions) || category.isFunctionInto(auxiliaries) ||
                category.isFunctionInto(controlParticles) || category.isFunctionInto(pastParticiples) ||
                otherFilteredCategorySet.contains(category.toString())) {
            return null;
        }
        if(!category.isFunctionInto(somethingVerbal) && !category.isFunctionInto(somethingAdjunctive)) {
            return null;
        }
        QuestionTemplate template = getTemplate(predicateIdx, words, categories, ccgDeps);
        if (template == null) {
            // Unable to generate template.
            return null;
        }
        return generateQuestionFromTemplate(template, argumentIdx);
    }

    private static QuestionTemplate getTemplate(int predicateIndex,
                                                List<String> words,
                                                List<Category> categories,
                                                Collection<ResolvedDependency> dependencies) {
        Map<Integer, ResolvedDependency> slotToDependency = new HashMap<>();
        Category predicateCategory = categories.get(predicateIndex);
        int numArguments = predicateCategory.getNumberOfArguments();
        if (numArguments == 0 || belongsTo(predicateCategory, adjuncts)) {
            return null;
        }
        for (ResolvedDependency dep : dependencies) {
            int argNum = dep.getArgNumber();
            int predIdx = dep.getHead();
            int argIdx = dep.getArgument();
            if (predIdx == predicateIndex && predIdx != argIdx) {
                slotToDependency.put(argNum, dep);
            }
        }
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        QuestionSlot verb;
        boolean hasParticle = predicateCategory.getArgument(numArguments).isFunctionInto(Category.PR);
        if (hasParticle) {
            // TODO: pay attention to PR.
            int particleIndex = slotToDependency.get(numArguments).getArgument();
            verb = new VerbSlot(predicateIndex, particleIndex, auxChain, predicateCategory);
        } else {
            verb = new VerbSlot(predicateIndex, auxChain, predicateCategory);
        }
        QuestionSlot[] arguments = new QuestionSlot[numArguments + 1];
        for (int argNum = 1; argNum <= numArguments; argNum++) {
            ResolvedDependency dep = slotToDependency.get(argNum);
            Category argumentCategory = predicateCategory.getArgument(argNum);
            ArgumentSlot slot;
            if (argumentCategory.equals(Category.PR)) {
                slot = null;
            } else if (dep == null) {
                slot = new UnrealizedArgumentSlot(argNum, argumentCategory);
            } else {
                slot = new ArgumentSlot(dep.getArgumentIndex(), argNum, argumentCategory);
                if (slot.hasPreposition && !dep.getPreposition().equals(Preposition.NONE)) {
                    // TODO: pay attention to resolved PP.
                    slot.resolvedPreposition = dep.getPreposition();
                }
            }
            arguments[argNum] = slot;
        }
        if (numArguments == 2 && predicateCategory.getArgument(1).equals(Category.Sdcl)) {
            // T1, T2 said, or T2, said T1
            QuestionSlot[] slots = new QuestionSlot[] { arguments[2], verb, arguments[1] };
            return new QuestionTemplate(slots, words, categories);
        }
        if (predicateCategory.isFunctionInto(intransitiveVerb) ||
                belongsTo(predicateCategory, transitiveVerbs) ||
                belongsTo(predicateCategory, ditransitiveVerbs)) {
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
        return null;
    }

    private static List<String> generateQuestionFromTemplate(QuestionTemplate template, int targetArgNum) {
        int totalArgs = template.getNumArguments();
        List<String> question = new ArrayList<>();
        // If target is not part of the template, return.
        if (!template.argNumToSlotId.containsKey(targetArgNum)) {
            return question;
        }

        String[] wh = template.getWhWordByArgNum(targetArgNum);
        // {Pat} built a robot -> Who built a robot ?
        // Who gave T3 T2?
        if (targetArgNum == 1) {
            add(question, wh[0]);
            addAll(question, template.getActiveVerb(verbHelper));
            for (int slotId = 2; slotId < template.slots.length; slotId++) {
                ArgumentSlot argSlot = (ArgumentSlot) template.slots[slotId];
                if (totalArgs >= 3 && argSlot.hasPreposition) {
                    continue;
                }
                addAll(question, template.getPlaceHolderWordByArgNum(argSlot.argumentNumber));
            }
            add(question, wh[1]);
            return question;
        }

        // {Pat} built a robot* -> What did Pat build / What was built ?
        // What did T1 give T3?, i.e. What did John give Pat?
        // (to) Who did T1 give T3?, Who did John give a robot to?
        String[] verb = template.getActiveSplitVerb(verbHelper);
        add(question, wh[0]);
        add(question, verb[0]);
        addAll(question, template.getPlaceHolderWordByArgNum(1));
        add(question, verb[1]);
        for (int slotId = 2; slotId < template.slots.length; slotId++) {
            ArgumentSlot argSlot = (ArgumentSlot) template.slots[slotId];
            if (argSlot.argumentNumber == targetArgNum || (totalArgs >= 3 && argSlot.hasPreposition)) {
                continue;
            }
            addAll(question, template.getPlaceHolderWordByArgNum(argSlot.argumentNumber));
        }
        add(question, wh[1]);
        return question;
    }

    private static void add(List<String> question, String word) {
        if (!word.isEmpty()) {
            question.add(word);
        }
    }

    private static void addAll(List<String> question, String[] words) {
        for (String w : words) {
            add(question, w);
        }
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

