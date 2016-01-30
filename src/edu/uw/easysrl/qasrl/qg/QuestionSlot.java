package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;

import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luheng on 12/10/15.
 */
public class QuestionSlot {
    public static class PredicateSlot {
        public Category category;
        public int indexInSentence;
        public List<Integer> auxiliaries;

        public PredicateSlot(int indexInSentence, List<Integer> auxiliaries, Category category) {
            this.indexInSentence = indexInSentence;
            this.category = category;
            this.auxiliaries = auxiliaries;
        }

        public String toString(List<String> sentenceWords) {
            List<String> res = new ArrayList<>();
            auxiliaries.forEach(aux -> res.add(sentenceWords.get(aux)));
            res.add(sentenceWords.get(indexInSentence));
            return String.format("%s:%s", category, StringUtils.join(res));
        }
    }

    public static class ArgumentSlot {
        public Category category;
        public int indexInSentence;
        public int argumentNumber;
        public String preposition;

        public ArgumentSlot(int indexInSentence, int argumentNumber, Category argumentCategory, String preposition) {
            this.indexInSentence = indexInSentence;
            this.argumentNumber = argumentNumber;
            this.category = argumentCategory;
            //hasPreposition = argumentCategory.isFunctionInto(Category.PP);
            this.preposition = preposition;
        }

        public String toString(List<String> sentenceWords) {
            if (!preposition.isEmpty()) {
                return String.format("T%d:%s:%s", argumentNumber, category,
                                     preposition + " " + sentenceWords.get(indexInSentence));
            }
            return String.format("T%d:%s:%s", argumentNumber, category, sentenceWords.get(indexInSentence));
        }
    }

    public static class UnrealizedArgumentSlot extends ArgumentSlot {
        public UnrealizedArgumentSlot(int argumentNumber, Category argumentCategory) {
            super(-1, argumentNumber, argumentCategory, "");
        }

        @Override
        public String toString(List<String> sentenceWords) {
            return String.format("T%d:%s:__", argumentNumber, category);
        }
    }
}
