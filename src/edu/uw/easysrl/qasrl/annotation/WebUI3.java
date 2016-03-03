package edu.uw.easysrl.qasrl.annotation;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.text.html.Option;

import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.corpora.GreedyAnswerAligner;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;

/**
 * For check-boxes.
 * Usage: WebUI3 [port number] [n-best]
 */
public class WebUI3 {
    // Parameters.
    private static int nBest = 50;

    private static POMDP baseLearner;
    private static ResponseSimulatorGold goldSimulator;
    private static Map<String, POMDP> activeLearningMap;
    private static Map<String, List<Integer>> sentenceIdsMap;
    private static Map<String, ActiveLearningHistory> activeLearningHistoryMap;
    private static Map<String, BufferedWriter> annotationFileWriterMap;
    private static Map<String, String> annotationFileNameMap;

    private static final int maxNumberOfUsers = 100;

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");
    private static final String annotationPath  = "./webapp/annotation_files/";
    private static final String webInfPath  = "./webapp/WEB-INF/";

    private static final int[] sentencesToAnnotate = new int[] {
            90, 99, 156, 192, 199, 217, 224, 268, 294, 397, 444, 469, 563, 705, 762, 992, 1016, 1078, 1105, 1124, 1199,
            1232, 1261, 1304, 1305, 1489, 1495, 1516, 1564, 1674, 1695
    };

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        nBest = Integer.parseInt(args[1]);

        baseLearner = new POMDP(nBest, 10000 /* horizon */ , 0.0 /* money penalty */);
        goldSimulator = new ResponseSimulatorGold(baseLearner.goldParses, new QuestionGenerator());
        activeLearningMap = new HashMap<>();
        activeLearningHistoryMap = new HashMap<>();
        annotationFileWriterMap = new HashMap<>();
        annotationFileNameMap = new HashMap<>();
        sentenceIdsMap = new HashMap<>();

        // Servlet Context Handler
        ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletHandler.setContextPath("/");
        servletHandler.setResourceBase(System.getProperty("java.io.tmpdir"));
        servletHandler.addServlet(AnnotationServlet.class, "/annotate/*");
        servletHandler.addServlet(LoginServlet.class, "/login/*");

        // Resource handler wrapped with context.
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        //resourceHandler.setResourceBase(annotationPath);
        resourceHandler.setBaseResource(Resource.newResource(annotationPath));
        ContextHandler resourceContextHandler = new ContextHandler("/annotation_files");
        resourceContextHandler.setHandler(resourceHandler);

