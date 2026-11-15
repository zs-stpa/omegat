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
package org.omegat.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.omegat.core.Core;
import org.omegat.core.TestCore;
import org.omegat.core.data.NotLoadedProject;
import org.omegat.core.data.ProjectProperties;
import org.omegat.core.data.RealProject;
import org.omegat.core.data.TestCoreState;
import org.omegat.core.segmentation.SRX;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.filters2.text.TextFilter;
import org.omegat.util.DirectoryMonitor;
import org.omegat.util.Language;

/**
 * The stats command must close the project on every path. The project's
 * directory monitors are non-daemon threads and Main does not force an exit
 * on success, so an open project keeps the JVM alive after the statistics
 * have been written.
 *
 * @author Stephan Pakebusch (stephan.pakebusch at zollsoft.de)
 */
public class StatsCommandTest extends TestCore {

    private File projectRoot;
    private ProjectProperties props;
    private RealProject project;

    @Before
    public final void setUpProject() throws Exception {
        Core.setSegmenter(new Segmenter(SRX.getDefault()));
        TestCoreState.initFilters(List.of(TextFilter.class));

        projectRoot = Files.createTempDirectory("omegat-stats").toFile();
        props = new ProjectProperties(projectRoot);
        props.setSourceLanguage(new Language("en"));
        props.setTargetLanguage(new Language("de"));
        props.setSentenceSegmentingEnabled(true);
        props.setSupportDefaultTranslations(true);
        props.setProjectFilters(FilterMaster.createDefaultFiltersConfig());
        props.autocreateDirectories();
        Files.write(new File(props.getSourceRoot(), "source.txt").toPath(),
                "First sentence.\n\nSecond sentence.\n".getBytes(StandardCharsets.UTF_8));

        project = new RealProject(props);
        Core.setProject(project);
        project.loadProject(false);
        assertTrue("fixture project must load", project.isProjectLoaded());
    }

    @After
    public final void tearDownProject() throws Exception {
        if (project.isProjectLoaded()) {
            project.closeProject();
        }
        Core.setProject(new NotLoadedProject());
        FileUtils.deleteDirectory(projectRoot);
    }

    @Test
    public void testOutputFileClosesProject() throws Exception {
        File statsFile = new File(projectRoot, "stats.json");
        StatsCommand command = new StatsCommand();
        command.format = "json";
        command.output = statsFile.getAbsolutePath();

        assertEquals(0, command.reportStats(project));

        assertTrue("statistics must be written", statsFile.isFile());
        assertFalse("project must be closed after writing the file", project.isProjectLoaded());
        assertMonitorsStopped();
    }

    @Test
    public void testConsoleOutputClosesProject() throws Exception {
        StatsCommand command = new StatsCommand();
        command.output = null;

        assertEquals(0, command.reportStats(project));

        assertFalse("project must be closed after printing", project.isProjectLoaded());
        assertMonitorsStopped();
    }

    @Test
    public void testFailureClosesProject() throws Exception {
        StatsCommand command = new StatsCommand();
        command.format = "bogus";
        command.output = new File(projectRoot, "stats.json").getAbsolutePath();

        try {
            command.reportStats(project);
            fail("unknown format must not pass silently");
        } catch (RuntimeException expected) {
            // thrown by the writer selection; the command's caller logs it
        }

        assertFalse("project must be closed even when reporting fails", project.isProjectLoaded());
        assertMonitorsStopped();
    }

    @Test
    public void testUnloadedProjectIsReported() {
        assertTrue("fixture must still be open", project.isProjectLoaded());
        RealProject unloaded = new RealProject(props);
        StatsCommand command = new StatsCommand();
        command.output = new File(projectRoot, "stats.json").getAbsolutePath();

        assertEquals(1, command.reportStats(unloaded));
    }

    /**
     * The monitors this project started must be gone again. Other test classes
     * in the same JVM may leave monitors behind, so only count those watching
     * a folder below this test's project root.
     */
    private void assertMonitorsStopped() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (liveProjectMonitors() == 0) {
                return;
            }
            Thread.sleep(20);
        }
        assertEquals("directory monitors of the closed project are still alive", 0, liveProjectMonitors());
    }

    private long liveProjectMonitors() {
        String root = projectRoot.getAbsolutePath() + File.separator;
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t instanceof DirectoryMonitor && t.isAlive())
                .filter(t -> ((DirectoryMonitor) t).getDir().getAbsolutePath().startsWith(root)).count();
    }
}
