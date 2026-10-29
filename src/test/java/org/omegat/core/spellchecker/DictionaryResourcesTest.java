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
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.omegat.core.spellchecker.SpellCheckDictionaryType.HUNSPELL;
import static org.omegat.core.spellchecker.SpellCheckDictionaryType.MORFOLOGIK;

/**
 * Licence and upstream of a bundled dictionary come from the
 * {@code <stem>.properties} its module ships; nothing is guessed from the
 * module licence when that file is missing.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class DictionaryResourcesTest {

    private static final String LINK = "https://example.org/zz/README_zz_ZZ.txt";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path jar;

    @Before
    public void setUp() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        // The module licence must never leak into the dictionary's column.
        manifest.getMainAttributes().putValue("Plugin-License", "GNU Public License version 3 or later");
        jar = folder.newFile("omegat-language-zz.jar").toPath();
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream jarOut = new JarOutputStream(out, manifest)) {
            jarOut.putNextEntry(new JarEntry("org/languagetool/resource/zz/hunspell/zz_ZZ.dict"));
            jarOut.write("fsa".getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
            jarOut.putNextEntry(new JarEntry("org/omegat/languages/zz/zz_ZZ.properties"));
            jarOut.write(("license=GPL-2.0/LGPL-2.1/MPL-1.1\nupstream=" + LINK + "\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            jarOut.closeEntry();
            jarOut.putNextEntry(new JarEntry("org/omegat/languages/zz/zz_YY.properties"));
            jarOut.write("license=\nupstream= \n".getBytes(StandardCharsets.ISO_8859_1));
            jarOut.closeEntry();
            jarOut.putNextEntry(new JarEntry("org/omegat/languages/zz/zz_ZZ.morfologik.properties"));
            jarOut.write("license=CC-BY-SA-4.0\nupstream=https://example.org/zz/automaton\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            jarOut.closeEntry();
        }
    }

    @Test
    public void testOriginComesFromThePropertiesOfTheStem() {
        DictionaryInfo info = DictionaryResources.completeOrigin(new DictionaryInfo(null, null, 7L, 3L), jar,
                "zz_ZZ", HUNSPELL);
        assertEquals("GPL-2.0/LGPL-2.1/MPL-1.1", info.license());
        assertEquals(LINK, info.upstream());
        assertEquals("counts and sizes untouched", Long.valueOf(7), info.entries());
    }

    @Test
    public void testEngineSpecificPropertiesWinForThatEngine() {
        DictionaryInfo automaton = DictionaryResources.completeOrigin(DictionaryInfo.UNKNOWN, jar, "zz_ZZ",
                MORFOLOGIK);
        assertEquals("CC-BY-SA-4.0", automaton.license());
        assertEquals("https://example.org/zz/automaton", automaton.upstream());
        assertEquals("the plain file still serves the other engine", "GPL-2.0/LGPL-2.1/MPL-1.1",
                DictionaryResources.completeOrigin(DictionaryInfo.UNKNOWN, jar, "zz_ZZ", HUNSPELL).license());
    }

    @Test
    public void testWithoutPropertiesNothingIsGuessed() {
        assertSame(DictionaryInfo.UNKNOWN, DictionaryResources.completeOrigin(DictionaryInfo.UNKNOWN, jar, "qq_QQ", HUNSPELL));
        assertSame(DictionaryInfo.UNKNOWN, DictionaryResources.completeOrigin(DictionaryInfo.UNKNOWN, null, "zz_ZZ", HUNSPELL));
        assertSame(DictionaryInfo.UNKNOWN, DictionaryResources.completeOrigin(DictionaryInfo.UNKNOWN, jar, null, HUNSPELL));
        DictionaryInfo blank = DictionaryResources.completeOrigin(DictionaryInfo.UNKNOWN, jar, "zz_YY", HUNSPELL);
        assertNull("blank values count as absent", blank.license());
        assertNull(blank.upstream());
    }

    @Test
    public void testDeclaredValuesWin() throws Exception {
        DictionaryInfo declared = new DictionaryInfo("MIT", "https://example.org/dict", null, null);
        assertSame(declared, DictionaryResources.completeOrigin(declared, jar, "zz_ZZ", HUNSPELL));
        DictionaryInfo licensed = new DictionaryInfo("MIT", null, null, null);
        DictionaryInfo completed = DictionaryResources.completeOrigin(licensed, jar, "zz_ZZ", HUNSPELL);
        assertEquals("MIT", completed.license());
        assertEquals(LINK, completed.upstream());
        DictionaryInfo fromStream = DictionaryResources.originFrom(DictionaryInfo.UNKNOWN,
                new ByteArrayInputStream("license= Apache-2.0 \n".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("Apache-2.0", fromStream.license());
        assertNull(fromStream.upstream());
    }

    @Test
    public void testHunspellCountReadsFirstLineWithBom() throws Exception {
        assertEquals(Long.valueOf(42), DictionaryResources.hunspellEntryCount(
                new ByteArrayInputStream("﻿42\nword\n".getBytes(StandardCharsets.UTF_8))));
        assertNull(DictionaryResources.hunspellEntryCount(
                new ByteArrayInputStream("word\n".getBytes(StandardCharsets.UTF_8))));
        assertNull(DictionaryResources.hunspellEntryCount(null));
    }
}
