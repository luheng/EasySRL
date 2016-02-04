package edu.uw.easysrl.corpora.qa;

import java.util.Collection;

import edu.uw.easysrl.util.QAUtils;

public class QASlotPrepositions {

	/**
	 *  TODO: In the future, we want to only include those prepositions that
	 *  occurred in the sentence. To do this without a pos-tagger, we can
	 *  compare the words in the sentence against a complete list of English
	 *  propositions.
	 */
	
	public static final String[] values = {
		"aboard", "about", "above", "across", "afore", "after", "against", "ahead", "along", "alongside", "amid",
		"amidst", "among", "amongst", "around", "as", "aside", "astride", "at", "atop", "before",
		"behind", "below", "beneath", "beside", "besides", "between", "beyond", "by", "despite", "down",
		"during", "except", "for", "from", "given", "in", "inside", "into", "near", "next",
		"of", "off", "on", "onto", "opposite", "out", "outside", "over", "pace", "per",
		"round", "since", "than", "through", "throughout", "till", "times", "to", "toward", "towards",
		"under", "underneath", "until", "unto", "up", "upon", "versus", "via", "with ", "within",
		"without"
	};
	
	public static final Collection<String> ppSet = QAUtils.asSet(values);
	
	public static final String[] mostFrequentPPs = {
		"by",
		"to",
		"for",
		"with",
		"about",
	//	"of"
	};

	public static final Collection<String> mostFrequentPPSet = QAUtils.asSet(mostFrequentPPs);
}