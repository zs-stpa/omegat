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

import java.awt.event.InputEvent;

import org.junit.Test;

import org.omegat.gui.actionpanel.ActionPanelView.DragEffect;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class DragEffectTest {

    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int META = InputEvent.META_DOWN_MASK;

    @Test
    public void testEffectTable() {
        Object[][] rows = {
                { true, 0, DragEffect.MOVE }, { true, SHIFT, DragEffect.COPY }, { true, CTRL, DragEffect.MOVE },
                { true, SHIFT | CTRL, DragEffect.COPY }, { false, 0, DragEffect.REMOVE },
                { false, SHIFT, DragEffect.NONE }, { false, CTRL, DragEffect.REMOVE_SILENT },
                { false, META, DragEffect.REMOVE_SILENT },
                // Shift wins outside: a copy gesture never deletes.
                { false, SHIFT | META, DragEffect.NONE }, { false, SHIFT | CTRL, DragEffect.NONE },
                { false, InputEvent.ALT_DOWN_MASK, DragEffect.REMOVE },
                { false, InputEvent.BUTTON1_DOWN_MASK, DragEffect.REMOVE },
                { true, InputEvent.BUTTON1_DOWN_MASK | SHIFT, DragEffect.COPY } };
        for (Object[] row : rows) {
            assertEquals("inside=" + row[0] + " modifiers=" + row[1], row[2],
                    DragEffect.of((Boolean) row[0], (Integer) row[1]));
        }
    }
}
