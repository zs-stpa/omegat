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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One sample per action type, so the tests that must know every type fail
 * the moment a new one is added without a sample.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
final class SampleSpecs {

    private SampleSpecs() {
    }

    static List<ActionSpec> all() {
        return List.of(new ActionSpec.MenuActionSpec("cmd"), new ActionSpec.EditorKeyActionSpec("key"),
                new ActionSpec.ScriptActionSpec("f.groovy"),
                new ActionSpec.SearchActionSpec("q", false,
                        new LinkedHashMap<>(Map.of("search_window_whole_words", "true"))),
                new ActionSpec.SnippetActionSpec("t"),
                new ActionSpec.PreferenceActionSpec("theme_color_mode", "combobox", 0, 0,
                        List.of("default", "dark", "sync")),
                new ActionSpec.ProjectFlagActionSpec("SentenceSegmentingEnabled"),
                new ActionSpec.AutotextRefActionSpec("src"), new ActionSpec.ColorSchemeActionSpec("c"),
                new ActionSpec.ShortcutSetActionSpec("s"),
                new ActionSpec.UrlActionSpec("https://omegat.org/?q={comment}", "escape"),
                new ActionSpec.ExternalSearchActionSpec("Open in &Xcode"),
                new ActionSpec.RecentProjectsActionSpec(),
                new ActionSpec.UnknownActionSpec("hologram", Map.of("beam", "wide")));
    }

    /** Every permitted implementation has a sample. */
    static void assertCoversAllTypes(List<ActionSpec> samples) {
        Set<Class<?>> permitted = new HashSet<>(Arrays.asList(ActionSpec.class.getPermittedSubclasses()));
        Set<Class<?>> covered = samples.stream().map(ActionSpec::getClass).collect(Collectors.toSet());
        assertEquals(permitted, covered);
    }
}
