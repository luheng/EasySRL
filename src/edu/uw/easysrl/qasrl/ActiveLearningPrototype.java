package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import uk.co.flamingpenguin.jewel.cli.CliFactory;

import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Active Learning experiments (n-best reranking).
 * Created by luheng on 1/5/16.
 */
public class ActiveLearningPrototype {
    // Training pool.
    static List<List<InputWord>> sentences;
    static List<Parse> goldParses;

    // Modules :)
    static BaseCcgParser parser;
    static QuestionGenerator questionGenerator;
    static ResponseSimulator responseSimulator;

    public static StringBuffer debugOutput;

    public static void main(String[] args) {
        // TODO: timer
        initialize(args, 5000);
        run();
    }

    private static void initialize(String[] args, int nBest) {
        // TODO: use ActiveLearning.CommandLineArguments
        EasySRL.CommandLineArguments commandLineOptions;
        try {
            commandLineOptions = CliFactory.parseArguments(EasySRL.CommandLineArguments.class, args);
        } catch (Exception e) {
            return;
        }
        // Initialize corpora.
        sentences = new ArrayList<>();
        goldParses = new ArrayList<>();
        DataLoader.readDevPool(sentences, goldParses);
        // Initialize parser.
        parser = new BaseCcgParser.EasyCCGParser(commandLineOptions.getModel(), nBest);
        // Initialize the other modules.
        questionGenerator = new QuestionGenerator();
        responseSimulator = new ResponseSimulator(questionGenerator);
    }

    private static void run() {
        debugOutput = new StringBuffer();

        // TODO: shuffle input
        Results oneBest = new Results();
        Results reRanked = new Results();
        Results oracle = new Results();
        Accuracy oneBestAcc = new Accuracy();
        Accuracy reRankedAcc = new Accuracy();
        Accuracy oracleAcc = new Accuracy();

        int numSentencesParsed = 0;
        int avgBestK = 0, avgOracleK = 0;
        // Effect query: a query whose response boosts the score of a non-top parse but not the top one.
        int numQueries = 0, numEffectiveQueries = 0;

        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            List<InputWord> sentence = sentences.get(sentIdx);
            List<String> words = sentence.stream().map(w->w.word).collect(Collectors.toList());
            Parse goldParse = goldParses.get(sentIdx);
            // TODO: get parse scores.
            /****************** Base n-best Parser ***************/
            List<Parse> parses = parser.parseNBest(sentences.get(sentIdx));
            if (parses == null) {
                continue;
            }
            numSentencesParsed ++;
            /****************** QueryGenerator ******************/
            Map<String, Query> allQueries = new HashMap<>();
            IntStream.range(0, parses.size()).forEach(k -> generateQueries(allQueries, words, parses.get(k), k));
            /***************** QueryFilter **********************/
            List<Query> queryList = allQueries.values().stream().filter(
                    query -> !(query.answerToParses.size() == 1 &&
                            query.answerToParses.values().iterator().next().size() == parses.size())
            ).collect(Collectors.toList());
            // TODO: sort with lambda
            Collections.sort(queryList, new Comparator<Query>() {
                @Override
                public int compare(Query o1, Query o2) {
                    if (o1.answerScores.size() < o2.answerScores.size()) {
                        return -1;
                    }
                    return o1.answerScores.size() == o2.answerScores.size() ? 0 : 1;
                }
            });
            // Debug: print queries.
            // TODO: print gold response along with query info.
            /*
            System.out.println("\n" + String.format("S[%d]:\t", sentIdx) +
                    words.stream().collect(Collectors.joining(" ")));
            queryList.forEach(query -> query.print(words));
             */

            /******************* Response simulator ************/
            // TODO: re-ranker; get simulated response and fix dependencies
            List<Response> responseList = queryList.stream()
                    .map(q -> responseSimulator.answerQuestion(q, words, goldParse))
                    .collect(Collectors.toList());
            double[] votes = parses.stream().mapToDouble(p->0.0).toArray();
            /******************* ReRanker ******************/
            for (int i = 0; i < queryList.size(); i++) {
                Query query = queryList.get(i);
                Response response = responseList.get(i);
                int minK = parses.size();
                for (int answerId : response.answerIds) {
                    if (query.answerToParses.containsKey(answerId)) {
                        for (int k : query.answerToParses.get(answerId)) {
                            votes[k] += 1.0;
                            if (k < minK) {
                                minK = k;
                            }
                        }
                    }
                }
                ++ numQueries;
                if (minK > 0 && minK < parses.size()) {
                    ++ numEffectiveQueries;
                }
            }
            /******************* Evaluate *******************/
            List<Results> results = CcgEvaluation.evaluate(parses, goldParse.dependencies);
            int bestK = 0, oracleK = 0;
            for (int k = 1; k < parses.size(); k++) {
                if (votes[k] > votes[bestK]) {
                    bestK = k;
                }
                if (results.get(k).getF1() > results.get(oracleK).getF1()) {
                    oracleK = k;
                }
            }
            avgBestK += bestK;
            avgOracleK += oracleK;
            oneBest.add(results.get(0));
            reRanked.add(results.get(bestK));
            oracle.add(results.get(oracleK));
            oneBestAcc.add(CcgEvaluation.evaluateTags(parses.get(0).categories, goldParse.categories));
            reRankedAcc.add(CcgEvaluation.evaluateTags(parses.get(bestK).categories, goldParse.categories));
            oracleAcc.add(CcgEvaluation.evaluateTags(parses.get(oracleK).categories, goldParse.categories));
        }
        System.out.println("\n1-best:\navg-k = 1.0\n" + oneBestAcc + "\n" + oneBest);
        System.out.println("re-ranked:\navg-k = " + 1.0 * avgBestK / numSentencesParsed + "\n" + reRankedAcc + "\n" + reRanked);
        System.out.println("oracle:\navg-k = " + 1.0 * avgOracleK / numSentencesParsed + "\n"+ oracleAcc + "\n" + oracle);
        System.out.println("Number of queries = " + numQueries);
        System.out.println("Number of effective queries = " + numEffectiveQueries);
    }

    private static void generateQueries(Map<String, Query> queries, List<String> words, Parse parse, int rankId) {
        assert queries != null;
        // Map from question string to
        for (ResolvedDependency targetDependency : parse.dependencies) {
            int argId = targetDependency.getArgument();
            List<String> question =
                    questionGenerator.generateQuestion(targetDependency, words, parse.categories, parse.dependencies);
            if (question == null || question.size() == 0) {
                continue;
            }
            String questionStr = StringUtils.join(question);
            if (!queries.containsKey(questionStr)) {
                queries.put(questionStr, new Query(question, 1.0 /* question score */));
            }
            queries.get(questionStr).addAnswer(argId, rankId, 1.0 /* answer score */);
            // TODO: question scorer here.
            // TODO: need to distinguish between multi-args and argument ambiguity from different parses.
        }
    }
}

