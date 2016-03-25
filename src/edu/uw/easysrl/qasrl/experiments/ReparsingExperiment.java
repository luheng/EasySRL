package edu.uw.easysrl.qasrl.experiments;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils.*;
import edu.uw.easysrl.qasrl.model.Evidence;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Round1-3: We can change about 10% of the sentences and improve about 5%.
 * Average cost: if we don't ask PP questions, avg cost is about 30c per sentence ...
 * Created by luheng on 3/20/16.
 */
public class ReparsingExperiment {

    private static final int nBest = 100;
    private static HITLParser myHTILParser;
    private static Map<Integer, List<AlignedAnnotation>> annotations;

    private static final String[] annotationFiles = {
            "./Crowdflower_data/f878213.csv",
            "./Crowdflower_data/f882410.csv",
            "./Crowdflower_data/all-checkbox-responses.csv"
    };

    public static void main(String[] args) {
        myHTILParser = new HITLParser(nBest);
        annotations = ExperimentUtils.loadCrowdflowerAnnotation(annotationFiles);
        assert annotations != null;

        runExperiment();
    }

    private static void runExperiment() {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
            annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());

        Results avgBaseline = new Results(),
                avgReranked = new Results(),
                avgReparsed = new Results(),
                avgBeselineChanged = new Results(),
                avgReparsedChanged = new Results(),
                avgOracleChanged = new Results();
        int numWorsenedSentences = 0,
                numUnchangedSentences = 0,
                numImprovedSentences = 0,
                numChanged = 0;

