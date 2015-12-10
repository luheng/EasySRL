package edu.uw.easysrl.qasrl;

import com.sun.tools.javac.comp.Resolve;
import edu.stanford.nlp.util.StringUtils;
import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import org.eclipse.jetty.util.StringUtil;


import java.util.*;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerationSandbox {

    public static void generateFromGoldCCG(
            Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>> alignedDependencies) {
        for (int sentIdx : alignedDependencies.keySet()) {
            List<AlignedDependency<CCGBankDependency, QADependency>> deps = alignedDependencies.get(sentIdx);
            if (deps == null) {
                continue;
            }
            Sentence sentence = deps.get(0).sentence;
            List<String> words = sentence.getWords();
            Collection<CCGBankDependency> ccgDeps = sentence.getCCGBankDependencyParse().getDependencies();
            for (AlignedDependency<CCGBankDependency, QADependency> dep : deps) {
                CCGBankDependency ccgDep = dep.dependency1;
                if (ccgDep == null) {
                    continue;
                }
                Category category = ccgDep.getCategory();
                if (category.isFunctionInto(Category.valueOf("(S\\NP)/NP"))) // we will deal with adjuncts later ...
                    // || category.isFunctionInto(Category.valueOf("S|S"))) {
                    generateQuestions(ccgDep, words, ccgDeps, dep.dependency2);
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

    private static void generateQuestions(CCGBankDependency targetDependency,
                                          List<String> words,
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

        if (targetDependency.getCategory().isFunctionInto(Category.valueOf("(S[dcl]\\NP)/NP"))) {
            if (targetSlotId == 1) {
                System.out.println("\n" + StringUtils.join(words));
                System.out.println(targetDependency.getCategory() + "\t" + targetDependency.getArgNumber());

                String q1 = "";
                if (slotToWord.containsKey(2)) {
                    q1 = "Who/What " + words.get(predicateIndex) + " " + words.get(slotToWord.get(2)) + "?";
                } else {
                    q1 = "Who/What " + words.get(predicateIndex) + " something?";
                }
                System.out.println(q1);
                System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
            } else {
                
            }
        }
        /*
                wordIndices.forEach(id -> {
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
        Map<Integer, List<AlignedDependency<CCGBankDependency, QADependency>>>
            mappedDependencies = PropBankAligner.getCcgAndQADependenciesTrain();
        generateFromGoldCCG(mappedDependencies);
    }
}
