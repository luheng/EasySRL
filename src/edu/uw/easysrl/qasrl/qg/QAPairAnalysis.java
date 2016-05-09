package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import java.util.function.Predicate;
import java.util.function.Function;

import java.util.stream.Stream;

/**
 * Utility methods to operate on QuestionAnswerPairs and QAPairSurfaceForms.
 *
 * This class is for LOGIC, NOT DATA.
 * Use cases I have in mind inlude, for example, filtering out QAPairSurfaceForms
 * that don't contain certain classes of questions, if we want to do a restricted experiment.
 * Or, categorizing the QAPairSurfaceForms for error analysis.
 *
 * Why put the logic in this class, and not in methods on QAPairSurfaceForm?
 * The reasoning is that there will probably be a lot of related logic
 * that does not all take the form of functions taking just a single QAPairSurfaceForm.
 * Some might be functions on QuestionAnswerPairs;
 * others might be functions on lists of QAPairSurfaceForms.
 * But it's convenient to have all of these things
 * (which will likely make use of each other)
 * in one place, instead of having to hunt around various classes for them.
 *
 * Created by julianmichael on 3/17/2016.
 */
public class QAPairAnalysis {

    // TODO methods to extract interesting information from QuestionAnswerPairs and QAPairSurfaceForms.

    // maybe this can be useful in calculating queryPrompt confidence?
    public static ImmutableSet<Integer> parseIdsSupportingQuestionString(String question,
                                                                         ImmutableList<QuestionAnswerPair> qaPairs) {
        return qaPairs.stream()
            .filter(qaPair -> qaPair.getQuestion().equals(question))
            .map(QuestionAnswerPair::getParseId)
            .collect(toImmutableSet());
    }

    /**
     * This method will probably serve most of our needs; example would be
     *   getAll(surfaceForm, QuestionAnswerPair::getPredicateCategory).collect(toImmutableSet())
     * to get us a set of all predicate categories aggregated into the surface form.
     * These are simple and varied enough to do inline instead of preparing a bunch of helper methods for them.
     * Returning a stream because depending on the situation we might want a list or set (or to map it some more).
     */
    public static <T> Stream<T> getAll(QAPairSurfaceForm surfaceForm, Function<QuestionAnswerPair, T> mapper) {
        return surfaceForm.getQAPairs()
            .stream()
            .map(mapper);
    }

    // TODO methods for analyzing queryPrompt confidence, answer confidence, that kind of thing?
    // we need to access the list of all parses; probably those should be passed in as parameters.

    // TODO useful, complex predicates on queryPrompt answer pairs go here as static methods

    public static boolean forAny(QAPairSurfaceForm surfaceForm, Predicate<QuestionAnswerPair> pred) {
        return surfaceForm.getQAPairs()
            .stream()
            .anyMatch(pred);
    }

    private QAPairAnalysis() {
        throw new AssertionError("no instances.");
    }
}
