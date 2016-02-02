package edu.uw.easysrl.qasrl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Simulated response.
 * Created by luheng on 1/29/16.
 */
public class Response {
    List<Integer> chosenOptions;
    // For debugging use.
    String debugginInfo;

    public Response() {
        chosenOptions = new ArrayList<>();
    }

    public Response(int option) {
        chosenOptions = new ArrayList<>();
        chosenOptions.add(option);
    }

    public void add(int option) {
        this.chosenOptions.add(option);
    }

    public Response(Collection<Integer> options) {
        this.chosenOptions = new ArrayList<>(options);
        Collections.sort(this.chosenOptions);
    }
}
