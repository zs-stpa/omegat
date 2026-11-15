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

package org.omegat.util.gui;

import static org.junit.Assert.assertEquals;

import java.awt.Cursor;
import java.awt.event.MouseEvent;

import javax.swing.JTextPane;

import org.junit.Test;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class JTextPaneLinkifierTest {

    /** Off a link, an editable pane shows the text cursor, a read-only one the arrow. */
    @Test
    public void testCursorOffLinkFollowsEditability() {
        JTextPane pane = new JTextPane();
        pane.setText("plain text without links");
        JTextPaneLinkifier.linkify(pane);
        MouseEvent move = new MouseEvent(pane, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 2, 2, 0,
                false);

        pane.setEditable(true);
        for (java.awt.event.MouseMotionListener l : pane.getMouseMotionListeners()) {
            l.mouseMoved(move);
        }
        assertEquals(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR), pane.getCursor());

        pane.setEditable(false);
        for (java.awt.event.MouseMotionListener l : pane.getMouseMotionListeners()) {
            l.mouseMoved(move);
        }
        assertEquals(Cursor.getDefaultCursor(), pane.getCursor());
    }
}
