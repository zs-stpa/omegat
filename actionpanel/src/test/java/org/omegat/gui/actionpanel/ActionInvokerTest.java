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
import static org.junit.Assert.assertTrue;

import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import org.junit.Test;

import org.omegat.externalfinder.item.ExternalFinderItem;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionInvokerTest {

    @Test
    public void testPluginItemIsNotDispatchedThroughMainMenu() {
        ActionListener mainMenu = e -> { };
        JMenuItem pluginItem = new JMenuItem("Open in Xcode");
        pluginItem.setActionCommand("externalFinder:Open in Xcode");
        pluginItem.addActionListener(e -> { });
        assertFalse(ActionInvoker.dispatchesThroughMainMenu(pluginItem, mainMenu));

        JMenuItem coreItem = new JMenuItem("Save");
        coreItem.setActionCommand("projectSaveMenuItem");
        coreItem.addActionListener(mainMenu);
        assertTrue(ActionInvoker.dispatchesThroughMainMenu(coreItem, mainMenu));
    }

    @Test
    public void testStoredEncodingNamesResolve() {
        assertEquals(ExternalFinderItem.ENCODING.ESCAPE, ActionInvoker.encodingOf("escape"));
        assertEquals(ExternalFinderItem.ENCODING.NONE, ActionInvoker.encodingOf("NONE"));
        assertEquals(ExternalFinderItem.ENCODING.DEFAULT, ActionInvoker.encodingOf("default"));
        assertEquals(ExternalFinderItem.ENCODING.DEFAULT, ActionInvoker.encodingOf("bogus"));
    }
}
