package edu.uw.easysrl.qasrl.ui;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.*;

import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataUtils;
import edu.uw.easysrl.qasrl.experiments.ReparsingHistory;
import edu.uw.easysrl.qasrl.model.Evidence;
import edu.uw.easysrl.qasrl.model.HITLParser;
import edu.uw.easysrl.qasrl.model.HITLParsingParameters;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.util.GuavaCollectors;
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
public class TestQuestionCreatingUI {
    // Parameters.
    private static int nBest = 100;

    // Shared data: nBestList, sentences, etc.
    private static HITLParser myHITLParser;

    // user name -> all the queries for the current sentence.
    private static Map<String, List<ScoredQuery<QAStructureSurfaceForm>>> activeLearningMap;
    // user name -> all the answered queries.
    private static Map<String, ReparsingHistory> activeLearningHistoryMap;

    // user name -> current query id.
    private static Map<String, Integer> queryIdMap;
    // user name -> remaining sentence ids.
    private static Map<String, List<Integer>> sentenceIdsMap;

    // user name -> annotation file.
    private static Map<String, BufferedWriter> annotationFileWriterMap;
    private static Map<String, String> annotationFileNameMap;

    // user name -> parameters
    private static Map<String, Set<String>> parametersMap;

    private static ImmutableList<Integer> testQuestionIds;

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");
    private static final String annotationPath  = "./webapp/annotation_files/";
    private static final String webInfPath  = "./webapp/WEB-INF/";

