package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.Parse;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public final class PredicationUtils {


    public static Predication withIndefinitePronouns(Predication pred) {
        if(pred instanceof Verb) {
            return ((Verb) pred).transformArgs((argNum, args) -> {
                    if(Category.NP.matches(pred.getPredicateCategory().getArgument(argNum))) {
                        final Pronoun pro;
                        if(args.isEmpty()) {
                            pro = Pronoun.fromString("something").get();
                        } else {
                            pro = ((Noun) args.get(0).getPredication())
                                .getPronoun()
                                .withDefiniteness(Noun.Definiteness.INDEFINITE);
                        }
                        return ImmutableList.of(Argument.withNoDependency(pro));
                    } else {
                        return args.stream()
                            .map(arg -> new Argument(arg.getDependency(), withIndefinitePronouns(arg.getPredication())))
                            .collect(toImmutableList());
                    }
                });
        } else if(pred instanceof Noun) {
            return ((Noun) pred).getPronoun().withDefiniteness(Noun.Definiteness.INDEFINITE);
        } else {
            System.err.println("Skipping replacing args with indefinite pronouns. pred: " +
                               pred.getPredicate() + " (" + pred.getPredicateCategory() + ")");
            return pred;
        }
    }

    // :( this is sad. need better abstraction. thing to transform from any A <: Predication to A.
    public static Verb addPlaceholderArguments(Verb verb) {
        return (Verb) addPlaceholderArguments((Predication) verb);
    }

    public static Predication addPlaceholderArguments(Predication pred) {
        return pred.transformArgs((argNum, args) -> {
                if(args.size() > 0) {
                    return args;
                } else {
                    // TODO XXX should add indefinite pro-form of the correct category.
                    if(!pred.getPredicateCategory().getArgument(argNum).matches(Category.NP)) {
                        System.err.println("Filling in empty non-NP argument with pronoun. initial pred: " +
                                           pred.getPredicate() + " (" + pred.getPredicateCategory() + ")");
                        System.err.println("arg category: " + pred.getPredicateCategory().getArgument(argNum));
                    }
                    return ImmutableList.of(Argument.withNoDependency(Pronoun.fromString("something").get()));
                }
            });
    }

    public static ImmutableList<Verb> sequenceVerbArgChoices(Verb verb) {
        final ImmutableList<ImmutableMap<Integer, Argument>> sequencedArgChoices = sequenceMap(verb.getArgs());
        return sequencedArgChoices.stream()
            .map(argChoice -> verb.transformArgs((argNum, args) -> ImmutableList.of(argChoice.get(argNum))))
            .collect(toImmutableList());
    }

    public static <A, B> ImmutableList<ImmutableMap<A, B>> sequenceMap(ImmutableMap<A, ImmutableList<B>> map) {
        Stream<ImmutableMap<A, B>> paths = Stream.of(ImmutableMap.of());
        for(Map.Entry<A, ImmutableList<B>> mapEntry : map.entrySet()) {
            A key = mapEntry.getKey();
            ImmutableList<B> choices = mapEntry.getValue();
            paths = paths
                .flatMap(path -> choices.stream()
                .map(choice -> new ImmutableMap.Builder<A, B>()
                     .putAll(path)
                     .put(key, choice)
                     .build()));
        }
        return paths.collect(toImmutableList());
    }

}
