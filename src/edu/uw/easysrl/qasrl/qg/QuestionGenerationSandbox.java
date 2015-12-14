package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.AlignedDependency;
import edu.uw.easysrl.qasrl.PropBankAligner;
import edu.uw.easysrl.qasrl.qg.QuestionSlot.*;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.CountDictionary;

import java.io.*;
import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerationSandbox {
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
            Category.valueOf("(S\\NP)/(S\\NP)"),
            Category.valueOf("((S\\NP)\\(S\\NP))/S")
    };

    // Categories to skip ..
    private static final Category prepositions = Category.valueOf("((S\\NP)\\(S\\NP))/NP");
    private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
    private static final Category controlParticles = Category.valueOf("(S[to]\\NP)/(S[b]\\NP)");
    private static final Category pastParticiples = Category.valueOf("(S[dcl]\\NP)/(S[pt]\\NP)");

    private static final String[] otherFilteredCategories = new String[] {
            "(S/S)/NP", "(S\\NP)\\(S\\NP)",
            "S[em]/S[dcl]", "(S/S)/(S/S)",
            "(S[b]\\NP)/(S[pt]\\NP)", "S[qem]/S[dcl]",
            "(S\\S)/S[dcl]", "(S[adj]\\NP)/(S[to]\\NP)",
            "S/S",
    };

    private static final Set<String> otherFilteredCategorySet;
    static {
        otherFilteredCategorySet = new HashSet<>();
        Collections.addAll(otherFilteredCategorySet, otherFilteredCategories);
    }

    private static VerbHelper verbHelper;
    private static ArgumentHelper argumentHelper = new ArgumentHelper();

    /**
     * Go over all the sentences with aligned ccg-qa dependencies and generate questions. (we can as well generate
     * for unaligned dependencies, but we want to do evaluation with the annotated QAs.
     * @param alignedDependencies gold ccg dependencies
     */
    public static void generateFromGoldCCG(
            Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> alignedDependencies)
            throws IOException {

        /**
         * Initialize stats.
         */
        CountDictionary coveredDeps = new CountDictionary();
        CountDictionary uncoveredDeps = new CountDictionary();
        CountDictionary alignedDeps = new CountDictionary();
        int numDependenciesProcessed = 0;
        int numQuestionsGenerated = 0;
        int numAligned = 0;

        // TODO: shuffle sentence ids :)
        for (int sentIdx : alignedDependencies.keySet()) {
            List<AlignedDependency<CCGBankDependency, QADependency>> deps = alignedDependencies.get(sentIdx);
            if (deps == null) {
                continue;
            }
            Sentence sentence = deps.get(0).sentence;
            List<String> words = sentence.getWords();
            List<Category> categories = sentence.getLexicalCategories();
            Collection<CCGBankDependency> ccgDeps = sentence.getCCGBankDependencyParse().getDependencies();
            for (AlignedDependency<CCGBankDependency, QADependency> dep : deps) {
                CCGBankDependency ccgDep = dep.dependency1;
                QADependency qaDep = dep.dependency2;
                if (ccgDep == null) {
                    continue;
                }
                if (qaDep != null) {
                    numAligned ++;
                    alignedDeps.addString(ccgDep.getCategory().toString());
                }
                Category category = ccgDep.getCategory();
                // Skip copula verbs for now.
                if (verbHelper.isCopula(words, ccgDep.getSentencePositionOfPredicate())) {
                    continue;
                }
                // Generate question for the target dependency.
                if (category.isFunctionInto(prepositions) || category.isFunctionInto(auxiliaries) ||
                    category.isFunctionInto(controlParticles) || category.isFunctionInto(pastParticiples) ||
                        otherFilteredCategorySet.contains(category.toString())) {
                    continue;
                }
                if (category.isFunctionInto(somethingVerbal) || category.isFunctionInto(somethingAdjunctive)) {
                    numDependenciesProcessed ++;
                    //List<String> question = generateQuestions(ccgDep, qaDep, words, categories, ccgDeps);
                    QuestionTemplate template = getTemplate(ccgDep.getSentencePositionOfPredicate(), words, categories,
                                                            ccgDeps);
                    if (template == null) {
                        uncoveredDeps.addString(ccgDep.getCategory().toString());
                        if (category.getNumberOfArguments() > 3) {
                            printPredicateInfo(null, sentence, ccgDep, qaDep);
                        }
                        continue;
                    }
                    List<String> question = generateQuestions(template, ccgDep, qaDep, words, categories, ccgDeps);
                    if (question.size() == 0) {
                        continue;
                    }
                    numQuestionsGenerated ++;
                    coveredDeps.addString(ccgDep.getCategory().toString());

                    // output tempalte.
                    //if (template.getNumArguments() == 3) {
                        String ccgInfo = ccgDep.getCategory() + "_" + ccgDep.getArgNumber();
                        System.out.println("\n\n" + StringUtils.join(words));
                        System.out.println(ccgInfo);
                        for (QuestionSlot slot : template.slots) {
                            System.out.print(slot.toString(words) + "\t");
                        }
                        System.out.println();
                        question.forEach(w -> System.out.print(w + " "));
                        System.out.println("?");
                        System.out.println(qaDep == null ? "[no-qa]" : StringUtils.join(qaDep.getQuestion()) + "?");
                }
            }
        }
        System.out.println("\n++++++++++++++++++++++++++++++++++++++++++");
        System.out.println(String.format("Now able to generate %d " +
                        "(%.2f%% of %d dependencies, %.2f%% of %d aligned dependencies) questions.",
                numQuestionsGenerated,
                100.0 * numQuestionsGenerated / numDependenciesProcessed, numDependenciesProcessed,
                100.0 * numQuestionsGenerated / numAligned, numAligned));
        // uncoveredDeps.prettyPrint();
    }

    private static List<String> generateQuestions(QuestionTemplate template,
                                                  CCGBankDependency ccgDep, QADependency qaDep,
                                                  List<String> words, List<Category> categories,
                                                  Collection<CCGBankDependency> dependencies) {
        int targetArgNum = ccgDep.getArgNumber();
        int totalArgs = template.getNumArguments();
        List<String> question = new ArrayList<>();
        // If target is not part of the template, return.
        if (!template.argNumToSlotId.containsKey(targetArgNum)) {
            return question;
        }

        String[] wh = template.getWhWord(targetArgNum);
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
                addAll(question, template.getPlaceHolderWord(argSlot.argumentNumber));
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
        addAll(question, template.getPlaceHolderWord(1));
        add(question, verb[1]);
        for (int slotId = 2; slotId < template.slots.length; slotId++) {
            ArgumentSlot argSlot = (ArgumentSlot) template.slots[slotId];
            if (argSlot.argumentNumber == targetArgNum || (totalArgs >= 3 && argSlot.hasPreposition)) {
                continue;
            }
            addAll(question, template.getPlaceHolderWord(argSlot.argumentNumber));
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

    private static QuestionTemplate getTemplate(int predicateIndex, List<String> words, List<Category> categories,
                                                Collection<CCGBankDependency> dependencies) {
        Map<Integer, CCGBankDependency> slotToDependency = new HashMap<>();
        Category predicateCategory = categories.get(predicateIndex);
        int numArguments = predicateCategory.getNumberOfArguments();
        if (numArguments == 0 || belongsTo(predicateCategory, adjuncts)) {
            return null;
        }
        for (CCGBankDependency dep : dependencies) {
            int argNum = dep.getArgNumber();
            int predId = dep.getSentencePositionOfPredicate();
            int argId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && predId != argId) {
                slotToDependency.put(argNum, dep);
            }
        }
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        QuestionSlot verb;
        boolean hasParticle = predicateCategory.getArgument(numArguments).isFunctionInto(Category.PR);
        if (hasParticle) {
            int particleIndex = slotToDependency.get(numArguments).getSentencePositionOfArgument();
            verb = new VerbSlot(predicateIndex, particleIndex, auxChain, predicateCategory);
        } else {
            verb = new VerbSlot(predicateIndex, auxChain, predicateCategory);
        }
        QuestionSlot[] arguments = new QuestionSlot[numArguments + 1];
        for (int argNum = 1; argNum <= numArguments; argNum++) {
            CCGBankDependency dep = slotToDependency.get(argNum);
            Category argumentCategory = predicateCategory.getArgument(argNum);
            arguments[argNum] = (dep == null ? new UnrealizedArgumentSlot(argNum, argumentCategory) :
                        new ArgumentSlot(dep.getSentencePositionOfArgument(), argNum, argumentCategory));
        }
        if (numArguments == 2 && predicateCategory.getArgument(1).equals(Category.Sdcl)) {
            // T1, T2 said, or T2, said T1
            QuestionSlot[] slots = new QuestionSlot[] { arguments[2], verb, arguments[1] };
            return new QuestionTemplate(slots, words, categories, dependencies);
        }
        if (predicateCategory.isFunctionInto(intransitiveVerb) ||
                belongsTo(predicateCategory, transitiveVerbs) ||
                belongsTo(predicateCategory, ditransitiveVerbs)) {
            List<QuestionSlot> slots = new ArrayList<>();
            slots.add(arguments[1]);
            slots.add(verb);
            for (int i = numArguments; i > 1; i--) {
                if (!arguments[i].category.equals(Category.PR)) {
                    slots.add(arguments[i]);
                }
            }
            QuestionSlot[] slotList = slots.toArray(new QuestionSlot[slots.size()]);
            return new QuestionTemplate(slotList, words, categories, dependencies);
        }
        return null;
    }

    // TODO: generate questions from predicted ...
    private static void generateQuestions(ResolvedDependency targetDependency, List<String> words,
                                          Collection<ResolvedDependency> dependencies) {
    }


    private static void printPredicateInfo(BufferedWriter writer, Sentence sentence, CCGBankDependency ccgDep,
                                           QADependency qaDep)
            throws IOException {
        List<String> words = sentence.getWords();
        int predicateIndex = ccgDep.getSentencePositionOfPredicate();
        List<Integer> wordIndices = new ArrayList<>();
        Map<Integer, Integer> wordIdToSlot = new HashMap<>();
        wordIndices.add(predicateIndex);
        for (CCGBankDependency d : sentence.getCCGBankDependencyParse().getDependencies()) {
            int predId = d.getSentencePositionOfPredicate();
            int argId = d.getSentencePositionOfArgument();
            if (predId == predicateIndex && predId != argId) {
                wordIdToSlot.put(argId, d.getArgNumber());
                wordIndices.add(argId);
            }
        }
        Collections.sort(wordIndices);
        if (writer != null) {
            writer.write(StringUtils.join(words) + "\n");
        } else {
            System.out.print(StringUtils.join(words) + "\n");
        }
        List<String> lineBuf = new ArrayList<>();
        wordIndices.forEach(id -> {
            if (id == predicateIndex) {
                lineBuf.add(String.format("%s ", words.get(id)));
            } else if (id == ccgDep.getSentencePositionOfArgument()) {
                lineBuf.add(String.format("{%d:%s} ", wordIdToSlot.get(id), words.get(id)));
            } else {
                lineBuf.add(String.format("%d:%s ", wordIdToSlot.get(id), words.get(id)));
            }
        });
        if (writer != null) {
            writer.write(StringUtils.join(lineBuf) + "\n");
            writer.write(words.get(predicateIndex) + "\t" + ccgDep.getCategory() + "\t" +
                    ccgDep.getArgNumber() + "\n");
            writer.write(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
            writer.write("\n\n");
        } else {
            System.out.print(StringUtils.join(lineBuf) + "\n");
            System.out.print(words.get(predicateIndex) + "\t" + ccgDep.getCategory() + "\t" +
                   ccgDep.getArgNumber() + "\n");
            System.out.print(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
            System.out.print("\n\n");
        }
    }

    private static void outputInfo(CCGBankDependency ccgDep,
                                   QADependency qaDep,
                                   List<String> words,
                                   List<Category> categories,
                                   Collection<CCGBankDependency> dependencies,
                                   Map<Integer, Integer> wordToSlot,
                                   Map<Integer, Integer> slotToWord,
                                   List<Integer> wordIndices,
                                   List<String> question) {
        Category predicateCategory = ccgDep.getCategory();
        int predicateIndex = ccgDep.getSentencePositionOfPredicate();
        int targetSlotId = ccgDep.getArgNumber();
        String ccgInfo = ccgDep.getCategory() + "_" + ccgDep.getArgNumber();
        System.out.println("\n" + StringUtils.join(words));
        wordIndices.forEach(id -> {
            if (id == predicateIndex) {
                System.out.print(String.format("%s ", words.get(id)));
            } else if (id == ccgDep.getSentencePositionOfArgument()) {
                System.out.print(String.format("{%d:%s} ", wordToSlot.get(id), words.get(id)));
            } else {
                System.out.print(String.format("%d:%s ", wordToSlot.get(id), words.get(id)));
            }
        });
        System.out.println("\n" + words.get(predicateIndex) + "\t" + ccgInfo);
        System.out.println(StringUtils.join(question) + "?");
        System.out.println(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
    }

    public static void main(String[] args) {
        // hacky: Initialize inflection dictionary ..
        verbHelper = new VerbHelper(VerbInflectionDictionary.buildFromPropBankTraining());
        Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>>
            mappedDependencies = PropBankAligner.getCcgAndQADependenciesTrain();
        try {
            generateFromGoldCCG(mappedDependencies);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