    private static boolean isJeopardyStyle = true;
    private static boolean isCheckboxVersion = true;

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = 0.05;
        queryPruningParameters.minPromptConfidence = 0.1;
        queryPruningParameters.skipPPQuestions = false;
        queryPruningParameters.skipBinaryQueries = true;
    }
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.supertagPenaltyWeight = 5.0;
        reparsingParameters.skipPrepositionalQuestions = false;
    }

    private static void initializeData() {
        activeLearningMap = new HashMap<>();
        activeLearningHistoryMap = new HashMap<>();

        annotationFileWriterMap = new HashMap<>();
        annotationFileNameMap = new HashMap<>();
        queryIdMap = new HashMap<>();
        sentenceIdsMap = new HashMap<>();
        parametersMap = new HashMap<>();

        myHITLParser = new HITLParser(nBest);
        myHITLParser.setQueryPruningParameters(queryPruningParameters);

        testQuestionIds = CrowdFlowerDataUtils.getTestSentenceIds();
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
                if (request.getParameter("UsePronouns") != null) {
                    newRequest += "&UsePronouns=True";
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
                        + "<input name=\"UsePronouns\" type=\"checkbox\" value=\"True\" />&nbsp Use pronoun in questions. <br/>"
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
                initializeUser(userName, request);
            }

            final BufferedWriter fileWriter = annotationFileWriterMap.get(userName);

            // Handle response.
            if (request.getParameterValues("Skip") != null) {
                // Advance to the next query.
                final int lastQueryId = queryIdMap.get(userName);
                queryIdMap.put(userName, lastQueryId + 1);
            } else if (request.getParameterValues("UserAnswer") != null) {
                final ImmutableList<String> userAnswers = ImmutableList.copyOf(request.getParameterValues("UserAnswer"));
                final int lastQueryId = queryIdMap.get(userName);
                final List<ScoredQuery<QAStructureSurfaceForm>> queryPool = activeLearningMap.get(userName);
                final ScoredQuery<QAStructureSurfaceForm> query = queryPool.get(lastQueryId);
                final int sentId = query.getSentenceId();
                final ImmutableList<String> sentence = myHITLParser.getSentence(sentId);

                String annotationStr = "";
                annotationStr += "SID=" + sentId + "\n";
                annotationStr += "QID=" + lastQueryId + "\n";

                annotationStr += query.toString(sentence) + "\n";
                annotationStr += "[RESPONSES]:\n";
                for (String answer : userAnswers) {
                    annotationStr += answer + "\n";
                }
                if (request.getParameter("Comment") != null) {
                    annotationStr += "[COMMENT]:\n" + request.getParameter("Comment").trim() + "\n";
                }
                // Writing annotation to file.
                fileWriter.write(annotationStr + "\n");
                fileWriter.flush();

                // TODO: store user options.
                ImmutableList<Integer> userOptions = IntStream.range(0, query.getOptions().size())
                        .boxed()
                        .filter(i -> userAnswers.contains(query.getOptions().get(i)))
                        .collect(GuavaCollectors.toImmutableList());

                // Advance to the next query.
                queryIdMap.put(userName, lastQueryId + 1);
                final ImmutableSet<Evidence> evidenceSet = myHITLParser.getEvidenceSet(query, userOptions);
                activeLearningHistoryMap.get(userName).addEntry(
                        sentId,
                        query,
                        userOptions,
                        evidenceSet);
                System.out.println(query.toString(myHITLParser.getSentence(sentId),
                        'G', myHITLParser.getGoldOptions(query),
                        'U', userOptions));
                evidenceSet.forEach(ev -> System.out.println(ev.toString(myHITLParser.getSentence(sentId))));
                System.out.println();
            }
            httpResponse.setContentType("text/html; charset=utf-8");
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            update(userName, httpResponse.getWriter());
        }

        private void initializeUser(String userName, final HttpServletRequest request) throws IOException {
            String userFileName = userName + "_" + dateFormat.format(new Date()) + ".txt";
            annotationFileWriterMap.put(userName, new BufferedWriter(new FileWriter(
                    new File(annotationPath + userFileName))));
            annotationFileNameMap.put(userName, userFileName);

            sentenceIdsMap.put(userName, new ArrayList<>(testQuestionIds));
            activeLearningHistoryMap.put(userName, new ReparsingHistory(myHITLParser));
            parametersMap.put(userName, new HashSet<>());
            if (request.getParameter("UsePronouns") != null) {
                parametersMap.get(userName).add("UsePronouns");
            }
            initializeNextSentenceForUser(userName);
        }

        private boolean initializeNextSentenceForUser(String userName) {
            final List<Integer> sentenceIds = sentenceIdsMap.get(userName);
            final Set<String> parameters = parametersMap.get(userName);
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList = null;
            while (sentenceIds.size() > 1) {
                queryList = parameters.contains("UsePronouns") ?
                        myHITLParser.getPPAttachmentQueriesForSentence(sentenceIds.get(0)) :
                        myHITLParser.getPronounPPAttachmentQueriesForSentence(sentenceIds.get(0));
                if (!queryList.isEmpty()) {
                    break;
                }
                sentenceIds.remove(0);
            }
            if (queryList == null || queryList.isEmpty()) {
                return false;
            }
            System.err.println("sentence " + sentenceIds.get(0) + " has " + queryList.size() + " queries.");
            activeLearningMap.put(userName, queryList);
            queryIdMap.put(userName, 0);
            return true;
        }

        private void update(final String userName, final PrintWriter httpWriter) {
            final List<Integer> sentenceIds = sentenceIdsMap.get(userName);
            List<ScoredQuery<QAStructureSurfaceForm>> queryPool = activeLearningMap.get(userName);
            int queryId = queryIdMap.get(userName);

            // Get next query from pool.
            if (queryId >= queryPool.size()) {
                sentenceIds.remove(0);
                if (!initializeNextSentenceForUser(userName)) {
                    return;
                }
                queryPool = activeLearningMap.get(userName);
                queryId = 0;
            }

            ScoredQuery<QAStructureSurfaceForm> query = queryPool.get(queryId);

            httpWriter.println(WebUIHelper.printHTMLHeader());
            httpWriter.println("<h1><font face=\"arial\">Annotation Demo</font></h1>\n");

            httpWriter.println("<body style=\"padding-left: 80px; padding-right=80px;\">");
            //httpWriter.println("<container>\n" + WebUIHelper.printInstructions() + "</container>\n");

            // Print the progress bar.
            int numTotalSentences = testQuestionIds.size();
            int numAnsweredSentences = numTotalSentences - sentenceIds.size();
            int numTotalQueries = queryPool.size();
            httpWriter.println(WebUIHelper.printProgressBar(numAnsweredSentences, 0 /* numSkipped */, numTotalSentences));
            httpWriter.println(WebUIHelper.printProgressBar(queryId, 0 /* numSkipped */, numTotalQueries));

            // Print sentence.
            final ImmutableList<String> words = myHITLParser.getSentence(query.getSentenceId());

            httpWriter.println("<container><div class=\"row\">\n");
            httpWriter.println("<div class=\"col-md-12\">");

            // Annotation margin.
            httpWriter.println("<panel panel-default>\n");
            httpWriter.println("<h5><span class=\"label label-primary\" for=\"Sentence\">Sentence:</span></h5>");
            httpWriter.println("<div id=\"Sentence\"> " + TextGenerationHelper.renderHTMLSentenceString(words,
                    -1 /* don't know the predicate id */, false /* highlight predicate */) + " </div>");

            // TODO: handle radiobutton.
            if (isJeopardyStyle) {
                httpWriter.println("<h5><span class=\"label label-primary\" for=\"Question\">Answer:</span><br></h5>");
                httpWriter.println("<div id=\"Question\"> " + query.getPrompt() + " </div>");

                httpWriter.println("<h5><span class=\"label label-primary\" for=\"AnswerOptions\">Question Options:</span></h5>");
                httpWriter.println("<form class=\"form-group\" id=\"AnswerOptions\" action=\"\" method=\"get\">");
                query.getOptions().forEach(optionStr ->
                        httpWriter.println(String.format("<input name=\"UserAnswer\" type=\"checkbox\" value=\"%s\" />&nbsp %s <br/>",
                                optionStr, optionStr)));
            } else {
                httpWriter.println("<h5><span class=\"label label-primary\" for=\"Question\">Question:</span><br></h5>");
                httpWriter.println("<div id=\"Question\"> " + query.getPrompt() + " </div>");

                httpWriter.println("<h5><span class=\"label label-primary\" for=\"AnswerOptions\">Answer Options:</span></h5>");
                httpWriter.println("<form class=\"form-group\" id=\"AnswerOptions\" action=\"\" method=\"get\">");
                query.getOptions().forEach(optionStr ->
                        httpWriter.println(String.format("<input name=\"UserAnswer\" type=\"checkbox\" value=\"%s\" />&nbsp %s <br/>",
                                optionStr, optionStr)));
            }

            // Add user name.
            httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));

            // Comment box.
            httpWriter.println("<br><span class=\"label label-primary\" for=\"Comment\">Reason for gold:</span> <br>");
            httpWriter.println("<input type=\"textarea\" name=\"Comment\" id=\"Comment\" class=\"form-control\" placeholder=\"Comments (if any)\"/> <br>");
            httpWriter.println("<button class=\"btn btn-primary\" type=\"submit\" id=\"SubmitAnswer\" value=\"Submit!\" disabled>" + "Submit!</button>");
            httpWriter.println("<button class=\"btn btn-primary\" input name=\"Skip\" type=\"submit\" value=\"Skip\">Skip</button>");
            httpWriter.println("</form>");
            httpWriter.println("</panel>\n");

            // Debugging info.
            httpWriter.write("<br/><br/><hr/>");

            final ReparsingHistory myHistory = activeLearningHistoryMap.get(userName);
            httpWriter.write("<br/><br/><hr/>");

            httpWriter.write("<p>" + query.toString(words,
                    'G', myHITLParser.getGoldOptions(query),
                    'O', myHITLParser.getOracleOptions(query),
                    'B', myHITLParser.getOneBestOptions(query))
                    .replace("\t", "&nbsp&nbsp&nbsp").replace("\n", "<br/>") + "</p>");

            httpWriter.write("<br/><br/>");

            // Add user name parameter ..
            //httpWriter.println(String.format("<input type=\"hidden\" input name=\"UserName\" value=\"%s\"/>", userName));
            //httpWriter.println("</form>");

            // File download link
            String annotationFileName = annotationFileNameMap.get(userName);
            httpWriter.println(String.format("<br><a href=\"%s\" download=\"%s\">Click to download annotation file.</a>",
                    "/annotation_files/" + annotationFileName, annotationFileName));

            httpWriter.println("</div></div></container>\n");
            httpWriter.println("</body>");
        }
    }
}
