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

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

import org.omegat.filters2.master.PluginUtils;
import org.omegat.tokenizer.ITokenizer.StemmingMode;
import org.omegat.util.Language;
import org.omegat.util.Token;

/**
 * Exercises the ICU based tokenizer on the scripts without word spaces that
 * ICU4J ships dictionaries for.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class IcuTokenizerTest extends TokenizerTestBase {

    /**
     * Stemming modes consult the project language, which no unit test has;
     * the modes without stemming cover the segmentation this tokenizer adds.
     */
    @Test
    public void testKhmer() {
        // "I love the Khmer language. 2 times"
        String orig = "\u1781\u17d2\u1789\u17bb\u17c6\u179f\u17d2\u179a\u179b\u17b6\u1789\u17cb"
                + "\u1797\u17b6\u179f\u17b6\u1781\u17d2\u1798\u17c2\u179a\u17d4 2 \u178a\u1784";
        ITokenizer tok = new IcuTokenizer();
        String i = "\u1781\u17d2\u1789\u17bb\u17c6";
        String love = "\u179f\u17d2\u179a\u179b\u17b6\u1789\u17cb";
        String khmerLanguage = "\u1797\u17b6\u179f\u17b6\u1781\u17d2\u1798\u17c2\u179a";
        String times = "\u178a\u1784";
        assertVerbatim(new String[] { i, love, khmerLanguage, "\u17d4", " ", "2", " ", times },
                tok.tokenizeVerbatimToStrings(orig), tok.tokenizeVerbatim(orig), orig);
        assertResult(new String[] { i, love, khmerLanguage, times },
                tok.tokenizeWordsToStrings(orig, StemmingMode.NONE));
        // Glossary mode keeps the number, as the digit filter is off there.
        // (The string variant consults the project language for stemming
        // modes, the token variant does not.)
        assertResult(new String[] { i, love, khmerLanguage, "2", times },
                Token.getTextsFromString(tok.tokenizeWords(orig, StemmingMode.GLOSSARY), orig));
    }

    /**
     * OmegaT tags must survive as one token each in the verbatim paths (case
     * changes rewrite verbatim tokens) and stay out of the word lists, like
     * the default tokenizer does with its word iterator.
     */
    @Test
    public void testTagsStayWhole() {
        String i = "\u0e82\u0ec9\u0ead\u0e8d";
        String love = "\u0eae\u0eb1\u0e81";
        String orig = "<g0>" + i + "</g0>" + love + "<x1/>";
        ITokenizer tok = new IcuTokenizer();
        assertVerbatim(new String[] { "<g0>", i, "</g0>", love, "<x1/>" }, tok.tokenizeVerbatimToStrings(orig),
                tok.tokenizeVerbatim(orig), orig);
        assertResult(new String[] { i, love }, tok.tokenizeWordsToStrings(orig, StemmingMode.NONE));
        assertResult(new String[] { i, love },
                Token.getTextsFromString(tok.tokenizeWords(orig, StemmingMode.GLOSSARY), orig));
        // Angle brackets that are no tag are ordinary punctuation.
        String noTag = "<b>" + i + "</b>";
        assertVerbatim(new String[] { "<", "b", ">", i, "<", "/", "b", ">" },
                tok.tokenizeVerbatimToStrings(noTag), tok.tokenizeVerbatim(noTag), noTag);
        assertResult(new String[] { "b", i, "b" }, tok.tokenizeWordsToStrings(noTag, StemmingMode.NONE));
    }

    /**
     * Digits glued to a word stay in one segment with it (the word rules bind
     * letters and numbers before the dictionary engine runs), exactly as the
     * JDK iterator behaves; this pins the limit rather than approving it.
     */
    @Test
    public void testDigitsGluedToWordsStayOneSegment() {
        String i = "\u1781\u17d2\u1789\u17bb\u17c6";
        String times = "\u178a\u1784";
        String orig = i + "2" + times;
        ITokenizer tok = new IcuTokenizer();
        assertVerbatim(new String[] { orig }, tok.tokenizeVerbatimToStrings(orig), tok.tokenizeVerbatim(orig), orig);
        assertEquals(1, new DefaultTokenizer().tokenizeVerbatim(orig).length);
    }

    /**
     * Zero width spaces separate words in Khmer and Burmese texts; they are
     * their own verbatim token and never a word. Supplementary characters keep
     * the offsets in UTF-16 units, as the editor expects.
     */
    @Test
    public void testSeparatorsAndSupplementaryCharacters() {
        String myanmarLanguage = "\u1019\u103c\u1014\u103a\u1019\u102c\u1018\u102c\u101e\u102c";
        String speech = "\u1005\u1000\u102c\u1038";
        String orig = myanmarLanguage + "\u200b" + speech + " \ud83d\ude00 ok";
        ITokenizer tok = new IcuTokenizer();
        assertVerbatim(new String[] { myanmarLanguage, "\u200b", speech, " ", "\ud83d\ude00", " ", "ok" },
                tok.tokenizeVerbatimToStrings(orig), tok.tokenizeVerbatim(orig), orig);
        assertResult(new String[] { myanmarLanguage, speech, "ok" },
                tok.tokenizeWordsToStrings(orig, StemmingMode.NONE));
        Token[] verbatim = tok.tokenizeVerbatim(orig);
        assertEquals(orig.indexOf("ok"), verbatim[verbatim.length - 1].getOffset());
    }

    /**
     * The tokenizer is the default for its three languages, Thai stays with
     * the Lucene analyzer; this is the test that fails if the core changes
     * how tokenizers are selected.
     */
    @Test
    public void testSelectedAsDefaultForItsLanguages() {
        PluginUtils.loadPlugins(Collections.emptyMap());
        for (String lang : new String[] { "km", "lo", "my", "km-KH", "my-MM" }) {
            assertEquals(lang, IcuTokenizer.class, PluginUtils.getTokenizerClassForLanguage(new Language(lang)));
        }
        assertEquals(LuceneThaiTokenizer.class, PluginUtils.getTokenizerClassForLanguage(new Language("th")));
    }

    @Test
    public void testLao() {
        // "Hello, I love the Lao language"
        String hello = "\u0eaa\u0eb0\u0e9a\u0eb2\u0e8d\u0e94\u0eb5";
        String i = "\u0e82\u0ec9\u0ead\u0e8d";
        String love = "\u0eae\u0eb1\u0e81";
        String language = "\u0e9e\u0eb2\u0eaa\u0eb2";
        String lao = "\u0ea5\u0eb2\u0ea7";
        String orig = hello + " " + i + love + language + lao;
        ITokenizer tok = new IcuTokenizer();
        assertVerbatim(new String[] { hello, " ", i, love, language, lao }, tok.tokenizeVerbatimToStrings(orig),
                tok.tokenizeVerbatim(orig), orig);
        assertResult(new String[] { hello, i, love, language, lao },
                tok.tokenizeWordsToStrings(orig, StemmingMode.NONE));
    }

    @Test
    public void testBurmese() {
        // "Myanmar language" + "speech"
        String myanmarLanguage = "\u1019\u103c\u1014\u103a\u1019\u102c\u1018\u102c\u101e\u102c";
        String speech = "\u1005\u1000\u102c\u1038";
        String orig = myanmarLanguage + speech;
        ITokenizer tok = new IcuTokenizer();
        assertVerbatim(new String[] { myanmarLanguage, speech }, tok.tokenizeVerbatimToStrings(orig),
                tok.tokenizeVerbatim(orig), orig);
        assertResult(new String[] { myanmarLanguage, speech }, tok.tokenizeWordsToStrings(orig, StemmingMode.NONE));
    }

    @Test
    public void testEmptyAndLatin() {
        ITokenizer tok = new IcuTokenizer();
        assertEquals(0, tok.tokenizeVerbatim("").length);
        assertEquals(0, tok.tokenizeWords("", StemmingMode.NONE).length);
        String orig = "OmegaT 6.1, ok?";
        assertVerbatim(new String[] { "OmegaT", " ", "6.1", ",", " ", "ok", "?" },
                tok.tokenizeVerbatimToStrings(orig), tok.tokenizeVerbatim(orig), orig);
        assertResult(new String[] { "OmegaT", "ok" }, tok.tokenizeWordsToStrings(orig, StemmingMode.NONE));
    }
}
