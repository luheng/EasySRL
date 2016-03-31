//package edu.uw.easysrl.qasrl.annotation;
//
//import edu.uw.easysrl.syntax.grammar.Category;
//import edu.uw.easysrl.main.InputReader;
//import edu.uw.easysrl.qasrl.Parse;
//import edu.uw.easysrl.qasrl.DataLoader;
//import edu.uw.easysrl.qasrl.TextGenerationHelper;
//import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
//
//import org.apache.commons.csv.CSVRecord;
//
//import java.io.*;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * Annotation record from a single annotator in the checkbox setting.
// * Created by julianmichael on 3/4/2016.
// */
//public class RecordedCheckboxAnnotation extends Annotation {
//
//    private static enum GoldSupportMethod {
//        GOLD_BY_STRING, GOLD_BY_PRED_AND_ARG;
//        // GOLD_BY_UNLABELED_ATTACHMENT;
//    }
//    private static final GoldSupportMethod goldSupport = GoldSupportMethod.GOLD_BY_PRED_AND_ARG;
//
//    public int queryId;
//    String answer;
//    boolean checked;
//    boolean goldChecked;
//
//    // XXX? fields for different gold judgment methods
//    int predicateId;
//    Category predicateCategory;
//    int argumentNumber;
//
//    private RecordedCheckboxAnnotation() {
//    }
//
//    private RecordedCheckboxAnnotation(RecordedCheckboxAnnotation other) {
//        super(other);
//        this.queryPrompt = other.queryPrompt;
//        this.queryId = other.queryId;
//        this.answer = other.answer;
//        this.checked = other.checked;
//        this.goldChecked = other.goldChecked;
//
//        this.predicateId = other.predicateId;
//        this.predicateCategory = other.predicateCategory;
//        this.argumentNumber = other.argumentNumber;
//    }
//
//    public static List<RecordedCheckboxAnnotation> loadFromCSVRecordCrowdFlower(CSVRecord record, List<QuestionAnswerPair> goldQAPairs) {
//        List<RecordedCheckboxAnnotation> annotations = new ArrayList<>();
//        RecordedCheckboxAnnotation annotation = new RecordedCheckboxAnnotation();
//        final int sentenceId = Integer.parseInt(record.get("sent_id"));
//        annotation.sentenceId = sentenceId;
//        annotation.sentenceString = record.get("sentence");
//        annotation.queryId = Integer.parseInt(record.get("query_id"));
//        annotation.comment = record.get("comment");
//        annotation.annotatorId = record.get("_worker_id");
//        annotation.trust = Double.parseDouble(record.get("_trust"));
//        annotation.queryPrompt = record.get("queryPrompt");
//
//        annotation.predicateId = Integer.parseInt(record.get("pred_id"));
//        String qkey = record.get("question_key");
//        String[] qkeyInfo = qkey.split("\\.");
//        annotation.predicateCategory = Category.valueOf(qkeyInfo[1]);
//        annotation.argumentNumber = Integer.parseInt(qkeyInfo[2]);
//
//        List<String> userOptions = Arrays.asList(record.get("userOptions").split("\n"));
//        List<String> choices = Arrays.asList(record.get("choice").split("\n"));
//
//        // each answer checkbox is a different annotation;
//        // the variable `annotation` just holds all of the fields common to the ones for every answer.
//        for(String answer : userOptions) {
//            RecordedCheckboxAnnotation answerAnnotation = new RecordedCheckboxAnnotation(annotation);
//            answerAnnotation.answer = answer;
//            answerAnnotation.checked = choices.contains(answer);
//            // compute the gold userOptions
//            answerAnnotation.goldChecked = goldSupportsAnnotation(goldQAPairs, answerAnnotation);
//            annotations.add(answerAnnotation);
//        }
//        return annotations;
//    }
//
//    public static List<RecordedCheckboxAnnotation> loadAnnotationRecordsFromFile(String fileName) throws IOException {
//        final List<List<InputReader.InputWord>> sentencesWords = new ArrayList<>();
//        final List<Parse> goldParses = new ArrayList<>();
//        DataLoader.readDevPool(sentencesWords, goldParses);
//        final List<List<String>> sentencesLists = sentencesWords
//            .stream()
//            .map(sent -> sent.stream().map(iw -> iw.word).collect(Collectors.toList()))
//            .collect(Collectors.toList());
//        final List<String> sentences = sentencesLists
//            .stream()
//            .map(TextGenerationHelper::renderString)
//            .collect(Collectors.toList());
//        final Map<Integer, List<QuestionAnswerPair>> allGoldQAPairs = new HashMap<>();
//
//        List<RecordedCheckboxAnnotation> annotations = new ArrayList<>();
//        BufferedReader reader;
//
//        reader = new BufferedReader(new FileReader(new File(fileName)));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            List<RecordedCheckboxAnnotation> curr = new LinkedList<>();
//            RecordedCheckboxAnnotation pre = new RecordedCheckboxAnnotation();
//            line = line.trim();
//            // Example: ITER=0
//            // if (line.startsWith("ITER=")) {
//            // curr.iterationId = Integer.parseInt(line.split("=")[1]);
//
//            // Example: SID=1199
//            final int sentenceId = Integer.parseInt(line.split("=")[1]);
//            pre.sentenceId = sentenceId;
//            pre.sentenceString = sentences.get(sentenceId);
//
//            // QID=XX
//            reader.readLine();
//
//            // queryPrompt
//            pre.queryPrompt = reader.readLine().trim();
//            pre.predicateId = -1; // these aren't stored by MultiQueries by default, unless that has changed
//
//            // userOptions
//            while (!(line = reader.readLine()).startsWith("COMMENT")) {
//                RecordedCheckboxAnnotation newForAnswer = new RecordedCheckboxAnnotation(pre);
//                String[] info = line.split("\\t");
//                String answer = info[1].trim();
//                newForAnswer.answer = answer;
//                String match = info[0];
//                newForAnswer.checked = (match.contains("*"));
//                if(!answer.contains("Answer not listed.")) {
//                    curr.add(newForAnswer);
//                }
//            }
//
//            // we need to just compute the gold userOptions ourselves.
//            final List<QuestionAnswerPair> goldQAPairs;
//            if(allGoldQAPairs.containsKey(sentenceId)) {
//                goldQAPairs = allGoldQAPairs.get(sentenceId);
//            } else {
//                final List<Parse> goldParse = new LinkedList<>();
//                goldParse.add(goldParses.get(sentenceId));
//                final QueryGeneratorBothWays queryGen = new QueryGeneratorBothWays(sentenceId,
//                                                                                   sentencesLists.get(sentenceId),
//                                                                                   goldParse);
//                goldQAPairs = queryGen.allQAPairs;
//                allGoldQAPairs.put(sentenceId, goldQAPairs);
//            }
//            curr.forEach(anno -> anno.goldChecked = goldSupportsAnnotation(goldQAPairs, anno));
//
//            // comment
//            String comment = line.split("::")[1].trim();
//            curr.forEach(anno -> anno.comment = comment);
//
//            // empty line
//            reader.readLine();
//
//            annotations.addAll(curr);
//        }
//        System.out.println(String.format("Loaded %d annotation records from file: %s.", annotations.size(), fileName));
//        return annotations;
//    }
//
//    public int getAnswerId() {
//        if(checked) {
//            return 1;
//        } else {
//            return 0;
//        }
//    }
//
//    public int getGoldAnswerId() {
//        if(goldChecked) {
//            return 1;
//        } else {
//            return 0;
//        }
//    }
//
//    public List<String> getAnswerOptions() {
//        List<String> opts = new ArrayList<String>();
//        opts.add("unchecked");
//        opts.add("checked");
//        return opts;
//    }
//
//    public String getAnnotationKey() {
//        return String.format("%s\n%d\n%s\n%s",
//                             "RecordedCheckboxAnnotation",
//                             sentenceId,
//                             queryPrompt,
//                             answer);
//    }
//
//    @Override
//    public String toString() {
//        // Number of iteration in user session.
//        String result = "SID=" + sentenceId + "\n"
//            + "Sentence: " + sentenceString + "\n"
//            + "Question: " + queryPrompt + "\n";
//        if(checked) {
//            result += "*";
//        } else {
//            result += " ";
//        }
//        if(goldChecked) {
//            result += "G";
//        } else {
//            result += " ";
//        }
//        result += " " + answer;
//        return result;
//    }
//
//    /*
//    public static void main(String[] args) {
//        String fileName = args[0];
//        try {
//            List<Annotation> annotations = new ArrayList<>(loadAnnotationRecordsFromFile(fileName));
//            double[] stats = InterAnnotatorAgreement.binaryStats(annotations);
//            System.out.println(String.format("Accuracy: %.2f", stats[0]));
//            System.out.println(String.format("Precision: %.2f", stats[1]));
//            System.out.println(String.format("Recall: %.2f", stats[2]));
//            System.out.println(String.format("F1: %.2f", stats[3]));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//*/
//    private static boolean goldSupportsAnnotation(List<QuestionAnswerPair> goldQAPairs, RecordedCheckboxAnnotation candidate) {
//        switch(goldSupport) {
//        case GOLD_BY_STRING: return goldSupportsAnnotationByString(goldQAPairs, candidate);
//        case GOLD_BY_PRED_AND_ARG: return goldSupportsAnnotationByPredAndArg(goldQAPairs, candidate);
//        default: assert false; return false;
//        }
//    }
//
//    private static boolean goldSupportsAnnotationByString(List<QuestionAnswerPair> goldQAPairs, RecordedCheckboxAnnotation candidate) {
//        List<QuestionAnswerPair> questionMatches = goldQAPairs
//            .stream()
//            .filter(qa -> qa.renderQuestion().equals(candidate.queryPrompt))
//            .collect(Collectors.toList());
//        boolean exactMatch = questionMatches
//            .stream()
//            .filter(qa -> qa.renderAnswer().equals(candidate.answer))
//            .findFirst()
//            .isPresent();
//        boolean badQuestion = questionMatches.isEmpty() && candidate.answer.toLowerCase().contains("bad queryPrompt.");
//        return exactMatch || badQuestion;
//    }
//
//    private static boolean goldSupportsAnnotationByPredAndArg(List<QuestionAnswerPair> goldQAPairs, RecordedCheckboxAnnotation candidate) {
//        assert candidate.predicateCategory != null;
//        List<QuestionAnswerPair> questionStringMatches = goldQAPairs
//            .stream()
//            .filter(qa -> qa.renderQuestion().equals(candidate.queryPrompt))
//            .collect(Collectors.toList());
//        boolean exactMatch = goldQAPairs
//            .stream()
//            .filter(qa -> qa.predicateCategory.matches(candidate.predicateCategory))
//            .filter(qa -> qa.predicateIndex == candidate.predicateId)
//            .filter(qa -> qa.targetDep.getArgNumber() == candidate.argumentNumber)
//            .filter(qa -> qa.renderAnswer().equals(candidate.answer))
//            .findFirst()
//            .isPresent();
//        boolean badQuestion = questionStringMatches.isEmpty() && candidate.answer.toLowerCase().contains("bad queryPrompt.");
//        return exactMatch || badQuestion;
//    }
//}
