package edu.uw.easysrl.active_learning;

/**
 * Created by luheng on 1/14/16.
 */
public class TicToc {
    private static long stamp;

    public static void tic() {
        stamp = System.currentTimeMillis();
    }

    // Return time in seconds.
    public static long toc() {
        return (System.currentTimeMillis() - stamp) / 1000;
    }
}
