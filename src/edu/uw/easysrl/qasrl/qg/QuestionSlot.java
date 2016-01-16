package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.syntax.grammar.Category;

import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luheng on 12/10/15.
 */
public abstract class QuestionSlot {
    public Category category;
    public int indexInSentence;
    public int argumentNumber;

    public QuestionSlot(int indexInSentence, int argumentNumber, Category category) {
        this.indexInSentence = indexInSentence;
        this.argumentNumber = argumentNumber;
        this.category = category;
    }

    public abstract String toString(List<String> sentenceWords);

    public static class VerbSlot extends QuestionSlot {
        public List<Integer> auxiliaries;
        public boolean hasParticle;
        public int particleIndex;

        public VerbSlot(int indexInSentence, List<Integer> auxiliaries, Category category) {
            super(indexInSentence, -1, category);
            this.auxiliaries = auxiliaries;
            hasParticle = false;
            particleIndex = -1;
        }

        public VerbSlot(int verbIndex, int particleIndex, List<Integer> auxiliaries, Category category) {
            super(verbIndex, -1, category);
            this.auxiliaries = auxiliaries;
            this.hasParticle = true;
            this.particleIndex = particleIndex;
        }

        @Override
        public String toString(List<String> sentenceWords) {
            List<String> res = new ArrayList<>();
            auxiliaries.forEach(aux -> res.add(sentenceWords.get(aux)));
            res.add(sentenceWords.get(indexInSentence));
            if (hasParticle) {
                res.add(sentenceWords.get(particleIndex));
            }
            return String.format("%s:%s", category, StringUtils.join(res));
        }
    }

    public static class ArgumentSlot extends QuestionSlot {
        public String preposition;
        public boolean isVerb;

        public ArgumentSlot(int indexInSentence, int argumentNumber, Category argumentCategory, String preposition) {
            super(indexInSentence, argumentNumber, argumentCategory);
            //hasPreposition = argumentCategory.isFunctionInto(Category.PP);
            this.preposition = preposition;
            isVerb = argumentCategory.isFunctionInto(Category.valueOf("S\\NP"));
        }

        @Override
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

