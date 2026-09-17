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

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Test;

import org.omegat.util.TestPreferencesInitializer;

/**
 * Pins the complete set of search window options that a search preset
 * captures. Any change to the search window's configurability (an option
 * added, removed or renamed) fails this test on purpose: the golden list
 * below must then be updated consciously, together with a thought about
 * what existing presets should do — at runtime, a preset whose stored set
 * differs from the current one raises the update dialog in
 * {@link ActionInvoker}.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class SearchOptionsContractTest {

    private static final Set<String> EXPECTED = Set.of(
            "search_window_search_type",
            "search_window_replace_type",
            "search_window_case_sensitive",
            "search_window_space_match_nbsp",
            "search_window_whole_words",
            "search_window_case_sensitive_replace",
            "search_window_space_match_nbsp_replace",
            "search_window_replace_untranslated",
            "search_window_search_source",
            "search_window_search_translation",
            "search_window_search_state",
            "search_window_search_notes",
            "search_window_search_comments",
            "search_window_reg_expressions",
            "search_window_glossary_search",
            "search_window_memory_search",
            "search_window_tm_search",
            "search_window_all_results",
            "search_window_file_names",
            "search_window_advanced_visible",
            "search_window_search_author",
            "search_window_author_name",
            "search_window_date_from",
            "search_window_date_from_value",
            "search_window_date_to",
            "search_window_date_to_value",
            "search_window_number_of_results",
            "search_window_dir",
            "search_window_search_files",
            "search_window_search_recursive",
            "search_window_auto_sync",
            "search_window_back_to_initial_segment",
            "search_window_exclude_orphans",
            "search_window_full_half_width_insensitive");

    @Before
    public void setUp() throws IOException {
        TestPreferencesInitializer.init();
    }

    @Test
    public void testCapturedOptionSetIsComplete() {
        assertEquals(new TreeSet<>(EXPECTED),
                new TreeSet<>(SearchActionWizard.captureSearchOptions().keySet()));
    }
}
