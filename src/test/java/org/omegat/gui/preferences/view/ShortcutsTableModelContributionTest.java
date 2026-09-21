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

package org.omegat.gui.preferences.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;

import javax.swing.KeyStroke;

import org.junit.Test;

import org.omegat.gui.shortcuts.PropertiesShortcuts;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ShortcutsTableModelContributionTest {

    /**
     * A key a module contributed shows the module's label and scope, not the
     * raw key and "Editor"; a menu accelerator on the same keystroke is a
     * hard conflict, since a window binding fires before the menu bar.
     */
    @Test
    public void testContributedKeyShowsLabelAndScopeAndClashesWithMenu() throws IOException {
        PropertiesShortcuts menuSet = new PropertiesShortcuts();
        menuSet.loadFromClasspath("/org/omegat/gui/shortcuts/test.properties");
        PropertiesShortcuts editorSet = new PropertiesShortcuts();
        editorSet.contribute("/org/omegat/gui/shortcuts/contrib.properties", getClass().getClassLoader(), "Module",
                key -> "CONTRIB_ONE".equals(key) ? "Module function" : null);
        ShortcutsTableModel model = new ShortcutsTableModel(menuSet, editorSet, Map.of());
        int contributed = -1;
        int menu = -1;
        for (int row = 0; row < model.getRowCount(); row++) {
            if ("Module function".equals(model.getFunctionLabel(row))) {
                contributed = row;
                assertEquals("Module", model.getValueAt(row, ShortcutsTableModel.COLUMN_SCOPE));
            }
            if ("TEST_SAVE".equals(model.getFunctionLabel(row))) {
                menu = row;
            }
        }
        assertTrue(contributed >= 0 && menu >= 0);
        assertEquals(ShortcutsTableModel.ConflictKind.NONE, model.getConflictKind(contributed));
        model.setShortcut(contributed, KeyStroke.getKeyStroke("ctrl S"));
        assertEquals(ShortcutsTableModel.ConflictKind.CONFLICT, model.getConflictKind(contributed));
        assertEquals(ShortcutsTableModel.ConflictKind.CONFLICT, model.getConflictKind(menu));
    }
}
