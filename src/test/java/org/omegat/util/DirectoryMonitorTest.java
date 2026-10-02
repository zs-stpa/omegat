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

package org.omegat.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The monitor reports the monitored folder's own files, but never the
 * content of version control bookkeeping or the team checkout mirror
 * (.repositories) below it - those files belong to the team machinery, not
 * to the monitored resource.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class DirectoryMonitorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File write(String relative) throws Exception {
        File file = new File(folder.getRoot(), relative);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.writeString(file.toPath(), "content", StandardCharsets.UTF_8);
        return file;
    }

    @Test
    public void testMirrorAndVcsContentNotReported() throws Exception {
        File plain = write("glossary.txt");
        File nested = write("sub/extra.txt");
        write(".repositories/git_team.git/glossary/glossary.txt");
        write(".git/config");
        write(".svn/entries");
        // A FILE named like a skipped directory is still reported, matching
        // the **/.git/** glob semantics of the source-scan excludes.
        File gitNamedFile = write("sub/.git");

        List<File> seen = new ArrayList<>();
        DirectoryMonitor monitor = new DirectoryMonitor(folder.getRoot(), seen::add);
        monitor.checkChanges();

        assertTrue(seen.contains(plain));
        assertTrue(seen.contains(nested));
        assertTrue(seen.contains(gitNamedFile));
        assertEquals("only the resource's own files are reported", 3, seen.size());
    }

    @Test
    public void testRootLocationDoesNotDisqualifyContent() throws Exception {
        // A monitor rooted INSIDE a skipped directory still sees its own
        // files: a project living under a path with a "CVS" or ".git"
        // segment must not lose its monitors.
        File root = folder.newFolder(".repositories", "checkout", "glossary");
        File inside = new File(root, "glossary.txt");
        Files.writeString(inside.toPath(), "content", StandardCharsets.UTF_8);

        List<File> seen = new ArrayList<>();
        DirectoryMonitor monitor = new DirectoryMonitor(root, seen::add);
        monitor.checkChanges();

        assertEquals(List.of(inside), seen);
        assertFalse(monitor.isUnderSkippedDirectory(inside));
    }

    /**
     * The skip set must keep covering every directory pattern of the
     * source scan's default excludes; a name added there without a monitor
     * counterpart would silently reopen the mirror double-load.
     */
    @Test
    public void testSkipSetCoversDefaultExcludeDirectories() {
        for (String exclude : org.omegat.core.data.ProjectProperties.getDefaultExcludes()) {
            if (exclude.startsWith("**/") && exclude.endsWith("/**")) {
                String name = exclude.substring(3, exclude.length() - 3);
                assertTrue("skip set misses directory exclude " + name,
                        DirectoryMonitor.SKIPPED_DIR_NAMES.contains(name));
            }
        }
    }

    @Test
    public void testAcceptsValidatesDirectCallbackArguments() throws Exception {
        File inRoot = write("glossary.txt");
        File mirrored = write(".repositories/git_team.git/glossary/glossary.txt");
        File outside = File.createTempFile("outside", ".txt");
        outside.deleteOnExit();

        DirectoryMonitor monitor = new DirectoryMonitor(folder.getRoot(), f -> {
        });
        assertTrue(monitor.accepts(inRoot));
        assertFalse("mirror content is not the monitored resource", monitor.accepts(mirrored));
        assertFalse("paths outside the root are foreign", monitor.accepts(outside));
    }
}
