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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.TestPreferencesInitializer;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ShortcutLookupTest {

    @Before
    public void setUp() throws Exception {
        TestPreferencesInitializer.init();
    }

    @Test
    public void testUndefinedKeyYieldsNull() {
        // Menu items without any entry in the shortcut properties exist
        // whenever another branch or plugin contributes menu items;
        // PropertiesShortcuts.getKeyStroke would throw for them.
        assertNull(ShortcutLookup.find(PropertiesShortcuts.getMainMenuShortcuts(),
                "menuItemWithoutAnyShortcutDefinition"));
    }

    @Test
    public void testDefinedKeyYieldsKeyStroke() {
        PropertiesShortcuts shortcuts = PropertiesShortcuts.getMainMenuShortcuts();
        // The singleton loads once per JVM; guard against a run where an
        // earlier test baked in a config with this key explicitly unbound.
        assertNotNull(shortcuts.getShortcutValue("projectOpenMenuItem"));
        assertNotNull(ShortcutLookup.find(shortcuts, "projectOpenMenuItem"));
    }
}
