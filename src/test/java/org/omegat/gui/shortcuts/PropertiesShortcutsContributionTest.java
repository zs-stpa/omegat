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

package org.omegat.gui.shortcuts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.KeyStroke;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.util.RuntimePreferences;

/**
 * A module adds its shortcut defaults and labels to a set.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class PropertiesShortcutsContributionTest {

    private static final String ROOT = "/org/omegat/gui/shortcuts/";
    private static final String CONTRIBUTION = ROOT + "contrib.properties";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String previousConfigDir;

    @Before
    public void setUp() throws IOException {
        previousConfigDir = RuntimePreferences.getConfigDir();
        RuntimePreferences.setConfigDir(folder.newFolder("config").getAbsolutePath());
    }

    @After
    public void tearDown() {
        RuntimePreferences.setConfigDir(previousConfigDir);
    }

    @Test
    public void testContributedKeysJoinTheSetWithLabels() throws IOException {
        PropertiesShortcuts set = PropertiesShortcuts.loadBundled(ROOT, "test.properties");
        assertFalse(set.getKeys().contains("CONTRIB_ONE"));
        set.contribute(CONTRIBUTION, getClass().getClassLoader(), "Module", key -> "Module: " + key);
        assertTrue(set.getKeys().contains("CONTRIB_ONE"));
        assertEquals(KeyStroke.getKeyStroke("ctrl shift 1"), set.getKeyStroke("CONTRIB_ONE"));
        assertEquals("ctrl shift 2", set.getDefaultValue("CONTRIB_TWO"));
        assertEquals("Module: CONTRIB_ONE", set.contributedLabel("CONTRIB_ONE"));
        assertEquals("Module", set.contributedScope("CONTRIB_TWO"));
        // Labels and scope apply to the contributed keys only.
        assertNull(set.contributedLabel("TEST_SAVE"));
        assertNull(set.contributedScope("TEST_SAVE"));
        // The set's own keys stay.
        assertEquals(KeyStroke.getKeyStroke("ctrl S"), set.getKeyStroke("TEST_SAVE"));

        // Contributing the same file again replaces, never duplicates.
        set.contribute(CONTRIBUTION, getClass().getClassLoader(), "Module 2", key -> null);
        assertEquals("Module 2", set.contributedScope("CONTRIB_ONE"));
        assertNull(set.contributedLabel("CONTRIB_ONE"));

        set.uncontribute(CONTRIBUTION);
        assertFalse(set.getKeys().contains("CONTRIB_ONE"));
        assertNull(set.contributedScope("CONTRIB_ONE"));
        assertEquals(KeyStroke.getKeyStroke("ctrl S"), set.getKeyStroke("TEST_SAVE"));
    }

    @Test(expected = java.io.FileNotFoundException.class)
    public void testMissingContributionIsReported() throws IOException {
        new PropertiesShortcuts().contribute(ROOT + "no-such-file.properties", getClass().getClassLoader(), "Module",
                key -> null);
    }

    @Test
    public void testUserOverrideAndReloadKeepContribution() throws IOException {
        Files.writeString(new File(RuntimePreferences.getConfigDir(), "test.properties").toPath(),
                "CONTRIB_ONE=ctrl shift 9\nCONTRIB_TWO=ctrl shift 2\n", StandardCharsets.ISO_8859_1);
        PropertiesShortcuts set = PropertiesShortcuts.loadBundled(ROOT, "test.properties");
        set.contribute(CONTRIBUTION, getClass().getClassLoader(), "Module", key -> null);
        // Saved before the module's defaults were known: the override stands,
        // the value equal to the default is no override.
        assertEquals(KeyStroke.getKeyStroke("ctrl shift 9"), set.getKeyStroke("CONTRIB_ONE"));
        assertTrue(set.isModified("CONTRIB_ONE"));
        assertFalse(set.isModified("CONTRIB_TWO"));

        set.reload();
        assertTrue(set.getKeys().contains("CONTRIB_TWO"));
        assertEquals("ctrl shift 2", set.getDefaultValue("CONTRIB_TWO"));
        assertEquals(KeyStroke.getKeyStroke("ctrl shift 9"), set.getKeyStroke("CONTRIB_ONE"));
    }
}
