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

package org.omegat.gui.preferences.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import org.omegat.util.Preferences;
import org.omegat.util.TestPreferencesInitializer;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class TagProcessingOptionsControllerTest {

    @Before
    public void setUp() throws Exception {
        TestPreferencesInitializer.init();
    }

    /**
     * Opening the page must not flag a reload by itself. With the custom
     * pattern never saved, the field shows the coded default while the
     * stored value is empty; the preferences dialog builds every page on
     * opening, so a false flag here made every OK ask for a project reload.
     */
    @Test
    public void testUnsavedCustomPatternIsNoChange() {
        assertFalse(Preferences.existsPreference(Preferences.CHECK_CUSTOM_PATTERN));
        TagProcessingOptionsController controller = new TagProcessingOptionsController();
        controller.getGui();
        assertFalse(controller.isReloadRequired());
        // Reading must not write the default back.
        assertFalse(Preferences.existsPreference(Preferences.CHECK_CUSTOM_PATTERN));
    }

    /** A saved empty pattern means "no custom tags" and must stay so. */
    @Test
    public void testSavedEmptyCustomPatternIsNoChange() {
        Preferences.setPreference(Preferences.CHECK_CUSTOM_PATTERN, "");
        TagProcessingOptionsController controller = new TagProcessingOptionsController();
        controller.getGui();
        assertFalse(controller.isReloadRequired());
        assertTrue(Preferences.existsPreference(Preferences.CHECK_CUSTOM_PATTERN));
        assertEquals("", Preferences.getPreference(Preferences.CHECK_CUSTOM_PATTERN));
    }

    /** Positive control: an edited pattern does flag the reload. */
    @Test
    public void testEditedCustomPatternRequiresReload() {
        TagProcessingOptionsController controller = new TagProcessingOptionsController();
        TagProcessingOptionsPanel panel = (TagProcessingOptionsPanel) controller.getGui();
        panel.customPatternRegExpTF.setText("[0-9]+");
        assertTrue(controller.isReloadRequired());
    }
}
