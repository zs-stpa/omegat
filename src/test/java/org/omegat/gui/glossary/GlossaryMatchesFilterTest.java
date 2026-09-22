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

package org.omegat.gui.glossary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Frame;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.core.TestCore;
import org.omegat.core.data.EntryKey;
import org.omegat.core.data.IProject;
import org.omegat.core.data.NotLoadedProject;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.data.TMXEntry;
import org.omegat.gui.glossary.GlossaryMatchesFilter.Scope;

/**
 * Scope decision and entry gating of the glossary matches editor filter.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class GlossaryMatchesFilterTest extends TestCore {

    @Test
    public void testScopeDecision() {
        assertTrue(GlossaryMatchesFilter.included(false, Scope.UNTRANSLATED_ONLY));
        assertFalse(GlossaryMatchesFilter.included(true, Scope.UNTRANSLATED_ONLY));
        assertTrue(GlossaryMatchesFilter.included(true, Scope.TRANSLATED_ONLY));
        assertFalse(GlossaryMatchesFilter.included(false, Scope.TRANSLATED_ONLY));
        assertTrue(GlossaryMatchesFilter.included(true, Scope.ALL_SEGMENTS));
        assertTrue(GlossaryMatchesFilter.included(false, Scope.ALL_SEGMENTS));
    }

    @Test
    public void testAllowedGatesOnCollectedEntryNumbers() {
        GlossaryMatchesFilter filter = new GlossaryMatchesFilter(Scope.ALL_SEGMENTS, null, Set.of(2));
        SourceTextEntry first = entry("one", 1);
        SourceTextEntry second = entry("two", 2);
        assertFalse(filter.allowed(first));
        assertTrue(filter.allowed(second));
        assertFalse("no active segment means nothing to show", filter.allowed(null));
    }

    private static SourceTextEntry entry(String source, int number) {
        EntryKey key = new EntryKey("file.txt", source, null, null, null, null);
        return new SourceTextEntry(key, number, null, null, Collections.emptyList());
    }

    /**
     * The origin filter relies on the glossary readers stamping every entry
     * with the exact path string that {@link GlossaryManager} keys its map
     * with (file.getPath()). Pin that invariant.
     */
    @Test
    public void testReaderOriginMatchesManagerKey() throws Exception {
        File glossary = folder.newFile("terms.tsv");
        Files.writeString(glossary.toPath(), "Anamnese\tanamnesis\n", StandardCharsets.UTF_8);
        List<GlossaryEntry> read = GlossaryReaderTSV.read(glossary, false);
        assertEquals(1, read.size());
        assertEquals("reader origin must equal the manager's map key (file.getPath())",
                glossary.getPath(), read.get(0).getOrigins(false)[0]);
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testCollectEntriesRestrictedToOneGlossary() {
        SourceTextEntry first = entry("one", 1);
        SourceTextEntry second = entry("two", 2);
        IProject project = new NotLoadedProject() {
            @Override
            public List<SourceTextEntry> getAllEntries() {
                return List.of(first, second);
            }

            @Override
            public TMXEntry getTranslationInfo(SourceTextEntry ste) {
                return EMPTY_TRANSLATION;
            }
        };
        IGlossaries pane = new IGlossaries() {
            @Override
            public List<GlossaryEntry> getDisplayedEntries() {
                return Collections.emptyList();
            }

            @Override
            public void showCreateGlossaryEntryDialog(Frame parent) {
            }

            @Override
            public void refresh() {
            }
        };
        GlossaryEntry onlyA = new GlossaryEntry("one", "eins", "", false, "A");
        GlossaryEntry mergedAb = new GlossaryEntry("two", new String[] { "zwei", "zwo" },
                new String[] { "", "" }, new boolean[] { false, false }, new String[] { "A", "B" });
        GlossaryManager manager = new GlossaryManager(pane) {
            @Override
            public List<GlossaryEntry> searchSourceLocalMatches(SourceTextEntry ste) {
                return ste == first ? List.of(onlyA) : List.of(mergedAb);
            }
        };
        assertEquals("only the merged match carries glossary B", Set.of(2),
                GlossaryMatchesFilter.collectEntries(project, manager, Scope.ALL_SEGMENTS, "B"));
        assertEquals(Set.of(1, 2),
                GlossaryMatchesFilter.collectEntries(project, manager, Scope.ALL_SEGMENTS, "A"));
        assertEquals("no restriction collects every match", Set.of(1, 2),
                GlossaryMatchesFilter.collectEntries(project, manager, Scope.ALL_SEGMENTS, null));
    }
}
