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

package org.omegat.externalfinder.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.Map;

import org.junit.Test;

import org.omegat.externalfinder.item.PlaceholderTemplate;
import org.omegat.util.OStrings;

/**
 * Pins the places that must move together when a placeholder is added:
 * the parser's name list, the dialog links and the bundle tooltips.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class PlaceholderLinksTest {

    @Test
    public void testEveryPlaceholderHasLinkAndTooltip() {
        Map<String, String> links = PlaceholderLinks.links();
        for (String name : PlaceholderTemplate.NAMES) {
            assertTrue("link for " + name, links.containsKey(name));
            assertTrue(links.get(name).startsWith("{" + name));
        }
        for (String name : links.keySet()) {
            String tip = OStrings.getString("EXTERNALFINDER_PLACEHOLDER_TIP_" + name.toUpperCase(Locale.ENGLISH));
            assertFalse(tip.isEmpty());
        }
    }

    @Test
    public void testInsertedTextsParse() {
        for (String text : PlaceholderLinks.links().values()) {
            PlaceholderTemplate template = PlaceholderTemplate.parse(text);
            assertTrue(text, template.hasPlaceholders());
            assertEquals(text.startsWith("{target"), template.usesSelection());
        }
    }
}
