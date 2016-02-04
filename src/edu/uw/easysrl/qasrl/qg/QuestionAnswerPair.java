package edu.uw.easysrl.qasrl.qg;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

public class QuestionAnswerPair {
    public final List<String> questionWords;
    public final List<String> answerWords;

    // here is the punctuation we want to avoid spaces before,
    // and that we don't want at the end of the question or answer.
    // For now, those are the same.
    private static final String trimPunctuation = " ,.:;!?";
    private static Set<String> noSpaceBefore = new HashSet<String>();
    private static Set<String> noSpaceAfter = new HashSet<String>();
    static {
        noSpaceBefore.add(".");
        noSpaceBefore.add(",");
        noSpaceBefore.add("\'");
        noSpaceBefore.add("!");
        noSpaceBefore.add("?");
        noSpaceBefore.add(";");
        noSpaceBefore.add(":");
        noSpaceBefore.add("n\'t");
        noSpaceBefore.add("%");

        noSpaceAfter.add("$");
    }


    public QuestionAnswerPair(List<String> questionWords, List<String> answerWords) {
        this.questionWords = questionWords;
        this.answerWords = answerWords;
    }

    public String renderQuestion() {
        String str = renderString(questionWords);
        if(!str.isEmpty()) return renderString(questionWords) + "?";
        else return str;
    }

    public String renderAnswer() {
        return renderString(answerWords);
    }

    public static String renderString(List<String> words) {
        StringBuilder result = new StringBuilder();
        if(words.size() == 0) return "";
        Optional<String> prevWord = Optional.empty();
        for(String word : words) {
            boolean noSpace = (prevWord.isPresent() && noSpaceAfter.contains(prevWord.get())) || noSpaceBefore.contains(word);
            if(!noSpace) {
                result.append(" ");
            }
            result.append(word);
            prevWord = Optional.of(word);
        }
        result.deleteCharAt(0);
        while(result.length() > 0 &&
              trimPunctuation.indexOf(result.charAt(result.length() - 1)) >= 0) {
            result.deleteCharAt(result.length() - 1);
        }
        result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
        return result.toString();
    }
}
