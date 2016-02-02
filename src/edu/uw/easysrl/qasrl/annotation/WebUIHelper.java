package edu.uw.easysrl.qasrl.annotation;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by luheng on 2/1/16.
 */
public class WebUIHelper {

    public static String printHTMLHeader() {
        return "<!DOCTYPE html>"
                + "<html lang=en>"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Annotation Prototype</title>"
                + "<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js\"></script>\n"
                + "<!-- Latest compiled and minified CSS -->\n"
                + "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css\">"
                + "\n"
                + "<!-- Optional theme -->\n"
                + "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css\">\n"
                + "\n"
                + "<!-- Latest compiled and minified JavaScript -->\n"
                + "<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js\"></script>"
                + "</head>"
                + "<h1><font face=\"arial\">Annotation Demo</font></h1>\n";
    }

    public static String printInstructions() {
        return "<div class=\"row col-xs-12 col-md-12\"><!-- Instructions -->"
        + "<div class=\"panel panel-default\">"
        + "<div class=\"panel-heading\"> <h3 class=\"panel-title\"> Instructions </h3></div>"
        + "<div class=\"panel-body\"><ul>"
        + "<li>Before answering the question, please read the sentence and make sure you understand the sentence correctly.</li>"
        + "<li>Choose the best answer for the given question. </li>"
        + "</ul></div></div> ";
    }

    public static String getHighlightedSentenceString(List<String> words, int predicateIndex) {
        return IntStream.range(0, words.size())
                .mapToObj(i -> {
                    String w = translateBrackets(words.get(i));
                    return i == predicateIndex ? "<mark>" + w + "</mark>" : w;
                }).collect(Collectors.joining(" "));
    }

    public static String getQuestionString(String question) {
        return question.substring(0, 1).toUpperCase() + question.substring(1) + "?";
    }

    public static String translateBrackets(String word) {
        if (word.equalsIgnoreCase("-LRB-")) {
            word = "(";
        } else if (word.equalsIgnoreCase("-RRB-")) {
            word = ")";
        } else if (word.equalsIgnoreCase("-LCB-")) {
            word = "{";
        } else if (word.equalsIgnoreCase("-RCB-")) {
            word = "}";
        } else if (word.equalsIgnoreCase("-LSB-")) {
            word = "[";
        } else if (word.equalsIgnoreCase("-RSB-")) {
            word = "]";
        }
        return word;
    }

    // TODO: group words such as A - B
}
