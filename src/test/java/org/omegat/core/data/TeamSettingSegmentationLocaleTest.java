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

package org.omegat.core.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.util.LocaleRule;
import org.omegat.util.TestPreferencesInitializer;

/**
 * A team member's segmentation file written by an old localized OmegaT
 * must not register as a divergence for members running another UI
 * language: the canonical comparison value normalizes the rule set names
 * to the stable codes, and sharing writes the codes to the team.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class TeamSettingSegmentationLocaleTest {

    private static final Path LEGACY_DE_SRX = Paths
            .get("src/test/resources/data/segmentation/migrate/locale_de_names/segmentation.srx");

    @Rule
    public final LocaleRule localeRule = new LocaleRule(Locale.of("en"));

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ProjectProperties config;

    @BeforeClass
    public static void setUpClass() throws Exception {
        TestPreferencesInitializer.init();
    }

    @Before
    public void setUp() throws Exception {
        File projectDir = folder.newFolder("project");
        config = new ProjectProperties(projectDir);
        Files.createDirectories(new File(config.getProjectInternal()).toPath());
    }

    @Test
    public void testLegacyGermanTeamFileReadsCanonicallyUnderEnglishUi() throws Exception {
        File teamFile = folder.newFile("team-segmentation.srx");
        Files.copy(LEGACY_DE_SRX, teamFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String teamCanonical = TeamSettingFiles.SEGMENTATION.loadStoredFrom(config,
                path -> path.endsWith("segmentation.srx") ? teamFile : null);
        assertNotNull(teamCanonical);
        assertTrue("canonical form carries the stable codes",
                teamCanonical.contains("\"Default\"") && teamCanonical.contains("\"HTML\""));
        assertFalse("localized rule set names are normalized away",
                teamCanonical.contains("Standard") || teamCanonical.contains("Segmentierung"));
        assertTrue("a user-defined rule set keeps its name",
                teamCanonical.contains("Eigene Abkürzungen"));

        // The same rules stored locally (sharing writes the canonical form)
        // read back identically: no divergence between a member who wrote
        // the file under German UI and one reading it under English UI.
        TeamSettingFiles.SEGMENTATION.saveStored(config, teamCanonical);
        assertEquals("stored and team canonical forms agree - no divergence", teamCanonical,
                TeamSettingFiles.SEGMENTATION.loadStored(config));

        String written = Files.readString(
                new File(config.getProjectInternal(), "segmentation.srx").toPath(),
                StandardCharsets.UTF_8);
        assertTrue("sharing writes the stable codes to the team file",
                written.contains("\"Default\""));
        assertFalse(written.contains("languagerulename=\"Standard\""));
    }
}
