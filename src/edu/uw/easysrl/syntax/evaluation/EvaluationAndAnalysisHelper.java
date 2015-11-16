package edu.uw.easysrl.syntax.evaluation;

import edu.uw.easysrl.corpora.qa.QASentence;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.QADependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.parser.SRLParser;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luheng on 11/15/15.
 */
public class EvaluationAndAnalysisHelper {
    final File outputFile;
    final BufferedWriter outputWriter;
    final boolean outputToConsole, outputToError;
    final Results results;
    final Map<String, Results> labelResults;

    public EvaluationAndAnalysisHelper(File outputFile, boolean outputToConsole, boolean outputToError)
            throws IOException {
        this.outputFile = outputFile;
        outputWriter = outputFile == null ? null : new BufferedWriter(new FileWriter(outputFile));
        this.outputToConsole = outputToConsole;
        this.outputToError = outputToError;
        results = new Results();
        labelResults = new HashMap<>();
        SRLFrame.getAllSrlLabels().forEach(label->labelResults.put(label.toString(), new Results()));
    }

    public void close() throws IOException {
        if (outputWriter != null) {
            outputWriter.close();
        }
    }

    public void addResults(Results results) {
        this.results.add(results);
    }

    public void processNewParse(QASentence sentence, List<SRLParser.CCGandSRLparse> parses) {
        String output = "";
        output += "sentence:\t" + StringUtils.join(sentence.getWords(), " ");
        if (parses == null || parses.size() == 0) {
            output += "\nparse:\t[failed to parse]";
        } else {
            List<String> leaves = new ArrayList<>();
            parses.get(0).getCcgParse().getLeaves()
                    .forEach(leaf -> leaves.add(leaf.getWord() + "|" + leaf.getCategory()));
            output += "\nparse:\t" + StringUtils.join(leaves, " ");
        }
        addOutput(output);
    }

    public void processMatchedDependency(QASentence sentence, QADependency gold, ResolvedDependency predicted) {
        String output = "matched:\t" + gold.toString(sentence.getWords()) + "\t" + predicted.toString();
        addOutput(output);
        labelResults.get(gold.getLabel().toString()).add(new Results(1, 1, 1));
    }

    public void processMissingDependency(QASentence sentence, QADependency gold) {
        String output = "missing:\t" + gold.toString(sentence.getWords()) + "\t\t";
        addOutput(output);
        labelResults.get(gold.getLabel().toString()).add(new Results(0, 0, 1));
    }

    public void processWrongDependency(QASentence sentence, ResolvedDependency predicted) {
        String output = "wrong:\t" + "\t\t" + predicted.toString();
        addOutput(output);
        labelResults.get(predicted.getSemanticRole().toString()).add(new Results(1, 0, 0));
    }

    private void addOutput(String output) {
        if (outputWriter != null) {
            try {
                outputWriter.write(output + "\n");
            } catch (IOException e) {
            }
        }
        if (outputToConsole) {
            System.out.println(output);
        }
        if (outputToError) {
            System.err.println(output);
        }
    }

    public Results getResults() {
        return results;
    }

    // TODO
    public void outputResults() {
        addOutput("[results]:\n" + results.toString());
        labelResults.forEach((label, results1) -> {
            if (results1.getFrequency() > 0) {
                addOutput(String.format("[%s] freq=%d:\n%s", label, results1.getFrequency(), results1.toString()));
            }
        });
    }
}
