package edu.uw.easysrl.qasrl.ui;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;

/**
 * Debugging gold parses ...
 * Created by luheng on 2/27/16.
 */
public class DebuggingUI extends AbstractHandler {

    private final ParsePrinter printer = ParsePrinter.HTML_PRINTER;
    private final ImmutableList<ImmutableList<InputReader.InputWord>> sentences;
    private final ImmutableList<Parse> goldParses;

    private DebuggingUI(ImmutableList<ImmutableList<InputReader.InputWord>> sentences,
                        ImmutableList<Parse> goldParses) throws IOException {
        this.sentences = sentences;
        this.goldParses = goldParses;
    }

    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request,
                       final HttpServletResponse response) throws IOException, ServletException {
        int sentenceId = 0;
        if (baseRequest.getParameter("sentenceId") != null) {
            sentenceId = Integer.parseInt(baseRequest.getParameter("sentenceId"));
        }
        response.setContentType("text/html; charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        doParse(sentenceId, response.getWriter());
        baseRequest.setHandled(true);
    }

    public static void main(final String[] args) throws Exception {
        final Server server = new Server(Integer.valueOf(args[0]));
        final ParseData dev = ParseData.loadFromDevPool().get();
        server.setHandler(new DebuggingUI(dev.getSentenceInputWords(), dev.getGoldParses()));
        server.start();
        server.join();
    }

    // @formatter:off

    private void doParse(int sentenceId, final PrintWriter response) {
        Parse parse = goldParses.get(sentenceId);
        String sentence = sentences.get(sentenceId).stream().map(w -> w.word).collect(Collectors.joining(" "));

        response.println("<html><head><title>EasySRL Parser Demo</title></head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js\"></script>\n"
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
        response.println("<h1><font face=\"arial\">EasySRL Parser Demo</font></h1>");
        response.println("      <div><a href=https://github.com/mikelewis0/EasySRL>Download here!</a></div>      \n"
                + "        <br><form action=\"\" method=\"get\">\n"
                + "      <input type=\"text\"  size=\"40\" name=\"sentenceId\" value=\"" + sentenceId + "\"> \n"
                + "      <input type=\"submit\" value=\"Parse!\">" + "    </form>");

        System.out.println(sentence);
        response.println("<p>" + sentence + "</p>");
        response.println(printer.print(parse.syntaxTree, 0));
    }
}