        List<DebugBlock> debugging = new ArrayList<>();
        for (int sentenceId : sentenceIds) {
            final ImmutableList<String> sentence = myHTILParser.getSentence(sentenceId);
            final NBestList nBestList = myHTILParser.getNBestList(sentenceId);

            final List<AlignedAnnotation> annotated = annotations.get(sentenceId);
            boolean isCheckboxStyle = !annotated.stream()
                    .anyMatch(annot -> annot.answerOptions.stream()
                            .anyMatch(op -> op.contains(QAPairAggregatorUtils.answerDelimiter)));

            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queryList =
                    myHTILParser.getAllQueriesForSentence(sentenceId, false /* jeopardy */, isCheckboxStyle);

            int oracleParseId = nBestList.getOracleId();
            Results oracleF1 = nBestList.getResults(oracleParseId);
            Results baselineF1 = nBestList.getResults(0);
            Results currentF1 = nBestList.getResults(0);
            Results currentReparsedF1 = nBestList.getResults(0);

            Parse reparse = null;

            final int numParses = nBestList.getN();
            final Set<Evidence> allEvidenceSet = new HashSet<>();
            int queryCount = 0;
            String sentenceDebuggingString = "";
            double[] penalty = new double[numParses];
            Arrays.fill(penalty, 0);

            for (ScoredQuery<QAStructureSurfaceForm> query : queryList) {
                AlignedAnnotation annotation = ExperimentUtils.getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                queryCount ++;
                int[] optionDist = QualityControl.getUserResponses(query, annotation);
                ImmutableList<Integer> goldOptions = myHTILParser.getGoldOptions(query),
                                       oneBestOptions = myHTILParser.getOneBestOptions(query),
                                       oracleOptions = myHTILParser.getOracleOptions(query),
                                       userOptions = myHTILParser.getUserOptions(query, annotation);

                ImmutableSet<Evidence> evidenceSet = myHTILParser.getEvidenceSet(query, userOptions);
                if (evidenceSet == null || evidenceSet.isEmpty()) {
                    continue;
                }
                allEvidenceSet.addAll(evidenceSet);

                int rerankedId = myHTILParser.getRerankedParseId(sentenceId, allEvidenceSet);
                reparse = myHTILParser.getReparsed(sentenceId, allEvidenceSet);
                Results rerankedF1 = nBestList.getResults(rerankedId);
                Results reparsedF1 = CcgEvaluation.evaluate(reparse.dependencies,
                        myHTILParser.getGoldParse(sentenceId).dependencies);

                // Print debugging information.
                String sentenceStr = sentence.stream().collect(Collectors.joining(" "));
                int predId = query.getPredicateId().getAsInt();
                Category category = query.getPredicateCategory().get();
                int argNum = query.getArgumentNumber().getAsInt();
                String result =  "SID=" + sentenceId + "\t" + sentenceStr + "\n" + "PRED=" + predId+ "\t \t"
                        + query.getPrompt() + "\t" + category + "." + argNum + "\t"
                        + String.format("Baseline:: %.3f%%\tOracle:: %.3f%%\tOracle ParseId::%d\tRerank ParseId::%d\n",
                        100.0 * baselineF1.getF1(), 100.0 * oracleF1.getF1(), oracleParseId, rerankedId);
                result += evidenceSet.stream()
                        .map(ev -> "Penalizing:\t" + ev.toString(sentence))
                        .collect(Collectors.joining("\n")) + "\n";

                // TODO: add gold, oracle and one-best penalty.
                for (int j = 0; j < optionDist.length; j++) {
                    String matchStr = "";
                    for (int k = 0; k < optionDist[j]; k++) {
                        matchStr += "*";
                    }
                    matchStr += (goldOptions.contains(j) ? "G" : "") +
                                (oracleOptions.contains(j) ? "O" : "") +
                                (oneBestOptions.contains(j) ? "U" : "");
                    String option = query.getOptions().get(j);
                    String headStr = "-";
                    if (j < query.getQAPairSurfaceForms().size()) {
                        QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(j);
                        final List<Integer> argList = qa.getArgumentIndices();
                        headStr = DebugPrinter.getShortListString(argList) + ":" +
                                argList.stream().map(sentence::get).collect(Collectors.joining(","));
                    }
                    String parseIdsStr = DebugPrinter.getShortListString(query.getOptionToParseIds().get(j));
                    result += String.format("%-8s\t%.3f\t%-40s\t%-30s\t-\t-\t%s\n",
                            matchStr, query.getOptionScores().get(j), option, headStr, parseIdsStr);
                }
                String f1Impv = " ";
                if (rerankedF1 != null) {
                    if (rerankedF1.getF1() < currentF1.getF1() - 1e-8) {
                        f1Impv = "[-]";
                    } else if (rerankedF1.getF1() > currentF1.getF1() + 1e-8) {
                        f1Impv = "[+]";
                    }
                }

                result += String.format("F1: %.3f%% -> %.3f%% %s\n",
                        100.0 * currentReparsedF1.getF1(),
                        100.0 * reparsedF1.getF1(),
                        f1Impv);
                result += String.format("Reranked F1: %.3f%%\n", 100.0 * rerankedF1.getF1());
                result += String.format("Reparsed F1: %.3f%%\n", 100.0 * reparsedF1.getF1());
                sentenceDebuggingString += result + "\n";
                currentF1 = rerankedF1;
                currentReparsedF1 = reparsedF1;
            }
            boolean changedOneBest = reparse != null &&
                    CcgEvaluation.evaluate(reparse.dependencies, nBestList.getParse(0).dependencies).getF1()
                            < 1.0 - 1e-3;
            double deltaF1 = currentReparsedF1.getF1() - baselineF1.getF1();
            String changeStr;
            if (deltaF1 < - 1e-8) {
                numWorsenedSentences ++;
                changeStr = "Worsened.";
            } else if (deltaF1 > 1e-8) {
                numImprovedSentences ++;
                changeStr = "Improved.";
            } else {
                numUnchangedSentences ++;
                changeStr = "Unchanged.";
            }

            avgBaseline.add(baselineF1);
            avgReranked.add(currentF1);
            avgReparsed.add(currentReparsedF1);

            if (changedOneBest) {
                avgBeselineChanged.add(baselineF1);
                avgReparsedChanged.add(currentReparsedF1);
                avgOracleChanged.add(oracleF1);
                numChanged ++;
            }
            if (queryCount > 0) {
                sentenceDebuggingString += String.format("Final F1: %.3f%% over %.3f%% baseline.\t%s\n",
                        100.0 * currentReparsedF1.getF1(),
                        100.0 * baselineF1.getF1(),
                        changeStr);
                debugging.add(new DebugBlock(deltaF1, sentenceDebuggingString));
            }
        }

        System.out.println(
                "Baseline:\n" + avgBaseline + "\n" +
                "Reranked:\n" + avgReranked + "\n" +
                "Reparsed:\n" + avgReparsed);

        System.out.println(
                "Baseline-changed\n" + avgBeselineChanged + "\n" +
                "Reparsed-changed:\n" + avgReparsedChanged + "\n" +
                "Oracle-changed\n" + avgOracleChanged);

        System.out.println(
                "Num modified: " + numChanged + "\n" +
                "Num improved: " + numImprovedSentences + "\n" +
                "Num worsened: " + numWorsenedSentences + "\n" +
                "Num the same: " + numUnchangedSentences);

        debugging.stream()
                .sorted((b1, b2) -> Double.compare(b1.deltaF1, b2.deltaF1))
                // .filter(b -> Math.abs(b.deltaF1) > 1e-3)
                .forEach(b -> System.out.println(b.block));
    }
}
