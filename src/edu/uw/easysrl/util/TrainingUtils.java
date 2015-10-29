package edu.uw.easysrl.util;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.syntax.training.Optimization;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Created by luheng on 10/28/15.
 */
public class TrainingUtils {


    public static List<String> parseStrings(final Properties settings, final String field) {
        return Arrays.asList(settings.getProperty(field).split(";"));
    }

    public static List<Integer> parseIntegers(final Properties settings, final String field) {
        return parseStrings(settings, field).stream().map(x -> Integer.valueOf(x)).collect(Collectors.toList());
    }

    public static List<Double> parseDoubles(final Properties settings, final String field) {
        return parseStrings(settings, field).stream().map(x -> Double.valueOf(x)).collect(Collectors.toList());
    }



}
