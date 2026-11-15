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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

import org.omegat.util.Preferences;

/**
 * Pins the catalog to the preferences dialog: every preference a dialog
 * controller reads or writes is either offered on the panel or listed here
 * with the reason it is not. A new dialog setting therefore fails this test
 * until someone decides where it belongs. Blind spot: settings a controller
 * routes through another class instead of a Preferences constant (the "Use
 * TAB to advance" checkbox goes through EditorSettings) are not seen here.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class PreferenceDialogContractTest {

    /** Dialog preferences deliberately absent from the catalog, with reason. */
    private static final Map<String, String> EXCLUDED = Map.ofEntries(
            Map.entry("SPELLCHECKER_DICTIONARY_DIRECTORY", "path text field"),
            Map.entry("TEAM_AUTHOR", "free text"),
            Map.entry("EXTERNAL_COMMAND", "free text"),
            Map.entry("STATS_OUTPUT_FORMAT", "multi-select flag set"),
            Map.entry("EXT_TMX_MATCH_TEMPLATE", "free text template"),
            Map.entry("VIEW_OPTION_MOD_INFO_TEMPLATE", "free text template"),
            Map.entry("VIEW_OPTION_MOD_INFO_TEMPLATE_WO_DATE", "free text template"),
            Map.entry("BEST_MATCH_EXPLANATORY_TEXT", "free text"),
            Map.entry("MARK_PARA_TEXT", "free text"),
            Map.entry("EDITOR_INITIAL_SEGMENT_LOAD_COUNT", "open-ended count, no useful slider range"),
            Map.entry("CHECK_CUSTOM_PATTERN", "regex text"),
            Map.entry("CHECK_REMOVE_PATTERN", "regex text"),
            Map.entry("DONT_CHECK_PRINTF_TAGS", "coupled radio triple"),
            Map.entry("CHECK_SIMPLE_PRINTF_TAGS", "coupled radio triple"),
            Map.entry("CHECK_ALL_PRINTF_TAGS", "coupled radio triple"),
            Map.entry("AC_CHARTABLE_CUSTOM_CHAR_STRING", "free text"),
            Map.entry("MENUUI_CLASS_NAME", "class name, restart required"),
            Map.entry("THEME_CLASS_NAME", "class name, restart required"),
            Map.entry("THEME_DARK_CLASS_NAME", "class name, restart required"),
            Map.entry("TF_SRC_FONT_NAME", "font family chooser"),
            Map.entry("PROJECT_FILES_SHOW_ON_LOAD", "no dialog control, internal restore only"),
            Map.entry("PROXY_USER_NAME", "credential"), Map.entry("PROXY_PASSWORD", "credential"),
            Map.entry("TARGET_LOCALE", "read only, to pick the spellchecker language"));

    private static final Pattern USAGE = Pattern.compile("\\bPreferences\\.([A-Z][A-Z0-9_]+)\\b");

    @Test
    public void testEveryDialogPreferenceIsCataloguedOrExcluded() throws Exception {
        Set<String> dialogKeys = dialogPreferenceNames();
        assertTrue("controller scan found nothing", dialogKeys.size() > 50);
        Set<String> catalogued = new TreeSet<>();
        for (java.lang.reflect.Field field : Preferences.class.getFields()) {
            if (field.getType() == String.class && PreferenceCatalog.isCatalogued((String) field.get(null))) {
                catalogued.add(field.getName());
            }
        }
        Set<String> missing = new TreeSet<>(dialogKeys);
        missing.removeAll(catalogued);
        missing.removeAll(EXCLUDED.keySet());
        assertEquals("dialog preferences neither catalogued nor excluded with a reason", Set.of(), missing);
        Set<String> stale = new TreeSet<>(EXCLUDED.keySet());
        stale.removeAll(dialogKeys);
        assertEquals("exclusions no dialog controller uses any more", Set.of(), stale);
        Set<String> both = new TreeSet<>(EXCLUDED.keySet());
        both.retainAll(catalogued);
        assertEquals("excluded and catalogued at once", Set.of(), both);
    }

    /** Preference constant names used by the dialog controllers, less defaults. */
    private static Set<String> dialogPreferenceNames() throws IOException {
        Path dir = Paths.get("..", "src", "main", "java", "org", "omegat", "gui", "preferences", "view");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("src", "main", "java", "org", "omegat", "gui", "preferences", "view");
        }
        assertTrue("preferences dialog sources not found at " + dir.toAbsolutePath(), Files.isDirectory(dir));
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!file.getFileName().toString().endsWith("Controller.java")) {
                    continue;
                }
                Matcher m = USAGE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    String name = m.group(1);
                    if (!name.endsWith("_DEFAULT")) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }
}
