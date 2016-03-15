package edu.uw.easysrl.qasrl.annotation;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AlignedAnnotation;
import edu.uw.easysrl.qasrl.annotation.CrowdFlowerDataReader;
import edu.uw.easysrl.qasrl.annotation.QualityControl;
import edu.uw.easysrl.qasrl.pomdp.Evidence;
import edu.uw.easysrl.qasrl.pomdp.ExpectedAccuracy;
import edu.uw.easysrl.qasrl.pomdp.POMDP;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPairReduced;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationPrecisionRecall {
    private static final int nBest = 100;
    private static final int maxNumOptionsPerQuestion = 6;
    static {
        GroupedQuery.maxNumNonNAOptionsPerQuery = maxNumOptionsPerQuestion - 2;
    }

    // Query pruning parameters.
    private static QueryPruningParameters queryPruningParameters = new QueryPruningParameters(
            1,     /* top K */
            0.1,   /* min question confidence */
            0.05,  /* min answer confidence */
            0.05   /* min attachment entropy */
    );

    private static final String[] annotationFiles = {
            //"./Crowdflower_data/all-checkbox-responses.csv",
            "./Crowdflower_data/f878213.csv",
            //"./Crowdflower_data/f882410.csv"
    };

    static final boolean isCheckbox = false;

    public static void main(String[] args) {
        Map<Integer, List<AlignedAnnotation>> annotations = loadData(annotationFiles);
        assert annotations != null;

        POMDP learner = new POMDP(nBest, 1000, 0.0);
        learner.setQueryPruningParameters(queryPruningParameters);
        //  TODO: double check gold simulator

        for (int minAgreement = 2; minAgreement <= 5; minAgreement++) {
            computePrecisionRecall(annotations, minAgreement, learner, false /* look at pp */);
        }
        for (int minAgreement = 2; minAgreement <= 5; minAgreement++) {
            computePrecisionRecall(annotations, minAgreement, learner, true /* look at pp */);
        }
    }

    private static Map<Integer, List<AlignedAnnotation>> loadData(String[] fileNames) {
        Map<Integer, List<AlignedAnnotation>> sentenceToAnnotations;
        List<AlignedAnnotation> annotationList = new ArrayList<>();
        try {
            for (String fileName : fileNames) {
                CrowdFlowerDataReader.readAggregatedAnnotationFromFile(fileName, isCheckbox).stream()
                        .filter(annotation -> !annotation.annotatorToAnswerId.keySet().contains("36562414"))
                        .forEach(annotationList::add);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        sentenceToAnnotations = new HashMap<>();
        annotationList.forEach(annotation -> {
            int sentId = annotation.sentenceId;
            if (!sentenceToAnnotations.containsKey(sentId)) {
                sentenceToAnnotations.put(sentId, new ArrayList<>());
            }
            sentenceToAnnotations.get(sentId).add(annotation);
        });
        return sentenceToAnnotations;
    }

    private static void computePrecisionRecall(Map<Integer, List<AlignedAnnotation>> annotations, int minAgreement,
                                               POMDP learner, boolean analyzePrepositionQuestions) {
        List<Integer> sentenceIds = annotations.keySet().stream().sorted().collect(Collectors.toList());
        //System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences.");

        int numGoldIsNA = 0, numUserIsNA = 0, numUserNAMatch = 0,  numOneBestIsNA = 0, numOnebestNAMatch  = 0;
        int numGoldArgs = 0, numUserArgs = 0, numUserArgMatch = 0, numOneBestArgs = 0, numOnebestArgMatch = 0;

        for (int sentenceId : sentenceIds) {
            learner.initializeForSentence(sentenceId, annotations.get(sentenceId), isCheckbox);

            final List<String> sentence = learner.getSentenceById(sentenceId);
            final List<Parse> parses = learner.allParses.get(sentenceId);
            final List<GroupedQuery> queries = learner.getQueryPool();
            final Parse goldParse = learner.goldParses.get(sentenceId);
            final int oracleParseId = learner.getOracleParseId(sentenceId);

            for (GroupedQuery query : queries) {
                AlignedAnnotation annotation = getAlignedAnnotation(query, annotations.get(sentenceId));
                if (annotation == null) {
                    continue;
                }
                int[] optionDist = QualityControl.getUserResponses(query, annotation);

                final List<Integer> oracleOptions = new ArrayList<>();
                final List<Integer> oneBestOptions = new ArrayList<>();
                Response multiResponse = new Response();

                Set<Integer> listedArgIds = new HashSet<>();
                for (int j = 0; j < optionDist.length; j++) {
                    GroupedQuery.AnswerOption option = query.getAnswerOptions().get(j);
                    if (option.getParseIds().contains(oracleParseId)) {
                        oracleOptions.add(j);
                    }
                    if (option.getParseIds().contains(0 /* parse id of one-best */)) {
                        oneBestOptions.add(j);
                    }
                    if (optionDist[j] >= minAgreement) {
                        multiResponse.add(j);
                    }
                    if (!option.isNAOption()) {
                        listedArgIds.addAll(option.getArgumentIds());
                    }
                }

                boolean isPPQuestion =  QualityControl.queryIsPrepositional(query);
                if (analyzePrepositionQuestions != isPPQuestion) {
                    continue;
                }

                Set<Integer> goldArgIds     = new HashSet<>();
                int numMaxOverlap           = 0;
                int goldArgNum              = -1;
                final int predId            = query.getPredicateIndex();
                final Category goldCategory = goldParse.categories.get(predId);
                boolean questionMatch       = false;
                // Search for gold arNnum.
                for (int j = 1; j <= goldCategory.getNumberOfArguments(); j++) {
                    final int argNum = j;
                    Set<String> questions = QuestionGenerator.generateAllQAPairs(predId, argNum, sentence, goldParse)
                            .stream()
                            .map(QuestionAnswerPairReduced::renderQuestion)
                            .collect(Collectors.toSet());
                    Set<Integer> argIds = goldParse.dependencies.stream()
                            .filter(d -> d.getHead() == predId && d.getArgNumber() == argNum)
                            .map(ResolvedDependency::getArgument)
                            .collect(Collectors.toSet());
                    if ((goldCategory == query.getCategory() && argNum == query.getArgNum()) ||
                            questions.contains(query.getQuestion())) {
                        questionMatch = true;
                        goldArgIds.addAll(argIds);
                        break;
                    }
                    int numOverlapWithListed = (int) listedArgIds.stream().filter(argIds::contains).count();
                    if (numOverlapWithListed > numMaxOverlap) {
                        goldArgIds.clear();
                        goldArgIds.addAll(argIds);
                        numMaxOverlap = numOverlapWithListed;
                        goldArgNum = argNum;
                    }
                }

                /*if (!questionMatch) {
                    System.err.println(query.getQuestion() + "\t" + goldCategory + "\n" + goldArgNum + "\t" + numMaxOverlap);
                }*/

                Set<Integer> oracleArgIds  = getChosenArgumentIds(query, oracleOptions);
                Set<Integer> onebestArgIds = getChosenArgumentIds(query, oneBestOptions);
                Set<Integer> userArgIds    = getChosenArgumentIds(query, multiResponse.chosenOptions);
                Set<Integer> penalizableArgIds = listedArgIds.stream()
                        .filter(id -> !goldArgIds.contains(id))
                        .collect(Collectors.toSet());
                Set<Integer> effectivePenalizableArgIds = penalizableArgIds.stream()
                        .filter(onebestArgIds::contains)
                        .collect(Collectors.toSet());
                Set<Integer> penalizedArgIds = listedArgIds.stream()
                        .filter(id -> !userArgIds.contains(id))
                        .collect(Collectors.toSet());
                Set<Integer> effectivePenalizedArgIds = penalizedArgIds.stream()
                        .filter(onebestArgIds::contains)
                        .collect(Collectors.toSet());

                boolean goldIsNA    = !questionMatch;
                boolean oracleIsNA  = responseIsBadQuestion(query, oneBestOptions);
                boolean onebestIsNA = responseIsBadQuestion(query, oneBestOptions);
                boolean userIsNA    = responseIsBadQuestion(query, multiResponse.chosenOptions);

                // Compute precision and recall.
                numGoldIsNA += goldIsNA ? 1 : 0;
                numUserIsNA += userIsNA ? 1 : 0;
                numOneBestIsNA += onebestIsNA ? 1 : 0;

                numGoldArgs += goldArgIds.size();
                numUserArgs += userArgIds.size();
                numOneBestArgs += onebestArgIds.size();

                numUserNAMatch  += goldIsNA && userIsNA ? 1 : 0;
                numUserArgMatch += goldArgIds.stream().filter(userArgIds::contains).count();

                numOnebestNAMatch += goldIsNA && onebestIsNA ? 1 : 0;
                numOnebestArgMatch += goldArgIds.stream().filter(onebestArgIds::contains).count();

                if (goldArgIds.stream().filter(userArgIds::contains).count() < userArgIds.size()) {
                    // TODO: debug precision loss.
                }
            }
        }

        Results userNaF1     = new Results(numUserIsNA, numUserNAMatch, numGoldIsNA),
                userArgF1    = new Results(numUserArgs, numUserArgMatch, numGoldArgs),
                onebestNaF1  = new Results(numOneBestIsNA, numOnebestNAMatch, numGoldIsNA),
                onebestArgF1 = new Results(numOneBestArgs, numOnebestArgMatch, numGoldArgs);

        System.out.println("Min agreement: " + minAgreement);
        //System.out.println("Look at prepositions: " + analyzePrepositionQuestions);
        System.out.println("Bad question P/R/F1:\n" + userNaF1);
        System.out.println("Attachment P/R/F1:\n" + userArgF1);
        //System.out.println("Bad question P/R/F1:\n" + onebestNaF1);
        //System.out.println("Attachment P/R/F1:\n" + onebestArgF1);
    }

    private static Results getF1(Set<Integer> userSet, Set<Integer> truthSet) {
        return new Results(userSet.size(),
                (int) userSet.stream().filter(truthSet::contains).count(),
                truthSet.size());
    }

    private static Set<Integer> getChosenArgumentIds(final GroupedQuery query, final Collection<Integer> optionIds) {
        final Set<Integer> argIds = new HashSet<>();
        optionIds.stream()
                .map(query.getAnswerOptions()::get)
                .filter(op -> !op.isNAOption())
                .forEach(op -> argIds.addAll(op.getArgumentIds()));
        return argIds;
    }

    private static boolean responseIsBadQuestion(final GroupedQuery query, final Collection<Integer> optionIds) {
        for (int id : optionIds) {
            final GroupedQuery.AnswerOption option = query.getAnswerOptions().get(id);
            if (GroupedQuery.BadQuestionOption.class.isInstance(option)) {
                return true;
            }
        }
        return false;
    }

    private static AlignedAnnotation getAlignedAnnotation(GroupedQuery query, List<AlignedAnnotation> annotations) {
        String qkey =query.getPredicateIndex() + "\t" + query.getQuestion();
        for (AlignedAnnotation annotation : annotations) {
            String qkey2 = annotation.predicateId + "\t" + annotation.question;
            if (qkey.equals(qkey2)) {
                return annotation;
            }
        }
        return null;
    }
}
