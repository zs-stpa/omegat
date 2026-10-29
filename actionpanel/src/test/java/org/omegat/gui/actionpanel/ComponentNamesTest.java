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
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ComponentNamesTest {

    @Test
    public void testEveryNameCarriesThePrefix() {
        ActionSpec spec = new ActionSpec.MenuActionSpec("projectNewMenuItem");
        List<String> names = List.of(ComponentNames.PANEL, ComponentNames.EMPTY_HINT, ComponentNames.PREFS_TABLE,
                ComponentNames.PREFS_SEARCH, ComponentNames.row("x"), ComponentNames.control("x"),
                ComponentNames.label("x"), ComponentNames.icon("x"), ComponentNames.readout("x"),
                ComponentNames.rowMenu("x"), ComponentNames.rowMenuEntry("x", "K"),
                ComponentNames.paneMenu("MENU_LAYOUT"), ComponentNames.prefs("BTN_UP"),
                ComponentNames.assignBranch("ASSIGN_MENU_EDITOR"), ComponentNames.ASSIGN_MENU_BAR,
                ComponentNames.assignMenuBranch(List.of("viewMenu", "viewModificationInfoMenu")),
                ComponentNames.assignPreferenceGroup("PREFERENCES_GENERAL", "General"),
                ComponentNames.assignPreferenceGroup(null, "Fallback"), ComponentNames.assignLeaf(spec),
                ComponentNames.assignAutotext("mfg"));
        for (String name : names) {
            assertTrue(name, name.startsWith(ComponentNames.PREFIX));
        }
        assertEquals(names.size(), names.stream().distinct().count());
    }

    @Test
    public void testRowNamesDeriveFromTheId() {
        assertEquals("actionpanel.row.42", ComponentNames.row("42"));
        assertEquals("actionpanel.row.42.control", ComponentNames.control("42"));
        assertEquals("actionpanel.row.42.label", ComponentNames.label("42"));
        assertEquals("actionpanel.row.42.icon", ComponentNames.icon("42"));
        assertEquals("actionpanel.row.42.readout", ComponentNames.readout("42"));
        assertEquals("actionpanel.row.42.menu", ComponentNames.rowMenu("42"));
        assertEquals("actionpanel.row.42.menu.row_menu_apply_filter",
                ComponentNames.rowMenuEntry("42", "ROW_MENU_APPLY_FILTER"));
    }

    @Test
    public void testAssignmentNames() {
        assertEquals("actionpanel.assign.editor", ComponentNames.assignBranch("ASSIGN_MENU_EDITOR"));
        assertEquals("actionpanel.assign.snippet_freetext", ComponentNames.assignBranch("ASSIGN_SNIPPET_FREETEXT"));
        assertEquals("actionpanel.assign.menubar.viewMenu.viewModificationInfoMenu",
                ComponentNames.assignMenuBranch(List.of("viewMenu", "viewModificationInfoMenu")));
        assertEquals("actionpanel.assign.menu.projectNewMenuItem",
                ComponentNames.assignLeaf(new ActionSpec.MenuActionSpec("projectNewMenuItem")));
        assertEquals("actionpanel.assign.preference.source_font_size", ComponentNames.assignLeaf(
                new ActionSpec.PreferenceActionSpec("source_font_size", "slider", 8, 32, List.of("12"))));
        assertEquals("actionpanel.assign.search.TODO", ComponentNames
                .assignLeaf(new ActionSpec.SearchActionSpec("TODO", false, Map.of("k", "v"))));
        assertEquals("actionpanel.assign.preference.group.preferences_general",
                ComponentNames.assignPreferenceGroup("PREFERENCES_GENERAL", "General"));
        assertEquals("actionpanel.assign.preference.group.general",
                ComponentNames.assignPreferenceGroup(null, "General"));
    }

    /** Every ready action offered on the settings branch has a name of its own. */
    @Test
    public void testPreferenceLeafNamesAreUnique() {
        List<String> names = new java.util.ArrayList<>();
        collectLeafNames(PreferenceCatalog.groups(), names);
        assertTrue(names.size() > 30);
        assertEquals(names.size(), names.stream().distinct().count());
    }

    private static void collectLeafNames(List<PreferenceCatalog.Group> groups, List<String> names) {
        for (PreferenceCatalog.Group group : groups) {
            group.entries().forEach(entry -> names.add(ComponentNames.assignLeaf(entry.spec())));
            collectLeafNames(group.children(), names);
        }
    }

    @Test
    public void testBundleKeyNamesAreLowerCase() {
        assertEquals("actionpanel.menu.display_mode_icon", ComponentNames.paneMenu("DISPLAY_MODE_ICON"));
        assertEquals("actionpanel.prefs.btn_import", ComponentNames.prefs("BTN_IMPORT"));
    }
}
