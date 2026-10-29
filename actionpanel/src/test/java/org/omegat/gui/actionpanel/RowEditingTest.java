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
import static org.junit.Assert.assertNull;

import java.awt.Color;
import java.text.MessageFormat;

import org.junit.Test;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class RowEditingTest {

    @Test
    public void testCopyKeepsEverythingButIdAndName() {
        ActionRow row = new ActionRow("Search TODO", "icons/s.png", new ActionSpec.ScriptActionSpec("s.groovy"),
                "#ff0000", null, "#0000ff");
        ActionRow copy = RowEditing.copyOf(row);
        assertNotEquals(row.id(), copy.id());
        assertEquals(MessageFormat.format(ActionPanelModule.getString("ROW_COPY_NAME"), "Search TODO"),
                copy.name());
        assertEquals(row.iconRef(), copy.iconRef());
        assertEquals(row.action(), copy.action());
        assertEquals(row.textColor(), copy.textColor());
        assertEquals(row.backgroundColor(), copy.backgroundColor());
        assertEquals(row.borderColor(), copy.borderColor());
    }

    @Test
    public void testHexRoundTrip() {
        assertEquals("#0a80ff", RowEditing.toHex(new Color(0x0a, 0x80, 0xff)));
        assertEquals(new Color(0x0a, 0x80, 0xff), RowEditing.decode("#0a80ff"));
        assertNull(RowEditing.decode(null));
        assertNull(RowEditing.decode(""));
        assertNull(RowEditing.decode("teal"));
    }
}
