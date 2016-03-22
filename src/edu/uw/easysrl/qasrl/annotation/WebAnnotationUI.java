package edu.uw.easysrl.qasrl.annotation;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.IntStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.*;

import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;

/**
 * For check-boxes.
 * Usage: WebAnnotationUI [port number] [n-best]
 */
public class WebAnnotationUI {
    // Parameters.
    private static int nBest = 100;

    // Shared data: nBestList, sentences, etc.
    private static ParseData parseData;
    private static Map<Integer, NBestList> allParses;
    private static BaseCcgParser parser;
    private static BaseCcgParser.ConstrainedCcgParser reparser;
    private static int maxTagsPerWord = 50;

    // user name -> all the queries for the current sentence.
    private static Map<String, List<ScoredQuery<QAStructureSurfaceForm>>> activeLearningMap;
    // user name -> all the answered queries.
    private static Map<String, List<ScoredQuery<QAStructureSurfaceForm>>> activeLearningHistoryMap;
    // user name -> current query id.
    private static Map<String, Integer> queryIdMap;
    // user name -> remaining sentence ids.
    private static Map<String, List<Integer>> sentenceIdsMap;
    // user name -> annotation file.
    private static Map<String, BufferedWriter> annotationFileWriterMap;
    private static Map<String, String> annotationFileNameMap;
    // user name -> parameters
    private static Map<String, QueryPruningParameters> parametersMap;

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");
    private static final String annotationPath  = "./webapp/annotation_files/";
    private static final String webInfPath  = "./webapp/WEB-INF/";

    private static final int[] sentencesToAnnotate = new int[] {
            90, 99, 156, 192, 199, 217, 224, 268, 294, 397, 444, 469, 563, 705, 762, 992, 1016, 1078, 1105, 1124, 1199,
            1232, 1261, 1304, 1305, 1489, 1495, 1516, 1564, 1674, 1695
    };

    private static void initializeData() {
        activeLearningMap = new HashMap<>();
        activeLearningHistoryMap = new HashMap<>();
        annotationFileWriterMap = new HashMap<>();
        annotationFileNameMap = new HashMap<>();
        queryIdMap = new HashMap<>();
        sentenceIdsMap = new HashMap<>();
        parametersMap = new HashMap<>();

        // Load parseData and nBestLists;
        parseData = ParseData.loadFromDevPool().get();
        System.out.println(String.format("Read %d sentences from the dev set.", parseData.getGoldParses().size()));

        String preparsedFile = "parses.100best.out";
        parser = new BaseCcgParser.MockParser(preparsedFile, nBest);
        System.err.println("Parse initialized.");
        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, BaseCcgParser.rootCategories,
                maxTagsPerWord, 1 /* nbest */);

