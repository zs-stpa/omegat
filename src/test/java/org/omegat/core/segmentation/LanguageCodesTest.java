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

package org.omegat.core.segmentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;

import org.omegat.util.LocaleRule;

/**
 * The localized names of the default segmentation rule sets must map back
 * to their stable codes no matter which UI language wrote or reads them.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class LanguageCodesTest {

    private static final Map<String, String> KEY_TO_CODE = Map.ofEntries(
            Map.entry(LanguageCodes.CATALAN_KEY, LanguageCodes.CATALAN_CODE),
            Map.entry(LanguageCodes.CZECH_KEY, LanguageCodes.CZECH_CODE),
            Map.entry(LanguageCodes.GERMAN_KEY, LanguageCodes.GERMAN_CODE),
            Map.entry(LanguageCodes.ENGLISH_KEY, LanguageCodes.ENGLISH_CODE),
            Map.entry(LanguageCodes.SPANISH_KEY, LanguageCodes.SPANISH_CODE),
            Map.entry(LanguageCodes.FINNISH_KEY, LanguageCodes.FINNISH_CODE),
            Map.entry(LanguageCodes.FRENCH_KEY, LanguageCodes.FRENCH_CODE),
            Map.entry(LanguageCodes.ITALIAN_KEY, LanguageCodes.ITALIAN_CODE),
            Map.entry(LanguageCodes.JAPANESE_KEY, LanguageCodes.JAPANESE_CODE),
            Map.entry(LanguageCodes.DUTCH_KEY, LanguageCodes.DUTCH_CODE),
            Map.entry(LanguageCodes.POLISH_KEY, LanguageCodes.POLISH_CODE),
            Map.entry(LanguageCodes.RUSSIAN_KEY, LanguageCodes.RUSSIAN_CODE),
            Map.entry(LanguageCodes.SWEDISH_KEY, LanguageCodes.SWEDISH_CODE),
            Map.entry(LanguageCodes.SLOVAK_KEY, LanguageCodes.SLOVAK_CODE),
            Map.entry(LanguageCodes.CHINESE_KEY, LanguageCodes.CHINESE_CODE),
            Map.entry(LanguageCodes.DEFAULT_KEY, LanguageCodes.DEFAULT_CODE),
            Map.entry(LanguageCodes.F_TEXT_KEY, LanguageCodes.F_TEXT_CODE),
            Map.entry(LanguageCodes.F_HTML_KEY, LanguageCodes.F_HTML_CODE));

    @Rule
    public final LocaleRule localeRule = new LocaleRule(Locale.of("en"));

    @Test
    public void testHistoricGermanNamesResolveUnderEnglishUi() {
        assertEquals(LanguageCodes.DEFAULT_CODE, LanguageCodes.getLanguageCodeByName("Standard"));
        assertEquals(LanguageCodes.DEFAULT_CODE, LanguageCodes.getLanguageCodeByName("Grundeinstellung"));
        assertEquals(LanguageCodes.F_TEXT_CODE,
                LanguageCodes.getLanguageCodeByName("Segmentierung der Textdateien"));
        assertEquals(LanguageCodes.F_HTML_CODE,
                LanguageCodes.getLanguageCodeByName("Segmentierung von HTML-, XHTML-, ODF- und Infix-Dateien"));
        assertEquals(LanguageCodes.F_HTML_CODE,
                LanguageCodes.getLanguageCodeByName("HTML, XHTML, ODF und Infix-Segmentierung"));
        assertEquals("names written with a trailing space (Bundle_de of the 3.0 era) still resolve",
                LanguageCodes.F_HTML_CODE, LanguageCodes
                        .getLanguageCodeByName("Segmentierung von HTML-, XHTML-, ODF- und Infix-Dateien "));
        assertNull(LanguageCodes.getLanguageCodeByName("no such rule set"));
    }

    /**
     * Every localized value of the CORE_SRX_RULES_* keys in every shipped
     * bundle must resolve to its stable code under a foreign UI language.
     * This pins future translation changes: a renamed value must be added
     * to rule-name-aliases.properties or this test fails. Should a future
     * translation ever reuse a name that already aliases ANOTHER code, the
     * ambiguous name must stay out of the table (collision rule) and be
     * skipped here with a comment instead.
     */
    @Test
    public void testEveryShippedTranslationResolves() throws Exception {
        File[] bundles = new File("src/main/resources/org/omegat")
                .listFiles((dir, name) -> name.startsWith("Bundle") && name.endsWith(".properties"));
        assertNotNull(bundles);
        assertTrue("expected the shipped bundles to be found", bundles.length > 30);
        for (File bundle : bundles) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(bundle)) {
                props.load(in);
            }
            for (Map.Entry<String, String> entry : KEY_TO_CODE.entrySet()) {
                String localized = props.getProperty(entry.getKey());
                if (localized == null || localized.isBlank()) {
                    continue;
                }
                assertEquals(bundle.getName() + ": " + entry.getKey() + "=" + localized,
                        entry.getValue(), LanguageCodes.getLanguageCodeByName(localized));
            }
        }
    }
}
