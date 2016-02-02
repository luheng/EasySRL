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
    private final int maxNumAnswerOptionsPerQuery = 4;

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        server.setHandler(new WebUI());
        server.start();
        server.join();
    }

    private WebUI() throws IOException {
        activeLearning = new ActiveLearning();
    }

    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request,
                       final HttpServletResponse httpResponse) throws IOException, ServletException {
        final String[] userAnswerInfo = baseRequest.getParameter("UserAnswer").split("_");
        if (!userAnswerInfo[0].equals("null")) {
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
        httpResponse.println(
                "<html><head><title>Annotation Prototype</title></head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js\"></script>\n"
                + "<!-- Latest compiled and minified CSS -->\n"
                + "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css\" integrity=\"sha512-dTfge/zgoMYpP7QbHy4gWMEGsbsdZeCXz7irItjcC3sPUFtf0kuFbDz/ixG7ArTxmDjLXDmezHubeNikyKGVyQ==\" crossorigin=\"anonymous\">\n"
                + "\n"
                + "<!-- Optional theme -->\n"
                + "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css\" integrity=\"sha384-aUGj/X2zp5rLCbBxumKTCw2Z50WgIr1vs/PFN4praOTvYXWlVyh2UtNUU0KAUhAX\" crossorigin=\"anonymous\">\n"
                + "\n"
                + "<!-- Latest compiled and minified JavaScript -->\n"
                + "<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js\" integrity=\"sha512-K1qjQ+NcF2TYO/eI3M6v8EiNYZfA95pQumfvcVrTHtwQVDG+aHRqLi/ETn2uB+1JqwYqVG3LIvdm9lj6imS/pQ==\" crossorigin=\"anonymous\"></script>"
                + "<style> hr{display:block; height: 1px; border:0; border-top: 1px solid #000000; margin: 1em 50; padding: 0; } </style>"
                + "<body style=\"padding:20\">");

        httpResponse.println("<h1><font face=\"arial\">Annotation Demo</font></h1>");
        httpResponse.println(WebUIHelper.printInstructions());

        // Get next query.
        GroupedQuery nextQuery = activeLearning.getNextQueryInQueue();
        // Print sentence
        final List<String> words = nextQuery.getSentence();
        String sentenceStr = IntStream.range(0, words.size())
                .mapToObj(i -> i == nextQuery.getPredicateIndex() ? "<mark>" + words.get(i) + "</mark>" : words.get(i))
                .collect(Collectors.joining(" "));
        httpResponse.println("<p>" + sentenceStr + "</p>");
        httpResponse.println("<p>" + nextQuery.getQuestion() + "</p>");


        httpResponse.println("<br><form action=\"\" method=\"get\">");
        final List<GroupedQuery.AnswerOption> options = nextQuery.getTopAnswerOptions(maxNumAnswerOptionsPerQuery);
        for (int i = 0; i < options.size(); i++) {
            GroupedQuery.AnswerOption option = options.get(i);
            if (option.isNAOption()) {
                continue;
            }
            String optionValue = "q_" + nextQuery.getQueryId() + "_a_" + i;
            String optionString = option.getAnswer();
            httpResponse.println(
                    String.format("<label><input name=\"UserAnswer\" type=\"radio\" value=\"%s\" />%s</label><br/>",
                            optionValue, optionString));
        }
        httpResponse.println(
                "<label><input name=\"UserAnswer\" type=\"radio\" value=\"bad_q\"/>" +
                        "Question is not understandable.</label><br/>");
        httpResponse.println(
                "<label><input name=\"UserAnswer\" type=\"radio\" value=\"no_ans\"/>" +
                        "Question is understandable, but not answerable given information in the sentence.</label><br/>");
        httpResponse.println("<input type=\"submit\" value=\"Submit!\">" + "</form>");

        System.out.println("--------");

        // TODO: print debugging info.
    }
}
