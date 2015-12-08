package edu.uw.easysrl.util;

import java.util.Collection;
import java.util.HashSet;

/**
 * Created by luheng on 11/12/15.
 */
public class QAUtils {
    public static Collection<String> asSet(String[] arr) {
        Collection<String> result = new HashSet<>();
        for (String str : arr) {
            result.add(str);
        }
        return result;
    }
}
