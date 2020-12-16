package com.fds.npl.lang.util;

import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

public class NLPUtilTest {

	@Test
	public void testTokenizeForNPL() {
		String sentence = "I am spot";
		NLPUtil nlp = new NLPUtil(sentence);
		Assert.assertEquals(nlp.getTokens(), new String[]{"I", "am", "spot"});
	}

	@Test
	public void testGetNPLTags() {
		String sentence = "I am spot";
		NLPUtil nlp = new NLPUtil(sentence);
		Assert.assertEquals(nlp.getTags(), new String[]{"PRP", "VBP", "NN"});
	}

	@Test
	public void testIgnoreChar() {
		List<String> sentences = Arrays.asList("He/Him", "He Him");
		for (String sentence : sentences) {
			NLPUtil nlp = new NLPUtil(sentence);
			Assert.assertEquals(nlp.expect("PRP").andThen().ignore("VBD").andThen().expectStartsWith("N").get(),
								"He Him");
		}
	}

	@Test
	public void testDependOn() {
		String sentence = "(22 Pts)";
		NLPUtil nlp = new NLPUtil(sentence);
		Assert.assertEquals(
				nlp.dependOn(nlp.leftParens).expectNumber().andThen().expect("NNS").andThen().ignore(nlp.rightParens)
						.get(), "22 Pts");
	}

	@Test
	public void testExpectProperNounFirstLast() {
		List<String> sentences = Arrays.asList("Jonathan Taylor", "Jonathan Taylor is a Running Back");
		for (String sentence : sentences) {
			NLPUtil nlp = new NLPUtil(sentence);
			Assert.assertEquals(nlp.expectProperNoun().get(), "Jonathan Taylor");
		}
	}

	@Test
	public void testExpectProperNounFirstInitialLast() {
		String sentence = "J. Taylor";
		NLPUtil nlp = new NLPUtil(sentence);
		Assert.assertEquals(nlp.expectProperNoun().get(), sentence);
	}

	@Test
	public void testExpectProperNounAmbiguousFirstInitialLast() {
		//A is ambiguous as it could be "*A* random person" or an initial.
		String sentence = "A. Rodgers";
		NLPUtil nlp = new NLPUtil(sentence);
		Assert.assertEquals(nlp.expectProperNoun().get(), sentence);
	}

	@Test
	public void testAndMaybeWithMiddleInitial() {
		List<String> sentences = Arrays.asList("J. Taylor", "Jonathan Taylor");
		for (String sentence : sentences) {
			NLPUtil nlp = new NLPUtil(sentence);
			String result = nlp.expectStartsWith("N").andMaybe().expect(".").disregardIfNotFound().andMaybe()
					.expectStartsWith("N").get();
			Assert.assertTrue(result.startsWith("J"));
			Assert.assertTrue(result.contains("Taylor"), "Didn't find \"Taylor\" in " + sentence);
		}
	}

	@Test
	public void testExpectCommonNoun() {
		List<String> sentences = Arrays.asList("dog", "street cat", "train", "newspaper");
		for (String sentence : sentences) {
			NLPUtil nlp = new NLPUtil(sentence);
			String result = nlp.expectCommonNoun().get();
			Assert.assertEquals(result, sentence);
		}
	}

	@Test
	public void testExpectVerb() {
		List<String> sentences = Arrays.asList("jumped", "running", "throws");
		for (String sentence : sentences) {
			NLPUtil nlp = new NLPUtil(sentence);
			String tag = nlp.getTags()[0];
			String result = nlp.expectVerb().get();
			Assert.assertEquals(result, sentence, "Expected " + sentence + " to be a verb but was a " + tag);
		}
	}

	@Test
	public void testExpect() {
		List<String> sentences = Arrays.asList("Jonathan", "jumped", "in", "a", "pool");
		List<String> tags = Arrays.asList("NNP", "VBN", "IN", "DT", "NN");
		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);
			String tag = tags.get(i);
			NLPUtil nlp = new NLPUtil(sentence);
			String result = nlp.expect(tag).get();
			Assert.assertEquals(result, sentence);
		}
	}

	@Test
	public void testExpectStartsWith() {
		List<String> sentences = Arrays.asList("Jonathan", "jumped", "in", "a", "pool");
		List<String> tags = Arrays.asList("N", "V", "I", "D", "N");
		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);
			String tag = tags.get(i);
			NLPUtil nlp = new NLPUtil(sentence);
			String result = nlp.expectStartsWith(tag).get();
			Assert.assertEquals(result, sentence);
		}
	}

	@Test
	public void testReplace() {
		String sentences = "Jonathan jumped in a pool";
		NLPUtil nlp = new NLPUtil(sentences);
		nlp.expectProperNoun().andThen().expectVerb().andThen().expect("IN");
		nlp.getArticle().andMaybe().expect("DT");
		String lastToken = nlp.getArticle().getLastToken();
		if (lastToken != null && !lastToken.isEmpty()) {
			nlp.getArticle().nevermind();
			nlp.replace("the", "DT");
		}
		nlp.getArticle().andThen().expect("DT").andThen().expect("NN");
		String result = nlp.getArticle().get();
		Assert.assertEquals(result, "Jonathan jumped in the pool");
	}

	@Test
	public void testRemoveUsedTokens() {
		String sentence = "Jonathan Taylor is a Running Back";
		NLPUtil nlp = new NLPUtil(sentence);
		nlp.expectProperNoun();
		nlp.removeUsedTokens(nlp.getArticle().getIndex());
		Assert.assertEquals(nlp.getArticle().peekToken(), "Jonathan Taylor");
		Assert.assertEquals(nlp.getTags().length, 4);
	}

	@Test
	public void testSetArticle() {
		String sentences = "Jonathan jumped in a pool";
		NLPUtil nlp = new NLPUtil(sentences);
		nlp.expectProperNoun().andThen().expectVerb().andThen().expect("IN");
		NLPUtil.NPLArticle copy = nlp.getArticle().copy();
		nlp.getArticle().andThen().expect("DT").andThen().expect("NN");
		nlp.setArticle(copy);
		String result = nlp.getArticle().get();
		Assert.assertEquals(result, "Jonathan jumped in");
	}

	@Test
	public void subArray() {
		String[] arr = new String[]{"1","2","3","4"};
		NLPUtil nlp = new NLPUtil("test");
		Assert.assertEquals(nlp.subArray(arr,2),new String[]{"3","4"});
	}
}