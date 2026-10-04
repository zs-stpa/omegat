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

import java.awt.Component;
import java.awt.GraphicsEnvironment;

import javax.swing.JPanel;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vlsolutions.swing.docking.DockKey;
import com.vlsolutions.swing.docking.DockViewTitleBar;
import com.vlsolutions.swing.docking.Dockable;
import com.vlsolutions.swing.docking.ui.DockingUISettings;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class CustomContainerFactoryTest {

    @BeforeClass
    public static void installDockingUI() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        DockingUISettings.setInstance(new CustomDockingUISettings());
        DockingUISettings.getInstance().installUI();
    }

    /**
     * A title bar taken out of the hierarchy and inserted again (the
     * framework does that when it splits a container) must keep following
     * the DockKey name.
     */
    @Test
    public void testTitleFollowsRenameAfterReinsertion() {
        DockKey key = new DockKey("editor", "First Steps");
        JPanel component = new JPanel();
        Dockable dockable = new Dockable() {
            @Override
            public DockKey getDockKey() {
                return key;
            }

            @Override
            public Component getComponent() {
                return component;
            }
        };
        DockViewTitleBar bar = new CustomContainerFactory().createTitleBar();
        bar.setDockable(dockable);
        assertEquals("First Steps", bar.getTitleLabel().getText());

        bar.removeNotify();
        bar.addNotify();
        key.setName("Editor - a.txt");
        assertEquals("Editor - a.txt", bar.getTitleLabel().getText());

        // Repeated re-parenting must not stack listeners: a second rename
        // still yields the plain name once.
        bar.removeNotify();
        bar.addNotify();
        key.setName("Editor - b.txt");
        assertEquals("Editor - b.txt", bar.getTitleLabel().getText());
    }
}
