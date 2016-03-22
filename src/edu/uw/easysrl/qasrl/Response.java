package edu.uw.easysrl.qasrl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Simulated response.
 * Created by luheng on 1/29/16.
 */
@Deprecated
public class Response {
    public List<Integer> chosenOptions;
    public double trust = 1.0;
    // For debugging use.
    public String debugInfo = "";

    public Response() {
        chosenOptions = new ArrayList<>();
    }

    public Response(int option) {
        chosenOptions = new ArrayList<>();
        chosenOptions.add(option);
    }

    public void add(int option) {
        if (!chosenOptions.contains(option)) {
            chosenOptions.add(option);
        }
    }

    public Response(Collection<Integer> options) {
        this.chosenOptions = new ArrayList<>(options);
        Collections.sort(this.chosenOptions);
    }
}