/*
for (ResolvedDependency dep : goldDependencies) {
    if (debugPredicates.contains(dep.getHead())) {
        List<String> question = questionGenerator.generateQuestion(
                dep, words, goldCategories.get(sentIdx), goldDependencies);
        String questionStr = (question == null || question.size() == 0) ? "---" :
                StringUtils.join(question);
        System.out.println(dep.toString(words) + "\t" + dep.getCategory() + "\t" + questionStr);
    }
}
*/


    /*

            //Set<ResolvedDependency> newDependencies = fixDependencies()
                /*
            debugOutput.append("*** gold ***\n");
            for (ResolvedDependency goldDep : goldDependencies) {
                if (!CcgEvaluation.matchesAny(goldDep, newDependencies)) {
                    List<String> question = questionGenerator.generateQuestion(
                            goldDep, words, goldParses.get(sentIdx).categories, goldDependencies);
                    String questionStr = (question == null || question.size() == 0) ? "-noq-" :
                            StringUtils.join(question);
                    debugOutput.append(
                            String.format("%s\t%s\t%d\t%s\t%s\t%s\t%s\t", words.get(goldDep.getHead()),
                                    goldDep.getCategory(), goldDep.getArgNumber(),
                                    goldDep.getCategory().getArgument(goldDep.getArgNumber()),
                                    StringUtils.capitalize(questionStr) + "?",
                                    "---", words.get(goldDep.getArgument())));
                    if (questionStr.equals("-noq-") || categories.size() == 0) {
                        debugOutput.append("recall loss\n");
                    } else {
                        debugOutput.append("tagging error\t");
                        debugOutput.append(categories.get(goldDep.getHead()) + "\n");
                    }
                }
            }
            */

    /*
    private static Set<ResolvedDependency> fixDependencies(List<String> words, List<Category> categories,
                                                           Set<ResolvedDependency> dependencies) {
        //Set<String> fixedDependencies = new HashSet<>();
        Set<ResolvedDependency> newDependencies = new HashSet<>();

        // Generate possible questions over predicted dependencies.
        for (ResolvedDependency targetDependency : dependencies) {
            // boolean matched = CcgEvaluation.matchesAny(targetDependency, goldDependencies);
            // TODO: question scorer here.
            List<String> question =
                    questionGenerator.generateQuestion(targetDependency, words, categories, dependencies);
            if (question == null || question.size() == 0) {

                if (!matched) {
                    debugOutput.append(
                            String.format("%s\t%s\t%d\t%s\t%s\t%s\t%s\t", words.get(targetDependency.getHead()),
                                    targetDependency.getCategory(), targetDependency.getArgNumber(),
                                    targetDependency.getCategory().getArgument(targetDependency.getArgNumber()),
                                    "-noq-", words.get(targetDependency.getArgument()), "---"));
                    debugOutput.append(matched ? "matched\n" : "wrong\n");
                }

                continue;
            }
            Query query = new Query(question, 1.0);
            //

            String questionStr = StringUtils.join(question);
            int expectedAnswer = targetDependency.getArgumentIndex();
            List<Integer> simulatedAnswer = responseSimulator.answerQuestion(question, words, targetDependency,
                    goldCategories.get(sentIdx), goldDependencies);
            String simulatedAnswerStr = simulatedAnswer.get(0) == -1 ? "N/A" :
                    StringUtils.join(simulatedAnswer.stream().map(idx -> words.get(idx))
                            .collect(Collectors.toList()));

            // Fix dependency according to answer response. Ideally we can do self-training.
            // TODO: debug on N/A cases
            // TODO: better logic here
            boolean fixed = false;
            if (simulatedAnswer.get(0) >= 0 && (!simulatedAnswer.contains(targetDependency.getArgument()) ||
                    simulatedAnswer.size() > 1)) {
                fixedDependencies.add(getDependencyKey(targetDependency));
                simulatedAnswer.forEach(ans -> newDependencies.add(getFixedDependency(targetDependency, ans)));

                List<Integer> auxChain = questionGenerator.verbHelper.getAuxiliaryChain(words, categories,
                        targetDependency.getHead());
                if (auxChain.size() > 0) {
                    for (ResolvedDependency dep : dependencies) {
                        if (auxChain.contains(dep.getHead()) && dep.getArgument() == expectedAnswer) {
                            fixedDependencies.add(getDependencyKey(dep));
                            simulatedAnswer.forEach(ans -> newDependencies.add(getFixedDependency(dep, ans)));
                        }
                    }
                }
                fixed = true;
            }
            numQuestionsAsked ++;
            if (fixed) {
                numEffectiveQuestionsAsked ++;
            }
            if (!matched || fixed) {
                extendedDebugOutput.append(
                        String.format("%s\t%s\t%d\t%s\t%s\t%s\t%s\t", words.get(targetDependency.getHead()),
                                targetDependency.getCategory(), targetDependency.getArgNumber(),
                                targetDependency.getCategory().getArgument(targetDependency.getArgNumber()),
                                StringUtils.capitalize(questionStr) + "?",
                                words.get(expectedAnswer), simulatedAnswerStr));
                extendedDebugOutput.append(matched ? "matched\t" : "wrong\t");
                extendedDebugOutput.append(fixed ? "fixed\n" : "unfixed\n");
            }
            // Debugging information;
            if ((matched && fixed) || (!matched && !fixed)) {
                debugOutput.append(targetDependency.toString(words) + "\t" +
                                   targetDependency.getCategory() + "\t");
                debugOutput.append(StringUtils.join(question) + "?\t");
                debugOutput.append(words.get(expectedAnswer) + "\t" + simulatedAnswerStr + "\t");
                debugOutput.append(matched + "\t" + fixed + "\n");
                debugPredicates.add(targetDependency.getHead());
            }
        }
        dependencies.forEach(dep -> {
            if (!fixedDependencies.contains(getDependencyKey(dep))) {
                newDependencies.add(dep);
            }
        });
        newDependencies.addAll(dependencies);
        return newDependencies;
    }


    private static String getDependencyKey(ResolvedDependency dep) {
        return dep.getHead() + "_" + dep.getArgNumber();
    }

    private static ResolvedDependency getFixedDependency(ResolvedDependency dep, int newArgumentIndex) {
        return new ResolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(), newArgumentIndex,
                dep.getSemanticRole(), dep.getPreposition());
    }
*/