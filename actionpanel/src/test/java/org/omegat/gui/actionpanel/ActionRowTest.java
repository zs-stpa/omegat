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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import java.util.List;

import org.junit.Test;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionRowTest {

    @Test
    public void testNewRowsGetDistinctIds() {
        ActionRow a = new ActionRow("a", null, null);
        ActionRow b = new ActionRow("a", null, null);
        assertNotEquals(a.id(), b.id());
        assertNotEquals(a, b);
    }

    @Test
    public void testEditsKeepTheId() {
        ActionRow row = new ActionRow("a", null, null, "#ff0000", null, null);
        assertEquals(row.id(), row.withName("b").id());
        assertEquals(row.id(), row.withIconRef("x.png").id());
        assertEquals(row.id(), row.withAction(new ActionSpec.UrlActionSpec("https://omegat.org")).id());
        assertEquals(row.id(), row.withColor(1, "#00ff00").id());
    }

    @Test
    public void testFreshIdChangesOnlyTheId() {
        ActionRow row = new ActionRow("a", "i.png", new ActionSpec.ScriptActionSpec("s.groovy"), "#ff0000",
                "#00ff00", "#0000ff");
        ActionRow copy = row.withFreshId();
        assertNotEquals(row.id(), copy.id());
        assertEquals(row, new ActionRow(row.id(), copy.name(), copy.iconRef(), copy.action(), copy.textColor(),
                copy.backgroundColor(), copy.borderColor()));
    }

    @Test
    public void testUniqueIdsRenameLaterDuplicatesOnly() {
        ActionRow first = new ActionRow("a", null, null);
        ActionRow twin = new ActionRow(first.id(), "b", null, null, null, null, null);
        ActionRow other = new ActionRow("c", null, null);
        List<ActionRow> unique = ActionRow.withUniqueIds(List.of(first, twin, other));
        assertEquals(3, unique.size());
        assertSame(first, unique.get(0));
        assertSame(other, unique.get(2));
        assertNotEquals(first.id(), unique.get(1).id());
        assertEquals("b", unique.get(1).name());
        assertEquals(3, unique.stream().map(ActionRow::id).distinct().count());
    }

    @Test
    public void testUniqueIdsLeaveDistinctRowsUntouched() {
        List<ActionRow> rows = List.of(new ActionRow("a", null, null), new ActionRow("b", null, null));
        assertEquals(rows, ActionRow.withUniqueIds(rows));
    }
}
