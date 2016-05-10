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

    public static <T extends Predication> T withIndefinitePronouns(T pred) {
        if(pred instanceof Noun) {
            return (T) ((Noun) pred).getPronoun().withDefiniteness(Noun.Definiteness.INDEFINITE);
        } else {
            return (T) pred.transformArgs((argNum, args) -> {
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
        }
    }

    public static <T extends Predication> T addPlaceholderArguments(T pred) {
        return (T) pred.transformArgs((argNum, args) -> {
                if(args.size() > 0) {
                    return args.stream()
                        .map(arg -> new Argument(arg.getDependency(), addPlaceholderArguments(arg.getPredication())))
                        .collect(toImmutableList());
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

    public static <T extends Predication> ImmutableList<T> sequenceArgChoices(T pred) {
        final ImmutableList<ImmutableMap<Integer, Argument>> sequencedArgChoices = sequenceMap(pred.getArgs());
        return (ImmutableList<T>) sequencedArgChoices.stream()
            .map(argChoice -> pred.transformArgs((argNum, args) -> ImmutableList.of(argChoice.get(argNum))))
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
