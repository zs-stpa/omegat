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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The HTML report of a diverged file-backed team setting marks each line
 * with the version it belongs to, escapes markup, and lands as a file in
 * the requested directory under a filesystem-safe name.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class TeamSettingDiffReportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static TeamSetting setting(String key) {
        TeamSetting.Storage storage = new TeamSetting.Storage() {
            @Override
            public @Nullable String loadFrom(ProjectProperties config,
                    Function<String, @Nullable File> fileResolver) {
                return null;
            }

            @Override
            public void save(ProjectProperties config, @Nullable String raw) {
            }

            @Override
            public List<String> pathsUnderRoot(ProjectProperties config) {
                return List.of("omegat/test.conf");
            }
        };
        return TeamSetting.ofStoredFile(key, "TEAM_SETTING_NAME_SEGMENTATION", config -> null,
                (config, raw) -> { }, raw -> "", storage);
    }

    private String report(String key, String localValue, String teamValue) throws Exception {
        File file = TeamSettingDiffReport.write(setting(key), localValue, teamValue, folder.getRoot());
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void testLinesMarkedPerVersion() throws Exception {
        String html = report("diff_test", "shared\nlocal only\ntail", "shared\nteam only\ntail");
        assertTrue("report carries the GPL project header after the doctype",
                html.startsWith("<!DOCTYPE html>\n<!--\n OmegaT")
                        && html.contains("GNU General\n Public License"));
        assertTrue("unchanged line marked as both",
                html.contains("<tr class=\"both\"><td class=\"sign\">&nbsp;</td><td>shared</td></tr>"));
        assertTrue("removed line marked as local",
                html.contains("<tr class=\"local\"><td class=\"sign\">&minus;</td><td>local only</td></tr>"));
        assertTrue("added line marked as team",
                html.contains("<tr class=\"team\"><td class=\"sign\">+</td><td>team only</td></tr>"));
        assertTrue("shared tail marked as both",
                html.contains("<tr class=\"both\"><td class=\"sign\">&nbsp;</td><td>tail</td></tr>"));
    }

    @Test
    public void testMarkupEscapedAndAbsentSideEmpty() throws Exception {
        String html = report("diff_test", "<rule if=\"a &amp; b\">", null);
        assertTrue("markup of the compared content is escaped",
                html.contains("&lt;rule if=\"a &amp;amp; b\"&gt;"));
        assertFalse("raw markup must not survive into the report",
                html.contains("<rule if="));
        assertTrue("every line of the surviving side is marked local", html.contains("<tr class=\"local\">"));
        assertFalse("an absent side contributes no rows", html.contains("<tr class=\"team\">"));
    }

    @Test
    public void testPrefixOnlyOverlap() throws Exception {
        String html = report("diff_test", "shared\nextra", "shared");
        assertTrue(html.contains("<tr class=\"both\"><td class=\"sign\">&nbsp;</td><td>shared</td></tr>"));
        assertTrue(html.contains("<tr class=\"local\"><td class=\"sign\">&minus;</td><td>extra</td></tr>"));
        assertFalse(html.contains("<tr class=\"team\">"));
    }

    @Test
    public void testMovedBlockKeepsCommonSubsequence() throws Exception {
        String html = report("diff_test", "moved\nkeep one\nkeep two", "keep one\nkeep two\nmoved");
        assertTrue("the moved line leaves the local position",
                html.contains("<tr class=\"local\"><td class=\"sign\">&minus;</td><td>moved</td></tr>"));
        assertTrue("the moved line re-appears at the team position",
                html.contains("<tr class=\"team\"><td class=\"sign\">+</td><td>moved</td></tr>"));
        assertTrue(html.contains("<tr class=\"both\"><td class=\"sign\">&nbsp;</td><td>keep one</td></tr>"));
        int removal = html.indexOf("<tr class=\"local\">");
        int kept = html.indexOf("<td>keep one</td>");
        int addition = html.indexOf("<tr class=\"team\">");
        assertTrue("kept block stays between removal and addition",
                removal < kept && kept < addition);
    }

    @Test
    public void testWindowsLineEndings() throws Exception {
        String html = report("diff_test", "shared\r\nlocal only", "shared\r\nteam only");
        assertTrue(html.contains("<tr class=\"both\"><td class=\"sign\">&nbsp;</td><td>shared</td></tr>"));
        assertTrue(html.contains("<td>local only</td>"));
        assertTrue(html.contains("<td>team only</td>"));
        assertFalse("no carriage return survives into the report", html.contains("\r"));
    }

    @Test
    public void testLongIdenticalStretchesFoldIntoGapRow() throws Exception {
        StringBuilder base = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            base.append("rule line ").append(i).append('\n');
        }
        String local = base + "local tail";
        String team = base + "team tail";
        String html = report("diff_test", local, team);
        assertTrue("changed lines survive", html.contains("<td>local tail</td>"));
        assertTrue("ten context lines before the change stay",
                html.contains("<td>rule line 21</td>"));
        assertFalse("lines beyond the context window are folded",
                html.contains("<td>rule line 20</td>"));
        // The bundle string is localized ("identical lines"/"identische
        // Zeilen"); assert the count and the shared stem only.
        assertTrue("the fold is announced with its line count",
                html.contains("<tr class=\"gap\">") && html.contains("20 identi"));
    }

    @Test
    public void testTrailingIdenticalStretchFoldsAfterLastContextLine() throws Exception {
        StringBuilder tail = new StringBuilder();
        for (int i = 2; i <= 40; i++) {
            tail.append('\n').append("ctx ").append(i);
        }
        String html = report("diff_test", "local head" + tail, "team head" + tail);
        assertTrue("ten context lines after the change stay", html.contains("<td>ctx 11</td>"));
        assertFalse("lines beyond the trailing window are folded", html.contains("<td>ctx 12</td>"));
        assertTrue("the trailing fold is announced with its line count",
                html.contains("<tr class=\"gap\">") && html.contains("29 identi"));
    }

    @Test
    public void testNearbyChangesMergeIntoOneContextWindow() throws Exception {
        StringBuilder local = new StringBuilder();
        StringBuilder team = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            String line = "ctx " + i;
            local.append(i == 3 ? "local three" : i == 18 ? "local eighteen" : line).append('\n');
            team.append(i == 3 ? "team three" : i == 18 ? "team eighteen" : line).append('\n');
        }
        String html = report("diff_test", local.toString(), team.toString());
        assertTrue(html.contains("<td>local three</td>") && html.contains("<td>team eighteen</td>"));
        assertFalse("overlapping context windows leave no fold", html.contains("<tr class=\"gap\">"));
    }

    @Test
    public void testReportFileNameSanitized() throws Exception {
        File file = TeamSettingDiffReport.write(setting("team/setting:diff"), "a", "b", folder.getRoot());
        assertTrue(file.isFile());
        assertTrue("key characters unsafe for filenames are replaced",
                file.getName().matches("setting-diff_team_setting_diff_.*\\.html"));
    }
}
