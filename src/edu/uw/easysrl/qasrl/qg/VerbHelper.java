package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 12/9/15.
 */
public class VerbHelper {
    private static final String[] enAuxiliaryVerbs = {
            "be",
            "being",
            "am",
            "\'m",
            "is",
            "\'s",
            "are",
            "\'re",
            "was",
            "were",
            "been",
            "will",
            "\'ll",
            "would",
            "\'d",
            "wo", /* this is part of wo n't ... */
            "do",
            "does",
            "did",
            "done",
            "have",
            "having",
            "\'ve",
            "has",
            "had",
            "ca", /* in ca n't */
            "can",
            "could",
            "may",
            "might",
            "must",
            "need",
            "shall",
            "should",
            "ought",
            "going",
            "to"
    };

    private static final String[] enCopulaVerbs = {
            "be", "being", "am", "\'m", "is",
            "\'s", "are", "\'re", "was", "were", "been", "being"
    };

    private static final Set<String> enAuxiliaryVerbSet;
    static {
        enAuxiliaryVerbSet = new HashSet<>();
        Collections.addAll(enAuxiliaryVerbSet, enAuxiliaryVerbs);
    }

    private static final Set<String> enCopulaVerbSet;
    static {
        enCopulaVerbSet = new HashSet<>();
        Collections.addAll(enCopulaVerbSet, enCopulaVerbs);
    }

    private VerbInflectionDictionary inflectionDictionary = null;

    public VerbHelper(VerbInflectionDictionary inflectionDictionary) {
        this.inflectionDictionary = inflectionDictionary;
    }

    public List<Integer> getAuxiliaryChain(List<String> words, List<Category> categories, int index) {
        List<Integer> auxliaryIndices = new ArrayList<>();
        for (int i = index - 1; i >= 0; i--) {
            boolean isAux = isAuxiliaryVerb(words, categories, i);
            boolean isNeg = isNegationWord(words, categories, i);
            boolean isAdv = isModifierWord(words, categories, i);
            if (!isAux && !isNeg && !isAdv) {
                break;
            }
            if (isAux || isNeg) {
                auxliaryIndices.add(i);
            }
        }
        if (auxliaryIndices.size() > 0) {
            Collections.sort(auxliaryIndices);
        }
        return auxliaryIndices;
    }

    public boolean isAuxiliaryVerb(List<String> words, List<Category> categories, int index) {
        return index < words.size() && enAuxiliaryVerbSet.contains(words.get(index).toLowerCase()) &&
                categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)"));
    }

    public boolean isNegationWord(List<String> words, List<Category> categories, int index) {
        if(index < words.size()) {
            String word = words.get(index);
            return word.equalsIgnoreCase("n\'t") || word.equalsIgnoreCase("not");
        }
        return false;
    }

    public boolean isModifierWord(List<String> words, List<Category> categories, int index) {
        return index < words.size() && categories.get(index).isFunctionInto(Category.ADVERB);
    }

    public boolean isCopula(List<String> words, int index) {
        return enCopulaVerbSet.contains(words.get(index).toLowerCase());
    }

    /**
     * approved -> List { did, approve }
     */
    public String[] getAuxiliaryAndVerbStrings(List<String> words, List<Category> categories, int index) {
        String verbStr = words.get(index).toLowerCase();
        String[] infl = inflectionDictionary.getBestInflections(verbStr.toLowerCase());
        if (infl == null) {
            System.err.println("Can't find inflections for: " + words.get(index));
            return new String[] {"", verbStr};
        }
        // build
        if (verbStr.equals(infl[0])) {
            return new String[] {"would", infl[0]};
        }
        // builds
        if (verbStr.equals(infl[1])) {
            return new String[] {"does", infl[0]};
        }
        // building
        if (verbStr.equals(infl[2])) {
            return new String[] {"would", "be " + infl[2]};
        }
        // built
        if (verbStr.equals(infl[3])) {
            return new String[] {"did", infl[0]};
        }
        // built (pt)
        return new String[] {"have", infl[4]};
    }

    public boolean isUninflected(List<String> words, List<Category> categories, int index) {
        String verbStr = words.get(index).toLowerCase();
        String[] infl = inflectionDictionary.getBestInflections(verbStr.toLowerCase());
        return infl != null && verbStr.equals(infl[0]);
    }

    /**
     * Analysis code.
     */
    private static void seh() {
        Iterator<ParallelCorpusReader.Sentence> sentenceIterator = null;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCorpus(false);
        } catch (IOException e) {
        }

        while (sentenceIterator.hasNext()) {
            ParallelCorpusReader.Sentence sentence = sentenceIterator.next();
            List<String> words = sentence.getWords();
            List<Category> categories = sentence.getLexicalCategories();
            for (int i = 0; i < words.size(); i++) {
                /*
                if (isVerb(words, categories, i) { // && isPassive(words, categories, i)) {
                    List<Integer> aux = getAuxiliaryChain(words, categories, i);
                    aux.add(i);
                    for (int j = 0; j < words.size(); j++) {
                        System.out.print((j == 0 ? "" : " ") + (j == i ? "*" : "") + words.get(j));
                    }
                    System.out.println();
                    aux.forEach(id -> System.out.print(words.get(id) + " "));
                    System.out.println();
                    aux.forEach(id -> System.out.print(categories.get(id) + " "));
                    System.out.println("\n");
                }
                if (isAuxiliaryVerb(sentence.getWords(), sentence.getLexicalCategories(), i)) {
                    System.out.println();
                    for (int j = 0; j < words.size(); j++) {
                        System.out.print((j == 0 ? "" : " ") + (j == i ? "*" : "") + words.get(j));
                    }
                    System.out.println();
                    System.out.println(sentence.getWords().get(i) + "\t" + sentence.getLexicalCategories().get(i));
                }
                */
                Category category = sentence.getLexicalCategories().get(i);
                if (category.isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")) &&
                        !category.isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)|NP"))) {
                    System.out.println(sentence.getWords().get(i) + "\t" + category);
                }
            }
        }
    }

    public static void main(String[] args) {
        seh();
    }
}
