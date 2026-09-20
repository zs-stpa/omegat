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

import java.util.List;
import java.util.Objects;

import javax.swing.JMenuItem;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JCheckBoxFixture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vlsolutions.swing.docking.DockingDesktop;

import org.omegat.core.Core;
import org.omegat.gui.actionpanel.ActionSpec.MenuActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.PreferenceActionSpec;
import org.omegat.gui.main.TestCoreGUI;
import org.omegat.util.Preferences;

/**
 * The panel's controls are addressable by the stable row id, independent of
 * the row name, the position and the locale (see {@link ComponentNames}).
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionPanelTest extends TestCoreGUI {

    /** A checkbox item the test menu handler implements. */
    private static final String TOGGLE_COMMAND = "optionsGlossaryFuzzyMatchingCheckBoxMenuItem";

    @BeforeClass
    public static void keepPanelDocked() {
        // The module minimizes the pane on its very first appearance; the
        // tests want it in view.
        Preferences.setPreference(ActionPanelModule.INITIALIZED_PREFERENCE, true);
    }

    @AfterClass
    public static void clearRows() {
        // Acceptance classes share the JVM: leave no rows behind for the next one.
        GuiActionRunner.execute(() -> ActionPanelConfig.getInstance().setRows(List.of()));
    }

    @Test
    public void testRowsAreAddressableByStableNames() {
        assertNotNull(window);
        ActionRow toggle = new ActionRow("Fuzzy glossary", null, new MenuActionSpec(TOGGLE_COMMAND));
        ActionRow button = new ActionRow("About", null, new MenuActionSpec("helpAboutMenuItem"));
        ActionRow slider = new ActionRow("Font size", null,
                new PreferenceActionSpec(Preferences.TF_SRC_FONT_SIZE, PreferenceCatalog.KIND_SLIDER, 8, 32,
                        List.of("12")));
        setRows(List.of(toggle, button, slider));
        // The layout may give the pane little room; maximized, every control
        // is on screen for the robot.
        GuiActionRunner.execute(() -> {
            DockingDesktop desktop = Objects.requireNonNull(Core.getMainWindow()).getDesktop();
            desktop.maximize(desktop.getContext().getDockableByKey(ActionPanelModule.DOCK_KEY));
        });
        robot().waitForIdle();

        window.panel(ComponentNames.PANEL).requireVisible();
        window.button(ComponentNames.control(button.id())).requireEnabled().requireText("About");
        window.panel(ComponentNames.row(slider.id())).requireVisible();
        window.label(ComponentNames.label(slider.id())).requireText("Font size");
        window.slider(ComponentNames.control(slider.id())).requireVisible();
        window.label(ComponentNames.readout(slider.id())).requireVisible();

        // The checkbox row mirrors and drives its menu item (menu items carry
        // their handler field as action command, not as component name).
        JMenuItem item = GuiActionRunner.execute(() -> {
            MenuActionCatalog catalog = new MenuActionCatalog();
            catalog.rebuild();
            MenuActionCatalog.MenuEntry entry = catalog.lookup(TOGGLE_COMMAND);
            assertNotNull(entry);
            return entry.getItem();
        });
        boolean before = GuiActionRunner.execute(item::isSelected);
        JCheckBoxFixture box = window.checkBox(ComponentNames.control(toggle.id()));
        if (before) {
            box.requireSelected();
        } else {
            box.requireNotSelected();
        }
        box.click();
        robot().waitForIdle();
        assertEquals(!before, (boolean) GuiActionRunner.execute(item::isSelected));

        // Renaming and reordering keep the id, so the same names still work.
        setRows(List.of(slider, button, toggle.withName("Renamed")));
        window.checkBox(ComponentNames.control(toggle.id())).requireText("Renamed");
        window.button(ComponentNames.control(button.id())).requireText("About");
    }

    private void setRows(List<ActionRow> rows) {
        GuiActionRunner.execute(() -> ActionPanelConfig.getInstance().setRows(rows));
        // The view rebuilds on a later EDT round.
        robot().waitForIdle();
    }
}
