package edu.uw.easysrl.qasrl;

/**
 * A simple interactive prototype
 * ref: http://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println
 * Created by luheng on 1/17/16.
 */
public class ActiveLearningInteractive {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public static void main(String[] args) {
        System.out.println(ANSI_GREEN + "This text is green!" + ANSI_RESET);
    }
}
