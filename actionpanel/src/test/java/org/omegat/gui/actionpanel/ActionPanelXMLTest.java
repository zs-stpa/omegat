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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.omegat.gui.actionpanel.ActionSpec.UnknownActionSpec;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionPanelXMLTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testRoundTripAllSpecTypes() throws IOException {
        List<ActionRow> rows = List.of(
                new ActionRow("menu", null, new ActionSpec.MenuActionSpec("projectNewMenuItem")),
                new ActionRow("editor", "actionpanel/icons/e.png",
                        new ActionSpec.EditorKeyActionSpec("editorNextSegment")),
                new ActionRow("script", null, new ActionSpec.ScriptActionSpec("hello.groovy")),
                new ActionRow("search", null, new ActionSpec.SearchActionSpec("TODO", true,
                        Map.of("search_window_case_sensitive", "true"))),
                new ActionRow("pref", null, new ActionSpec.PreferenceActionSpec("source_font_size",
                        "slider", 8, 32, List.of())),
                new ActionRow("colored", "actionpanel/icons/c.png",
                        new ActionSpec.SnippetActionSpec("Hi"), "#ff0000", "#00ff00", "#0000ff"),
                new ActionRow("snippet", null, new ActionSpec.SnippetActionSpec("Best regards\nMe")),
                new ActionRow("autotext", null, new ActionSpec.AutotextRefActionSpec("mfg")),
                new ActionRow("scheme", null, new ActionSpec.ColorSchemeActionSpec("dark-red")),
                new ActionRow("set", "/tmp/icon.svg", new ActionSpec.ShortcutSetActionSpec("vimlike")),
                new ActionRow("url", null, new ActionSpec.UrlActionSpec("https://omegat.org/support")),
                new ActionRow("empty", null, null));
        File file = new File(folder.getRoot(), "actionpanel.xml");
        ActionPanelXML.write(rows, file);
        List<ActionRow> read = ActionPanelXML.read(file);
        assertEquals(rows, read);
    }

    @Test
    public void testUnknownTypeSurvivesRoundTrip() throws IOException {
        File file = new File(folder.getRoot(), "actionpanel.xml");
        Files.writeString(file.toPath(),
                "<actionpanel version=\"99\">"
                        + "<row name=\"future\"><action type=\"hologram\" beam=\"wide\"/></row>"
                        + "<unknownelement/></actionpanel>",
                StandardCharsets.UTF_8);
        List<ActionRow> read = ActionPanelXML.read(file);
        assertEquals(1, read.size());
        UnknownActionSpec spec = (UnknownActionSpec) read.get(0).action();
        assertEquals("hologram", spec.type());
        assertEquals(Map.of("beam", "wide"), spec.attributes());

        ActionPanelXML.write(read, file);
        List<ActionRow> reread = ActionPanelXML.read(file);
        assertEquals(read, reread);
    }

    @Test
    public void testMissingActionAndIcon() throws IOException {
        File file = new File(folder.getRoot(), "actionpanel.xml");
        Files.writeString(file.toPath(), "<actionpanel version=\"1\"><row name=\"bare\"/></actionpanel>",
                StandardCharsets.UTF_8);
        List<ActionRow> read = ActionPanelXML.read(file);
        assertEquals(1, read.size());
        assertEquals("bare", read.get(0).name());
        assertNull(read.get(0).iconRef());
        assertNull(read.get(0).action());
    }

    @Test
    public void testDoctypeIsRejected() throws IOException {
        File file = new File(folder.getRoot(), "evil.xml");
        Files.writeString(file.toPath(),
                "<!DOCTYPE actionpanel [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                        + "<actionpanel><row name=\"&x;\"/></actionpanel>",
                StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ActionPanelXML.read(file));
    }

    @Test
    public void testWriteCreatesParentAndLeavesNoTempFile() throws IOException {
        File file = new File(folder.getRoot(), "sub/dir/actionpanel.xml");
        ActionPanelXML.write(List.of(new ActionRow("a", null, null)), file);
        assertTrue(file.isFile());
        String[] siblings = file.getParentFile().list();
        assertEquals(1, siblings == null ? 0 : siblings.length);
    }
}
