package edu.uw.easysrl.qasrl.analysis;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/2/16.
 */
public class PPAttachment {
    public static final Category verbAdjunct = Category.valueOf("((S\\NP)\\(S\\NP))/NP");
    public static final Category nounAdjunct = Category.valueOf("(NP\\NP)/NP");

    List<String> sentence;
    int ppHead;

    Map<String, Set<Parse>> verbArgQuestions;
    Map<String, Set<Parse>> verbAdjQuestions;
    Map<String, Set<Parse>> nounAdjQuestions;
    Map<String, Set<Parse>> ppArgQuestions;

    Table<Integer, String, Set<Parse>> verbArgAnswers;
    Table<Integer, String, Set<Parse>> verbAdjAnswers;
    Table<Integer, String, Set<Parse>> nounAdjAnswers;
    Table<Integer, String, Set<Parse>> ppArgAnswers;

    Set<Integer> qaIds;

    public static Optional<PPAttachment> findPPAttachmentAmbiguity(int ppHead, List<String> sentence,
                                                                   List<QuestionAnswerPairReduced> qaList,
                                                                   List<Parse> parseList) {
        PPAttachment ppAttachment = new PPAttachment();
        ppAttachment.ppHead = ppHead;
        ppAttachment.sentence = sentence;

        // Look for noun/verb adjunct attachment.
        for (int i = 0; i < qaList.size(); i++) {
            final QuestionAnswerPairReduced qa = qaList.get(i);
            final Parse parse = parseList.get(i);
            int argNum = qa.targetDep.getArgNumber();
            int argId = qa.targetDep.getArgument();
            Category category = qa.predicateCategory;
            if (qa.predicateIndex == ppHead) {
                if (qa.predicateCategory == verbAdjunct) {
                    if (argNum == 2) {
                        AnalysisHelper.addQA(argId, qa, parse, ppAttachment.verbAdjQuestions, ppAttachment.verbAdjAnswers);
                        ppAttachment.qaIds.add(i);
                    } else if (argNum == 3) {
                        AnalysisHelper.addQA(argId, qa, parse, ppAttachment.ppArgQuestions, ppAttachment.ppArgAnswers);
                        ppAttachment.qaIds.add(i);
                    }
                } else if (qa.predicateCategory == nounAdjunct) {
                    if (argNum == 1) {
                        AnalysisHelper.addQA(argId, qa, parse, ppAttachment.nounAdjQuestions, ppAttachment.nounAdjAnswers);
                        ppAttachment.qaIds.add(i);
                    } else if (argNum == 2) {
                        AnalysisHelper.addQA(argId, qa, parse, ppAttachment.ppArgQuestions, ppAttachment.ppArgAnswers);
                        ppAttachment.qaIds.add(i);
                    }
                }
            } else if (argId == ppHead && category.getArgument(argNum) == Category.PP) {
                AnalysisHelper.addQA(qa.predicateIndex, qa, parse, ppAttachment.verbArgQuestions, ppAttachment.verbArgAnswers);
                ppAttachment.qaIds.add(i);
            }
        }
        int ambiguity = (ppAttachment.verbAdjQuestions.isEmpty() ? 0 : 1)
                        + (ppAttachment.nounAdjQuestions.isEmpty() ? 0 : 1)
                        + (ppAttachment.verbArgQuestions.isEmpty() ? 0 : 1);
        return ambiguity > 1 ? Optional.of(ppAttachment) : Optional.empty();
    }

    public static double getAttchmentScore(Map<String, Set<Parse>> questions, Table<Integer, String, Set<Parse>> answers,
                                           List<Parse> allParses) {
        Set<Parse> parses = new HashSet<>();
        questions.values().forEach(parses::addAll);
        return AnalysisHelper.getScore(parses) / AnalysisHelper.getScore(allParses);
    }

    private PPAttachment() {
        verbAdjAnswers = HashBasedTable.create();
        verbArgAnswers = HashBasedTable.create();
        nounAdjAnswers = HashBasedTable.create();
        ppArgAnswers = HashBasedTable.create();

        verbAdjQuestions = new HashMap<>();
        verbArgQuestions = new HashMap<>();
        nounAdjQuestions = new HashMap<>();
        ppArgQuestions = new HashMap<>();

        qaIds = new HashSet<>();
    }

