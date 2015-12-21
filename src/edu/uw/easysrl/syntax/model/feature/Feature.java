package edu.uw.easysrl.syntax.model.feature;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleClass;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.parser.AbstractParser;

public abstract class Feature implements Serializable {
	/**
	 * Unique identifier for this feature.
	 */
	final int id;
	private final static AtomicInteger count = new AtomicInteger();

	protected Feature() {
		this.id = count.getAndIncrement();
	}

	public abstract FeatureKey getDefault();

	public abstract void resetDefaultIndex();

	public static class FeatureKey implements Serializable {
		private static final long serialVersionUID = 1L;
		private final int[] values;
		private final int hashCode;

		FeatureKey(final int... values) {
			this.values = values;
			this.hashCode = Arrays.hashCode(values);
		}

		@Override
		public boolean equals(final Object other) {
			return hashCode() == other.hashCode() && Arrays.equals(values, ((FeatureKey) other).values);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return Arrays.toString(values);
		}

		public int[] getValues() {
			return values;
		}
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 6283022233440386377L;

	public static abstract class LexicalCategoryFeature extends Feature {

		/**
		 *
		 */
		private static final long serialVersionUID = -5413460311778529355L;

		public abstract double getValue(List<InputWord> sentence, int word, Category category);

		public abstract FeatureKey getFeatureKey(List<InputWord> inputWords, int wordIndex, Category category);

		@Override
		public abstract FeatureKey getDefault();

	}

	static FeatureKey hash(final int... objects) {
		return new FeatureKey(objects);
	}
}
