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
    // A1 said (that) A2
    private static final Category sayVerb = Category.valueOf("(S[dcl]\\NP)/S");
    // A1, A2 said
    private static final Category sayVerb2 = Category.valueOf("(S[dcl]\\S[dcl])\\NP");

    private static final Category controlVerb = Category.valueOf("(S[dcl]\\NP)/(S[to]\\NP)");

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
                /**
                 * Skip copula verbs for now.
                 */
                if (verbHelper.isCopula(words, ccgDep.getSentencePositionOfPredicate())) {
                    continue;
                }
                /**
                 * Generate question for the target dependency.
                 */
                if (category.isFunctionInto(prepositions) || category.isFunctionInto(auxiliaries) ||
                    category.isFunctionInto(controlParticles) || category.isFunctionInto(pastParticiples) ||
                        otherFilteredCategorySet.contains(category.toString())) {
                    continue;
                }
                if (category.isFunctionInto(somethingVerbal) || category.isFunctionInto(somethingAdjunctive)) {
                    numDependenciesProcessed ++;
                    List<String> question = generateQuestions(ccgDep, qaDep, words, categories, ccgDeps);
                    if (question.size() > 0) {
                        numQuestionsGenerated ++;
                        coveredDeps.addString(ccgDep.getCategory().toString());
                        /*
                        String ccgInfo = ccgDep.getCategory() + "_" + ccgDep.getArgNumber();
                        System.out.println("\n" + StringUtils.join(words));
                        System.out.println(ccgInfo);
                        System.out.println(StringUtils.join(question) + "?");
                        System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
                        */
                    } else {
                        uncoveredDeps.addString(ccgDep.getCategory().toString());
                    }
                }
            }
        }
        System.out.println("\n++++++++++++++++++++++++++++++++++++++++++");
        System.out.println(String.format("Now able to generate %d " +
                        "(%.2f%% of %d dependencies, %.2f%% of %d aligned dependencies) questions.",
                numQuestionsGenerated,
                100.0 * numQuestionsGenerated / numDependenciesProcessed, numDependenciesProcessed,
                100.0 * numQuestionsGenerated / numAligned, numAligned));

        /*
        for (String depStr : uncoveredDeps.getStrings()) {
            int freq = uncoveredDeps.getCount(depStr);
            int qaFreq = alignedDeps.getCount(depStr);
            if (freq < 10 || depStr.equals("<UNK>")) {
                continue;
            }
            String filename = String.format("/Users/luheng/Workspace/EasySRL/examples_%03d_%03d_%s.txt", freq, qaFreq,
                    depStr.replace("/", ":"));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "utf-8"));
            System.out.println(filename);

            for (int sentIdx : alignedDependencies.keySet()) {
                if (alignedDependencies.get(sentIdx) == null) {
                    continue;
                }
                Sentence sentence = alignedDependencies.get(sentIdx).get(0).sentence;
                for (AlignedDependency<CCGBankDependency, QADependency> dep : alignedDependencies.get(sentIdx)) {
                    CCGBankDependency ccgDep = dep.dependency1;
                    if (ccgDep != null && ccgDep.getCategory().toString().equals(depStr)) {
                        printPredicateInfo(writer, sentence, ccgDep, dep.dependency2);
                    }
                }
            }
            writer.close();
        }*/
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


    private static List<String> generateQuestions(CCGBankDependency ccgDep,
                                                  QADependency qaDep,
                                                  List<String> words,
                                                  List<Category> categories,
                                                  Collection<CCGBankDependency> dependencies) {
        Map<Integer, CCGBankDependency> slotToDependency = new HashMap<>();
        List<Integer> wordIndices = new ArrayList<>();
        Map<Integer, Integer> wordIdToSlot = new HashMap<>();
        Map<Integer, Integer> slotToWordId = new HashMap<>();

        Category predicateCategory = ccgDep.getCategory();
        int predicateIndex = ccgDep.getSentencePositionOfPredicate();
        int targetSlotId = ccgDep.getArgNumber();
        int maxSlotId = 0;
        wordIndices.add(predicateIndex);
        for (CCGBankDependency dep : dependencies) {
            int slotId = dep.getArgNumber();
            int predId = dep.getSentencePositionOfPredicate();
            int argId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && predId != argId) {
                slotToDependency.put(slotId, dep);
                slotToWordId.put(slotId, argId);
                wordIdToSlot.put(argId, slotId);
                wordIndices.add(argId);
                maxSlotId = Math.max(maxSlotId, slotId);
            }
        }
        assert slotToDependency.containsKey(targetSlotId);
        Collections.sort(wordIndices);
        List<String> question = new ArrayList<>();
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        /**
         * Intransitive/Ergative verbs?
         */
        if (predicateCategory.isFunctionInto(intransitiveVerb) && predicateCategory.getNumberOfArguments() == 1) {
            question.add("What");
            if (auxChain.size() > 0) {
                auxChain.forEach(aux -> question.add(words.get(aux)));
            } else if (predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
                       predicateCategory.isFunctionInto(Category.valueOf("S[pss]\\NP"))) {
                question.add("might");
                question.add("be");
            } else if (verbHelper.isUninflected(words, categories, predicateIndex)) {
                question.add("might");
            }
            question.add(words.get(predicateIndex));
            return question;
        }
        /**
         * For simple transitive verbs, such as A built B
         */
        if (predicateCategory.isFunctionInto(transitiveVerb) && predicateCategory.getNumberOfArguments() == 2) {
            String[] auxAndVerb = verbHelper.getAuxiliaryAndVerbStrings(words, categories, predicateIndex);
            /**
             * {Pat} built a robot -> Who built a robot ?
             */
            if (targetSlotId == 1) {
                int otherSlot = 2;
                String phWord = slotToWordId.containsKey(otherSlot) ?
                        argumentHelper.getPlaceHolderString(words, categories, predicateIndex,
                                slotToWordId.get(otherSlot), otherSlot) : "something";
                question.add("Who");
                auxChain.forEach(aux -> question.add(words.get(aux)));
                // TODO: post-process verb as in verb-splitter
                question.add(words.get(predicateIndex));
                question.add(phWord);
            }
            /**
             * {Pat} built a robot* -> What did Pat build / What was built ?
             */
            else if (targetSlotId == 2) {
                int otherSlot = 1;
                String phWord = slotToWordId.containsKey(otherSlot) ?
                        argumentHelper.getPlaceHolderString(words, categories, predicateIndex,
                                slotToWordId.get(otherSlot), otherSlot) : "someone";
                question.add("What");
                /**
                 * What did Pat/someone build?
                 */
                if (verbHelper.isPassive(words, categories, predicateIndex)) {
                    auxChain.forEach(aux -> question.add(words.get(aux)));
                    question.add(words.get(predicateIndex));
                }
                /**
                 * What was built?
                 */
                else {
                    if (auxChain.size() == 0) {
                        question.add(auxAndVerb[0]);
                        question.add(phWord);
                        question.add(auxAndVerb[1]);
                    } else {
                        auxChain.forEach(aux -> question.add(words.get(aux)));
                        question.add(phWord);
                        question.add(words.get(predicateIndex));
                    }
                }
            }
            return question;
        }
        if (predicateCategory.isFunctionInto(sayVerb)) {
            String[] auxAndVerb = verbHelper.getAuxiliaryAndVerbStrings(words, categories, predicateIndex);
            /**
             * {Pat} said the robot is awesome.
             */
            if (targetSlotId == 1) {
                String phWord = "something";
                question.add("Who");
                auxChain.forEach(aux -> question.add(words.get(aux)));
                question.add(words.get(predicateIndex));
                question.add(phWord);
            }
            /**
             * Pat said {the robot is awesome}.
             */
            else if (targetSlotId == 2) {
                String phWord = "someone";
                question.add("What");
                if (verbHelper.isPassive(words, categories, predicateIndex)) {
                    question.add("What");
                    auxChain.forEach(aux -> question.add(words.get(aux)));
                    question.add(words.get(predicateIndex));
                } else {
                    if (auxChain.size() == 0) {
                        question.add(auxAndVerb[0]);
                        question.add(phWord);
                        question.add(auxAndVerb[1]);
                    } else {
                        auxChain.forEach(aux -> question.add(words.get(aux)));
                        question.add(phWord);
                        question.add(words.get(predicateIndex));
                    }
                }
            }
           outputInfo(ccgDep, qaDep, words, categories, dependencies, wordIdToSlot, slotToWordId, wordIndices, question);
           return question;
        }
        /**
         * Ditransitive verbs: ....
         * A gave B C
         * A charged B C on D ...
         */
        //if (predicateCategory.isFunctionInto(transitiveVerb) && predicateCategory.getNumberOfArguments() >= 3) {
        if (predicateCategory.isFunctionInto(Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"))) {
            // outputInfo(ccgDep, qaDep, words, categories, dependencies, wordIdToSlot, slotToWordId, wordIndices, question);
        }
        return question;
    }

    private static List<QuestionSlot> getTemplate(int predicateIndex,
                                                  List<String> words, List<Category> categories,
                                                  Collection<CCGBankDependency> allDependencies) {
        Map<Integer, CCGBankDependency> slotToDependency = new HashMap<>();
        for (CCGBankDependency dep : allDependencies) {
            int slotId = dep.getArgNumber();
            int predId = dep.getSentencePositionOfPredicate();
            int argId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && predId != argId) {
                slotToDependency.put(slotId, dep);
            }
        }

        List<QuestionSlot> template = new ArrayList<>();
        Category predicateCategory = categories.get(predicateIndex);
        List<Integer> auxChain = verbHelper.getAuxiliaryChain(words, categories, predicateIndex);
        CCGBankDependency anyDependency = slotToDependency.values().iterator().next();
        QuestionSlot verbSlot = new VerbSlot(anyDependency, auxChain, words, allDependencies);

        // 1. Intransitive verbs. i.e. T1 ran.
        if (predicateCategory.isFunctionInto(intransitiveVerb) && predicateCategory.getNumberOfArguments() == 1) {
            template.add(new ArgumentSlot(1, slotToDependency.get(1), words, allDependencies));
            template.add(verbSlot);
            return template;
        }
        // 2. Simple transitive verbs, i.e. T1 built T2,
        //    Transitive verb with PPs:     T1 focuses on T2, or
        //    Control verbs:                T1 promised to do T2
        if ((predicateCategory.isFunctionInto(transitiveVerb) ||
                    predicateCategory.isFunctionInto(transitiveVerbPP) ||
                    predicateCategory.isFunctionInto(controlVerb)) &&
                predicateCategory.getNumberOfArguments() == 2) {
            template.add(new ArgumentSlot(1, slotToDependency.get(1), words, allDependencies));
            template.add(verbSlot);
            template.add(new ArgumentSlot(2, slotToDependency.get(2), words, allDependencies));
            return template;
        }
        // 3. Verbs such as "say", "confirm", i.e. T1 said (that) T2
        if (predicateCategory.isFunctionInto(sayVerb)) {
            template.add(new ArgumentSlot(1, slotToDependency.get(1), words, allDependencies));
            template.add(verbSlot);
            template.add(new ArgumentSlot(2, slotToDependency.get(2), words, allDependencies));
            return template;
        }
        // 4. Verbs such as "say", but with a different construction, i.e. T1, T2 said
        if (predicateCategory.isFunctionInto(sayVerb2)) {
            template.add(new ArgumentSlot(2, slotToDependency.get(2), words, allDependencies));
            template.add(verbSlot);
            template.add(new ArgumentSlot(1, slotToDependency.get(1), words, allDependencies));
            return template;
        }
        // 5. Ditransitive verbs with 3 arguments ..., i.e. T1 gave T3 T2
        //    or with pp:                                   T1 gave T3 to T2
        if (predicateCategory.isFunctionInto(ditransitiveVerb)) {
            template.add(new ArgumentSlot(1, slotToDependency.get(1), words, allDependencies));
            template.add(verbSlot);
            template.add(new ArgumentSlot(3, slotToDependency.get(3), words, allDependencies));
            template.add(new ArgumentSlot(2, slotToDependency.get(2), words, allDependencies));
            return template;
        }
        return template;
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


// Backup space

   /*
            String ccgInfo = targetDependency.getCategory() + "_" + targetDependency.getArgNumber();
            System.out.println("\n" + StringUtils.join(words));
            wordIndices.forEach(id -> {
                if (id == targetDependency.getSentencePositionOfArgument()) {
                    System.out.print("{" + words.get(id) + "} ");
                } else {
                    System.out.print(words.get(id) + " ");
                }
            });
            System.out.println("\n" + ccgInfo);
            System.out.println(StringUtils.join(question) + "?");
            System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
            */

  /*
            String ccgInfo = targetDependency.getCategory() + "_" + targetDependency.getArgNumber();
            System.out.println("\n" + StringUtils.join(words));
            wordIndices.forEach(id -> {
                if (id == targetDependency.getSentencePositionOfArgument()) {
                    System.out.print("{" + words.get(id) + "} ");
                } else {
                    System.out.print(words.get(id) + " ");
                }
            });
            System.out.println("\n" + ccgInfo);
            System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
            */