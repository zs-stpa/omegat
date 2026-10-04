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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.gui.actionpanel.PanelPackage.Kind;
import org.omegat.gui.actionpanel.PanelPackage.Resource;
import org.omegat.util.TestPreferencesInitializer;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class PanelPackageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        TestPreferencesInitializer.init();
    }

    @Test
    public void testReferencedResourcesAndShippedScripts() throws Exception {
        File shipped = folder.newFolder("shipped");
        Files.writeString(new File(shipped, "shipped.groovy").toPath(), "// shipped", StandardCharsets.UTF_8);
        // Shipped means: same name in the installation AND the file lives in
        // the scripts folder; a same-named script elsewhere is the user's own.
        File elsewhere = new File(folder.newFolder("elsewhere"), "shipped.groovy");
        Files.writeString(elsewhere.toPath(), "// mine", StandardCharsets.UTF_8);
        File icon = folder.newFile("star.png");
        List<ActionRow> rows = List.of(
                new ActionRow("search", null, new ActionSpec.ExternalSearchActionSpec("Open in Xcode")),
                new ActionRow("own", null, new ActionSpec.ScriptActionSpec("mine.groovy")),
                new ActionRow("shipped", null, new ActionSpec.ScriptActionSpec("shipped.groovy")),
                new ActionRow("scheme", icon.getAbsolutePath(), new ActionSpec.ColorSchemeActionSpec("dusk")),
                new ActionRow("autotext", null, new ActionSpec.AutotextRefActionSpec("mfg")),
                new ActionRow("again", icon.getAbsolutePath(), new ActionSpec.SnippetActionSpec("x")),
                new ActionRow("own copy", null, new ActionSpec.ScriptActionSpec(elsewhere.getAbsolutePath())));
        List<Resource> resources = PanelPackage.referencedResources(rows, shipped);
        List<String> summary = new ArrayList<>();
        for (Resource r : resources) {
            summary.add(r.kind() + ":" + r.name() + (r.shipped() ? ":shipped" : "") + (r.packable() ? "" : ":info"));
        }
        assertEquals(List.of("EXTERNAL_SEARCH:Open in Xcode", "SCRIPT:mine.groovy", "SCRIPT:shipped.groovy:shipped",
                "COLOR_SCHEME:dusk.properties", "ICON:star.png", "AUTOTEXT:mfg:info", "SCRIPT:shipped-2.groovy"),
                summary);
    }

    @Test
    public void testEveryActionTypeIsClassifiedAsResourceOrNot() throws Exception {
        List<ActionSpec> samples = SampleSpecs.all();
        SampleSpecs.assertCoversAllTypes(samples);
        Set<Class<?>> withResource = Set.of(ActionSpec.ExternalSearchActionSpec.class,
                ActionSpec.ScriptActionSpec.class, ActionSpec.ColorSchemeActionSpec.class,
                ActionSpec.ShortcutSetActionSpec.class, ActionSpec.AutotextRefActionSpec.class);
        for (ActionSpec sample : samples) {
            List<Resource> resources = PanelPackage.referencedResources(
                    List.of(new ActionRow("row", null, sample)), folder.newFolder());
            assertEquals(sample.type(), withResource.contains(sample.getClass()), !resources.isEmpty());
        }
    }

    @Test
    public void testSameNameDifferentFilesGetDistinctPackedNames() throws Exception {
        File a = folder.newFolder("a");
        File b = folder.newFolder("b");
        File iconA = new File(a, "star.png");
        File iconB = new File(b, "star.png");
        Files.write(iconA.toPath(), new byte[] { 1 });
        Files.write(iconB.toPath(), new byte[] { 2 });
        List<ActionRow> rows = List.of(new ActionRow("one", iconA.getAbsolutePath(), null),
                new ActionRow("two", iconB.getAbsolutePath(), null),
                new ActionRow("one again", iconA.getAbsolutePath(), null));
        List<Resource> resources = PanelPackage.referencedResources(rows, folder.newFolder("none"));
        assertEquals(List.of("star.png", "star-2.png"), resources.stream().map(Resource::name).toList());

        File target = new File(folder.getRoot(), "icons." + PanelPackage.EXTENSION);
        PanelPackage.write(target, rows, resources);
        PanelPackage.Contents contents = PanelPackage.read(target);
        assertEquals(ActionPanelConfig.ICON_FOLDER + "/star.png", contents.rows().get(0).iconRef());
        assertEquals(ActionPanelConfig.ICON_FOLDER + "/star-2.png", contents.rows().get(1).iconRef());
        assertEquals(ActionPanelConfig.ICON_FOLDER + "/star.png", contents.rows().get(2).iconRef());
        assertEquals(2, contents.entries().get(1).data()[0]);
    }

    @Test
    public void testUnsafeEntryNames() {
        assertTrue(PanelPackage.isSafeName("scripts/a..b.groovy"));
        assertFalse(PanelPackage.isSafeName("scripts/../x"));
        assertFalse(PanelPackage.isSafeName("/etc/passwd"));
    }

    @Test
    public void testPackageRoundTripRewritesIconsAndCarriesFiles() throws Exception {
        File icon = folder.newFile("star.png");
        Files.write(icon.toPath(), new byte[] { 1, 2, 3 });
        File script = folder.newFile("mine.groovy");
        Files.writeString(script.toPath(), "println 1", StandardCharsets.UTF_8);
        List<ActionRow> rows = List.of(
                new ActionRow("run", icon.getAbsolutePath(), new ActionSpec.ScriptActionSpec(script.getAbsolutePath())),
                new ActionRow("plain", null, new ActionSpec.SnippetActionSpec("hi")));
        List<Resource> resources = PanelPackage.referencedResources(rows, folder.newFolder("none"));
        assertEquals(2, resources.size());
        File target = new File(folder.getRoot(), "panel." + PanelPackage.EXTENSION);
        PanelPackage.write(target, rows, resources);

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(target.toPath()))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                entries.add(e.getName());
            }
        }
        assertEquals(List.of(PanelPackage.ROWS_ENTRY, "scripts/mine.groovy", "icons/star.png"), entries);

        PanelPackage.Contents contents = PanelPackage.read(target);
        assertEquals(2, contents.rows().size());
        assertEquals(ActionPanelConfig.ICON_FOLDER + "/star.png", contents.rows().get(0).iconRef());
        // The absolute script path is rewritten to the packed name, which the
        // importing machine resolves in its scripts folder.
        assertEquals(new ActionSpec.ScriptActionSpec("mine.groovy"), contents.rows().get(0).action());
        assertEquals(2, contents.entries().size());
        assertEquals(Kind.SCRIPT, contents.entries().get(0).kind());
        assertEquals("println 1", new String(contents.entries().get(0).data(), StandardCharsets.UTF_8));
        // The script is carried, the icon too: nothing unresolved.
        assertTrue(contents.unresolved().isEmpty());
    }

    @Test
    public void testPlainXmlStillReadsAndReportsUnresolved() throws Exception {
        List<ActionRow> rows = List.of(
                new ActionRow("scheme", null, new ActionSpec.ColorSchemeActionSpec("no-such-scheme")),
                new ActionRow("menu", null, new ActionSpec.MenuActionSpec("projectNewMenuItem")));
        File xml = new File(folder.getRoot(), "old-export.xml");
        ActionPanelXML.write(rows, xml);
        PanelPackage.Contents contents = PanelPackage.read(xml);
        assertEquals(rows, contents.rows());
        assertTrue(contents.entries().isEmpty());
        assertEquals(1, contents.unresolved().size());
        assertEquals(Kind.COLOR_SCHEME, contents.unresolved().get(0).kind());
        assertFalse(contents.unresolved().get(0).present());
    }
}
