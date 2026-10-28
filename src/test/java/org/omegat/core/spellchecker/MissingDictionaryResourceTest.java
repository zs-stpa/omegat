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
import static org.junit.Assert.assertNull;

import java.io.InputStream;

import org.jspecify.annotations.Nullable;
import org.junit.Test;

/**
 * A module whose resources are not reachable (wrong data broker language,
 * missing file) yields "no dictionary", not a NullPointerException that
 * would abort spell checker initialisation.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class MissingDictionaryResourceTest {

    private static final class HunspellWithoutResources extends AbstractHunspellDictionary {
        @Override
        protected String[] getDictionaries() {
            return new String[] { "zz_ZZ" };
        }

        @Override
        protected @Nullable InputStream getResourceAsStream(String resource) {
            return null;
        }
    }

    private static final class MorfologikWithoutResources extends AbstractMorfologikDictionary {
        @Override
        protected String[] getDictionaries() {
            return new String[] { "zz_ZZ" };
        }

        @Override
        protected @Nullable InputStream getResourceAsStream(String resource) {
            return null;
        }
    }

    @Test
    public void testHunspellWithoutResourcesYieldsNoDictionary() throws Exception {
        try (HunspellWithoutResources provider = new HunspellWithoutResources()) {
            assertNull(provider.getHunspellDictionary("zz_ZZ"));
            assertEquals("zz_ZZ", provider.getDictionaryStem("zz"));
            DictionaryInfo info = provider.getDictionaryInfo("zz_ZZ");
            assertNull(info.entries());
            assertNull(info.sizeBytes());
        }
    }

    @Test
    public void testMorfologikWithoutResourcesYieldsNoDictionary() throws Exception {
        try (MorfologikWithoutResources provider = new MorfologikWithoutResources()) {
            assertNull(provider.getMorfologikDictionary("zz_ZZ"));
            assertNull(provider.extractDictionary(java.nio.file.Files.createTempDirectory("missing"), "zz_ZZ"));
            DictionaryInfo info = provider.getDictionaryInfo("zz_ZZ");
            assertNull(info.entries());
            assertNull(info.sizeBytes());
        }
    }
}
