package edu.uw.easysrl.qasrl.annotation;

/**
 * Created by luheng on 2/1/16.
 */
public class WebUIHelper {

    public static String printInstructions() {
        return "<div class=\"row col-xs-12 col-md-12\"><!-- Instructions -->"
        + "<div class=\"panel panel-primary\">"
        + "<div class=\"panel-heading\"><strong>Instructions</strong></div>"
        + "<div class=\"panel-body\"><p>Instructions</p><ul>"
        + "<li>Before answering the question, please read the sentence and make sure you understand the sentence correctly.</li>"
        + "<li>Choose the best answer for the given question. </li>"
        + "</ul></div></div> ";
    }
}
