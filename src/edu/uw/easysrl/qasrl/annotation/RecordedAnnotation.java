package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.syntax.grammar.Category;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luheng on 2/12/16.
 */
public class RecordedAnnotation {
    // Number of iteration in user session.
    public int iterationId, sentenceId;

    // Predicate information
    public int predicateId, argumentNumber;
    public Category predicateCategory;

    // Question information.
    public int questionId;
    public String question;

    // Answer information/
    List<String> answerStrings;
    int answerId, goldAnswerId;

    // Other
    public String comment;

    protected RecordedAnnotation() {
        answerStrings = new ArrayList<>();
    }

    public static List<RecordedAnnotation> loadAnnotationRecordsFromFile(String fileName) throws IOException {
        List<RecordedAnnotation> annotations = new ArrayList<>();
        BufferedReader reader;

        reader = new BufferedReader(new FileReader(new File(fileName)));
        String line;
        RecordedAnnotation curr;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Example: ITER=0
            if (line.startsWith("ITER=")) {
                annotations.add(new RecordedAnnotation());
                curr = annotations.get(annotations.size() - 1);
                curr.iterationId = Integer.parseInt(line.split("=")[1]);

                // Example: SID=1199 ...
                line = reader.readLine().trim();
                curr.sentenceId = Integer.parseInt(line.split("\t")[0].split("=")[1]);

                // Example: 26:in (NP\NP)/NP.1
                line = reader.readLine().trim();
                String[] info = line.split("\\s+");
                curr.predicateId = Integer.parseInt(info[0].split(":")[0]);
                curr.predicateCategory = Category.valueOf(info[1].split("\\.")[0]);
                curr.argumentNumber = Integer.parseInt(info[1].split("\\.")[1]);

                // Example: QID=4 ent=1.43  marg=0.32 What would be in front seats?
                line = reader.readLine().trim();
                info = line.split("\\t");
                curr.questionId = Integer.parseInt(info[0].split("=")[1]);
                curr.question = info[info.length - 1].trim();

                // 0     prob=0.06 additional safety equipment (12:equipment)  32,41,47
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("Comment:") || line.isEmpty()) {
                        curr.comment = line;
                        break;
                    }
                    info = line.split("\\t");
                    int id = Integer.parseInt(info[0]);
                    assert (id == curr.answerStrings.size());
                    curr.answerStrings.add(info[3]);
                    String match = info[1];
                    if (match.contains("*")) {
                        curr.answerId = id;
                    }
                    if (match.contains("G")) {
                        curr.goldAnswerId = id;
                    }
                }
            }
        }
        System.out.println(String.format("Loaded %d annotation records from file: %s.", annotations.size(), fileName));
        return annotations;
    }

    public boolean isSameQuestionAs(final RecordedAnnotation other) {
        return sentenceId == other.sentenceId
                && predicateId == other.predicateId
                && argumentNumber == other.argumentNumber
                && question.equalsIgnoreCase(other.question)
                && answerStrings.size() == other.answerStrings.size()
                && goldAnswerId == other.goldAnswerId;
    }

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = "ITER=" + iterationId + "\n"
                + "SID=" + sentenceId + "\n"
                + "PRED=" + predicateId + " : " + predicateCategory + "." + argumentNumber + "\n"
                + "QID=" + questionId + " : " + question + "\n"
                + "ANS/GOLD=" + answerId + "/" + goldAnswerId + "\n";
        for (int i = 0; i < answerStrings.size(); i++) {
            result += i + "\t" + answerStrings.get(i) + "\n";
        }
        return result;
    }

    public static void main(String[] args) {
        String fileName = args[0];
        try {
            List<RecordedAnnotation> annotations = loadAnnotationRecordsFromFile(fileName);
            annotations.forEach(r -> {
                System.out.println(r.toString()
                );
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
