package edu.uw.easysrl.qasrl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.experiments.ExperimentUtils;
import edu.uw.easysrl.qasrl.model.AttachmentHelper;
import edu.uw.easysrl.qasrl.qg.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.QADependenciesSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.query.*;
import edu.uw.easysrl.util.GuavaCollectors;
import edu.uw.easysrl.util.Util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Unfinished gold simulator ...
 */
public class GoldSimulator {
    private final ParseData parseData;

    public GoldSimulator(ParseData parseData) {
        this.parseData = parseData;
    }

    public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query) {
        /*
        final int sentenceId = query.getSentenceId();
        final ImmutableList<String> sentence = parseData.getSentences().get(sentenceId);
        final Parse goldParse = parseData.getGoldParses().get(sentenceId);

        Set<Integer> chosenOptions = new HashSet<>();
        final ImmutableList<ScoredQuery<QAStructureSurfaceForm>> goldQueries =
                generateGoldQueries(sentenceId, sentence, goldParse, query.getQueryType());
        final ImmutableList<QAStructureSurfaceForm> qaOptions = query.getQAPairSurfaceForms();

        if (query.getQueryType() == QueryType.Forward) {
            goldQueries.stream()
                    .filter(q -> q.getPredicateId().getAsInt() == query.getPredicateId().getAsInt())
                    .filter(q -> q.getPrompt().equalsIgnoreCase(query.getPrompt()) ||
                            (q.getPredicateCategory().get() == query.getPredicateCategory().get() &&
                             q.getArgumentNumber().getAsInt() == query.getArgumentNumber().getAsInt()))
                              */
        return null;
    }

    private static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateGoldQueries(
            final int sentenceId, final ImmutableList<String> sentence, final Parse goldParse,
            QueryType queryType) {
        QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> queryGenerator =
                QueryGenerators.checkboxQueryGenerator();
        QAPairAggregator<QAStructureSurfaceForm> aggregator = QAPairAggregators.aggregateForMultipleChoiceQA();
        if (queryType == QueryType.Jeopardy) {
            queryGenerator = QueryGenerators.jeopardyCheckboxQueryGenerator();
        } else if (queryType == QueryType.Clefted) {
            queryGenerator = QueryGenerators.cleftedQueryGenerator();
            aggregator = QAPairAggregators.aggregateWithAnswerAdjunctDependencies();
        }
        return queryGenerator.generate(aggregator.aggregate(
                QuestionGenerator.generateQAPairsForParse(sentenceId, -1 /* parse id*/, sentence, goldParse)));
    }
}
