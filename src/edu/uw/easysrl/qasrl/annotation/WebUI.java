package edu.uw.easysrl.qasrl.annotation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.uw.easysrl.qasrl.ActiveLearning;
import edu.uw.easysrl.qasrl.GroupedQuery;
import edu.uw.easysrl.qasrl.Response;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class WebUI extends AbstractHandler {
    private final ActiveLearning activeLearning;
    private final int nBest = 10;
    private final int maxNumAnswerOptionsPerQuery = 4;

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        server.setHandler(new WebUI());
        server.start();
        server.join();
    }

    private WebUI() throws IOException {
        activeLearning = new ActiveLearning(nBest);
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
            // TODO: compare with gold simulator.
            query.print(query.getSentence(), response);

            activeLearning.respondToQuery(query, response);
            System.out.println(activeLearning.getRerankedF1());
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

        httpResponse.println("<container><div class=\"row\">\n<div class=\"span12\">");
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
                    String.format("<label><input name=\"UserAnswer\" type=\"radio\" class=\"radio\" value=\"%s\" />%s</label><br/>",
                            optionValue, optionString));
        }
        httpResponse.println(String.format(
                "<label><input name=\"UserAnswer\" type=\"radio\" class=\"radio\" value=\"%s\"/>" +
                "Question is not understandable.</label><br/>",
                qLabel + "_a_" + badQuestionOptionId));
        httpResponse.println(String.format(
                "<label><input name=\"UserAnswer\" type=\"radio\" class=\"radio\" value=\"%s\"/>" +
                "Answer is not listed.</label><br/>",
                qLabel + "_a_" + badQuestionOptionId));
        httpResponse.println(
                "<button class=\"btn btn-primary\" type=\"submit\" value=\"Submit!\">Submit!</button>" +
                "</form>");

        httpResponse.println("</panel>\n");
        httpResponse.println("</div></div></container>\n");
        httpResponse.println("</body>");

        System.out.println("--------");

        // TODO: print debugging info.
    }
}
