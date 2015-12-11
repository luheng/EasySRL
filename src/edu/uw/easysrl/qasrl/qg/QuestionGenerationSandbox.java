package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.AlignedDependency;
import edu.uw.easysrl.qasrl.PropBankAligner;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.CountDictionary;

import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerationSandbox {
    private static final Category somethingVerbal = Category.valueOf("S|NP");
    private static final Category somethingAdjunctive = Category.valueOf("S|S");
    private static final Category intransitiveVerb = Category.valueOf("S\\NP");
    // TODO: Changing this to (S\NP)/NP causes a np exception, why?
    private static final Category transitiveVerb = Category.valueOf("(S[dcl]\\NP)/NP");
    // TODO: check this
    private static final Category ditransitiveVerb = Category.valueOf("((S\\NP)/NP)/NP");

    private static VerbHelper verbHelper;
    private static ArgumentHelper argumentHelper = new ArgumentHelper();

    private static CountDictionary coveredDeps;
    private static CountDictionary uncoveredDeps;
    private static int numDependenciesProcessed, numQuestionsGenerated;

    /**
     * Go over all the sentences with aligned ccg-qa dependencies and generate questions. (we can as well generate
     * for unaligned dependencies, but we want to do evaluation with the annotated QAs.
     * @param alignedDependencies gold ccg dependencies
     */
    public static void generateFromGoldCCG(
            Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> alignedDependencies) {

        /**
         * Initialize stats.
         */
        coveredDeps = new CountDictionary();
        uncoveredDeps = new CountDictionary();
        numDependenciesProcessed = 0;
        numQuestionsGenerated = 0;

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
                if (ccgDep == null) {
                    continue;
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
                if (category.isFunctionInto(somethingVerbal) || category.isFunctionInto(somethingAdjunctive)) {
                    numDependenciesProcessed ++;
                    QADependency goldQA = dep.dependency2;
                    String ccgInfo = ccgDep.getCategory() + "_" + ccgDep.getArgNumber();
                    List<String> question = generateQuestions(ccgDep, words, categories, ccgDeps, goldQA);
                    if (question.size() > 0) {
                        numQuestionsGenerated ++;
                        coveredDeps.addString(ccgInfo);
                        /*
                        System.out.println("\n" + StringUtils.join(words));
                        System.out.println(ccgInfo);
                        System.out.println(StringUtils.join(question) + "?");
                        System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
                        */
                    } else {
                        uncoveredDeps.addString(ccgInfo);
                    }
                }
            }
        }
        System.out.println("\n++++++++++++++++++++++++++++++++++++++++++");
        System.out.println(String.format("Now able to generate %d (%.2f%% of %d dependencies) questions.",
                numQuestionsGenerated, 100.0 * numQuestionsGenerated / numDependenciesProcessed,
                numDependenciesProcessed));
    }

    private static List<String> generateQuestions(CCGBankDependency targetDependency,
                                                  List<String> words,
                                                  List<Category> categories,
                                                  Collection<CCGBankDependency> dependencies,
                                                  QADependency goldQA) {
        Map<Integer, CCGBankDependency> slotToDependency = new HashMap<>();
        List<Integer> wordIndices = new ArrayList<>();
        Map<Integer, Integer> wordIdToSlot = new HashMap<>();
        Map<Integer, Integer> slotToWord = new HashMap<>();

        Category predicateCategory = targetDependency.getCategory();
        int predicateIndex = targetDependency.getSentencePositionOfPredicate();
        int targetSlotId = targetDependency.getArgNumber();
        int maxSlotId = 0;
        wordIndices.add(predicateIndex);
        for (CCGBankDependency dep : dependencies) {
            int slotId = dep.getArgNumber();
            int predId = dep.getSentencePositionOfPredicate();
            int argId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && predId != argId) {
                slotToDependency.put(slotId, dep);
                slotToWord.put(slotId, argId);
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

            /**
             * {Pat} built a robot -> Who built a robot ?
             */
            if (targetSlotId == 1) {
                int otherSlot = 2;
                String phWord = slotToWord.containsKey(otherSlot) ?
                        argumentHelper.getPlaceHolderString(words, categories, predicateIndex,
                                slotToWord.get(otherSlot), otherSlot) : "something";
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
                String phWord = slotToWord.containsKey(otherSlot) ?
                        argumentHelper.getPlaceHolderString(words, categories, predicateIndex,
                                slotToWord.get(otherSlot), otherSlot) : "someone";
                /**
                 * What did Pat/someone build?
                 */
                if (verbHelper.isPassive(words, categories, predicateIndex)) {
                    question.add("What");
                    auxChain.forEach(aux -> question.add(words.get(aux)));
                    question.add(words.get(predicateIndex));
                }
                /**
                 * What was built?
                 */
                else {
                    question.add("What");
                    if (auxChain.size() == 0) {
                        String[] split = verbHelper.getAuxiliaryAndVerbStrings(words, categories, predicateIndex);
                        question.add(split[0]);
                        question.add(phWord);
                        question.add(split[1]);
                    } else {
                        auxChain.forEach(aux -> question.add(words.get(aux)));
                        question.add(phWord);
                        question.add(words.get(predicateIndex));
                    }
                }
            }
            return question;
        }
        /**
         * A gave B C
         * A charged B C on D ...
         */
        if (predicateCategory.isFunctionInto(transitiveVerb) && predicateCategory.getNumberOfArguments() >= 3) {

        }
        return question;
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
        generateFromGoldCCG(mappedDependencies);
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