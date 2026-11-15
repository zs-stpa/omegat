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

package org.omegat.core.spellchecker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.junit.Test;

/**
 * Loads the fictitious zz_ZZ pair from the test class path through the
 * generic provider, the way a dictionary-only language module does.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class BundledHunspellDictionaryTest {

    @Test
    public void testRegisterServesStemLanguageAndAliases() throws Exception {
        BundledHunspellDictionary.register("zz_ZZ", "zz-YY");
        assertEquals("zz_ZZ", BundledHunspellDictionary.getRegisteredStems().get("zz"));
        assertEquals("zz_ZZ", BundledHunspellDictionary.getRegisteredStems().get("zz_YY"));
        assertTrue(SpellCheckerManager.getHunspellDictionaryLanguages().contains("zz_ZZ"));
        assertTrue(SpellCheckerManager.getHunspellDictionaryLanguages().contains("zz"));

        for (String code : new String[] { "zz_ZZ", "zz", "zz_YY" }) {
            try (BundledHunspellDictionary provider = new BundledHunspellDictionary()) {
                Dictionary dictionary = provider.getHunspellDictionary(code);
                assertNotNull("dictionary for " + code, dictionary);
                Hunspell speller = new Hunspell(dictionary);
                assertTrue(speller.spell("omega"));
                assertTrue("affix rule applied", speller.spell("omegas"));
                assertTrue(!speller.spell("omegat"));
            }
        }
    }

    @Test
    public void testExplicitLanguagePairWinsOverDerivedOne() {
        // Serbian ships sr (Cyrillic) and sr_Latn; "sr" must stay Cyrillic in
        // both registration orders.
        BundledHunspellDictionary.register("zy_Latn");
        BundledHunspellDictionary.register("zy");
        assertEquals("zy", BundledHunspellDictionary.getRegisteredStems().get("zy"));
        BundledHunspellDictionary.register("zx");
        BundledHunspellDictionary.register("zx_Latn");
        assertEquals("zx", BundledHunspellDictionary.getRegisteredStems().get("zx"));
        assertEquals("zx_Latn", BundledHunspellDictionary.getRegisteredStems().get("zx_Latn"));
        // OmegaT asks with the script upper-cased and with a hyphen.
        assertEquals("zx_Latn", BundledHunspellDictionary.getRegisteredStems().get("zx_LATN"));
        assertEquals("zx_Latn", BundledHunspellDictionary.getRegisteredStems().get("zx-LATN"));
        assertTrue(SpellCheckerManager.getHunspellDictionaryLanguages().contains("zx_LATN"));
    }

    @Test
    public void testUnknownLanguageYieldsNothing() throws Exception {
        try (BundledHunspellDictionary provider = new BundledHunspellDictionary()) {
            assertNull(provider.getHunspellDictionary("qq_QQ"));
        }
    }

    @Test
    public void testInfoCountsEntriesAndSizesBothFiles() throws Exception {
        BundledHunspellDictionary.register("zz_ZZ");
        try (BundledHunspellDictionary provider = new BundledHunspellDictionary()) {
            DictionaryInfo info = provider.getDictionaryInfo("zz");
            assertEquals(Long.valueOf(3), info.entries());
            String base = "/org/omegat/dictionaries/hunspell/zz_ZZ";
            long aff = Files.size(Path.of(getClass().getResource(base + ".aff").toURI()));
            long dic = Files.size(Path.of(getClass().getResource(base + ".dic").toURI()));
            assertEquals(Long.valueOf(aff + dic), info.sizeBytes());
            // From zz_ZZ.properties next to the pair.
            assertEquals("Test-License-1.0", info.license());
            assertEquals("https://example.org/zz", info.upstream());
            assertEquals(DictionaryInfo.UNKNOWN, provider.getDictionaryInfo("qq_QQ"));
        }
        assertEquals(Long.valueOf(3),
                SpellCheckerManager.getDictionaryInfo("zz_ZZ", SpellCheckDictionaryType.HUNSPELL).entries());
        assertEquals(DictionaryInfo.UNKNOWN,
                SpellCheckerManager.getDictionaryInfo("qq_QQ", SpellCheckDictionaryType.HUNSPELL));
    }

    @Test
    public void testExtractGivesTheWordList() throws Exception {
        BundledHunspellDictionary.register("zz_ZZ");
        Path dir = Files.createTempDirectory("bundled-hunspell");
        Path dic = SpellCheckerManager.extractDictionary("zz", SpellCheckDictionaryType.HUNSPELL, dir);
        assertNotNull(dic);
        assertEquals(dir.resolve("zz_ZZ.dic"), dic);
        assertTrue(Files.exists(dir.resolve("zz_ZZ.aff")));
    }

    @Test
    public void testInstallCopiesBothFiles() throws Exception {
        BundledHunspellDictionary.register("zz_ZZ");
        Path dir = Files.createTempDirectory("bundled-hunspell");
        try (BundledHunspellDictionary provider = new BundledHunspellDictionary()) {
            Path dic = provider.installHunspellDictionary(dir, "zz");
            assertNotNull(dic);
            assertEquals("zz_ZZ.dic", dic.getFileName().toString());
            assertTrue(Files.exists(dir.resolve("zz_ZZ.aff")));
            assertEquals("3", Files.readAllLines(dic).get(0));
        }
    }
}
