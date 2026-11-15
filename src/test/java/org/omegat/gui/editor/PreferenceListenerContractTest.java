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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

import org.omegat.util.Preferences;

/**
 * Pins the editor's preference subscriptions to the preferences dialog:
 * the keys the View and Tag Processing pages write are exactly the keys the
 * editor redraws on, and no dialog controller refreshes derived state by
 * hand any more (see ADR 2026012).
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class PreferenceListenerContractTest {

    private static final Path CONTROLLERS = Paths.get("src", "main", "java", "org", "omegat", "gui", "preferences",
            "view");

    private static final Pattern WRITTEN = Pattern
            .compile("Preferences\\.setPreference\\(\\s*Preferences\\.([A-Z][A-Z0-9_]+)");

    /** Refresh calls the controllers used to make; consumers subscribe now. */
    private static final Pattern REFRESH_CALL = Pattern.compile("updateViewPreferences\\(|"
            + "updateTagValidationPreferences\\(|PatternConsts\\.update\\w+\\(|ModificationInfoManager\\.reset\\(|"
            + "getAutoCompleter\\(\\)\\.resetKeys\\(|setUseTabForAdvance\\(");

    @Test
    public void testViewPageKeysAreSubscribed() throws Exception {
        assertEquals(constantNames(EditorSettings.VIEW_KEYS), writtenKeys("ViewOptionsController.java"));
    }

    @Test
    public void testTagProcessingPageKeysAreSubscribed() throws Exception {
        assertEquals(constantNames(EditorSettings.TAG_VALIDATION_KEYS),
                writtenKeys("TagProcessingOptionsController.java"));
    }

    @Test
    public void testNoControllerRefreshesDerivedStateByHand() throws IOException {
        assertTrue(Files.isDirectory(CONTROLLERS));
        Set<String> offenders = new TreeSet<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith("Controller.java")) {
                    continue;
                }
                Matcher m = REFRESH_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                if (m.find()) {
                    offenders.add(name + ": " + m.group());
                }
            }
        }
        // The spellchecker page keeps one deliberate call: reloading
        // dictionaries while checking stays on is no preference change.
        offenders.remove("SpellcheckerConfigurationController.java: setAutoSpellChecking(");
        // The shortcuts page rebinds the autocompleter after a shortcut set
        // changes; shortcuts live outside Preferences, so no listener applies.
        offenders.remove("ShortcutsPreferencesController.java: getAutoCompleter().resetKeys(");
        assertEquals(Set.of(), offenders);
    }

    private static Set<String> writtenKeys(String controller) throws IOException {
        Matcher m = WRITTEN.matcher(Files.readString(CONTROLLERS.resolve(controller), StandardCharsets.UTF_8));
        Set<String> keys = new TreeSet<>();
        while (m.find()) {
            keys.add(m.group(1));
        }
        assertTrue(controller + " writes nothing?", !keys.isEmpty());
        return keys;
    }

    private static Set<String> constantNames(String[] values) throws Exception {
        Set<String> names = new TreeSet<>();
        for (String value : values) {
            boolean found = false;
            for (Field field : Preferences.class.getFields()) {
                if (field.getType() == String.class && value.equals(field.get(null))) {
                    names.add(field.getName());
                    found = true;
                }
            }
            assertTrue("no Preferences constant for " + value, found);
        }
        return names;
    }
}
