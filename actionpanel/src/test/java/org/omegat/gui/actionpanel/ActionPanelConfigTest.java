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

package org.omegat.gui.actionpanel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.util.RuntimePreferences;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionPanelConfigTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String previousConfigDir;
    private final ActionPanelConfig config = ActionPanelConfig.getInstance();

    @Before
    public void setUp() throws IOException {
        previousConfigDir = RuntimePreferences.getConfigDir();
        RuntimePreferences.setConfigDir(folder.newFolder("config").getAbsolutePath());
        config.setRows(List.of());
    }

    @After
    public void tearDown() {
        config.setRows(List.of());
        RuntimePreferences.setConfigDir(previousConfigDir);
    }

    @Test
    public void testChangesPersistAndNotify() throws IOException {
        AtomicInteger notified = new AtomicInteger();
        Runnable listener = notified::incrementAndGet;
        config.addChangeListener(listener);
        try {
            ActionRow a = new ActionRow("a", null, null);
            ActionRow b = new ActionRow("b", null, null);
            config.setRows(List.of(a, b));
            assertEquals(1, notified.get());

            assertTrue(config.updateRow(a.id(), r -> r.withName("A")));
            assertEquals("A", config.findRow(a.id()).name());
            assertFalse(config.updateRow("no-such-id", r -> r));
            assertNull(config.findRow("no-such-id"));

            assertTrue(config.duplicateRow(a.id()));
            List<ActionRow> rows = config.getRows();
            assertEquals(List.of("A", MessageFormat.format(ActionPanelModule.getString("ROW_COPY_NAME"), "A"), "b"),
                    rows.stream().map(ActionRow::name).toList());
            assertNotEquals(a.id(), rows.get(1).id());
            assertFalse(config.duplicateRow("no-such-id"));

            assertTrue(config.removeRow(rows.get(1).id()));
            assertFalse(config.removeRow(rows.get(1).id()));
            assertEquals(List.of("A", "b"), config.getRows().stream().map(ActionRow::name).toList());
            // Four changes persisted and notified; the failed removal stays silent.
            assertEquals(4, notified.get());

            File file = ActionPanelConfig.getConfigFile();
            assertTrue(file.isFile());
            assertEquals(config.getRows(), ActionPanelXML.read(file));
        } finally {
            config.removeChangeListener(listener);
        }
    }

    @Test
    public void testLoadMintsIdsOnceForOldFiles() throws IOException {
        File file = ActionPanelConfig.getConfigFile();
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        java.nio.file.Files.writeString(file.toPath(),
                "<actionpanel version=\"1\"><row name=\"old\"/><row name=\"older\"/></actionpanel>");
        config.load();
        List<ActionRow> first = config.getRows();
        assertEquals(2, first.size());
        assertEquals(2, first.stream().map(ActionRow::id).distinct().count());
        config.load();
        assertEquals(first, config.getRows());
    }
}
