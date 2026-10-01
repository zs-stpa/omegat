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

package org.omegat.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import org.omegat.core.data.TMXEntry;
import org.omegat.core.data.TMXEntryFactoryForTest;
import org.omegat.gui.editor.ModificationInfoManager;

/**
 * Cached derivations of preferences follow the preference itself, without a
 * caller having to invalidate them.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class PreferenceListenersTest {

    @Before
    public void setUp() throws IOException {
        TestPreferencesInitializer.init();
        // Caches may hold patterns from earlier tests in this JVM.
        PatternConsts.updatePlaceholderPattern();
        PatternConsts.updateCustomTagPattern();
        PatternConsts.updateRemovePattern();
    }

    @Test
    public void testPlaceholderPatternFollowsJavaPatternPreference() {
        // The coded custom pattern default (digits) would match "{0}" too.
        Preferences.setPreference(Preferences.CHECK_CUSTOM_PATTERN, "");
        Preferences.setPreference(Preferences.CHECK_JAVA_PATTERN_TAGS, false);
        assertFalse(PatternConsts.getPlaceholderPattern().matcher("{0}").find());
        Preferences.setPreference(Preferences.CHECK_JAVA_PATTERN_TAGS, true);
        assertTrue(PatternConsts.getPlaceholderPattern().matcher("{0}").find());
        Preferences.setPreference(Preferences.CHECK_JAVA_PATTERN_TAGS, false);
        assertFalse(PatternConsts.getPlaceholderPattern().matcher("{0}").find());
    }

    @Test
    public void testPrintfAndCustomPatternsFollowPreferences() {
        Preferences.setPreference(Preferences.CHECK_ALL_PRINTF_TAGS, false);
        Preferences.setPreference(Preferences.CHECK_SIMPLE_PRINTF_TAGS, false);
        Preferences.setPreference(Preferences.CHECK_CUSTOM_PATTERN, "");
        assertFalse(PatternConsts.getPlaceholderPattern().matcher("%s").find());
        Preferences.setPreference(Preferences.CHECK_SIMPLE_PRINTF_TAGS, true);
        assertTrue(PatternConsts.getPlaceholderPattern().matcher("%s").find());
        Preferences.setPreference(Preferences.CHECK_SIMPLE_PRINTF_TAGS, false);

        Preferences.setPreference(Preferences.CHECK_CUSTOM_PATTERN, "\\[\\[\\w+\\]\\]");
        assertTrue(PatternConsts.getPlaceholderPattern().matcher("[[name]]").find());
        assertNotNull(PatternConsts.getCustomTagPattern());
        assertTrue(PatternConsts.getCustomTagPattern().matcher("[[name]]").matches());
        Preferences.setPreference(Preferences.CHECK_CUSTOM_PATTERN, "");
        assertFalse(PatternConsts.getPlaceholderPattern().matcher("[[name]]").find());
        assertNull(PatternConsts.getCustomTagPattern());
    }

    @Test
    public void testRemovePatternFollowsPreference() {
        Preferences.setPreference(Preferences.CHECK_REMOVE_PATTERN, "");
        assertNull(PatternConsts.getRemovePattern());
        Preferences.setPreference(Preferences.CHECK_REMOVE_PATTERN, "<x/>");
        assertNotNull(PatternConsts.getRemovePattern());
        assertTrue(PatternConsts.getRemovePattern().matcher("<x/>").matches());
        Preferences.setPreference(Preferences.CHECK_REMOVE_PATTERN, "");
        assertNull(PatternConsts.getRemovePattern());
    }

    @Test
    public void testModificationInfoTemplateFollowsPreference() {
        // No change date: the template without date applies.
        TMXEntry entry = TMXEntryFactoryForTest.createTMXEntry("s", "t", "someone", 1234L, true);
        Preferences.setPreference(Preferences.VIEW_OPTION_MOD_INFO_TEMPLATE_WO_DATE, "by ${creationId}");
        assertEquals("by someone", ModificationInfoManager.apply(entry));
        Preferences.setPreference(Preferences.VIEW_OPTION_MOD_INFO_TEMPLATE_WO_DATE, "creator=${creationId}");
        assertEquals("creator=someone", ModificationInfoManager.apply(entry));
    }
}
