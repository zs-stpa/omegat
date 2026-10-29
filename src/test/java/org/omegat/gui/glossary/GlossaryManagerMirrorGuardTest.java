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
import static org.junit.Assert.assertTrue;

import java.awt.Frame;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.core.TestCore;

/**
 * The team rebase hands GlossaryManager.fileChanged repository-side paths
 * (under the .repositories mirror) - during project load even BEFORE the
 * directory monitor exists. Such files must never enter the loaded
 * glossaries, or the mirror shows up as an extra glossary duplicating
 * every entry.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class GlossaryManagerMirrorGuardTest extends TestCore {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final IGlossaries PANE = new IGlossaries() {
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

    @Test
    public void testMirrorReloadBeforeMonitorStartIsIgnored() throws Exception {
        File mirrorGlossary = new File(folder.getRoot(),
                ".repositories/git_team.git/glossary/glossary.txt");
        assertTrue(mirrorGlossary.getParentFile().mkdirs());
        Files.writeString(mirrorGlossary.toPath(), "Anamnese\tanamnesis\n", StandardCharsets.UTF_8);

        GlossaryManager manager = new GlossaryManager(PANE);
        // As GlossaryRebaseOperation.reload does while the project is still
        // loading: no monitor has been started yet.
        manager.fileChanged(mirrorGlossary);

        assertEquals("mirror files must never load as project glossaries",
                Collections.emptyList(), manager.getLocalEntries());
    }

    @Test
    public void testLegitimatePathBeforeMonitorStartStillLoads() throws Exception {
        File glossary = new File(folder.getRoot(), "glossary/glossary.txt");
        assertTrue(glossary.getParentFile().mkdirs());
        Files.writeString(glossary.toPath(), "Anamnese\tanamnesis\n", StandardCharsets.UTF_8);

        GlossaryManager manager = new GlossaryManager(PANE);
        manager.fileChanged(glossary);

        assertEquals("the guard must not block ordinary files", 1, manager.getLocalEntries().size());
    }
}
