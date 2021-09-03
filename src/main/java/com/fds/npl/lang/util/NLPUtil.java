package com.fds.npl.lang.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.apache.commons.lang3.ArrayUtils;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NLPUtil {

	protected final Logger logger = LoggerFactory.getLogger(NLPUtil.class);

	protected String sentence;
	protected String[] tokens;
	protected String[] tags;

	protected final String leftParens = "-LRB-";
	protected final String rightParens = "-RRB-";

	protected NPLArticle a = new NPLArticle();

	public NLPUtil(String sentence) {
		this.sentence = sentence;
		tokens = tokenizeForNPL(sentence);
		tags = getNPLTags(tokens);
	}

	public String[] tokenizeForNPL(String event) {
		SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
		String[] tokens = tokenizer.tokenize(event);
		return tokens;
	}

	public String[] getTokens(){
		return tokens;
	}

	@NotNull
	public String[] getNPLTags(String[] tokens) {
		InputStream inputStreamPOSTagger = getClass().getResourceAsStream("/en-pos-maxent.bin");
		POSModel posModel = null;
		try {
			posModel = new POSModel(inputStreamPOSTagger);
		} catch (IOException e) {
			logger.error("Exception occurred while trying to get NLP Model File.",e);
		}
		POSTaggerME posTagger = new POSTaggerME(posModel);
		return posTagger.tag(tokens);
	}

	public String[] getTags() {
		return tags;
	}

	public NPLArticle ignore(String... tags) {
		if (a.found) {
			a.andThen();
		}
		if(!a.failure) {
			String lastToken = a.peekToken("");
			NPLArticle temp = a.copy();
			if (!expect(tags).peekToken("").equals(lastToken)) {
				a = temp;
				a.increment();
			}
			a.found = true;
		}
		return a;
	}

	/**
	 * dependOn is like ignore in that both do not retain the token sent. However, dependOn will fail
	 * if that token is not found.
 	 * @param tags
	 * @return
	 */
	public NLPUtil dependOn(String... tags) {
		String lastToken = a.peekToken("");
		NPLArticle temp = a.copy();
		String tokenFound = expect(tags).peekToken("");
		if (!tokenFound.equals(lastToken)) {
			a = temp;
			a.increment();
		} else {
			a.failure = true;
		}
		return this;
	}

	public NPLArticle expectProperNoun() {
		//Some special conditions here for Initials. "A" is a DT (like A/An) and "C" is a LS (List Item marker like A. B. C.)
		return expectStartsWith("N").or().expect("DT").or().expect("LS").andMaybe().expect(".").disregardIfNotFound()
				.andMaybe().expectStartsWith("N").disregardIfNotFound()
				//Hyphenated name? I don't know why a "-"'s tag is a ":"
				.andMaybe().expect(":").andThen().expectStartsWith("N").disregardIfNotFound();

	}
	public NPLArticle expectProperNounOld() {
		//Nouns can consist of multiple tokens like first and last name. So...
		ignore(leftParens).andThen().expectStartsWith("N").or().expect("DT").andMaybe().expect(".").disregardIfNotFound();
		if (a.token == null) {
			//Non a noun, or if it's an initial it could be "A" like A. Rodgers. That is picked up as DT.
			return a;
		}
		int origLen = a.peekToken().length();
		String lastToken;
		do {
			lastToken = a.peekToken();
			a.andThen().expect("NNP", "NNPS");
		} while (lastToken.length() != a.token.length() && a.hasNext());
		if (origLen != a.peekToken().length()) {
			//Need to undo the last "andThen()"
			a.found = true;
			ignore(rightParens);
		}
		return a;
	}

	public NPLArticle expectCommonNoun() {
		return expectStartWithOneOrMany("NN", "NNS");
	}

	public NPLArticle expectAnyNoun() {
		return expectProperNoun().or().expectCommonNoun();
	}

	public NPLArticle expectVerb() {
		//Can Verbs to more than on token like Nouns?
		return expectStartWithOneOrMany("V");
	}

	public NPLArticle expectAdjective() {
		return expectStartWithOneOrMany("JJ");
	}

	public NPLArticle expectStartWithOneOrMany(String... tags) {
		if (!a.found || a.failure) {
			String lastToken = a.getLastToken("");
			int maybeCount = a.maybes == null ? 0 : a.maybes.size();
			while(true) {
				expectStartsWith(tags);
				if (lastToken.equals(a.getLastToken("")) || !a.hasNext()) {
					if (a.maybes != null && maybeCount < a.maybes.size()) {
						a.disregardIfNotFound();
					}
					break;
				} else {
					a.andMaybe();
					lastToken = a.getLastToken();
				}
			}
		}
		return a;
	}

	public NPLArticle expectOneOrMany(String... tags) {
		if (!a.found || a.failure) {
			int maybeCount = a.maybes == null ? 0 : a.maybes.size();
			String lastToken = a.getLastToken("");
			while(true) {
				expect(tags);
				if (lastToken.equals(a.getLastToken("")) || !a.hasNext()) {
					if (a.maybes != null && maybeCount < a.maybes.size() && a.maybes.size() != 0) {
						a.disregardIfNotFound();
					}
					break;
				} else {
					a.andMaybe();
					lastToken = a.getLastToken();
				}
			}
		}
		return a;
	}

	public NPLArticle expectNumber() {
		return expect("CD");
	}

	public NPLArticle expectDecimal() {
		return expectNumber().andMaybe().expect("DT").andThen().expectNumber();
	}

	public NPLArticle expect(String... expectList) {
		if(!a.found) {
			for (String e : expectList) {
				if(a.getIndex() >= tags.length) {
					return a;
				}
				String thisTag = tags[a.getIndex()];
				if (thisTag.equals(e)) {
					String thisToken = tokens[a.getIndex()];
					a.push(thisTag, thisToken);
					break;
				}
			}
		}
		return a;
	}

	public NPLArticle expectStartsWith(String... expectList) {
		if(!a.found) {
			for (String e : expectList) {
				String thisTag = tags[a.getIndex()];
				if (thisTag.startsWith(e)) {
					String thisToken = tokens[a.getIndex()];
					a.push(thisTag, thisToken);
					break;
				}
			}
		}
		return a;
	}

	public void replace(String token, String tag) {
		int i = a.getIndex();
		tags[i] = tag;
		tokens[i] = token;
	}

	protected void removeUsedTokens(int i) {
		tokens = subArray(tokens,i);
		tags = subArray(tags, i);
	}

	protected NPLArticle getArticle() {
		return a;
	}

	protected NPLArticle setArticle(NPLArticle a) {
		this.a = a;
		return a;
	}

	protected String[] subArray(String[] arr, int i) {
		return Arrays.copyOfRange(arr, i, arr.length);
	}

	protected void removeFromIndex(int i) {
		ArrayUtils.remove(tokens, i);
		ArrayUtils.remove(tags, i);
	}

	public class NPLArticle {
		boolean found = false, failure = false, openDoubleQuote = false, openSingleQuote = false;
		Stack<NPLArticle> maybes;
		List<String> allTags;
		String tag;
		String token = null, lastToken = null;
		int i;

		public NPLArticle() {
			startOver();
		}

		public NPLArticle copy() {
			NPLArticle art = new NPLArticle();
			art.found = this.found;
			art.failure = this.failure;
			art.allTags = this.allTags;
			art.tag = this.tag;
			art.token = this.token;
			art.i = this.i;
			art.maybes = this.maybes;
			return art;
		}

		public List<String> getAllTags() {
			return allTags;
		}

		public String getTag() {
			return tag;
		}

		protected void setTag(String tag) {
			if (found && !failure) {
				if (allTags == null) {
					allTags = new ArrayList<>();
				}
				allTags.add(tag);
				this.tag = tag;
			}
		}

		public String get() {
			if (!found || failure) {
				return null;
			}
			NLPUtil.this.removeUsedTokens(getIndex());
			String ret = token;
			startOver();
			return ret;
		}

		public void failure() {
			failure = true;
		}

		protected void setToken(String token) {
			if (token != null) {
				if (this.token == null) {
					setTokenNoSpace(token);
				} else {
				setTokenNoSpace(" " + token);}
			}
		}

		protected void setTokenNoSpace(String token) {
			if (failure || found) {
				return;
			}
			if(token != null) {
				found = true;
				lastToken = token;
				increment();
				if (this.token == null) {
					this.token = token;
				} else {
					this.token += token;
				}
			}
		}

		public int getIndex() {
			return i;
		}

		public String peekToken() {
			return token;
		}

		public String peekToken(String defaultStr) {
			if(token != null) {
				return token;
			}
			return defaultStr;
		}

		public String getLastToken(String defaultStr) {
			if(lastToken != null) {
				return lastToken;
			}
			return defaultStr;
		}
		public String getLastToken() {
			return lastToken;
		}

		protected int increment(){
			return i++;
		}

		public NLPUtil or() {
			return NLPUtil.this;
		}

		public NLPUtil andThen() {
			if(!found) {
				failure();
				//Short Circut
				i = 0;
			}
			found = false;
			return NLPUtil.this;
		}

		public NLPUtil andMaybe() {
			if (maybes == null) {
				maybes = new Stack<>();
			}
			NPLArticle copy = copy();
			maybes.push(copy);
			andThen();
			return NLPUtil.this;
		}

		public NLPUtil nevermind() {
			if (maybes.isEmpty()) {
				logger.warn("Called Nevermind without first calling Maybe. This is not proper usage.");
			}
			NPLArticle pop = maybes.pop();
			NLPUtil.this.setArticle(pop);
			return NLPUtil.this;
		}

		public NPLArticle disregardIfNotFound() {
			NLPUtil retUtil = NLPUtil.this;
			if (maybes == null || maybes.isEmpty()) {
				logger.warn("Called Nevermind without first calling andMaybe(). This is not proper usage.");
			} else if (token == null || token.equals(maybes.get(maybes.size() - 1).token)) {
				retUtil = nevermind();
			} else {
				maybes.pop();
				found = true;
			}
			return retUtil.getArticle();
		}

		public NLPUtil ifNext() {
			NPLArticle ifNext = copy();
			ifNext.i = this.getIndex();
			return ifNext.andThen();
		}

		public void startOver() {
			i = 0;
			token = null;
			tag = null;
			allTags = null;
			failure = false;
			found = false;
		}

		public boolean hasNext() {
			if (getIndex() >= getTags().length) {
				return false;
			}
			return true;
		}

		public void push(String newTag, String newToken) {
			switch (newTag) {
				case ".":
				case ",":
				case "?":
				case "!":
					setTokenNoSpace(newToken);
					break;
				case "\"":
					if (openDoubleQuote) {
						setToken(newToken);
					} else {
						setTokenNoSpace(newToken);
					}
					openDoubleQuote = !openDoubleQuote;
					break;
				case "\'":
					if (openSingleQuote) {
						setToken(newToken);
					} else {
						setTokenNoSpace(newToken);
					}
					openSingleQuote = !openSingleQuote;
					break;
				default:
					setToken(newToken);
					break;
			}
			setTag(newTag);
		}
	}
}