        ResourceHandler webInfResourceHandler = new ResourceHandler();
        webInfResourceHandler.setDirectoriesListed(false);
        webInfResourceHandler.setBaseResource(Resource.newResource(webInfPath));
        ContextHandler webInfContextHandler = new ContextHandler("/WEB-INF");
        webInfContextHandler.setHandler(webInfResourceHandler);

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(new Handler[] {servletHandler, resourceContextHandler, webInfContextHandler });

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
                POMDP newLearner = new POMDP(baseLearner);
                activeLearningMap.put(userName, newLearner);
                activeLearningHistoryMap.put(userName, new ActiveLearningHistory());
                String userFileName = userName + "_" + dateFormat.format(new Date()) + ".txt";
                annotationFileWriterMap.put(userName, new BufferedWriter(new FileWriter(
                        new File(annotationPath + userFileName))));
                annotationFileNameMap.put(userName, userFileName);
                List<Integer> sentIds = new ArrayList<>();
                for (int sid : sentencesToAnnotate) {
                    sentIds.add(sid);
                }
                sentenceIdsMap.put(userName, sentIds);
                newLearner.initializeForSentence(sentIds.get(0));
            }

            final POMDP learner = activeLearningMap.get(userName);
            final ActiveLearningHistory history = activeLearningHistoryMap.get(userName);
            final BufferedWriter fileWriter = annotationFileWriterMap.get(userName);

            final String[] userAnswers = request.getParameterValues("UserAnswer");
            if (userAnswers != null) {
                Response response = new Response();
                for (String userAnswer : userAnswers) {
                    System.out.println(userAnswer);
                    int optionId = Integer.parseInt(userAnswer);
                    // Create user response objective.
                    response.add(optionId);
                }
                if (request.getParameter("Comment") != null) {
                    response.debugInfo = request.getParameter("Comment").trim();
                }
                GroupedQuery query = learner.history.getLastAction();
                int sentId = query.getSentenceId();
                // Get gold response for debugging.
                Response goldResponse = goldSimulator.answerQuestion(query);
                learner.receiveObservation(response);
                // Append to history
                history.add(query, response, goldResponse,
                        learner.getRerankedF1(query.getSentenceId()),
                        learner.getRerankedF1(sentId),
                        learner.getOneBestF1(sentId),
                        learner.getOracleF1(sentId));
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
            final POMDP learner = activeLearningMap.get(userName);
            final ActiveLearningHistory history = activeLearningHistoryMap.get(userName);
            final List<Integer> sentenceIds = sentenceIdsMap.get(userName);

            httpWriter.println(WebUIHelper.printHTMLHeader());
            httpWriter.println("<h1><font face=\"arial\">Annotation Demo</font></h1>\n");

            httpWriter.println("<body style=\"padding-left: 80px; padding-right=80px;\">");
            //httpWriter.println("<container>\n" + WebUIHelper.printInstructions() + "</container>\n");

            // Print progress bar.
            int numTotal = sentencesToAnnotate.length;
            int numAnswered = numTotal - sentenceIds.size();
            httpWriter.println(WebUIHelper.printProgressBar(numAnswered, 0 /* numSkipped */, numTotal));

            // Get next query.
            Optional<GroupedQuery> action;
            while (!(action = learner.generateAction()).isPresent()) {
                sentenceIds.remove(0);
                if (sentenceIds.size() == 0) {
                    return;
                }
                int newSentId = sentenceIds.get(0);
                learner.initializeForSentence(newSentId);
            }
            GroupedQuery nextQuery = action.get();
            // Print sentence
            final List<String> words = nextQuery.getSentence();

            httpWriter.println("<container><div class=\"row\">\n");
            httpWriter.println("<div class=\"col-md-12\">");
            // Annotation margin.
            httpWriter.println("<panel panel-default>\n");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Sentence\">Sentence:</span></h5>");
            httpWriter.println("<div id=\"Sentence\"> " + TextGenerationHelper.renderHTMLSentenceString(words,
                    nextQuery.getPredicateIndex(), true /* highlight predicate */) + " </div>");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Question\">Question:</span><br></h5>");
            httpWriter.println("<div id=\"Question\"> " + nextQuery.getQuestion() + " </div>");

            httpWriter.println("<h5><span class=\"label label-primary\" for=\"AnswerOptions\">Answer Options:</span></h5>");
            httpWriter.println("<form class=\"form-group\" id=\"AnswerOptions\" action=\"\" method=\"get\">");
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            final List<GroupedQuery.AnswerOption> options = nextQuery.getAnswerOptions();

            int badQuestionOptionId = -1,
                    unlistedAnswerId = -1;
            for (int i = 0; i < options.size(); i++) {
                GroupedQuery.AnswerOption option = options.get(i);
                /*
                if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                    badQuestionOptionId = i;
                    continue;
                }
                if (GroupedQuery.NoAnswerOption.class.isInstance(option)) {
                    unlistedAnswerId = i;
                    continue;
                }
                */
                String optionValue = String.valueOf(i);
                String optionString = option.getAnswer().replace(QuestionAnswerPair.answerDelimiter, "<i><b> _AND_ </i></b>");
                httpWriter.println(String.format("<input name=\"UserAnswer\" type=\"checkbox\" value=\"%s\" />&nbsp %s <br/>",
                        optionValue, optionString));
            }
            /*
            if (badQuestionOptionId >= 0) {
                httpWriter.println(String.format("<input name=\"UserAnswer\" type=\"checkbox\" value=\"%s\"/> &nbsp Question is not valid. <br/>",
                        String.valueOf(badQuestionOptionId)));
            }
            if (unlistedAnswerId >= 0) {
                httpWriter.println(String.format("<input name=\"UserAnswer\" type=\"checkbox\" value=\"%s\"/> &nbsp Answer is not listed. <br/>",
                        String.valueOf(unlistedAnswerId)));
            }
            */

            // Comment box.
            httpWriter.println("<br><span class=\"label label-primary\" for=\"Comment\">Comments (if any):</span> <br>");
            httpWriter.println("<input type=\"textarea\" name=\"Comment\" id=\"Comment\" class=\"form-control\" placeholder=\"Comments (if any)\"/> <br>");

            httpWriter.println("<button class=\"btn btn-primary\" type=\"submit\" id=\"SubmitAnswer\" value=\"Submit!\" disabled>"
                    + "Submit!</button>");
            httpWriter.println("</form>");

            httpWriter.println("</panel>\n");

            // Add user name parameter ..
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            httpWriter.println("<button class=\"btn btn-primary\" input name=\"NextSentence\" type=\"submit\" value=\"SkipSent\">"
                    + "Switch to Next Sentence</button>");
            httpWriter.println("</form>");

            // Gold info and debugging info.
            if (history.size() > 0) {
                httpWriter.println(WebUIHelper.printDebuggingInfo(history) + "<br><br>");
                httpWriter.println(WebUIHelper.printSentenceDebuggingInfo(history) + "<br><br>");
            }
            httpWriter.println(WebUIHelper.printGoldInfo(nextQuery, goldSimulator.answerQuestion(nextQuery)) + "<br><br>");

            // File download link
            if (history.size() > 0) {
                String annotationFileName = annotationFileNameMap.get(userName);
                httpWriter.println(String.format("<a href=\"%s\" download=\"%s\">Click to download annotation file.</a>",
                        "/annotation_files/" + annotationFileName, annotationFileName));
            }

            httpWriter.println("</div></div></container>\n");
            httpWriter.println("</body>");
        }
    }
}