        allParses = new HashMap<>();
        IntStream.range(0, parseData.getSentences().size())
                .forEach(sentId -> {
                    final NBestList nBestList = ExperimentUtils.getNBestList(parser, sentId,
                            parseData.getSentenceInputWords().get(sentId));
                    if (nBestList != null) {
                        allParses.put(sentId, nBestList);
                    }
                });
    }

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        nBest = Integer.parseInt(args[1]);
        initializeData();

        // Servlet Context Handler
        ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletHandler.setContextPath("/");
        servletHandler.setResourceBase(System.getProperty("java.io.tmpdir"));
        servletHandler.addServlet(AnnotationServlet.class, "/annotate/*");
        servletHandler.addServlet(LoginServlet.class, "/login/*");

        // Resource handler wrapped with context.
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
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
                String newRequest = "/annotate?UserName=" + userName;
                // Forward parameters.
                if (request.getParameter("KeepBinary") != null) {
                    newRequest += "&KeepBinary=True";
                }
                httpResponse.sendRedirect(httpResponse.encodeRedirectURL(newRequest));
            } else {
                PrintWriter httpWriter = httpResponse.getWriter();
                httpWriter.println(WebUIHelper.printHTMLHeader());
                httpWriter.println("<body style=\"padding: 50px;\">");
                httpWriter.println("<form class=\"form-group\">\n"
                        + "<label for=\"UserName\">Please enter your name here: </label>\n"
                        + "<input type=\"text\" input name=\"UserName\"/>\n"
                        + "<br/>"
                        + "<input name=\"KeepBinary\" type=\"checkbox\" value=\"True\" />&nbsp Keep binary queries. <br/>"
                        + "<br/><button class=\"btn btn-primary\" type=\"submit\" class=\"btn btn-primary\">Go!</button>\n"
                        + "</form>");
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
                String userFileName = userName + "_" + dateFormat.format(new Date()) + ".txt";
                annotationFileWriterMap.put(userName, new BufferedWriter(new FileWriter(
                        new File(annotationPath + userFileName))));
                annotationFileNameMap.put(userName, userFileName);
                List<Integer> sentIds = new ArrayList<>();
                for (int sid : sentencesToAnnotate) {
                    sentIds.add(sid);
                }
                sentenceIdsMap.put(userName, sentIds);
                activeLearningHistoryMap.put(userName, new ArrayList<>());

                // Query pruning parameters.
                parametersMap.put(userName, new QueryPruningParameters());
                if (request.getParameter("KeepBinary") != null && !request.getParameter("KeepBinary").equals("True")) {
                    parametersMap.get(userName).skipBinaryQueries = false;
                }
                initializeForUserAndSentence(userName, sentIds.get(0));
            }

            final BufferedWriter fileWriter = annotationFileWriterMap.get(userName);
            final String[] userAnswers = request.getParameterValues("UserAnswer");
            if (userAnswers != null) {
                int lastQueryId = queryIdMap.get(userName);
                final List<ScoredQuery<QAStructureSurfaceForm>> queryPool = activeLearningMap.get(userName);
                ScoredQuery<QAStructureSurfaceForm> query = queryPool.get(lastQueryId);
                int sentId = query.getSentenceId();

                // TODO: response simulator.
                // TODO: append to history.

                // TODO: print history.
                String annotationStr = "";
                annotationStr += "SID=" + sentId + "\n";
                annotationStr += "QID=" + lastQueryId + "\n";

                // FIXME: to string method of Multi Query
                annotationStr += query.toString() + "\n";
                annotationStr += "[RESPONSES]:\n";
                for (String answer : userAnswers) {
                    annotationStr += answer + "\n";
                }
                if (request.getParameter("Comment") != null) {
                    annotationStr += "[COMMENT]:\n" + request.getParameter("Comment").trim();
                }
                // Writing annotation to file.
                fileWriter.write(annotationStr + "\n");
                fileWriter.flush();

                // Advance to the next query.
                queryIdMap.put(userName, lastQueryId + 1);
                activeLearningHistoryMap.get(userName).add(query);
            }
            httpResponse.setContentType("text/html; charset=utf-8");
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            update(userName, httpResponse.getWriter());
        }

        private void initializeForUserAndSentence(String userName, int sentenceId) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = ExperimentUtils
                    .generateAllCheckboxQueries(sentenceId,
                            parseData.getSentences().get(sentenceId),
                            allParses.get(sentenceId),
                            parametersMap.get(userName));
            activeLearningMap.put(userName, queryList);
            System.err.println("sentence " + sentenceId + " has " + queryList.size() + " queries.");
            activeLearningMap.put(userName, queryList);
            queryIdMap.put(userName, 0);
        }

        private void update(final String userName, final PrintWriter httpWriter) {
            final List<Integer> sentenceIds = sentenceIdsMap.get(userName);
            List<ScoredQuery<QAStructureSurfaceForm>> queryPool = activeLearningMap.get(userName);
            int queryId = queryIdMap.get(userName);

            // Get next query from pool.
            if (queryId >= queryPool.size()) {
                sentenceIds.remove(0);
                if (sentenceIds.size() == 0) {
                    return;
                }
                int newSentId = sentenceIds.get(0);
                initializeForUserAndSentence(userName, newSentId);
                queryPool = activeLearningMap.get(userName);
                queryId = 0;
            }

            ScoredQuery<QAStructureSurfaceForm> query = queryPool.get(queryId);

            httpWriter.println(WebUIHelper.printHTMLHeader());
            httpWriter.println("<h1><font face=\"arial\">Annotation Demo</font></h1>\n");

            httpWriter.println("<body style=\"padding-left: 80px; padding-right=80px;\">");
            //httpWriter.println("<container>\n" + WebUIHelper.printInstructions() + "</container>\n");

            // Print the progress bar.
            int numTotalSentences = sentencesToAnnotate.length;
            int numAnsweredSentences = numTotalSentences - sentenceIds.size();
            int numTotalQueries = queryPool.size();
            httpWriter.println(WebUIHelper.printProgressBar(numAnsweredSentences, 0 /* numSkipped */, numTotalSentences));
            httpWriter.println(WebUIHelper.printProgressBar(queryId, 0 /* numSkipped */, numTotalQueries));

            // Print sentence.
            final List<String> words = parseData.getSentences().get(query.getSentenceId());

            httpWriter.println("<container><div class=\"row\">\n");
            httpWriter.println("<div class=\"col-md-12\">");
            // Annotation margin.
            httpWriter.println("<panel panel-default>\n");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Sentence\">Sentence:</span></h5>");
            httpWriter.println("<div id=\"Sentence\"> " + TextGenerationHelper.renderHTMLSentenceString(words,
                    -1 /* don't the predicate id */, true /* highlight predicate */) + " </div>");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Question\">Question:</span><br></h5>");
            httpWriter.println("<div id=\"Question\"> " + query.getPrompt() + " </div>");

            httpWriter.println("<h5><span class=\"label label-primary\" for=\"AnswerOptions\">Answer Options:</span></h5>");
            httpWriter.println("<form class=\"form-group\" id=\"AnswerOptions\" action=\"\" method=\"get\">");
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));

            query.getOptions().forEach(optionStr ->
                httpWriter.println(String.format("<input name=\"UserAnswer\" type=\"checkbox\" value=\"%s\" />&nbsp %s <br/>", optionStr, optionStr)));

            // Comment box.
            httpWriter.println("<br><span class=\"label label-primary\" for=\"Comment\">Comments (if any):</span> <br>");
            httpWriter.println("<input type=\"textarea\" name=\"Comment\" id=\"Comment\" class=\"form-control\" placeholder=\"Comments (if any)\"/> <br>");
            httpWriter.println("<button class=\"btn btn-primary\" type=\"submit\" id=\"SubmitAnswer\" value=\"Submit!\" disabled>" + "Submit!</button>");
            httpWriter.println("</form>");
            httpWriter.println("</panel>\n");

            // Debugging info
            httpWriter.write("<br/><br/><h3>Debugging Information</h3><br/> <p> query confidence:\t" + query.getPromptScore() + "</p>");

            IntStream.range(0, query.getOptions().size()).boxed().forEach(i ->
                httpWriter.write("<br/>" + query.getOptions().get(i) + "&nbsp&nbsp&nbsp&nbsp" + query.getOptionScores().get(i)));

            httpWriter.write("<br/><br/>");

            // Add user name parameter ..
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            //httpWriter.println("<button class=\"btn btn-primary\" input name=\"NextSentence\" type=\"submit\" value=\"SkipSent\">"
            //            + "Switch to Next Sentence</button>");
            httpWriter.println("</form>");

            // TODO: Gold info and debugging info.

            // File download link
            if (activeLearningHistoryMap.get(userName).size() > 0) {
                String annotationFileName = annotationFileNameMap.get(userName);
                httpWriter.println(String.format("<br><a href=\"%s\" download=\"%s\">Click to download annotation file.</a>",
                        "/annotation_files/" + annotationFileName, annotationFileName));
            }

            httpWriter.println("</div></div></container>\n");
            httpWriter.println("</body>");
        }
    }
}
