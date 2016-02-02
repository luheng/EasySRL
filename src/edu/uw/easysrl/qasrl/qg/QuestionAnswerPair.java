package edu.uw.easysrl.qasrl.qg;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class QuestionAnswerPair {
    public final List<String> questionWords;
    public final List<String> answerWords;

    // here is the punctuation we want to avoid spaces before,
    // and that we don't want at the end of the question or answer.
    // For now, those are the same.
    private static final String trimPunctuation = ",.:;!?";
    private static Set<String> noSpaceWords = new HashSet<String>();
    static {
        noSpaceWords.add(".");
        noSpaceWords.add(",");
        noSpaceWords.add("\'");
        noSpaceWords.add("!");
        noSpaceWords.add("?");
        noSpaceWords.add(";");
        noSpaceWords.add(":");
        noSpaceWords.add("n\'t");
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

    private static String renderString(List<String> words) {
        StringBuilder result = new StringBuilder();
        if(words.size() == 0) return "";
        for(String word : words) {
            if(!noSpaceWords.contains(""+word.charAt(0))) {
                result.append(" ");
            }
            result.append(word);
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
