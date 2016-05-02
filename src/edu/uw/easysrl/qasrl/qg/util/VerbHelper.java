package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 12/9/15.
 */
public class VerbHelper {
    private static final VerbInflectionDictionary s_inflectionDictionary = VerbInflectionDictionary.buildFromPropBankTraining();
    public static final VerbHelper trainingSetVerbHelper = new VerbHelper(s_inflectionDictionary);

    private static final ImmutableSet<String> doVerbs = new ImmutableSet.Builder<String>()
        .add("do").add("does").add("did").add("done")
        .build();

    private static ImmutableSet<String> beVerbs = new ImmutableSet.Builder<String>()
        .add("be").add("being").add("been")
        .add("am").add("'m")
        .add("is").add("'s").add("ai") // as in ai n't
        .add("are").add("'re")
        .add("was").add("were")
        .build();

    private static final ImmutableSet<String> willVerbs = new ImmutableSet.Builder<String>()
        .add("will").add("'ll").add("wo") // as in wo n't
        .build();

    private static final ImmutableSet<String> haveVerbs = new ImmutableSet.Builder<String>()
        .add("have").add("having").add("'ve").add("has").add("had").add("'d")
        .build();

    private static final ImmutableSet<String> modalVerbs = new ImmutableSet.Builder<String>()
        .add("would").add("'d")
        .add("can").add("ca")
        .add("could")
        .add("may").add("might").add("must")
        .add("shall").add("should").add("ought")
        .build();

    private static final ImmutableSet<String> auxiliaryVerbs = new ImmutableSet.Builder<String>()
        .addAll(doVerbs).addAll(beVerbs).addAll(willVerbs).addAll(haveVerbs).addAll(modalVerbs)
        .build();

    private VerbInflectionDictionary inflectionDictionary;

    public VerbHelper(VerbInflectionDictionary inflectionDictionary) {
        this.inflectionDictionary = inflectionDictionary;
    }

    public Optional<String> getCopulaNegation(List<String> words, List<Category> categories, int index) {
        for(int i = index + 1; i < words.size(); i++) {
            if(isNegationWord(words, categories, i)) {
                return Optional.of(words.get(i));
            } else if(!isModifierWord(words, categories, i)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
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
        return index < words.size() && auxiliaryVerbs.contains(words.get(index).toLowerCase()) &&
                categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)"));
    }

    public boolean isNegationWord(List<String> words, List<Category> categories, int index) {
        if(index < words.size()) {
            String word = words.get(index);
            return word.equalsIgnoreCase("n\'t") || word.equalsIgnoreCase("not");
        }
        return false;
    }

    public static boolean isNegationWord(String word) {
        return word.equalsIgnoreCase("n\'t") || word.equalsIgnoreCase("not");
    }

    public boolean isModifierWord(List<String> words, List<Category> categories, int index) {
        return index < words.size() && categories.get(index).isFunctionInto(Category.ADVERB);
    }

    public static boolean isCopulaVerb(String word) {
        return beVerbs.contains(word.toLowerCase());
    }

    /**
     * approved -> List { did, approve }
     */
    public Optional<String[]> getAuxiliaryAndVerbStrings(List<String> words, List<Category> categories, int index) {
        String verbStr = words.get(index).toLowerCase();
        String[] infl = inflectionDictionary.getBestInflections(verbStr.toLowerCase());
        if (infl == null) {
            // System.err.println("Can't find inflections for: " + words.get(index) + " " + categories.get(index));
            return Optional.empty();
        }
        // eat
        if (verbStr.equals(infl[0])) {
            return Optional.of(new String[] {"would", infl[0]});
        }
        // eats
        if (verbStr.equals(infl[1])) {
            return Optional.of(new String[] {"does", infl[0]});
        }
        // eating
        if (verbStr.equals(infl[2])) {
            return Optional.of(new String[] {"would", "be " + infl[2]});
        }
        // ate
        if (verbStr.equals(infl[3])) {
            return Optional.of(new String[] {"did", infl[0]});
        }
        // eaten
        return Optional.of(new String[] {"have", infl[4]});
    }

    public boolean hasInflectedForms(String word) {
        return inflectionDictionary.getBestInflections(word.toLowerCase()) != null;
    }

    public Optional<String> getUninflected(String word) {
        if(isCopulaVerb(word)) {
            return Optional.of("be");
        }
        Optional<String[]> inflections = Optional.ofNullable(inflectionDictionary.getBestInflections(word.toLowerCase()));
        return inflections.map(infl -> infl[0]);
    }

    public boolean isUninflected(List<String> words, List<Category> categories, int index) {
        String verbStr = words.get(index).toLowerCase();
        String[] infl = inflectionDictionary.getBestInflections(verbStr.toLowerCase());
        return infl != null && verbStr.equals(infl[0]);
    }

    public static boolean isModal(String verb) {
        return modalVerbs.contains(verb.toLowerCase());
    }

    public static String getNormalizedModal(String word) {
        if(word.equalsIgnoreCase("ca")) {
            return "can";
        } else {
            return word;
        }
    }

    public static boolean isFutureTense(String verb) {
        return willVerbs.contains(verb.toLowerCase());
    }

    public static boolean isPastTense(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> verb.equalsIgnoreCase(infl[3]))
            .orElse(false);
    }

