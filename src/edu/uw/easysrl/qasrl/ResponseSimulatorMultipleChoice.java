package edu.uw.easysrl.qasrl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Scanner;

/**
 * Reads a choice from console.
 * Created by luheng on 1/17/16.
 */
public class ResponseSimulatorMultipleChoice extends ResponseSimulator {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    private Scanner scanner;

    public ResponseSimulatorMultipleChoice() {
        scanner = new Scanner(System.in);
    }
    /**
     *
     * @param query
     * @param sentence
     * @param unusedSideInfo
     * @return
     */
    public Response answerQuestion(QueryOld query, List<String> sentence, Parse unusedSideInfo) {
        int predicateIndex = query.predicateIndex;
        // print sentence
        for (int i = 0; i < sentence.size(); i++) {
            System.out.print(i > 0 ? " " : "");
            System.out.print(i == predicateIndex ? ANSI_YELLOW : "");
            System.out.print(sentence.get(i) + ANSI_RESET);
        }
        System.out.println();
        System.out.println("Q: " + query.question.stream().collect(Collectors.joining(" ")) + "?");
        // TODO: highlight answer in sentence?
        List<Integer> answerCandidates = new ArrayList<>(query.answerToParses.keySet());
        int numAnswers = answerCandidates.size();
        for (int i = 0; i < numAnswers; i++) {
            int answerPositionInSentence = answerCandidates.get(i);
            String answerStr = answerPositionInSentence >= 0 ? sentence.get(answerPositionInSentence) : "N/A";
            System.out.println(String.format("%d: %s", (i + 1), answerStr));
        }
        System.out.println(String.format("Please input a number between 1 to %d", numAnswers));
        int choice = Integer.parseInt(scanner.nextLine());
        List<Integer> answerList = new ArrayList<>();
        if (0 < choice && choice <= numAnswers) {
            answerList.add(answerCandidates.get(choice - 1));
        }
        return new Response(answerList);
    }

    public int answerQuestion(GroupedQuery query, List<String> sentence, Parse goldParse) {
        int predicateIndex = query.predicateIndex;
        // print sentence
        for (int i = 0; i < sentence.size(); i++) {
            System.out.print(i > 0 ? " " : "");
            System.out.print(i == predicateIndex ? ANSI_YELLOW : "");
            System.out.print(sentence.get(i) + ANSI_RESET);
        }
        System.out.println();
        System.out.println("Q: " + query.question + "?");
        // TODO: highlight answer in sentence? print answer spans
        int numAnswers = query.answerOptions.size();
        for (int i = 0; i < numAnswers; i++) {
            GroupedQuery.AnswerOption ao = query.answerOptions.get(i);
            // debugging
            String argIdsStr = ao.argumentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String argHeadsStr = (ao.argumentIds.size() == 0 || ao.argumentIds.get(0) == -1) ? "N/A" :
                    ao.argumentIds.stream().map(sentence::get).collect(Collectors.joining(","));
            System.out.println(String.format("%d: %s", (i + 1), ao.answer) + "\t" + argIdsStr + "\t" + argHeadsStr);
        }
        System.out.println(String.format("Please input a number between 1 to %d", numAnswers));
        int choice = Integer.parseInt(scanner.nextLine());
        return (0 < choice && choice <= numAnswers) ? choice - 1 : -1;
    }

    public static void main(String[] args) {

    }
}
