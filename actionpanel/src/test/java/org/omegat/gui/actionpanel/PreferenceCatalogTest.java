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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.omegat.gui.actionpanel.ActionSpec.PreferenceActionSpec;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.TestPreferencesInitializer;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class PreferenceCatalogTest {

    private static Locale savedLocale = Locale.getDefault();

    @BeforeClass
    public static void setUp() throws Exception {
        savedLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        OStrings.loadBundle(Locale.ENGLISH);
        TestPreferencesInitializer.init();
    }

    @AfterClass
    public static void tearDown() {
        Locale.setDefault(savedLocale);
        OStrings.loadBundle(savedLocale);
    }

    /**
     * Top level mirrors the page order of the preferences dialog. The
     * expectation is a literal: PreferencesWindowController builds its tree
     * from GUI controllers, so a reorder or a renamed page title upstream
     * has to be mirrored here by hand.
     */
    @Test
    public void testPageOrderFollowsDialog() {
        List<String> titles = new ArrayList<>();
        for (PreferenceCatalog.Group group : PreferenceCatalog.groups()) {
            titles.add(group.title());
        }
        assertEquals(List.of("General", "Machine Translation", "Glossaries", "Dictionary", "Appearance",
                "Auto-Completion", "Spellchecker", "Editor", "Keyboard Shortcuts", "Tag Processing", "TM Matches",
                "View", "Saving and Output", "Updates"), titles);
    }

    @Test
    public void testEntriesAreWellFormedAndUnique() throws Exception {
        Set<String> knownKeys = new HashSet<>();
        for (Field field : Preferences.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                knownKeys.add((String) field.get(null));
            }
        }
        Set<String> seen = new HashSet<>();
        List<PreferenceCatalog.Entry> entries = new ArrayList<>();
        collect(PreferenceCatalog.groups(), entries);
        assertFalse(entries.isEmpty());
        for (PreferenceCatalog.Entry entry : entries) {
            PreferenceActionSpec spec = entry.spec();
            assertTrue("unknown preference " + spec.key(), knownKeys.contains(spec.key()));
            assertTrue("duplicate " + spec.key(), seen.add(spec.key()));
            assertFalse("empty label for " + spec.key(), entry.label().isBlank());
            assertFalse("mnemonic left in label " + entry.label(), entry.label().contains("&"));
            switch (spec.kind()) {
            case PreferenceCatalog.KIND_TOGGLE -> {
                assertEquals(1, spec.values().size());
                assertTrue(List.of("true", "false").contains(spec.values().get(0)));
            }
            case PreferenceCatalog.KIND_SLIDER -> {
                assertTrue(spec.key(), spec.min() < spec.max());
                int coded = Integer.parseInt(spec.values().get(0));
                assertTrue(spec.key(), coded >= spec.min() && coded <= spec.max());
            }
            case PreferenceCatalog.KIND_COMBOBOX -> {
                assertTrue(spec.key(), spec.values().size() > 1);
                for (String value : spec.values()) {
                    assertNotNull(PreferenceCatalog.valueLabel(spec.key(), value));
                }
            }
            default -> throw new AssertionError("unknown kind " + spec.kind());
            }
            assertEquals(entry.label(), PreferenceCatalog.label(spec.key()));
            assertEquals(spec, ActionSpec.of(spec.type(), spec.attributes()));
        }
    }

    /**
     * Where core declares a coded default constant, the catalog must agree.
     * The one exception is listed with its reason. Literal defaults without a
     * constant are pinned by nothing but the dialog controllers themselves.
     */
    @Test
    public void testDefaultsMatchCoreConstants() throws Exception {
        // Core constant true, dialog and SegmentBuilder read without it.
        Set<String> knownDrift = Set.of("VIEW_OPTION_TEMPLATE_ACTIVE");
        int checked = 0;
        for (Field field : Preferences.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class
                    || knownDrift.contains(field.getName())) {
                continue;
            }
            String key = (String) field.get(null);
            Field constant;
            try {
                constant = Preferences.class.getField(field.getName() + "_DEFAULT");
            } catch (NoSuchFieldException e) {
                continue;
            }
            Boolean codedBoolean = PreferenceCatalog.codedDefault(key).orElse(null);
            Integer codedInt = PreferenceCatalog.codedIntDefault(key);
            if (constant.getType() == boolean.class && codedBoolean != null) {
                assertEquals(field.getName(), constant.get(null), codedBoolean);
                checked++;
            } else if (constant.getType() == int.class && codedInt != null) {
                assertEquals(field.getName(), constant.get(null), codedInt);
                checked++;
            }
        }
        assertTrue(checked >= 12);
    }

    @Test
    public void testFolderPagesSitWhereTheDialogHasThem() {
        List<PreferenceCatalog.Group> root = PreferenceCatalog.groups();
        PreferenceCatalog.Group appearance = root.get(4);
        assertEquals(PreferenceCatalog.Folder.COLOR_SCHEMES,
                appearance.children().get(appearance.children().size() - 1).folder());
        assertEquals(PreferenceCatalog.Folder.SHORTCUT_SETS, root.get(8).folder());
    }

    @Test
    public void testToggleDefaultHonoured() {
        PreferenceActionSpec spec = new PreferenceActionSpec("actionpanel_test_never_set",
                PreferenceCatalog.KIND_TOGGLE, 0, 0, List.of("true"));
        assertTrue(PreferenceCatalog.isSelected(spec));
        PreferenceActionSpec slider = new PreferenceActionSpec("actionpanel_test_never_set_int",
                PreferenceCatalog.KIND_SLIDER, 0, 100, List.of("42"));
        assertEquals(42, PreferenceCatalog.intValue(slider));
        assertFalse(PreferenceCatalog.isCatalogued("unknown"));
        assertFalse(PreferenceCatalog.isKnownKind("hologram"));
        assertEquals("unknown", PreferenceCatalog.label("unknown"));
        assertEquals("raw", PreferenceCatalog.valueLabel("some_key", "raw"));
    }

    private static void collect(List<PreferenceCatalog.Group> groups, List<PreferenceCatalog.Entry> into) {
        for (PreferenceCatalog.Group group : groups) {
            into.addAll(group.entries());
            collect(group.children(), into);
        }
    }
}
