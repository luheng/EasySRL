package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import java.util.function.Predicate;

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
 * in one place.
 *
 * Created by julianmichael on 3/17/2016.
 */
public class QAPairAnalyzer {

    // TODO methods to extract interesting information from QAPairSurfaceForms

    public static ImmutableSet<ResolvedDependency> getAllTargetDependencies(QAPairSurfaceForm surfaceForm) {
        return surfaceForm.getQAPairs()
            .stream()
            .map(IQuestionAnswerPair::getTargetDependency)
            .collect(toImmutableSet());
    }

    // TODO methods for analyzing question confidence, answer confidence, that kind of thing?
    // we need to access the list of all parses; how are we going to pass that around...

    // TODO predicates on question answer pairs go here as static methods

    // TODO other static methods to filter surface forms in more specific ways go here as well

    // these methods I'm suggesting (and the one below) might not be necessary if we just have enough
    // useful methods to extract the necessary information from surface forms to do these filters and stuff
    // in-line.

    public static <T extends QAPairSurfaceForm> ImmutableList<T>
        filterIfAny(ImmutableList<T> surfaceForms,
                    Predicate<IQuestionAnswerPair> pred) {
        return surfaceForms
            .stream()
            .filter(sf -> sf.getQAPairs()
                    .stream()
                    .anyMatch(pred))
            .collect(toImmutableList());
    }

    // NOTE: add filterIfAll if it's ever necessary (probably won't be...)

    private QAPairAnalyzer() {
        throw new AssertionError("no instances.");
    }
}
