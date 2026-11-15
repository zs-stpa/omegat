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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.core.spellchecker.DictionaryManager.SpellingDictionaryEntry;
import org.omegat.util.OStrings;

/**
 * Proves that the available-dictionaries display shows one distinguishable
 * line per dictionary, naming engine and origin. The flat per-source list
 * showed the same language up to three times with identical labels
 * (bundled Hunspell, bundled Morfologik, installed file).
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class DictionaryManagerTest {

    /**
     * Fictitious language codes: provider registrations are static and
     * cannot be removed, so they must never collide with real languages
     * other tests may query.
     */
    private static final String BOTH_SOURCES = "zz_ZZ";
    private static final String FILE_ONLY = "zz_YY";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private DictionaryManager manager;

    @Before
    public void setUp() throws Exception {
        File dir = folder.newFolder("dictionaries");
        Files.writeString(new File(dir, BOTH_SOURCES + ".aff").toPath(), "SET UTF-8\n",
                StandardCharsets.UTF_8);
        Files.writeString(new File(dir, BOTH_SOURCES + ".dic").toPath(), "1\nword\n",
                StandardCharsets.UTF_8);
        Files.writeString(new File(dir, FILE_ONLY + ".dict").toPath(), "", StandardCharsets.UTF_8);
        SpellCheckerManager.registerSpellCheckerDictionaryProvider(BOTH_SOURCES,
                SpellCheckDictionaryType.HUNSPELL, "test.Provider");
        SpellCheckerManager.registerSpellCheckerDictionaryProvider(BOTH_SOURCES,
                SpellCheckDictionaryType.MORFOLOGIK, "test.Provider");
        manager = new DictionaryManager(dir);
    }

    @Test
    public void testDisplayListShowsOneDistinguishableLinePerDictionary() {
        List<String> display = manager.getLocalDictionaryDisplayList();

        // One line per dictionary, made distinguishable by the origin
        // suffix; installed files sort before bundled ones per language.
        String installedHunspell = expectedLine(BOTH_SOURCES,
                OStrings.getString("GUI_SPELLCHECKER_SOURCE_INSTALLED", "Hunspell"));
        String bundledHunspell = expectedLine(BOTH_SOURCES,
                OStrings.getString("GUI_SPELLCHECKER_SOURCE_BUNDLED", "Hunspell"));
        String bundledMorfologik = expectedLine(BOTH_SOURCES,
                OStrings.getString("GUI_SPELLCHECKER_SOURCE_BUNDLED", "Morfologik"));
        List<String> bothSourceLines = display.stream()
                .filter(line -> line.contains(BOTH_SOURCES)).toList();
        assertEquals(List.of(installedHunspell, bundledHunspell, bundledMorfologik), bothSourceLines);

        String fileOnly = expectedLine(FILE_ONLY,
                OStrings.getString("GUI_SPELLCHECKER_SOURCE_INSTALLED", "Morfologik"));
        assertTrue("origin of a lone local file must be named", display.contains(fileOnly));
        assertEquals("no line may repeat", display.size(), display.stream().distinct().count());
    }

    @Test
    public void testAliasRegistrationsListOneDictionary() {
        // One bundled pair is registered under several spellings of its
        // code; the table shows it once, in the canonical spelling.
        SpellCheckerManager.registerSpellCheckerDictionaryProvider("zz_Latn", SpellCheckDictionaryType.HUNSPELL,
                "test.Provider");
        SpellCheckerManager.registerSpellCheckerDictionaryProvider("zz_LATN", SpellCheckDictionaryType.HUNSPELL,
                "test.Provider");
        SpellCheckerManager.registerSpellCheckerDictionaryProvider("zz-LATN", SpellCheckDictionaryType.HUNSPELL,
                "test.Provider");
        List<String> latin = manager.getLocalDictionaryEntries().stream().map(SpellingDictionaryEntry::getLanguageCode)
                .filter(code -> code.replace('-', '_').equalsIgnoreCase("zz_Latn")).toList();
        assertEquals(List.of("zz_Latn"), latin);
        assertEquals("zz_Latn", DictionaryManager.preferredSpelling("zz_LATN", "zz_Latn"));
        assertEquals("zz_LATN", DictionaryManager.preferredSpelling("zz-LATN", "zz_LATN"));
    }

    @Test
    public void testDisplayNameAcceptsHyphenAndScriptCodes() {
        assertEquals(DictionaryManager.getLanguageDisplayName("de_AT"),
                DictionaryManager.getLanguageDisplayName("de-AT"));
        assertEquals(DictionaryManager.getLanguageDisplayName("sr-Latn"),
                DictionaryManager.getLanguageDisplayName("sr_Latn"));
        // The script subtag is understood, not echoed.
        assertNotEquals("sr_Latn", DictionaryManager.getLanguageDisplayName("sr_Latn"));
    }

    @Test
    public void testSummaryCountsDictionariesAndLanguages() {
        String summary = DictionaryManager.summarize(List.of(
                new SpellingDictionaryEntry("zz_ZZ", SpellCheckDictionaryType.HUNSPELL, true),
                new SpellingDictionaryEntry("zz_ZZ", SpellCheckDictionaryType.HUNSPELL, false),
                new SpellingDictionaryEntry("zz_Latn", SpellCheckDictionaryType.HUNSPELL, false),
                new SpellingDictionaryEntry("zy-YY", SpellCheckDictionaryType.MORFOLOGIK, true)));
        // Four dictionaries, two languages: regions and scripts fold into the language.
        String expected = OStrings.getString("GUI_SPELLCHECKER_SUMMARY_DICTIONARIES", 4) + "\n"
                + OStrings.getString("GUI_SPELLCHECKER_SUMMARY_LANGUAGES", 2, "zy, zz");
        assertEquals(expected, summary);
        assertEquals("", DictionaryManager.summarize(List.of()));
        assertEquals("sr", DictionaryManager.baseLanguage("sr_Latn"));
        assertEquals("bo", DictionaryManager.baseLanguage("bo-CN"));
        assertEquals("tlh", DictionaryManager.baseLanguage("tlh"));
    }

    @Test
    public void testDescribeAndLocateInstalledHunspellFile() throws Exception {
        SpellingDictionaryEntry installed = new SpellingDictionaryEntry(BOTH_SOURCES,
                SpellCheckDictionaryType.HUNSPELL, true);
        DictionaryInfo info = manager.describe(installed);
        assertEquals(Long.valueOf(1), info.entries());
        File dir = new File(manager.getDirectory());
        long expectedSize = new File(dir, BOTH_SOURCES + ".aff").length()
                + new File(dir, BOTH_SOURCES + ".dic").length();
        assertEquals(Long.valueOf(expectedSize), info.sizeBytes());
        assertEquals(OStrings.getString("GUI_SPELLCHECKER_LICENSE_LOCAL"), info.license());
        assertEquals("the link of an installed file opens its folder", dir.toURI().toString(), info.upstream());
        Path located = manager.locate(installed);
        assertEquals(new File(dir, BOTH_SOURCES + ".dic").toPath(), located);
        // A Morfologik file without its .info has a size but no count.
        DictionaryInfo lone = manager.describe(new SpellingDictionaryEntry(FILE_ONLY,
                SpellCheckDictionaryType.MORFOLOGIK, true));
        assertNull(lone.entries());
        assertEquals(Long.valueOf(0), lone.sizeBytes());
    }

    private static String expectedLine(String code, String sources) {
        return OStrings.getString("GUI_SPELLCHECKER_LANG_WITH_SOURCES", code,
                DictionaryManager.getLanguageDisplayName(code), sources);
    }
}
