package edu.uw.easysrl.syntax.model;

import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;

import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;

public class SupertagFactoredModel extends Model {

	private final List<List<ScoredCategory>> tagsForWords;

	public SupertagFactoredModel(final List<List<ScoredCategory>> tagsForWords) {
		super(tagsForWords.size());
		this.tagsForWords = tagsForWords;
		computeOutsideProbabilities();
	}

	@Override
	public void buildAgenda(final PriorityQueue<AgendaItem> agenda, final List<InputWord> words) {
		for (int i = 0; i < words.size(); i++) {
			final InputWord word = words.get(i);
			for (final ScoredCategory cat : tagsForWords.get(i)) {
				agenda.add(new AgendaItem(
						new SyntaxTreeNodeLeaf(word.word, word.pos, word.ner, cat.getCategory(), i),
						cat.getScore(),                 /* inside score = category score */
						getOutsideUpperBound(i, i + 1), /* outside score upperbound */
						i,    /* start index */
						1,    /* length */
						false /* includeDeps */
				));
			}
		}
	}

	@Override
	public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, SyntaxTreeNode node) {
		final int length = leftChild.spanLength + rightChild.spanLength;
		final List<UnlabelledDependency> resolvedUnlabelledDependencies = node.getResolvedUnlabelledDependencies();
		int i = 0;
		for (final UnlabelledDependency dep : resolvedUnlabelledDependencies) {
			node = new SyntaxTreeNodeLabelling(
					node,
					dep.setLabel(SRLFrame.NONE),
					resolvedUnlabelledDependencies.subList(i + 1, resolvedUnlabelledDependencies.size()));
			i++;
		}

		// Add a penalty based on length of dependency.
		final int depLength = Math.abs(leftChild.getParse().getHeadIndex() - rightChild.getParse().getHeadIndex());
		double lengthPenalty = 0.00001 * depLength;

		// Extra penalty for clitics, to really make sure they attach locally.
		if (rightChild.getSpanLength() == 1 && rightChild.getParse().getWord().startsWith("'")) {
			lengthPenalty = lengthPenalty * 10;
		}

		return new AgendaItem(
				node,
				leftChild.getInsideScore() + rightChild.getInsideScore() - lengthPenalty,
				getOutsideUpperBound(leftChild.startOfSpan, leftChild.startOfSpan + length),
				leftChild.startOfSpan,
				length,
				false);
	}

	@Override
	public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode result, final UnaryRule rule) {
		return new AgendaItem(
				result,
				child.getInsideScore(), // the scores aren't changed.
				child.outsideScoreUpperbound,
				child.startOfSpan,
				child.spanLength,
				false /* includeDeps */);
	}

	@Override
	double getUpperBoundForWord(final int index) {
		// Factorized upperbound. I assume the tagsForWords are sorted so .get(0) is the highest scored.
		return tagsForWords.get(index).get(0).getScore();
	}

	public static class SupertagFactoredModelFactory extends ModelFactory {
		private final Tagger tagger;

		public SupertagFactoredModelFactory(final Tagger tagger) {
			super();
			this.tagger = tagger;
		}

		@Override
		public SupertagFactoredModel make(final List<InputWord> sentence) {
			return new SupertagFactoredModel(tagger.tag(sentence));
		}

		@Override
		public Model make(List<InputWord> sentence, List<List<Tagger.ScoredCategory>> scoredCategories) {
			return new SupertagFactoredModel(scoredCategories);
		}

		@Override
		public Collection<Category> getLexicalCategories() {
			return tagger.getLexicalCategories();
		}
	}
}
