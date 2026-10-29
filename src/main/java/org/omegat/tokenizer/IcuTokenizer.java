/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2026 Stephan Pakebusch
               Home page: https://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.tokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;

import org.omegat.util.Log;
import org.omegat.util.PatternConsts;
import org.omegat.util.StringUtil;
import org.omegat.util.Token;

/**
 * Tokenizer for scripts written without spaces between words, backed by the
 * dictionary-based word break iterator of ICU4J.
 *
 * ICU4J ships word dictionaries for Khmer, Lao and Burmese (and for Thai,
 * which OmegaT already covers with the Lucene Thai analyzer). The JDK break
 * iterator used by {@link DefaultTokenizer} has no dictionaries for these
 * scripts and returns whole phrases as one token, so matching, glossary
 * lookup and spell checking saw no word boundaries at all. No stemming or
 * stop word data exists for these languages; stemming modes therefore yield
 * the plain words. ICU offers no list of the scripts its dictionaries cover,
 * so the language list below is maintained by hand.
 *
 * Known limit, shared with the JDK iterator: digits glued to a word without
 * a space (a year, a count) keep the whole run as one segment, because the
 * word rules bind letters and numbers before the dictionary engine runs.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
@Tokenizer(languages = { "km", "lo", "my" }, isDefault = true)
public class IcuTokenizer extends BaseTokenizer {

    @Override
    protected TokenStream getTokenStream(final String strOrig, final boolean stemsAllowed,
            final boolean stopWordsAllowed) {
        return new BreakIteratorTokenStream(strOrig, true);
    }

    /**
     * Every character of the text ends up in exactly one token, OmegaT tags
     * stay whole as with {@link WordIterator}; this is what case changes and
     * the diff views rely on.
     */
    @Override
    public Token[] tokenizeVerbatim(final String strOrig) {
        if (StringUtil.isEmpty(strOrig)) {
            return EMPTY_TOKENS_LIST;
        }
        List<Token> result = new ArrayList<>(DEFAULT_TOKENS_COUNT);
        try (BreakIteratorTokenStream in = new BreakIteratorTokenStream(strOrig, false)) {
            in.reset();
            while (in.incrementToken()) {
                result.add(new Token(in.termAtt.toString(), in.offsetAtt.startOffset()));
            }
            in.end();
        } catch (IOException ex) {
            Log.log(ex);
        }
        return result.toArray(new Token[0]);
    }

    @Override
    public String[] tokenizeVerbatimToStrings(final String strOrig) {
        if (StringUtil.isEmpty(strOrig)) {
            return EMPTY_STRING_LIST;
        }
        return Token.getTextsFromString(tokenizeVerbatim(strOrig), strOrig);
    }

    /**
     * Emits the segments of an ICU word break iterator as Lucene tokens.
     * OmegaT tags are emitted as one segment each. With {@code wordsOnly}
     * the tags and the segments ICU classifies as neither letters, numbers,
     * kana nor ideographs (spaces, punctuation) are skipped, which is what
     * the Lucene analyzers do for the other languages. Numbers pass the
     * status filter and are left to the digit filter of the base class.
     */
    private static final class BreakIteratorTokenStream extends TokenStream {
        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
        private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
        private final String text;
        private final BreakIterator breaker;
        private final boolean wordsOnly;
        private final int[] tagStarts;
        private final int[] tagEnds;
        private int start;
        private int nextTag;

        BreakIteratorTokenStream(final String text, final boolean wordsOnly) {
            this.text = text;
            this.wordsOnly = wordsOnly;
            // The dictionary break engines are selected by script, not by
            // locale, so the root rules serve all supported languages alike.
            breaker = BreakIterator.getWordInstance(ULocale.ROOT);
            breaker.setText(text);
            List<int[]> tags = new ArrayList<>();
            Matcher m = PatternConsts.OMEGAT_TAG.matcher(text);
            while (m.find()) {
                tags.add(new int[] { m.start(), m.end() });
            }
            tagStarts = tags.stream().mapToInt(t -> t[0]).toArray();
            tagEnds = tags.stream().mapToInt(t -> t[1]).toArray();
        }

        @Override
        public void reset() {
            start = breaker.first();
            nextTag = 0;
        }

        @Override
        public boolean incrementToken() {
            while (start < text.length()) {
                int tokenStart = start;
                boolean tag = nextTag < tagStarts.length && tagStarts[nextTag] == tokenStart;
                int end;
                if (tag) {
                    // Jump the iterator past the tag; the character after a
                    // tag always starts a new segment unless it is a combining
                    // mark or a joiner, which then stays attached to the tag
                    // token.
                    end = breaker.isBoundary(tagEnds[nextTag]) ? tagEnds[nextTag] : breaker.current();
                    nextTag++;
                } else {
                    end = breaker.next();
                    if (end == BreakIterator.DONE) {
                        return false;
                    }
                }
                start = end;
                if (wordsOnly && (tag || breaker.getRuleStatus() < BreakIterator.WORD_NONE_LIMIT)) {
                    continue;
                }
                clearAttributes();
                termAtt.setEmpty().append(text, tokenStart, end);
                offsetAtt.setOffset(tokenStart, end);
                return true;
            }
            return false;
        }

        @Override
        public void end() throws IOException {
            super.end();
            offsetAtt.setOffset(text.length(), text.length());
        }
    }
}
