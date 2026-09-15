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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionPanelTableModelTest {

    private static ActionPanelTableModel model(String... names) {
        return new ActionPanelTableModel(
                List.of(names).stream().map(n -> new ActionRow(n, null, null)).toList(),
                new MenuActionCatalog());
    }

    private static List<String> names(ActionPanelTableModel model) {
        return model.getRows().stream().map(ActionRow::name).toList();
    }

    @Test
    public void testAddDefaults() {
        ActionPanelTableModel m = model("a");
        int index = m.addRow();
        assertEquals(1, index);
        ActionRow added = m.getRow(index);
        assertEquals(ActionPanelModule.getString("ROW_DEFAULT_NAME"), added.name());
        assertEquals(null, added.iconRef());
        assertEquals(null, added.action());
        assertEquals(2, m.getValueAt(1, ActionPanelTableModel.COLUMN_NUMBER));
    }

    @Test
    public void testDuplicateAppendsCopies() {
        ActionPanelTableModel m = model("a", "b", "c");
        int first = m.duplicateRows(new int[] { 0, 2 });
        assertEquals(3, first);
        assertEquals(List.of("a", "b", "c", "a", "c"), names(m));
    }

    @Test
    public void testRemoveScatteredSelection() {
        ActionPanelTableModel m = model("a", "b", "c", "d");
        m.removeRows(new int[] { 0, 2 });
        assertEquals(List.of("b", "d"), names(m));
    }

    @Test
    public void testMoveUpAndDownKeepsBlock() {
        ActionPanelTableModel m = model("a", "b", "c", "d");
        int[] moved = m.moveRows(new int[] { 1, 2 }, true);
        assertArrayEquals(new int[] { 0, 1 }, moved);
        assertEquals(List.of("b", "c", "a", "d"), names(m));

        moved = m.moveRows(new int[] { 0, 1 }, false);
        assertArrayEquals(new int[] { 1, 2 }, moved);
        assertEquals(List.of("a", "b", "c", "d"), names(m));
    }

    @Test
    public void testMoveAtEdgesIsNoOp() {
        ActionPanelTableModel m = model("a", "b", "c");
        assertArrayEquals(new int[] { 0 }, m.moveRows(new int[] { 0 }, true));
        assertEquals(List.of("a", "b", "c"), names(m));
        assertArrayEquals(new int[] { 2 }, m.moveRows(new int[] { 2 }, false));
        assertEquals(List.of("a", "b", "c"), names(m));
    }

    @Test
    public void testMoveScatteredSelectionTouchingEdgeFreezes() {
        // A scattered selection touching the edge stays put as a whole.
        ActionPanelTableModel m = model("a", "b", "c");
        assertArrayEquals(new int[] { 0, 2 }, m.moveRows(new int[] { 0, 2 }, true));
        assertEquals(List.of("a", "b", "c"), names(m));
    }

    @Test
    public void testMoveScatteredSelection() {
        ActionPanelTableModel m = model("a", "b", "c", "d");
        int[] moved = m.moveRows(new int[] { 1, 3 }, true);
        assertArrayEquals(new int[] { 0, 2 }, moved);
        assertEquals(List.of("b", "a", "d", "c"), names(m));
    }

    @Test
    public void testDragMoveToTarget() {
        ActionPanelTableModel m = model("a", "b", "c", "d");
        // Drag a+b behind d (insert position = end of list).
        int[] moved = m.moveRowsTo(new int[] { 0, 1 }, 4);
        assertArrayEquals(new int[] { 2, 3 }, moved);
        assertEquals(List.of("c", "d", "a", "b"), names(m));
        // Drag d (now index 1) to the very front.
        moved = m.moveRowsTo(new int[] { 1 }, 0);
        assertArrayEquals(new int[] { 0 }, moved);
        assertEquals(List.of("d", "c", "a", "b"), names(m));
    }

    @Test
    public void testAppendRows() {
        ActionPanelTableModel m = model("a");
        m.appendRows(List.of(new ActionRow("x", null, null), new ActionRow("y", null, null)));
        assertEquals(List.of("a", "x", "y"), names(m));
    }

    @Test
    public void testDescribeActionCoversEverySpecType() {
        // Renderer path: a forgotten branch here once broke the whole page.
        List<ActionSpec> specs = List.of(new ActionSpec.MenuActionSpec("cmd"),
                new ActionSpec.EditorKeyActionSpec("key"), new ActionSpec.ScriptActionSpec("f.groovy"),
                new ActionSpec.SearchActionSpec("q", false, java.util.Map.of()),
                new ActionSpec.SnippetActionSpec("t"), new ActionSpec.AutotextRefActionSpec("src"),
                new ActionSpec.ColorSchemeActionSpec("c"), new ActionSpec.ShortcutSetActionSpec("s"),
                new ActionSpec.PreferenceActionSpec("source_font_size", "slider", 8, 32, List.of()),
                new ActionSpec.ProjectFlagActionSpec("SentenceSegmentingEnabled"),
                new ActionSpec.UrlActionSpec("https://omegat.org/"),
                new ActionSpec.UnknownActionSpec("hologram", java.util.Map.of()));
        ActionPanelTableModel m = model("a");
        for (ActionSpec spec : specs) {
            m.setRow(0, m.getRow(0).withAction(spec));
            assertEquals(String.class,
                    m.getValueAt(0, ActionPanelTableModel.COLUMN_ACTION).getClass());
        }
    }

    @Test
    public void testNameEditThroughTableApi() {
        ActionPanelTableModel m = model("a");
        m.setValueAt("renamed", 0, ActionPanelTableModel.COLUMN_NAME);
        assertEquals("renamed", m.getRow(0).name());
    }
}
