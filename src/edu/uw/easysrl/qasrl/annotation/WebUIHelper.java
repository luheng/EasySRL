package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.qasrl.ActiveLearningBySentence;
import edu.uw.easysrl.qasrl.ActiveLearningHistory;
import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.Response;
import edu.uw.easysrl.syntax.evaluation.Results;


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
                + "<!-- Optional theme -->\n"
                + "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css\">\n"
                + "<link rel=\"stylesheet\" href=\"webapp/WEB-INF/style.css\">\n"
                + "<!-- Latest compiled and minified JavaScript -->\n"
                + "<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js\"></script>"
                + "</head>";
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

    public static String printProgressBar(int numAnswered, int numSkipped, int numTotal) {
        int w1 = (int) Math.ceil(1.0 * numAnswered / numTotal);
        int w2 = (int) Math.ceil(1.0 * numSkipped / numTotal);

        return String.format("<span class=\"label label-info\" for=\"progress\">%d annotated. %d skipped. %d remaining.</span>\n",
                        numAnswered, numSkipped, numTotal - numAnswered - numSkipped)
                + "<div class=\"progress\" id=\"progress\">"
                + String.format("<div class=\"progress-bar progress-bar-success\" style=\"width: %d%%\">", w1)
                + String.format("<span class=\"sr-only\">%d Annotated</span>", numAnswered)
                + "</div>"
                + String.format("<div class=\"progress-bar progress-bar-warning\" style=\"width: %d%%\">", w2)
                + String.format("<span class=\"sr-only\">%d Skipped</span>", numSkipped)
                + "</div>"
                + "</div>\n</div>";
    }

    public static String printGoldInfo(final GroupedQuery query, final Response goldResponse) {
        String result = "<button type=\"button\" class=\"btn btn-info\" data-toggle=\"collapse\" data-target=\"#goldinfo\">Sneak Peek Gold</button>"
                + "<div id=\"goldinfo\" class=\"collapse\">";
        result += "<p>[gold]:<br>" + query.getDebuggingInfo(goldResponse).replace("\n", "<br>").replace("\t", "&nbsp&nbsp") + "</p>";
        result += "</div>";
        return result;
    }

    public static String printDebuggingInfo(final ActiveLearningHistory history) {
        String result = "<button type=\"button\" class=\"btn btn-info\" data-toggle=\"collapse\" data-target=\"#debugging\">Debug Last Query</button>"
                        + "<div id=\"debugging\" class=\"collapse\">";
        result += "<p>" + history.printLatestHistory().replace("\n", "<br>").replace("\t", "&nbsp&nbsp") + "</p>";
        result += "</div>";
        return result;
    }

    public static String printSentenceDebuggingInfo(final ActiveLearningHistory history) {
        String result = "<button type=\"button\" class=\"btn btn-info\" data-toggle=\"collapse\" data-target=\"#debugging\">Debug Sentences</button>"
                + "<div id=\"debugsent\" class=\"collapse\">";
        result += "<table class=\"table\">\n<thead>\n<tr>\n" +
                    "<th>SID</th>\n<th>#Q</th>\n<th>Acc.</th>\n<th>1-Best</th>\n<th>Re-Rank</th>\n<th>Oracle</th>\n" +
                    "</tr>\n</thead>\n" + "<tbody>\n";
        for (int sentId : history.sentenceIds) {
            int numAnnotated = history.numQueriesPerSentence.get(sentId);
            int numCorrect = history.numCorrectPerSentence.get(sentId);
            double acc = numAnnotated > 0 ? 100.0 * numCorrect / numAnnotated : .0;
            result += "<tr>\n" +
                    "<td>" + sentId + "</td>\n" +
                    "<td>" + numAnnotated + "</td>\n" +
                    "<td>" + String.format("%d (%.3f%%)", numCorrect, acc) + "</td>\n" +
                    "<td>" + history.oneBestResults.get(sentId).getF1() + "</td>\n" +
                    "<td>" + history.rerankedResults.get(sentId).getF1() + "</td>\n" +
                    "<td>" + history.oracleResults.get(sentId).getF1() + "</td>\n" +
                    "</tr>";
        }
        result += "</tbody></table>\n</div>";
        return result;
    }
}
