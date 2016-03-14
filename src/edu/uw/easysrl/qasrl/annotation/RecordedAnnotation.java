package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.syntax.grammar.Category;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Annotation record from a single annotator.
 * Created by luheng on 2/12/16.
 */
public class RecordedAnnotation {
    // Number of iteration in user session.
    public int iterationId, sentenceId;
    public String sentenceString;

    // Predicate information
    public int predicateId, argumentNumber;
    public String predicateString;
    public Category predicateCategory;

    // Question information.
    public int questionId;
    public String question;

    // Answer information
    List<String> optionStrings;
    String answer;
    int answerId, goldAnswerId;

    // Multi-answer information
    List<String> multiAnswers;
    List<Integer> multiAnswerIds, goldAnswerIds;

    // Current accuracy
    double rerankF1, oracleF1, onebestF1;

    // Crowdflower computed stuff.
    double trust;

    // Other
    public String annotatorId;
    public String comment;

    protected RecordedAnnotation() {
        optionStrings = new ArrayList<>();
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
                String[] info = line.split("\\t");
                curr.sentenceId = Integer.parseInt(info[0].split("=")[1]);
                curr.sentenceString = info[1];

                // Example: 26:in (NP\NP)/NP.1
                line = reader.readLine().trim();
                info = line.split("\\s+");
                curr.predicateId = Integer.parseInt(info[0].split(":")[0]);
                curr.predicateString = info[0].split(":")[1];
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
                    // Comment line.
                    if (line.startsWith("Comment:")) {
                        curr.comment = line.split(":")[1].trim();
                        break;
                    }
                    // Empty comment line.
                    if (line.isEmpty()) {
                        break;
                    }
                    info = line.split("\\t");
                    int id = Integer.parseInt(info[0]);
                    assert (id == curr.optionStrings.size());
                    curr.optionStrings.add(info[3]);
                    String match = info[1];
                    if (match.contains("*")) {
                        curr.answerId = id;
                    }
                    if (match.contains("G")) {
                        curr.goldAnswerId = id;
                    }
                }

                // USER_ACC: 100.00%
                reader.readLine();

                // [ReRank-sentence]:  Precision = 75.86 Recall    = 75.86 F1        = 75.86
                // [OneBest-sentence]: Precision = 75.00 Recall    = 72.41 F1        = 73.68
                // [Oracle-sentence]:  Precision = 75.86 Recall    = 75.86 F1        = 75.86
                info = reader.readLine().trim().split("\\s+");
                curr.rerankF1 = Double.parseDouble(info[info.length - 1]);
                info = reader.readLine().trim().split("\\s+");
                curr.onebestF1 = Double.parseDouble(info[info.length - 1]);
                info = reader.readLine().trim().split("\\s+");
                curr.oracleF1 = Double.parseDouble(info[info.length - 1]);
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
                && optionStrings.size() == other.optionStrings.size()
                && goldAnswerId == other.goldAnswerId;
    }

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = "ITER=" + iterationId + "\n"
                + "SID=" + sentenceId + "\t" + sentenceString + "\n"
                + "PRED=" + predicateId + "\t" + predicateString + "\t" + predicateCategory + "." + argumentNumber + "\n"
                + "QID=" + questionId + "\t" + question + "\n"
                + "ANS/GOLD=" + answerId + "/" + goldAnswerId + "\n";
        for (int i = 0; i < optionStrings.size(); i++) {
            result += i + "\t" + optionStrings.get(i) + "\n";
        }
        result += String.format("1B=%.3f\tRR=%.3f\tOR=%.3f", onebestF1, rerankF1, oracleF1) + "\n";
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
