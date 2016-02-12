package edu.uw.easysrl.qasrl.annotation;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * Usage: WebUI [port number] [n-best]
 */
public class WebUI {
    // Parameters.
    private static int nBest = 50;
    private static int maxNumAnswerOptionsPerQuery = 4;
    private static int reorderQueriesEvery = 5;

    private static ActiveLearning baseLearner;
    private static ResponseSimulatorGold goldSimulator;
    private static Map<String, ActiveLearning> activeLearningMap;
    private static Map<String, ActiveLearningHistory> activeLearningHistoryMap;
    private static Map<String, BufferedWriter> annotationFileWriterMap;
    private static Map<String, String> annotationFileNameMap;

    private static final int maxNumberOfUsers = 100;

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");
    private static final String annotationPath  = "./webapp/annotation_files/";

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        nBest = Integer.parseInt(args[1]);

        baseLearner = new ActiveLearning(nBest);
        goldSimulator = new ResponseSimulatorGold(baseLearner.goldParses, new QuestionGenerator());
        activeLearningMap = new HashMap<>();
        activeLearningHistoryMap = new HashMap<>();
        annotationFileWriterMap = new HashMap<>();
        annotationFileNameMap = new HashMap<>();

        // Servlet Context Handler
        ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletHandler.setContextPath("/");
        servletHandler.setResourceBase(System.getProperty("java.io.tmpdir"));
        servletHandler.addServlet(AnnotationServlet.class, "/annotate/*");
        servletHandler.addServlet(LoginServlet.class, "/login/*");

        // Resource handler wrapped with context.
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setResourceBase(annotationPath);
        ContextHandler resourceContextHandler = new ContextHandler("/annotation_files");
        resourceContextHandler.setHandler(resourceHandler);

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(new Handler[] {servletHandler, resourceContextHandler});