    public static void main(String[] args) {
        POMDP learner = new POMDP(100 /* nbest */, 10000 /* horizon */, 0.0 /* money penalty */);
        int numAmbiguousPPAttachments = 0;
        int totalQAs = 0, coveredQAs = 0;
        for (int sid : learner.allParses.keySet()) {
            final List<String> sentence = learner.getSentenceById(sid);
            final List<Parse> allParses = learner.allParses.get(sid);
            if (allParses == null) {
                continue;
            }
            List<QuestionAnswerPairReduced> qaList = new ArrayList<>();
            List<Parse> parseList = new ArrayList<>();
            for (int parseId = 0; parseId < allParses.size(); parseId++) {
                final Parse parse = allParses.get(parseId);
                for (int predId = 0; predId < sentence.size(); predId++) {
                    final Category category = allParses.get(parseId).categories.get(predId);
                    for (int argNum = 1; argNum <= category.getNumberOfArguments(); argNum++) {
                        QuestionGenerator.generateAllQAPairs(predId, argNum, sentence, parse).forEach(qa -> {
                            qaList.add(qa);
                            parseList.add(parse);
                        });
                    }
                }
            }
            List<PPAttachment> ppAmbiguities = new ArrayList<>();
            for (int predId = 0; predId < sentence.size(); predId++) {
                Optional<PPAttachment> ppAttachmentOpt = findPPAttachmentAmbiguity(predId, sentence, qaList, parseList);
                if (ppAttachmentOpt.isPresent()) {
                    ppAmbiguities.add(ppAttachmentOpt.get());
                }
            }
            if (ppAmbiguities.isEmpty()) {
                continue;
            }

            if (sid < 500) {
                // Print
                System.out.println("SID=" + sid + "\t" + sentence.stream().collect(Collectors.joining(" ")));
                for (PPAttachment ppAttachment : ppAmbiguities) {
                    System.out.println(String.format("%d:%s", ppAttachment.ppHead, sentence.get(ppAttachment.ppHead)));
                    System.out.println(String.format("[verb-adjunct]\t \t%.3f",
                            getAttchmentScore(ppAttachment.verbAdjQuestions, ppAttachment.verbAdjAnswers, allParses)));
                    AnalysisHelper.printCollapsed(ppAttachment.verbAdjQuestions, ppAttachment.verbAdjAnswers, sentence, allParses);

                    System.out.println(String.format("[noun-adjunct]\t \t%.3f",
                            getAttchmentScore(ppAttachment.nounAdjQuestions, ppAttachment.nounAdjAnswers, allParses)));
                    AnalysisHelper.printCollapsed(ppAttachment.nounAdjQuestions, ppAttachment.nounAdjAnswers, sentence, allParses);

                    System.out.println(String.format("[pp-argument]\t \t%.3f",
                            getAttchmentScore(ppAttachment.ppArgQuestions, ppAttachment.ppArgAnswers, allParses)));
                    AnalysisHelper.printCollapsed(ppAttachment.ppArgQuestions, ppAttachment.ppArgAnswers, sentence, allParses);

                    System.out.println(String.format("[verb-argument]\t \t%.3f",
                            getAttchmentScore(ppAttachment.verbArgQuestions, ppAttachment.verbArgAnswers, allParses)));
                    AnalysisHelper.printCollapsed(ppAttachment.verbArgQuestions, ppAttachment.verbArgAnswers, sentence, allParses);
                }
                System.out.println();
            }
            totalQAs += qaList.size();
            coveredQAs += ppAmbiguities.stream().mapToInt(pp -> pp.qaIds.size()).sum();
            numAmbiguousPPAttachments += ppAmbiguities.size();
        }
        System.out.println("Found " + numAmbiguousPPAttachments + " ambiguous PP attachments.");
        System.out.println(String.format("Covered %d (%.3f%%) of the total %d QAs.",
                coveredQAs, 100.0 * coveredQAs / totalQAs, totalQAs));
    }
}
