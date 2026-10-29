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

package org.omegat.gui.glossary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.awt.Color;

import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;

import org.junit.BeforeClass;
import org.junit.Test;

import org.omegat.util.TestPreferencesInitializer;
import org.omegat.util.gui.Styles;

/**
 * The glossary pane attributes must pick up the currently configured
 * colors per call; the deprecated constants froze them at class load, so
 * a color change only applied after a restart.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class IGlossaryRendererTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        TestPreferencesInitializer.init();
    }

    @Test
    public void testAttributesFollowConfiguredColor() {
        // Pin the interface load order: the constant snapshots the color
        // BEFORE the test changes it.
        AttributeSet frozen = IGlossaryRenderer.SOURCE_ATTRIBUTES;
        Color custom = new Color(0x123456);
        try {
            Styles.EditorColor.COLOR_GLOSSARY_SOURCE.setColor(custom);
            assertEquals("fresh attributes carry the configured color", custom,
                    StyleConstants.getForeground(IGlossaryRenderer.sourceAttributes()));
            assertNotEquals("the deprecated constant keeps its load-time snapshot", custom,
                    StyleConstants.getForeground(frozen));
        } finally {
            Styles.EditorColor.COLOR_GLOSSARY_SOURCE.setColor(null);
        }
    }
}
