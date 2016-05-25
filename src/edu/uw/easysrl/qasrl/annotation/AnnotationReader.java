package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.query.QueryGenerator;
import edu.uw.easysrl.qasrl.query.QueryGeneratorUtils;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by luheng on 3/31/16.
 */
public class AnnotationReader {

    /**
     * Read reviewed test questions from tsv files (google spreadsheet export)s
     * @return
     */
    public static ImmutableList<RecordedAnnotation> readReviewedTestQuestionsFromTSV(String fileName)
            throws IOException {
        List<RecordedAnnotation> annotations = new ArrayList<>();
        BufferedReader reader;

        reader = new BufferedReader(new FileReader(new File(fileName)));
        String line;
        RecordedAnnotation curr;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            String[] info = line.split("\\t");
            // Example: {R|M|X} SID=50 {sentence}
            if (info.length > 0 && info[0].equals("R")) {
                annotations.add(new RecordedAnnotation());
                curr = annotations.get(annotations.size() - 1);
                curr.iterationId = -1;
                curr.sentenceId = Integer.parseInt(info[1].split("=")[1]);
                curr.queryId = -1;
                curr.sentenceString = info[2];

                // Example: \t [prompt]: 0.84 {prompt} {prompt head}
                line = reader.readLine().trim();
                info = line.split("\\t");
                curr.queryPrompt = info[1].trim().isEmpty() ? info[3] : info[2];
                String qkey = info[1].trim().isEmpty() ? info[4] : info[3];
                if (!qkey.isEmpty()) {
                    curr.predicateId = Integer.parseInt(qkey.split(":")[0]);
                    curr.predicateString = qkey.split("_")[0].split(":")[1];

                    //System.err.println(curr.sentenceString + "\n" + curr.queryPrompt + "\n" +
                    //       curr.sentenceString.split("\\s+")[curr.predicateId] + "\n" + curr.predicateString);
                }

                // \t [2] GU          0.16  None of the above.    11,21,28,37,41,50,52,62,66-67,82-83,91,93,96,99
                List<Integer> optionIds = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Options ends with empty line.
                    if (line.isEmpty() || line.trim().startsWith("[reason]:")) {
                        break;
                    }
                    info = line.split("\\t");
                    if (info[1].contains("U") || info[1].contains("T")) {
                        optionIds.add(curr.optionStrings.size());
                    }
                    curr.optionStrings.add(info[3]);
                }
                curr.userOptionIds = ImmutableList.copyOf(optionIds);
                curr.goldOptionIds = ImmutableList.of();
                if (!curr.optionStrings.contains(QueryGeneratorUtils.kNoneApplicableString)) {
                    curr.optionStrings.add(QueryGeneratorUtils.kNoneApplicableString);
                }

                // \t Reason: {reason}
                if (line.isEmpty()) {
                    line = reader.readLine().trim();
                }
                info = line.split("\\t");
                for (int i = 1; i < info.length; i++) {
                    if (!info[i].isEmpty()) {
                        curr.comment = info[i];
                        break;
                    }
                }

                // Empty line.
                reader.readLine();
            }
        }
        System.out.println(String.format("Loaded %d annotation records from file: %s.", annotations.size(), fileName));
        return ImmutableList.copyOf(annotations);
    }

    public static ImmutableList<RecordedAnnotation> readDisabledTestQuestionsFromTSV(String fileName)
            throws IOException {
        List<RecordedAnnotation> annotations = new ArrayList<>();
        BufferedReader reader;
        reader = new BufferedReader(new FileReader(new File(fileName)));
        String line;
        RecordedAnnotation curr;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            String[] info = line.split("\\t");
            if (info.length > 0 && info[0].equals("X")) {
                annotations.add(new RecordedAnnotation());
                curr = annotations.get(annotations.size() - 1);
                curr.iterationId = -1;
                curr.sentenceId = Integer.parseInt(info[1].split("=")[1]);
                curr.queryId = -1;
                curr.sentenceString = info[2];
                line = reader.readLine().trim();
                info = line.split("\\t");
                curr.queryPrompt = info[1].trim().isEmpty() ? info[3] : info[2];
                String qkey = info[1].trim().isEmpty() ? info[4] : info[3];
                if (!qkey.isEmpty()) {
                    curr.predicateId = Integer.parseInt(qkey.split(":")[0]);
                    curr.predicateString = qkey.split("_")[0].split(":")[1];
                }
                List<Integer> optionIds = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.trim().startsWith("[reason]:")) {
                        break;
                    }
                    info = line.split("\\t");
                    if (info[1].contains("U") || info[1].contains("T")) {
                        optionIds.add(curr.optionStrings.size());
                    }
                    curr.optionStrings.add(info[3]);
                }
                curr.userOptionIds = ImmutableList.copyOf(optionIds);
                curr.goldOptionIds = ImmutableList.of();
                if (!curr.optionStrings.contains(QueryGeneratorUtils.kNoneApplicableString)) {
                    curr.optionStrings.add(QueryGeneratorUtils.kNoneApplicableString);
                }
                if (line.isEmpty()) {
                    line = reader.readLine().trim();
                }
                info = line.split("\\t");
                for (int i = 1; i < info.length; i++) {
                    if (!info[i].isEmpty()) {
                        curr.comment = info[i];
                        break;
                    }
                }
                reader.readLine();
            }
        }
        System.out.println(String.format("Loaded %d disabled annotation records from file: %s.",
                annotations.size(), fileName));
        return ImmutableList.copyOf(annotations);
    }

    public static ImmutableList<RecordedAnnotation> loadAnnotationRecordsFromFile(String fileName) throws IOException {
        List<RecordedAnnotation> annotations = new ArrayList<>();
        BufferedReader reader;

        reader = new BufferedReader(new FileReader(new File(fileName)));
        String line;
        RecordedAnnotation curr;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Example: SID=50
            if (line.startsWith("SID=")) {
                annotations.add(new RecordedAnnotation());
                curr = annotations.get(annotations.size() - 1);
                curr.iterationId = -1;
                curr.sentenceId = Integer.parseInt(line.split("=")[1]);

                // Example: QID=0
                line = reader.readLine().trim();
                curr.queryId = Integer.parseInt(line.split("=")[1]);

                // Example: SID=50 \t J.P. Bolduc ...
                line = reader.readLine().trim();
                curr.sentenceString = line.split("\\t")[1];

                // Example: 20:company
                line = reader.readLine().trim();
                String[] questionInfo = line.split(":");

                // Example: 0.96 \t {prompt string}
                line = reader.readLine().trim();
                curr.queryPrompt = line.split("\\t")[1].trim();

                // 0     0.06 \t 0 \t {option string} \t {some other info}
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Empty comment line.
                    if (line.isEmpty()) {
                        break;
                    }
                    curr.optionStrings.add(line.split("\\t")[2]);
                }
                // [RESPONSES]:
                line = reader.readLine().trim();
                assert (line.equals("[RESPONSES]:"));
                List<Integer> answerIds = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    final String option = line.trim();
                    int optionId = -1;
                    for (int i = 0; i < curr.optionStrings.size(); i++) {
                        if (curr.optionStrings.get(i).equalsIgnoreCase(option)) {
                            optionId = i;
                            break;
                        }
                    }
                    if (optionId >= 0) {
                        answerIds.add(optionId);
                    }
                    if (line.equals("[COMMENT]:")) {
                        break;
                    }
                }
                curr.userOptionIds = ImmutableList.copyOf(answerIds);
                curr.comment = reader.readLine().trim();
                // Empty line.
                reader.readLine();

                // Other unassigned stuff.
                curr.goldOptionIds = ImmutableList.of();
            }
        }
        System.out.println(String.format("Loaded %d annotation records from file: %s.", annotations.size(), fileName));
        return ImmutableList.copyOf(annotations);
    }

    /**
     * The format used in first pilot annotation.
     * @param fileName
     * @return
     * @throws IOException
     */
    public static List<RecordedAnnotation> loadAnnotationRecordsFromFileOldFormat(String fileName) throws IOException {
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
                curr.queryId = Integer.parseInt(info[0].split("=")[1]);
                curr.queryPrompt = info[info.length - 1].trim();

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
                        curr.userOptionIds = ImmutableList.of(id);
                    }
                    if (match.contains("G")) {
                        curr.goldOptionIds = ImmutableList.of(id);
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

    public static void main(String[] args) throws IOException {
        final String tsvFileName = "./Crowdflower_unannotated/test_questions/reviewed_test_questions_jeopardy_pp.tsv";
        ImmutableList<RecordedAnnotation> annotations =
            readReviewedTestQuestionsFromTSV(tsvFileName);
        annotations.forEach(System.out::println);
    }
}
