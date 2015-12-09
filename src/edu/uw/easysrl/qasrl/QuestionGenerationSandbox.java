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
                if (category.isFunctionInto(Category.valueOf("S|NP"))) // we will deal with adjuncts later ...
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
        Map<Integer, CCGBankDependency> slots = new HashMap<>();
        List<Integer> wordIndices = new ArrayList<>();
        int targetSlotId = targetDependency.getArgNumber();
        int predicateIndex = targetDependency.getSentencePositionOfPredicate();
        wordIndices.add(predicateIndex);
        for (CCGBankDependency dep : dependencies) {
            int thisPredId = dep.getSentencePositionOfPredicate();
            int thisArgId = dep.getSentencePositionOfArgument();
            if (dep.getSentencePositionOfPredicate() == predicateIndex && thisPredId != thisArgId) {
                slots.put(dep.getArgNumber(), dep);
                wordIndices.add(thisArgId);
            }
        }
        assert slots.containsKey(targetSlotId);
        // print skeleton sentence ...
        Collections.sort(wordIndices);
        System.out.println(StringUtils.join(words));
        System.out.println(targetDependency.getCategory() + "\t" + targetDependency.getArgNumber());
        wordIndices.forEach(id -> System.out.print(words.get(id) + "\t"));
        System.out.println();
        System.out.println(goldQA == null ? "[no-qa]" : StringUtils.join(goldQA.getQuestion()) + "?");
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
