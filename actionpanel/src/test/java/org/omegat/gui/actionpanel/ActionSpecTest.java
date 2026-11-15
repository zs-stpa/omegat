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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import org.omegat.gui.actionpanel.ActionSpec.UnknownActionSpec;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionSpecTest {

    @Test
    public void testTagsAreUniqueAndRoundTrip() {
        List<ActionSpec> specs = SampleSpecs.all();
        SampleSpecs.assertCoversAllTypes(specs);
        Set<String> tags = new HashSet<>();
        for (ActionSpec spec : specs) {
            assertTrue("duplicate tag " + spec.type(), tags.add(spec.type()));
            assertEquals(spec, ActionSpec.of(spec.type(), spec.attributes()));
        }
    }

    @Test
    public void testUrlWithoutEncodingReadsAsDefault() {
        assertEquals(new ActionSpec.UrlActionSpec("https://omegat.org/", "default"),
                ActionSpec.of("url", java.util.Map.of("url", "https://omegat.org/")));
        assertEquals("https://omegat.org/", new ActionSpec.UrlActionSpec("https://omegat.org/").identity());
    }

    @Test
    public void testToggleKindRoundTrips() {
        ActionSpec spec = new ActionSpec.PreferenceActionSpec("glossary_stemming", "toggle", 0, 0,
                List.of("true"));
        assertEquals(spec, ActionSpec.of(spec.type(), spec.attributes()));
    }

    @Test
    public void testUnknownTagIsPreserved() {
        ActionSpec spec = ActionSpec.of("hologram", java.util.Map.of("beam", "wide"));
        assertTrue(spec instanceof UnknownActionSpec);
        assertEquals("hologram", spec.type());
        assertEquals(java.util.Map.of("beam", "wide"), spec.attributes());
    }
}
