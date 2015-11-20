package edu.uw.easysrl.syntax.parser;

import java.util.List;

import com.google.common.collect.Multimap;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser.SuperTaggingResults;
import edu.uw.easysrl.util.Util.Scored;

public interface Parser {
	/**
	 * Ignores the InputReader and parses the supplied list of words.
	 */
	List<Scored<SyntaxTreeNode>> parseTokens(List<String> words);

	List<Scored<SyntaxTreeNode>> parseSentence(SuperTaggingResults results, InputToParser input);

	List<Scored<SyntaxTreeNode>> doParsing(InputToParser input);

	List<Scored<SyntaxTreeNode>> parse(SuperTaggingResults results, String line);

	Multimap<Integer, Long> getSentenceLengthToParseTimeInNanos();

	long getParsingTimeOnlyInMillis();

	long getTaggingTimeOnlyInMillis();

	int getMaxSentenceLength();
}