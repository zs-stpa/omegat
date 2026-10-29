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

package org.omegat.gui.editor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.omegat.core.data.SourceTextEntry.DUPLICATE;
import org.omegat.util.TestPreferencesInitializer;
import org.omegat.util.gui.Styles.EditorColor;
import org.omegat.util.gui.Styles.TextStyle;

/**
 * The former "view source all/active bold" options are now the BOLD flag on
 * COLOR_SOURCE and COLOR_ACTIVE_SOURCE. The editor attribute path must
 * reproduce the old truth table: COLOR_SOURCE's flag styles every source
 * text, COLOR_ACTIVE_SOURCE's flag additionally styles the active segment's
 * source, and target text stays out of both.
 *
 * @author Stephan Pakebusch
 */
public class EditorSettingsTextStyleTest {

    @Before
    public void setUp() throws Exception {
        TestPreferencesInitializer.init();
    }

    @After
    public void tearDown() {
        EditorColor.COLOR_SOURCE.setTextStyle(EditorColor.COLOR_SOURCE.getDefaultTextStyle());
        EditorColor.COLOR_ACTIVE_SOURCE
                .setTextStyle(EditorColor.COLOR_ACTIVE_SOURCE.getDefaultTextStyle());
    }

    private static boolean isBold(EditorSettings settings, boolean isSource, boolean active) {
        AttributeSet attrs = settings.getAttributeSet(isSource, false, false, DUPLICATE.NONE, active,
                false, false, false);
        return StyleConstants.isBold(attrs);
    }

    @Test
    public void testSourceBoldTruthTable() {
        EditorSettings settings = new EditorSettings(null);

        // both flags off
        EditorColor.COLOR_SOURCE.setTextStyle(EnumSet.noneOf(TextStyle.class));
        EditorColor.COLOR_ACTIVE_SOURCE.setTextStyle(EnumSet.noneOf(TextStyle.class));
        assertFalse("inactive source plain", isBold(settings, true, false));
        assertFalse("active source plain", isBold(settings, true, true));

        // "active bold" only
        EditorColor.COLOR_ACTIVE_SOURCE.setTextStyle(EnumSet.of(TextStyle.BOLD));
        assertFalse("inactive source stays plain", isBold(settings, true, false));
        assertTrue("active source bold", isBold(settings, true, true));

        // "all bold" (active flag irrelevant, overlay is additive)
        EditorColor.COLOR_SOURCE.setTextStyle(EnumSet.of(TextStyle.BOLD));
        EditorColor.COLOR_ACTIVE_SOURCE.setTextStyle(EnumSet.noneOf(TextStyle.class));
        assertTrue("inactive source bold", isBold(settings, true, false));
        assertTrue("active source bold", isBold(settings, true, true));

        // target text never picks up the source flags
        EditorColor.COLOR_ACTIVE_SOURCE.setTextStyle(EnumSet.of(TextStyle.BOLD));
        assertFalse("inactive target plain", isBold(settings, false, false));
        assertFalse("active target plain", isBold(settings, false, true));
    }

    @Test
    public void testDefaultsKeepTheHistoricAppearance() {
        EditorSettings settings = new EditorSettings(null);
        EditorColor.COLOR_SOURCE.setTextStyle(EditorColor.COLOR_SOURCE.getDefaultTextStyle());
        EditorColor.COLOR_ACTIVE_SOURCE
                .setTextStyle(EditorColor.COLOR_ACTIVE_SOURCE.getDefaultTextStyle());
        assertTrue("source text is bold out of the box", isBold(settings, true, false));
        assertTrue("active source text is bold out of the box", isBold(settings, true, true));
        assertFalse("target text is not", isBold(settings, false, true));
    }

    /**
     * Other state styles overlay on top of the source flags instead of
     * replacing them.
     */
    @Test
    public void testStateStyleOverlaysAdditively() {
        EditorSettings settings = new EditorSettings(null);
        EditorColor.COLOR_SOURCE.setTextStyle(EnumSet.of(TextStyle.BOLD));
        EnumSet<TextStyle> before = EditorColor.COLOR_ACTIVE_SOURCE.getTextStyle().isEmpty()
                ? EnumSet.noneOf(TextStyle.class)
                : EnumSet.copyOf(EditorColor.COLOR_ACTIVE_SOURCE.getTextStyle());
        try {
            EditorColor.COLOR_ACTIVE_SOURCE.setTextStyle(EnumSet.of(TextStyle.ITALIC));
            AttributeSet attrs = settings.getAttributeSet(true, false, false, DUPLICATE.NONE, true,
                    false, false, false);
            assertTrue("COLOR_SOURCE bold survives", StyleConstants.isBold(attrs));
            assertTrue("COLOR_ACTIVE_SOURCE italic added", StyleConstants.isItalic(attrs));
        } finally {
            EditorColor.COLOR_ACTIVE_SOURCE.setTextStyle(before);
        }
    }
}
