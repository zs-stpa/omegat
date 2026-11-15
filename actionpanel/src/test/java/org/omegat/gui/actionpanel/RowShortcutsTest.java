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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.KeyStroke;

import org.junit.Test;

/**
 * The positional shortcuts: ten valid, distinct keystrokes that collide
 * with no default of the application on either platform.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class RowShortcutsTest {

    private static final String[] CORE_FILES = { "/org/omegat/gui/main/MainMenuShortcuts.properties",
            "/org/omegat/gui/main/MainMenuShortcuts.mac.properties", "/org/omegat/gui/main/EditorShortcuts.properties",
            "/org/omegat/gui/main/EditorShortcuts.mac.properties" };

    /** The file as written, bypassing the platform switch of PropertiesShortcuts. */
    private static Map<String, KeyStroke> read(String classpathFile) throws IOException {
        Properties props = new Properties();
        try (InputStream in = RowShortcutsTest.class.getResourceAsStream(classpathFile)) {
            assertNotNull(classpathFile, in);
            props.load(in);
        }
        Map<String, KeyStroke> strokes = new HashMap<>();
        props.forEach((k, v) -> strokes.put(k.toString(),
                v.toString().isEmpty() ? null : KeyStroke.getKeyStroke(v.toString())));
        return strokes;
    }

    @Test
    public void testTenDistinctKeystrokes() throws IOException {
        Map<String, KeyStroke> rows = read(ActionPanelModule.SHORTCUTS_FILE);
        assertEquals(ActionPanelModule.ROW_SHORTCUT_COUNT, rows.size());
        Set<KeyStroke> strokes = new HashSet<>();
        for (int position = 1; position <= ActionPanelModule.ROW_SHORTCUT_COUNT; position++) {
            KeyStroke stroke = rows.get(ActionPanelModule.rowShortcutKey(position));
            assertNotNull("row " + position, stroke);
            assertTrue("duplicate " + stroke, strokes.add(stroke));
        }
    }

    @Test
    public void testNoCollisionWithApplicationDefaults() throws IOException {
        Map<String, KeyStroke> rows = read(ActionPanelModule.SHORTCUTS_FILE);
        Set<KeyStroke> taken = new HashSet<>();
        for (String file : CORE_FILES) {
            Map<String, KeyStroke> core = read(file);
            assertTrue(file, core.size() > 10);
            core.values().forEach(stroke -> {
                if (stroke != null) {
                    taken.add(stroke);
                }
            });
        }
        rows.forEach((key, stroke) -> assertTrue(key + " collides: " + stroke, !taken.contains(stroke)));
    }

    @Test
    public void testLabels() {
        assertEquals("actionPanelRow3", ActionPanelModule.rowShortcutKey(3));
        assertNotNull(ActionPanelModule.shortcutLabel("actionPanelRow10"));
        assertTrue(ActionPanelModule.shortcutLabel("actionPanelRow1").contains("1"));
        assertNull(ActionPanelModule.shortcutLabel("editorNextSegment"));
    }
}
