package edu.uw.easysrl.qasrl.annotation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

// TODO: put n-best to config
// TODO: add (toggleable) debugging panel

/**
 * Usage: WebUI [port number] [n-best]
 */
public class WebUI extends AbstractHandler {
    private final ActiveLearning activeLearning;
    private ResponseSimulatorGold goldSimulator;
    private List<GroupedQuery> queryHistory;
    private List<Response> responseHistory;
    private List<Response> goldResponseHistory;
    private List<Results> evaluationHistory;
    private final int maxNumAnswerOptionsPerQuery = 4;

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        server.setHandler(new WebUI(Integer.parseInt(args[1])));
        server.start();
        server.join();
    }

    private WebUI(int nBest) throws IOException {
        activeLearning = new ActiveLearning(nBest);
        goldSimulator = new ResponseSimulatorGold(activeLearning.goldParses, new QuestionGenerator());
        queryHistory = new ArrayList<>();
        responseHistory = new ArrayList<>();
        goldResponseHistory = new ArrayList<>();
        evaluationHistory = new ArrayList<>();
    }

    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request,
                       final HttpServletResponse httpResponse) throws IOException, ServletException {
        final String userAnswer = baseRequest.getParameter("UserAnswer");
        if (userAnswer != null) {
            String[] userAnswerInfo = userAnswer.split("_");
            int queryId = Integer.parseInt(userAnswerInfo[1]);
            int optionId = Integer.parseInt(userAnswerInfo[3]);
            GroupedQuery query = activeLearning.getQueryById(queryId);
            Response response =new Response(optionId);

            query.print(query.getSentence(), response);
            activeLearning.respondToQuery(query, response);

            Results rerankResults = activeLearning.getRerankedF1();
            System.out.println(rerankResults);

            // Append to history
            queryHistory.add(query);
            responseHistory.add(response);
            goldResponseHistory.add(goldSimulator.answerQuestion(query));
            evaluationHistory.add(rerankResults);
        }
        httpResponse.setContentType("text/html; charset=utf-8");
        httpResponse.setStatus(HttpServletResponse.SC_OK);
        update(httpResponse.getWriter());
        baseRequest.setHandled(true);
    }

    // @formatter:off

    private void update(final PrintWriter httpResponse) {
        httpResponse.println(WebUIHelper.printHTMLHeader());

        httpResponse.println("<body style=\"padding-left: 50px; padding-right=50px;\">");
        httpResponse.println("<container>\n" + WebUIHelper.printInstructions() + "</container>\n");

        // Get next query.
        GroupedQuery nextQuery = activeLearning.getNextQueryInQueue();
        // Print sentence
        final List<String> words = nextQuery.getSentence();

        httpResponse.println("<container><div class=\"row\">\n<div class=\"span8\">");

        httpResponse.println("<panel panel-default>\n");
        httpResponse.println("<span class=\"label label-primary\">Sentence:</span>");
        httpResponse.println("<p>" + WebUIHelper.getHighlightedSentenceString(words, nextQuery.getPredicateIndex()) + "</p>");
        httpResponse.println("<span class=\"label label-primary\">Question:</span>");
        httpResponse.println("<p>" + nextQuery.getQuestion() + "</p>");

        httpResponse.println("<span class=\"label label-primary\">Answer Options:</span>");
        httpResponse.println("<br><form class=\"form-group\" action=\"\" method=\"get\">");
        final List<GroupedQuery.AnswerOption> options = nextQuery.getAnswerOptions();
        String qLabel = "q_" + nextQuery.getQueryId();

        int badQuestionOptionId = nextQuery.getAnswerOptions().size() - 1;
        for (int i = 0; i < options.size(); i++) {
            GroupedQuery.AnswerOption option = options.get(i);
            if (option.isNAOption()) {
                continue;
            }
            String optionValue = qLabel + "_a_" + i;
            String optionString = option.getAnswer();
            httpResponse.println(
                    String.format("<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\" />%s</label><br/>",
                            optionValue, optionString));
        }
        httpResponse.println(String.format(
                "<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\"/>" +
                "Question is not understandable.</label><br/>",
                qLabel + "_a_" + badQuestionOptionId));
        httpResponse.println(String.format(
                "<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\"/>" +
                "Answer is not listed.</label><br/>",
                qLabel + "_a_" + badQuestionOptionId));
        httpResponse.println(
                "<button class=\"btn btn-primary\" type=\"submit\" value=\"Submit!\">Submit!</button>" +
                "</form>");

        httpResponse.println("</panel>\n</div>\n");

        httpResponse.println("<div class=\"span4\">");
        httpResponse.println(WebUIHelper.printGoldInfo(nextQuery, goldSimulator.answerQuestion(nextQuery)));
        if (queryHistory.size() > 0) {
            int last = queryHistory.size() - 1;
            httpResponse.println(WebUIHelper.printDebuggingInfo(queryHistory.get(last), responseHistory.get(last),
                    goldResponseHistory.get(last), evaluationHistory.get(last)));
        }
        httpResponse.println("</div></div></container>\n");

        httpResponse.println("</body>");
    }
}
