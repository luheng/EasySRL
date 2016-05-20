package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.qasrl.query.*;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.QAPairAggregator;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import static edu.uw.easysrl.util.GuavaCollectors.*;

import java.util.*;
import java.util.stream.IntStream;

public abstract class QuestionGenerationPipeline {

    public static QuestionGenerationPipeline vpAttachmentQGPipeline = new QuestionGenerationPipeline() {
            @Override
            public ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse) {
                return QuestionGenerator.newVPAttachmentQuestions(sentenceId, parseId, parse);
            }

            @Override
            public QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator() {
                return QAPairAggregators.aggregateWithAnswerAdverbAndPPArgDependencies();
            }

            @Override
            public QuestionGenerationPipeline setQueryPruningParameters(final QueryPruningParameters pruningParameters) {
                return this;
            }

            @Override
            public QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator() {
                return QueryGenerators.checkboxQueryGenerator();
            }

            @Override
            public Optional<ResponseSimulator> getResponseSimulator(ImmutableMap<Integer, Parse> parses) {
                return Optional.of(new VPAttachmentResponseSimulator(parses));
            }
        };

    public static QuestionGenerationPipeline newCoreArgQGPipeline = new QuestionGenerationPipeline() {

            @Override
            public ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse) {
                return new ImmutableList.Builder<QuestionAnswerPair>()
                    .addAll(QuestionGenerator.newCoreNPArgQuestions(sentenceId, parseId, parse))
                    .addAll(QuestionGenerator.newCopulaQuestions(sentenceId, parseId, parse))
                    .addAll(QuestionGenerator.newPPObjPronounQuestions(sentenceId, parseId, parse))
                    .build();
            }

            @Override
            public QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator() {
                return QAPairAggregators.aggregateForMultipleChoiceQA();
            }

            @Override
            public Optional<QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>>> getQueryFilter() {
                final QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> queryFilter =
                        (queries, nBestList, queryPruningParameters) -> queries.stream()
                                .filter(query -> {
                                    final ImmutableList<String> words = query.getQAPairSurfaceForms().get(0).getQAPairs().get(0).getParse().syntaxTree
                                    .getLeaves().stream().map(l -> l.getWord())
                                    .collect(toImmutableList());
                                    final int predIndex = query.getQAPairSurfaceForms().get(0).getPredicateIndex();
                                    final boolean isAdj = query.getQAPairSurfaceForms().stream()
                                    .flatMap(sf -> sf.getQuestionStructures().stream())
                                    .allMatch(qStr -> qStr.category.isFunctionInto(Category.valueOf("S[adj]")));
                                    return query.getPromptScore() >= getQueryPruningParameters().minPromptConfidence &&
                                    query.getOptionEntropy() >= getQueryPruningParameters().minOptionEntropy &&
                                    (!getQueryPruningParameters().skipSAdjQuestions || !isAdj) &&
                                    (!getQueryPruningParameters().skipBinaryQueries || query.getQAPairSurfaceForms().size() > 1);
                                })
                                .collect(toImmutableList());
                return Optional.of(queryFilter);
            }

            private QueryPruningParameters queryPruningParameters = null;
            @Override
            public QueryPruningParameters getQueryPruningParameters() {
                if(queryPruningParameters == null) {
                    queryPruningParameters = new QueryPruningParameters();
                    queryPruningParameters.minPromptConfidence = 0.10;
                    queryPruningParameters.minOptionEntropy = 0.00;
                    queryPruningParameters.skipSAdjQuestions = false;
                    queryPruningParameters.skipBinaryQueries = false;
                    // not using the rest right now
                    // queryPruningParameters.minOptionConfidence = 0.05;
                    // queryPruningParameters.maxNumOptionsPerQuery = 6;
                    // queryPruningParameters.skipPPQuestions = false;
                    // queryPruningParameters.skipQueriesWithPronounOptions = false;
                }
                return queryPruningParameters;
            }

            @Override
            public QuestionGenerationPipeline setQueryPruningParameters(final QueryPruningParameters pruningParameters) {
                this.queryPruningParameters = pruningParameters;
                return this;
            }

            @Override
            public QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator() {
                return QueryGenerators.checkboxQueryGenerator();
            }

            @Override
            public Optional<ResponseSimulator> getResponseSimulator(ImmutableMap<Integer, Parse> parses) {
                return Optional.empty();
            }
        };

    public static QuestionGenerationPipeline coreArgQGPipeline = new QuestionGenerationPipeline() {

        @Override
        public ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse) {
            QuestionGenerator.setAskPPAttachmentQuestions(false);
            QuestionGenerator.setIndefinitesOnly(true);
            return new ImmutableList.Builder<QuestionAnswerPair>()
                    .addAll(QuestionGenerator.newCoreNPArgQuestions(sentenceId, parseId, parse))
                    .addAll(QuestionGenerator.newCopulaQuestions(sentenceId, parseId, parse))
                    .build();
        }

        private QueryPruningParameters queryPruningParameters = null;

        public QueryPruningParameters getQueryPruningParameters() {
            return queryPruningParameters;
        }

        public QuestionGenerationPipeline setQueryPruningParameters(final QueryPruningParameters pruningParameters) {
            this.queryPruningParameters = pruningParameters;
            return this;
        }

        @Override
        public QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator() {
            return QAPairAggregators.aggregateForMultipleChoiceQA();
        }

        @Override
        public QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator() {
            return QueryGenerators.checkboxQueryGenerator();
        }

        @Override
        public Optional<QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>>> getQueryFilter() {
            return Optional.of(QueryFilters.scoredQueryFilter());
        }

        @Override
        public Optional<ResponseSimulator> getResponseSimulator(ImmutableMap<Integer, Parse> parses) {
            return Optional.empty();
        }
    };


    public abstract ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse);

    public ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId, NBestList nBestList) {
        return IntStream.range(0, nBestList.getN()).boxed()
            .flatMap(parseId -> generateQAPairs(sentenceId, parseId, nBestList.getParse(parseId)).stream())
            .collect(toImmutableList());
    }

    public abstract QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator();
    public abstract QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator();
    public Optional<QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>>> getQueryFilter() {
        return Optional.empty();
    }

    public QueryPruningParameters getQueryPruningParameters() {
        return new QueryPruningParameters();
    }

    public abstract QuestionGenerationPipeline setQueryPruningParameters(final QueryPruningParameters pruningParameters);

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(int sentenceId, NBestList nBestList) {
        final ImmutableList<QuestionAnswerPair> allQAPairs = IntStream.range(0, nBestList.getN()).boxed()
            .flatMap(parseId -> this.generateQAPairs(sentenceId, parseId, nBestList.getParse(parseId)).stream())
            .collect(toImmutableList());

        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
            getQueryGenerator().generate(getQAPairAggregator().aggregate(allQAPairs));
        for(ScoredQuery<QAStructureSurfaceForm> query : queries) {
            query.computeScores(nBestList);
        }
        if(getQueryFilter().isPresent()) {
            return getQueryFilter().get().filter(queries, nBestList, getQueryPruningParameters());
        } else {
            return queries;
        }
    }

    public abstract Optional<ResponseSimulator> getResponseSimulator(ImmutableMap<Integer, Parse> parses);

    public ResponseSimulator getOneBestResponseSimulator(ImmutableMap<Integer, Parse> parses) {
        Optional<ResponseSimulator> simOpt = getResponseSimulator(parses);
        if(simOpt.isPresent()) {
            return simOpt.get();
        } else {
            return new OneBestResponseSimulator();
        }
    }

    public ResponseSimulator getGoldResponseSimulator(ImmutableMap<Integer, Parse> parses) {
        Optional<ResponseSimulator> simOpt = getResponseSimulator(parses);
        if(simOpt.isPresent()) {
            return simOpt.get();
        } else {
            // get the gold parses from somewhere else?
            return new ResponseSimulatorGold(ParseData.loadFromDevPool().get());
        }
    }
}
