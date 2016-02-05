package edu.uw.easysrl.qasrl.annotation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.plus.servlet.ServletHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;


// TODO: fix identical answer spans ...
// TODO: add option to "jump forward" queries
/**
 * Usage: WebUI [port number] [n-best]
 */
public class WebUI {
    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        nBest = Integer.parseInt(args[1]);

        activeLearning = new ActiveLearning(nBest);
        goldSimulator = new ResponseSimulatorGold(activeLearning.goldParses, new QuestionGenerator());
        queryHistory = new ArrayList<>();
        responseHistory = new ArrayList<>();
        goldResponseHistory = new ArrayList<>();
        evaluationHistory = new ArrayList<>();

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        handler.setResourceBase(System.getProperty("java.io.tmpdir"));
        server.setHandler(handler);

        handler.addServlet(AnnotationServlet.class, "/annotate/*");
        handler.addServlet(LoginServlet.class, "/login/*");

        server.start();
        server.join();
    }

    private static ActiveLearning activeLearning;
    private static ResponseSimulatorGold goldSimulator;
    private static List<GroupedQuery> queryHistory;
    private static List<Response> responseHistory;
    private static List<Response> goldResponseHistory;
    private static List<Results> evaluationHistory;

    private static int nBest = 50;
    private static int maxNumAnswerOptionsPerQuery = 4;
    private static int reorderQueriesEvery = 5;


    public static class LoginServlet extends HttpServlet {
        public void doGet(final HttpServletRequest request, final HttpServletResponse httpResponse)
                throws IOException, ServletException{
            if (request.getParameter("UserName") != null && request.getParameter("UserName").length() > 0) {
                String userName = request.getParameter("UserName");
                System.out.println(userName);
                httpResponse.sendRedirect(httpResponse.encodeRedirectURL("/annotate?UserName=" + userName));
            } else {
                PrintWriter httpWriter = httpResponse.getWriter();
                httpWriter.println(WebUIHelper.printHTMLHeader());
                httpWriter.println("<body style=\"padding: 50px;\">");
                httpWriter.println("<form>\n<div class=\"form-group\">"
                        + "<label for=\"UserName\">Please enter your name here: </label>\n"
                        + "<input type=\"text\" id=\"UserName\" input name=\"UserName\">\n"
                        + "<button type=\"submit\" class=\"btn btn-primary\">Go!</button>\n"
                        + "</div></form>");
                httpWriter.println("</body>");
            }
        }
    }

    public static class AnnotationServlet extends HttpServlet {
        public AnnotationServlet() {
        }

        @Override
        public void doGet(final HttpServletRequest request, final HttpServletResponse httpResponse)
                throws IOException, ServletException {
            if (request.getParameter("UserName") == null || request.getParameter("UserName").length() == 0) {
                httpResponse.sendRedirect(httpResponse.encodeRedirectURL("/login"));
                return;
            }
            if (request.getParameter("SwitchQuestion") != null) {
                for (int i = 1; i < 10; i++) {
                    activeLearning.getNextQueryInQueue();
                }
            }
            final String userName = request.getParameter("UserName");
            final String userAnswer = request.getParameter("UserAnswer");
            if (userAnswer != null) {
                String[] userAnswerInfo = userAnswer.split("_");
                int queryId = Integer.parseInt(userAnswerInfo[1]);
                int optionId = Integer.parseInt(userAnswerInfo[3]);
                GroupedQuery query = activeLearning.getQueryById(queryId);
                Response response = new Response(optionId);

                query.print(query.getSentence(), response);
                activeLearning.respondToQuery(query, response);

                Results rerankResults = activeLearning.getRerankedF1();
                System.out.println(rerankResults);

                // Append to history
                queryHistory.add(query);
                responseHistory.add(response);
                goldResponseHistory.add(goldSimulator.answerQuestion(query));
                evaluationHistory.add(rerankResults);
                if (queryHistory.size() % reorderQueriesEvery == 0) {
                    activeLearning.refreshQueryList();
                }
            }
            httpResponse.setContentType("text/html; charset=utf-8");
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            update(userName, httpResponse.getWriter());
        }

        private void update(final String userName, final PrintWriter httpWriter) {
            httpWriter.println(WebUIHelper.printHTMLHeader());
            httpWriter.println("<h1><font face=\"arial\">Annotation Demo</font></h1>\n");

            httpWriter.println("<body style=\"padding-left: 50px; padding-right=50px;\">");
            httpWriter.println("<container>\n" + WebUIHelper.printInstructions() + "</container>\n");

            // Get next query.
            GroupedQuery nextQuery = activeLearning.getNextQueryInQueue();
            // Print sentence
            final List<String> words = nextQuery.getSentence();

            httpWriter.println("<container><div class=\"row\">\n<div class=\"col-md-12\">");

            httpWriter.println("<panel panel-default>\n");
            httpWriter.println("<span class=\"label label-primary\">Sentence:</span>");
            httpWriter.println(WebUIHelper.getHighlightedSentenceString(words, nextQuery.getPredicateIndex()) + "<br>");
            httpWriter.println("<span class=\"label label-primary\">Question:</span>");
            httpWriter.println(nextQuery.getQuestion() + "<br>");

            httpWriter.println("<span class=\"label label-primary\">Answer Options:</span>");
            httpWriter.println("<br><form class=\"form-group\" action=\"\" method=\"get\">");
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
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
                httpWriter.println(
                        String.format("<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\" />&nbsp %s</label><br/>",
                                optionValue, WebUIHelper.translateBrackets(optionString)));
            }
            httpWriter.println(String.format(
                    "<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\"/>" +
                            "&nbsp Question is not understandable.</label><br/>",
                    qLabel + "_a_" + badQuestionOptionId));
            httpWriter.println(String.format(
                    "<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\"/>" +
                            "&nbsp Answer is not listed.</label><br/>",
                    qLabel + "_a_" + badQuestionOptionId));
            httpWriter.println(
                    "<button class=\"btn btn-primary\" type=\"submit\" value=\"Submit!\">Submit!</button>" +
                            "</form>");

            httpWriter.println("</panel>\n");

            // "Skip 10" button
            httpWriter.println("<br><form class=\"form-group\" action=\"\" method=\"get\">");
            // Add user name parameter ..
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            httpWriter.println("<button class=\"btn btn-primary\" input name=\"SwitchQuestion\" type=\"submit\" value=\"Skip10\">Skip 10 questions.</button>");
            httpWriter.println("</form>");

            // Gold info and debugging info.
            httpWriter.println(WebUIHelper.printGoldInfo(nextQuery, goldSimulator.answerQuestion(nextQuery)));
            if (queryHistory.size() > 0) {
                int last = queryHistory.size() - 1;
                httpWriter.println(WebUIHelper.printDebuggingInfo(queryHistory.get(last), responseHistory.get(last),
                        goldResponseHistory.get(last), evaluationHistory.get(last)));
            }

            httpWriter.println("</div></div></container>\n");
            httpWriter.println("</body>");
        }
    }
}