        server.setHandler(handlerCollection);
        server.start();
        server.join();
    }


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

        @Override
        public void doGet(final HttpServletRequest request, final HttpServletResponse httpResponse)
                throws IOException, ServletException {
            if (request.getParameter("UserName") == null || request.getParameter("UserName").length() == 0) {
                httpResponse.sendRedirect(httpResponse.encodeRedirectURL("/login"));
                return;
            }
            final String userName = request.getParameter("UserName");
            // Add new user.
            if (!activeLearningMap.containsKey(userName)) {
                if (activeLearningMap.size() >= maxNumberOfUsers) {
                    // TODO: limit number of users.
                }
                activeLearningMap.put(userName, new ActiveLearning(baseLearner));
                activeLearningHistoryMap.put(userName, new ActiveLearningHistory());
                String userFileName = userName + "_" + dateFormat.format(new Date()) + ".txt";
                annotationFileWriterMap.put(userName, new BufferedWriter(new FileWriter(
                        new File(annotationPath + userFileName))));
                annotationFileNameMap.put(userName, userFileName);
            }

            final ActiveLearning activeLearning = activeLearningMap.get(userName);
            final ActiveLearningHistory history = activeLearningHistoryMap.get(userName);
            final BufferedWriter fileWriter = annotationFileWriterMap.get(userName);

            if (request.getParameter("SwitchQuestion") != null) {
                for (int i = 1; i < 10; i++) {
                    activeLearning.getNextQueryInQueue();
                }
            }

            final String userAnswer = request.getParameter("UserAnswer");
            if (userAnswer != null) {
                String[] userAnswerInfo = userAnswer.split("_");
                int queryId = Integer.parseInt(userAnswerInfo[1]);
                int optionId = Integer.parseInt(userAnswerInfo[3]);
                GroupedQuery query = activeLearning.getQueryById(queryId);
                // Create user response objective.
                Response response = new Response(optionId);
                if (request.getParameter("Comment") != null) {
                    response.debugInfo = request.getParameter("Comment").trim();
                }
                // Get gold response for debugging.
                Response goldResponse = goldSimulator.answerQuestion(query);
                activeLearning.respondToQuery(query, response);
                Results rerankResults = activeLearning.getRerankedF1();
                // Append to history
                history.add(query, response, goldResponse, rerankResults, rerankResults /* TODO: fix later */ );
                if (history.size() % reorderQueriesEvery == 0) {
                    activeLearning.refreshQueryList();
                }
                // Print latest history.
                final String latestHistoryStr = history.printLatestHistory();
                System.out.println(latestHistoryStr);
                fileWriter.write(latestHistoryStr + "\n");
                fileWriter.flush();
            }
            httpResponse.setContentType("text/html; charset=utf-8");
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            update(userName, httpResponse.getWriter());
        }

        private void update(final String userName, final PrintWriter httpWriter) {
            final ActiveLearning activeLearning = activeLearningMap.get(userName);
            final ActiveLearningHistory history = activeLearningHistoryMap.get(userName);

            httpWriter.println(WebUIHelper.printHTMLHeader());
            httpWriter.println("<h1><font face=\"arial\">Annotation Demo</font></h1>\n");

            httpWriter.println("<body style=\"padding-left: 80px; padding-right=80px;\">");
            httpWriter.println("<container>\n" + WebUIHelper.printInstructions() + "</container>\n");

            // Print progress bar.
            int numAnswered = history.size();
            int numTotal = activeLearning.getTotalNumberOfQueries();
            int numSkipped = numTotal - activeLearning.getNumberOfRemainingQueries() - numAnswered;
            httpWriter.println(WebUIHelper.printProgressBar(numAnswered, numSkipped, numTotal));

            // Get next query.
            GroupedQuery nextQuery = activeLearning.getNextQueryInQueue();
            // Print sentence
            final List<String> words = nextQuery.getSentence();

            httpWriter.println("<container><div class=\"row\">\n");
            // httpWriter.println("<div class=\"col-md-2\"> </div>");
            httpWriter.println("<div class=\"col-md-12\">");
            // Annotation margin.
            httpWriter.println("<panel panel-default>\n");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Sentence\">Sentence:</span></h5>");
            httpWriter.println("<div id=\"Sentence\"> " + TextGenerationHelper.renderHTMLString(words, nextQuery.getPredicateIndex()) + " </div>");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Question\">Question:</span><br></h5>");
            httpWriter.println("<div id=\"Question\"> " + nextQuery.getQuestion() + " </div>");

            httpWriter.println("<h5><span class=\"label label-primary\" for=\"AnswerOptions\">Answer Options:</span></h5>");
            httpWriter.println("<form class=\"form-group\" id=\"AnswerOptions\" action=\"\" method=\"get\">");
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            final List<GroupedQuery.AnswerOption> options = nextQuery.getAnswerOptions();
            String qLabel = "q_" + nextQuery.getQueryId();

            int badQuestionOptionId = -1, unlistedAnswerId = -1;
            for (int i = 0; i < options.size(); i++) {
                GroupedQuery.AnswerOption option = options.get(i);
                if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                    badQuestionOptionId = i;
                    continue;
                }
                if (GroupedQuery.NoAnswerOption.class.isInstance(option)) {
                    unlistedAnswerId = i;
                    continue;
                }
                String optionValue = qLabel + "_a_" + i;
                String optionString = option.getAnswer();
                httpWriter.println(String.format("<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\" />&nbsp %s</label><br/>",
                        optionValue, optionString));
            }
            if (badQuestionOptionId >= 0) {
                httpWriter.println(String.format("<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\"/> &nbsp Question is not understandable.</label><br/>",
                        qLabel + "_a_" + badQuestionOptionId));
            }
            if (unlistedAnswerId >= 0) {
                httpWriter.println(String.format("<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\"/> &nbsp Answer is not listed.</label><br/>",
                        qLabel + "_a_" + unlistedAnswerId));
            }

            // Comment box.
            httpWriter.println("<br><span class=\"label label-primary\" for=\"Comment\">Comments (if any):</span> <br>");
            httpWriter.println("<input type=\"textarea\" name=\"Comment\" id=\"Comment\" class=\"form-control\" placeholder=\"Comments (if any)\"/> <br>");


            httpWriter.println("<button class=\"btn btn-primary\" type=\"submit\" value=\"Submit!\">Submit!</button>");
            httpWriter.println("</form>");

            httpWriter.println("</panel>\n");

            // Gold info and debugging info.
            httpWriter.println(WebUIHelper.printGoldInfo(nextQuery, goldSimulator.answerQuestion(nextQuery)));
            if (history.size() > 0) {
                httpWriter.println(WebUIHelper.printDebuggingInfo(history));
            }
            // "Skip 10" button
            httpWriter.println("<br><form class=\"form-group\" action=\"\" method=\"get\">");
            // Add user name parameter ..
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            httpWriter.println("<button class=\"btn btn-primary\" input name=\"SwitchQuestion\" type=\"submit\" value=\"Skip10\">Skip 10 questions</button>");
            httpWriter.println("</form>");

            // File download link
            if (history.size() > 0) {
                String annotationFileName = annotationFileNameMap.get(userName);
                httpWriter.println(String.format("<br><a href=\"%s\" download=\"%s\">Click to download annotation file.</a>",
                        "/annotation_files/" + annotationFileName, annotationFileName));
            }

            httpWriter.println("</div></div></container>\n");
            httpWriter.println("</body>");
        }
    }
}
