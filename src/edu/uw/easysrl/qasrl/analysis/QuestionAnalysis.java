package edu.uw.easysrl.qasrl.analysis;

import edu.uw.easysrl.qasrl.Accuracy;
import edu.uw.easysrl.qasrl.AnalysisHelper;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/4/16.
 */
public class QuestionAnalysis {

    static final int topK = 5;
    static final double minScore = 0.1;
    static final double maxMargin = 0.3;
    static int totalGold;
    static int[] topKCoverGold = new int[topK];
    static int[] topKTotal = new int[topK];
    static Accuracy cumulative05, cumulative07;
    static int c05Total = 0, c07Total = 0;

    private static void printQuestions(final TObjectDoubleHashMap<String> questionToScore,
                                       final Set<String> goldQuestions, List<String> sentence,
                                       int predHead, Category category, int argNum) {
        List<String> sortedQuestions = questionToScore.keySet().stream()
                .sorted((q1, q2) -> Double.compare(-questionToScore.get(q1), -questionToScore.get(q2)))
                .collect(Collectors.toList());
        // Skipping unambiguously matched cases.
        /*if (questionToScore.size() == 1 && goldQuestions.contains(sortedQuestions.get(0))) {
            return;
        }*/
        System.out.println(String.format("%d:%s\t%s.%d", predHead, sentence.get(predHead), category, argNum));
        System.out.println("[generated]");
        int count = 0;
        double cumulative = 0;
        boolean c05 = false, c07 = false;
        double topScore = questionToScore.get(sortedQuestions.get(0));
        for (String question : sortedQuestions) {
            boolean match = goldQuestions.contains(question);
            double score = questionToScore.get(question);
            for (int i = count; i < topK; i++) {
                //if (count == 0 || topScore - questionToScore.get(question) < maxMargin) {
                if (count == 0 || score > minScore) {
                    topKTotal[i]++;
                    topKCoverGold[i] += (match ? 1 : 0);
                }
            }
            if (cumulative < 0.50001) {
                c05 |= match;
                c05Total ++;
            }
            if (cumulative < 0.70001) {
                c07 |= match;
                c07Total ++;
            }
            cumulative += score;
            count ++;
            System.out.println(((match ? "*" : " ") + "\t" + question + "\t" + questionToScore.get(question)));
        }
        cumulative05.add(c05);
        cumulative07.add(c07);
        System.out.println("[gold]");
        goldQuestions.forEach(q -> System.out.println("\t" + q));
        System.out.println();
    }

    public static void main(String[] args) {
        POMDP learner = new POMDP(100 /* nbest */, 10000 /* horizon */, 0.0 /* money penalty */);

        totalGold = 0;
        Arrays.fill(topKCoverGold, 0);
        Arrays.fill(topKTotal, 0);
        cumulative05 = new Accuracy();
        cumulative07 = new Accuracy();

        for (int sid : learner.allParses.keySet()) {
            final List<String> sentence = learner.getSentenceById(sid);
            final List<Parse> allParses = learner.allParses.get(sid);
            final Parse goldParse = learner.goldParses.get(sid);
            if (allParses == null) {
                continue;
            }
            if (sid < 500) {
                System.out.println("SID=" + sid + "\t" + sentence.stream().collect(Collectors.joining(" ")));
            }
            double totalScore = AnalysisHelper.getScore(allParses);
            for (int predHead = 0; predHead < sentence.size(); predHead++) {
                Map<Category, Set<Integer>> taggings = new HashMap<>();
                for (int parseId = 0; parseId < allParses.size(); parseId ++) {
                    Parse parse = allParses.get(parseId);
                    Category category = parse.categories.get(predHead);
                    if (!taggings.containsKey(category)) {
                        taggings.put(category, new HashSet<>());
                    }
                    taggings.get(category).add(parseId);
                }
                for (Category category : taggings.keySet()) {
                    for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                        List<QuestionAnswerPairReduced> goldQAList;
                        Set<String> goldQuestions = new HashSet<>();
                        if (goldParse.categories.get(predHead) == category) {
                            goldQAList = QuestionGenerator.generateAllQAPairs(predHead, argNum, sentence, goldParse);
                            goldQAList.forEach(qa -> goldQuestions.add(qa.renderQuestion()));
                        }
                        Map<String, List<QuestionAnswerPairReduced>> questionToQAList = new HashMap<>();
                        Map<String, Set<Integer>> questionToParseIds = new HashMap<>();
                        TObjectDoubleHashMap<String> questionToScore = new TObjectDoubleHashMap<>();
                        Set<Integer> answerHeads = new HashSet<>();
                        for (int parseId : taggings.get(category)) {
                            QuestionGenerator.generateAllQAPairs(predHead, argNum, sentence, allParses.get(parseId))
                                    .forEach(qa -> {
                                        String question = qa.renderQuestion();
                                        if (!questionToParseIds.containsKey(question)) {
                                            questionToParseIds.put(question, new HashSet<>());
                                        }
                                        questionToParseIds.get(question).add(parseId);
                                        answerHeads.add(qa.targetDep.getArgument());
                                    });

                        }
                        if (answerHeads.size() < 2) {
                            continue;
                        }
                        questionToParseIds.keySet().forEach(question -> {
                            questionToParseIds.get(question).forEach(parseId -> {
                                double score = allParses.get(parseId).score / totalScore;
                                questionToScore.adjustOrPutValue(question, score, score);
                            });
                        });
                        //if (sid < 500 && questionToScore.size() > 0 && goldQuestions.size() > 0) {
                        if (questionToScore.size() > 0) {
                            printQuestions(questionToScore, goldQuestions, sentence, predHead, category, argNum);
                        }
                        if (goldQuestions.size() > 0) {
                            totalGold++;
                        }
                    }
                }
            }
        }
        System.out.println("Total match cases:\t" + totalGold);
        for (int i = 0; i < topK; i++) {
            System.out.println(String.format("%d\t%d\t%.3f\t%d\t%.3f%%", i, topKTotal[i],
                    1.0 * topKTotal[i] / learner.allParses.size(),
                    topKCoverGold[i], 100.0 * topKCoverGold[i] / totalGold));
        }
        System.out.println("cumulative 0.5:\t" + 1.0 * cumulative05.numCorrect / totalGold + "\t from total:\t" + c05Total);
        System.out.println("cumulative 0.7:\t" + 1.0 * cumulative07.numCorrect / totalGold + "\t from total:\t" + c07Total);
        //System.out.println("Found " + numCases + " pseudo-coordination cases.");
        //System.out.println(String.format("Covered %d QAs.", coveredQAs));
    }
}
