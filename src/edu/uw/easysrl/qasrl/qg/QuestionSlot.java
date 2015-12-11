package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by luheng on 12/10/15.
 */
public abstract class QuestionSlot {
    public int indexInSentence;
    public int argumentNumber;
    public Category predicateCategory;
    public List<String> sentenceWords;
    public Collection<CCGBankDependency> sentenceDependencies;

    public QuestionSlot(int indexInSentence, int argumentNumber, Category predicateCategory,
                        List<String> sentenceWords, Collection<CCGBankDependency> sentenceDependencies) {
        this.indexInSentence = indexInSentence;
        this.argumentNumber = argumentNumber;
        this.predicateCategory = predicateCategory;
        this.sentenceWords = sentenceWords;
        this.sentenceDependencies = sentenceDependencies;
    }

    public static class VerbSlot extends QuestionSlot {
        public List<Integer> auxiliaries;

        public VerbSlot(CCGBankDependency targetDependency, List<Integer> auxiliaries, List<String> sentenceWords,
                        Collection<CCGBankDependency> sentenceDependencies) {
            super(targetDependency.getSentencePositionOfPredicate(), -1, targetDependency.getCategory(),
                    sentenceWords, sentenceDependencies);
            this.auxiliaries = auxiliaries;
        }

        @Override
        public String toString() {
            List<String> res = new ArrayList<>();
            auxiliaries.forEach(aux -> res.add(sentenceWords.get(aux)));
            res.add(sentenceWords.get(indexInSentence));
            return String.format("%s:%s", predicateCategory, StringUtils.join(res));
        }
    }

    public static class ArgumentSlot extends QuestionSlot {
        public Category argumentCategory;
        public boolean hasPreposition;
        public boolean isVerb;

        public ArgumentSlot(int argumentNumber, CCGBankDependency targetDependency, List<String> sentenceWords,
                            Collection<CCGBankDependency> sentenceDependencies) {
            super(targetDependency.getSentencePositionOfArgument(), argumentNumber, targetDependency.getCategory(),
                    sentenceWords, sentenceDependencies);
            argumentCategory = predicateCategory.getArgument(argumentNumber);
            hasPreposition = argumentCategory.isFunctionInto(Category.PP);
            isVerb = argumentCategory.isFunctionInto(Category.valueOf("S\\NP"));
        }

        @Override
        public String toString() {
            return String.format("T%d:%s:%s", argumentNumber, argumentCategory, sentenceWords.get(indexInSentence));
        }
    }
}