    public static String getPastTense(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> infl[3])
            .orElse(verb + "[DBG]");
    }

    // if the number of the subject is not known, allows for the "do" form (as opposed to "does" form) long as subj is third person
    public static boolean isPresentTense(String verb, Noun subject) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> verb.equalsIgnoreCase(infl[1]) ||
                 (!(subject.getNumber().map(num -> num == Noun.Number.SINGULAR).orElse(true) && subject.getPerson() == Noun.Person.THIRD) &&
                  verb.equalsIgnoreCase(infl[0])))
            .orElse(false);
    }

    public static String getPresentTense(String verb, Noun subject) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> ((subject.getNumber().map(num -> num == Noun.Number.SINGULAR).orElse(true)) &&
                          subject.getPerson() == Noun.Person.THIRD) ? infl[1] : infl[0])
            .orElse(verb + "[DBG]");
    }

    // aka progressive form
    public static boolean isPresentParticiple(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> verb.equalsIgnoreCase(infl[2]))
            .orElse(false);
    }

    public static String getPresentParticiple(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> infl[2])
            .orElse(verb + "[DBG]");
    }

    // aka participle
    public static boolean isPastParticiple(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> verb.equalsIgnoreCase(infl[4]))
            .orElse(false);
    }

    public static String getPastParticiple(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> infl[4])
            .orElse(verb + "[DBG]");
    }

    public static boolean isStem(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> verb.equalsIgnoreCase(infl[0]))
            .orElse(true); // assume it's the stem if we don't know
    }

    public static String getStem(String verb) {
        return Optional.ofNullable(s_inflectionDictionary.getBestInflections(verb.toLowerCase()))
            .map(infl -> infl[0])
            .orElse(verb + "[DBG]");
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
                        System.out.printWithGoldDependency((j == 0 ? "" : " ") + (j == i ? "*" : "") + words.get(j));
                    }
                    System.out.println();
                    aux.forEach(id -> System.out.printWithGoldDependency(words.get(id) + " "));
                    System.out.println();
                    aux.forEach(id -> System.out.printWithGoldDependency(categories.get(id) + " "));
                    System.out.println("\n");
                }
                if (isAuxiliaryVerb(sentence.getWords(), sentence.getLexicalCategories(), i)) {
                    System.out.println();
                    for (int j = 0; j < words.size(); j++) {
                        System.out.printWithGoldDependency((j == 0 ? "" : " ") + (j == i ? "*" : "") + words.get(j));
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
