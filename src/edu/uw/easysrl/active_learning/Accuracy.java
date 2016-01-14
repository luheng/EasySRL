package edu.uw.easysrl.active_learning;

/**
 * Accuracy ...
 * Created by luheng on 1/11/16.
 */
public class Accuracy {
    int numCorrect, numTotal;

    public Accuracy() {
        numCorrect = 0;
        numTotal = 0;
    }

    public void add(boolean correct) {
        if (correct) {
            ++ numCorrect;
        }
        ++ numTotal;
    }

    public void add(Accuracy other) {
        numCorrect += other.numCorrect;
        numTotal += other.numTotal;
    }

    public double getAccuracy() {
        return 1.0 * numCorrect / numTotal;
    }

    public String toString() {
        return String.valueOf(getAccuracy());
    }
}
