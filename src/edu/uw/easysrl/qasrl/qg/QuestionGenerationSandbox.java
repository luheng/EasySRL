package edu.uw.easysrl.qasrl.qg;

import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.AlignedDependency;
import edu.uw.easysrl.qasrl.PropBankAligner;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerationSandbox {

    private static final Category somethingVerbal = Category.valueOf("S|NP");
    private static final Category somethingAdjunctive = Category.valueOf("S|S");
    private static final Category simpleTransitiveVerb = Category.valueOf("(S[dcl]\\NP)/NP");

    public static void generateFromGoldCCG(
            Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> alignedDependencies) {
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
                if (VerbHelper.isCopula(words, ccgDep.getSentencePositionOfPredicate())) {
                    continue;
                }
                if (category.isFunctionInto(somethingVerbal) || category.isFunctionInto(somethingAdjunctive)) {
                    generateQuestions(ccgDep, words, categories, ccgDeps, dep.dependency2);
                }
            /*
            for (CCGBankDependency dep : ccgDeps) {
                if (dep.getSentencePositionOfArgument() == dep.getSentencePositionOfPredicate()) {
                    continue;
                }
                Category category = dep.getCategory();
                if (category.isFunctionInto(Category.valueOf("S|NP"))) // we will deal with adjuncts later ...
                   // || category.isFunctionInto(Category.valueOf("S|S"))) {
                    generateQuestions(dep, words, ccgDeps);
                }
            }
            */
            }
        }
    }

    private static void generateQuestions(CCGBankDependency targetDependency,
                                          List<String> words,
                                          List<Category> categories,
                                          Collection<CCGBankDependency> dependencies,
                                          QADependency goldQA) {
        Map<Integer, CCGBankDependency> slotToDependency = new HashMap<>();
        List<Integer> wordIndices = new ArrayList<>();
        Map<Integer, Integer> wordIdToSlot = new HashMap<>();
        Map<Integer, Integer> slotToWord = new HashMap<>();

        int targetSlotId = targetDependency.getArgNumber();
        int predicateIndex = targetDependency.getSentencePositionOfPredicate();
        wordIndices.add(predicateIndex);
        for (CCGBankDependency dep : dependencies) {
            int slotId = dep.getArgNumber();
            int predId = dep.getSentencePositionOfPredicate();
            int argId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && predId != argId) {
                slotToDependency.put(dep.getArgNumber(), dep);
                slotToWord.put(slotId, argId);
                wordIdToSlot.put(argId, slotId);
                wordIndices.add(argId);
            }
        }
        assert slotToDependency.containsKey(targetSlotId);
        Collections.sort(wordIndices);

        if (targetDependency.getCategory().isFunctionInto(simpleTransitiveVerb)) {
            System.out.println("\n" + StringUtils.join(words));
            System.out.println(targetDependency.getCategory() + "\t" + targetDependency.getArgNumber());
            // Pat* built a robot -> Who built a robot ?
            if (targetSlotId == 1) {
                String q1;
                if (slotToWord.containsKey(2)) {
                    q1 = "Who " + words.get(predicateIndex) + " " + words.get(slotToWord.get(2)) + "?";
                } else {
                    q1 = "Who " + words.get(predicateIndex) + " something?";
                }
                System.out.println(q1);
                System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
            }
            // Pat built a robot* -> What did Pat build / What was built ?
            else if (targetSlotId == 2) {
                String q1;
                List<Integer> auxChain = VerbHelper.getAuxiliaryChain(words, categories, predicateIndex);
                if (VerbHelper.isPassive(words, categories, predicateIndex)) {
                    q1 = "What";
                    for (int aux : auxChain) {
                        q1 += " " + words.get(aux);
                    }
                    q1 += " " + words.get(predicateIndex) + "?";
                } else {
                    q1 = "What";
                    if (auxChain.size() == 0) {
                        String[] split = VerbHelper.splitVerb(words, categories, predicateIndex);
                        q1 += " " + split[0] + " someone " + split[1] + "?";
                    } else {
                        for (int aux : auxChain) {
                            q1 += " " + words.get(aux);
                        }
                        q1 += " someone " + words.get(predicateIndex) + "?";
                    }
                }
                System.out.println(q1);
                System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
            } else {
                System.err.println("How did this happen?");
            }
        }
        /* wordIndices.forEach(id -> {
                    if (id == predicateIndex) {
                        System.out.print("[" + words.get(id) + "]\t");
                    } else if (id == targetDependency.getSentencePositionOfArgument()) {
                        System.out.print("*" + wordIdToSlot.get(id) + ":" + words.get(id) + "\t");
                    } else {
                        System.out.print(wordIdToSlot.get(id) + ":" + words.get(id) + "\t");
                    }
                });*/
    }

    // TODO: generate questions from predicted ...
    private static void generateQuestions(ResolvedDependency targetDependency, List<String> words,
                                          Collection<ResolvedDependency> dependencies) {

    }

    public static void main(String[] args) {
        // hacky: Initialize inflection dictionary ..
        VerbHelper.inflectionDictionary = VerbInflectionDictionary.buildFromPropBankTraining();
        Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>>
            mappedDependencies = PropBankAligner.getCcgAndQADependenciesTrain();
        generateFromGoldCCG(mappedDependencies);
    }
}
