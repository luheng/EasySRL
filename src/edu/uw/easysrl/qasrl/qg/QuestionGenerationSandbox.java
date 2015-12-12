package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.AlignedDependency;
import edu.uw.easysrl.qasrl.PropBankAligner;
import edu.uw.easysrl.qasrl.qg.QuestionSlot;
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

    // TODO: Changing this to (S\NP)/NP causes a np exception, why?
    private static final Category transitiveVerb = Category.valueOf("(S\\NP)/NP");
    private static final Category transitiveVerbPP = Category.valueOf("(S\\NP)/PP");

    // Verbs such as say, think, add ... is there a name for them?
    // T1 said (that) T2
    private static final Category sayVerb = Category.valueOf("(S[dcl]\\NP)/S");
    // T1, T2 said, or T1, said T2
    private static final Category sayVerb2 = Category.valueOf("(S[dcl]\\S[dcl])|NP");

    // T1 agreed to do T2
    // Including: (S[dcl]\NP)/(S[to]\NP), (S[ng]\NP)/S[to]\NP), and (S[b]\NP)/(S[to]\NP)
    private static final Category controlVerb = Category.valueOf("(S\\NP)/(S[to]\\NP)");

    private static final Category ditransitiveVerb = Category.valueOf("((S\\NP)/NP)/NP");
    private static final Category ditransitiveVerbPP = Category.valueOf("((S\\NP)/PP)/NP");

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

        /**
         * Go over the sentences..
         */
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
                        continue;
                    }
                    List<String> question = generateQuestions(template, ccgDep, qaDep, words, categories, ccgDeps);
                    if (question.size() == 0) {
                        continue;
                    }
                    numQuestionsGenerated ++;
                    coveredDeps.addString(ccgDep.getCategory().toString());
                    /*
                    // output tempalte.
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
                    */
                }
            }
        }
        System.out.println("\n++++++++++++++++++++++++++++++++++++++++++");
        System.out.println(String.format("Now able to generate %d " +
                        "(%.2f%% of %d dependencies, %.2f%% of %d aligned dependencies) questions.",
                numQuestionsGenerated,
                100.0 * numQuestionsGenerated / numDependenciesProcessed, numDependenciesProcessed,
                100.0 * numQuestionsGenerated / numAligned, numAligned));
        uncoveredDeps.prettyPrint();
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
        writer.write(StringUtils.join(words) + "\n");
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
        writer.write(StringUtils.join(lineBuf) + "\n");
        writer.write(words.get(predicateIndex) + "\t" + ccgDep.getCategory() + "\t" +
                ccgDep.getArgNumber() + "\n");
        writer.write(qaDep == null ? "*N/A*" : StringUtils.join(qaDep.getQuestion()) + "?");
        writer.write("\n\n");
    }

    private static List<String> generateQuestions(QuestionTemplate template,
                                                  CCGBankDependency ccgDep, QADependency qaDep,
                                                  List<String> words, List<Category> categories,
                                                  Collection<CCGBankDependency> dependencies) {
        // TODO: slot id doesn't mean argument id , sighs
        int targetArgNum = ccgDep.getArgNumber();
        List<String> question = new ArrayList<>();
        // Intransitive verb: T1 V
        if (template.getNumArguments() == 1) {
            assert (targetArgNum == 1);
            question.add(template.getWhWord(targetArgNum));
            question.addAll(template.getActiveVerb(verbHelper));
            return question;
        }
        // Intransitive verb: T1 V T2
        if (template.getNumArguments() == 2) {
            // {Pat} built a robot -> Who built a robot ?
            if (targetArgNum == 1) {
                question.add(template.getWhWord(targetArgNum  ));
                question.addAll(template.getActiveVerb(verbHelper));
                question.add(template.getPlaceHolderWord(2));
                return question;
            }
            // {Pat} built a robot* -> What did Pat build / What was built ?
            if (targetArgNum == 2) {
                List<String> auxAndVerb = template.getActiveSplitVerb(verbHelper);
                question.add(template.getWhWord(targetArgNum));
                for (int i = 0; i < auxAndVerb.size() - 1; i++) {
                    question.add(auxAndVerb.get(i));
                }
                question.add(template.getPlaceHolderWord(1));
                question.add(auxAndVerb.get(auxAndVerb.size() - 1));
            }
            return question;
        }

        /**
         * Ditransitive verbs: ....
         * A gave B C
         * A charged B C on D ...
         */
        //if (predicateCategory.isFunctionInto(transitiveVerb) && predicateCategory.getNumberOfArguments() >= 3) {
        //if (predicateCategory.isFunctionInto(Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"))) {
            // outputInfo(ccgDep, qaDep, words, categories, dependencies, wordIdToSlot, slotToWordId, wordIndices, question);
        //}
        return question;
    }

    private static QuestionTemplate getTemplate(int predicateIndex, List<String> words, List<Category> categories,
                                                Collection<CCGBankDependency> dependencies) {
        Map<Integer, CCGBankDependency> slotToDependency = new HashMap<>();
        Category predicateCategory = categories.get(predicateIndex);
        int numArguments = predicateCategory.getNumberOfArguments();
        for (CCGBankDependency dep : dependencies) {
            int argNum = dep.getArgNumber();
            int predId = dep.getSentencePositionOfPredicate();
            int argId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && predId != argId) {
                slotToDependency.put(argNum, dep);
            }
        }
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        QuestionSlot verb = new VerbSlot(predicateIndex, auxChain, predicateCategory);
        QuestionSlot[] arguments = new QuestionSlot[numArguments + 1];
        QuestionSlot[] slots = null;
        for (int argNum = 1; argNum <= numArguments; argNum++) {
            CCGBankDependency dep = slotToDependency.get(argNum);
            Category argumentCategory = predicateCategory.getArgument(argNum);
            arguments[argNum] = (dep == null ? new UnrealizedArgumentSlot(argNum, argumentCategory) :
                    new ArgumentSlot(dep.getSentencePositionOfArgument(), argNum, argumentCategory));
        }
        // 1. Intransitive verbs. i.e. T1 ran.
        if (predicateCategory.isFunctionInto(intransitiveVerb) && numArguments == 1) {
            slots = new QuestionSlot[]{arguments[1], verb};
        }
        // 2. Simple transitive verbs, i.e. T1 built T2,
        //    Transitive verb with PPs:     T1 focuses on T2, or
        //    Control verbs:                T1 promised to do T2
        //    "say, think":                 T1 said (that) T2
        //    Special case of "say":        T1, T2 said (said T2)
        else if (numArguments == 2) {
            if (predicateCategory.isFunctionInto(transitiveVerb) ||
                    predicateCategory.isFunctionInto(transitiveVerbPP) ||
                    predicateCategory.isFunctionInto(controlVerb) ||
                    predicateCategory.isFunctionInto(sayVerb)) {
                slots = new QuestionSlot[]{arguments[1], verb, arguments[2]};
            }
            if (predicateCategory.isFunctionInto(sayVerb2)) {
                slots = new QuestionSlot[]{arguments[2], verb, arguments[1]};
            }
        }
        // 3. Ditransitive verbs with 3 arguments ..., i.e. T1 gave T3 T2
        //    or with pp:                                   T1 gave T3 to T2
        else if (numArguments == 3) {
            if (predicateCategory.isFunctionInto(ditransitiveVerb) ||
                    predicateCategory.isFunctionInto(ditransitiveVerbPP)) {
                slots = new QuestionSlot[]{arguments[1], verb, arguments[3], arguments[2]};
            }
        }
        return slots == null ? null : new QuestionTemplate(slots, words, categories, dependencies);
    }

    public static void outputInfo(CCGBankDependency ccgDep,
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

    // TODO: generate questions from predicted ...
    private static void generateQuestions(ResolvedDependency targetDependency, List<String> words,
                                          Collection<ResolvedDependency> dependencies) {
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

