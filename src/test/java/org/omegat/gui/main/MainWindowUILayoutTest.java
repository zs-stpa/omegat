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

package org.omegat.gui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.Test;

/**
 * A stored window layout may reference panes this installation cannot
 * resolve (written by a build with an extra module, or a newer version).
 * Restoring used to abort on the first unknown key and threw the whole
 * layout away; the filter drops just the foreign panes and re-collapses
 * the container tree.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class MainWindowUILayoutTest {

    private static final Set<String> KNOWN = Set.of("NOTES", "EDITOR");

    private static String dockable(String key) {
        return "<Dockable><Key dockName=\"" + key + "\"/></Dockable>";
    }

    private static String filter(String xml) throws Exception {
        return new String(MainWindowUI.dropUnknownDockables(xml.getBytes(StandardCharsets.UTF_8),
                KNOWN::contains), StandardCharsets.UTF_8);
    }

    @Test
    public void testForeignPaneDroppedAndSplitCollapsed() throws Exception {
        String xml = "<DockingDesktop><DockingPanel><Split orientation=\"0\" location=\"0.7\">"
                + dockable("EDITOR") + dockable("ACTION_PANEL") + "</Split></DockingPanel></DockingDesktop>";
        String filtered = filter(xml);
        assertFalse("foreign pane must be gone", filtered.contains("ACTION_PANEL"));
        assertTrue("known pane survives", filtered.contains("EDITOR"));
        assertFalse("a split with one child collapses to the child", filtered.contains("<Split"));
    }

    @Test
    public void testTabGroupKeepsRemainingTab() throws Exception {
        String xml = "<DockingDesktop><DockingPanel><TabbedDockable>" + dockable("NOTES")
                + dockable("ACTION_PANEL") + "</TabbedDockable></DockingPanel></DockingDesktop>";
        String filtered = filter(xml);
        assertTrue(filtered.contains("TabbedDockable"));
        assertTrue(filtered.contains("NOTES"));
        assertFalse(filtered.contains("ACTION_PANEL"));
    }

    @Test
    public void testAutoHideAndFloatingReferencesDropped() throws Exception {
        String xml = "<DockingDesktop><DockingPanel>" + dockable("EDITOR") + "</DockingPanel>"
                + "<Border zone=\"1\">" + dockable("ACTION_PANEL") + "</Border>"
                + "<Floating>" + dockable("ACTION_PANEL") + "</Floating></DockingDesktop>";
        String filtered = filter(xml);
        assertFalse(filtered.contains("ACTION_PANEL"));
        assertFalse("an emptied floating entry is dropped entirely", filtered.contains("<Floating"));
        assertFalse("an emptied auto-hide border must not stay visible", filtered.contains("<Border"));
        assertTrue(filtered.contains("EDITOR"));
    }

    @Test
    public void testForeignMaximizedAndTabGroupEntriesDropped() throws Exception {
        String xml = "<DockingDesktop><DockingPanel>" + dockable("EDITOR")
                + "<MaximizedDockable><Key dockName=\"ACTION_PANEL\"/></MaximizedDockable>"
                + "</DockingPanel><TabGroups><TabGroup>"
                + "<Dockable><Key dockName=\"ACTION_PANEL\"/></Dockable>"
                + "</TabGroup></TabGroups></DockingDesktop>";
        String filtered = filter(xml);
        assertFalse(filtered.contains("ACTION_PANEL"));
        assertFalse("a foreign maximized entry is dropped whole",
                filtered.contains("MaximizedDockable"));
        assertFalse("an emptied tab group block is dropped", filtered.contains("TabGroup"));
        assertTrue(filtered.contains("EDITOR"));
    }

    @Test
    public void testLayoutWithoutForeignKeysPassesThroughUntouched() throws Exception {
        byte[] xml = ("<DockingDesktop><DockingPanel>" + dockable("NOTES") + "</DockingPanel></DockingDesktop>")
                .getBytes(StandardCharsets.UTF_8);
        assertSame("no rewrite when every key is known", xml,
                MainWindowUI.dropUnknownDockables(xml, KNOWN::contains));
    }

    @Test
    public void testNestedSplitsCollapseBottomUp() throws Exception {
        String xml = "<DockingDesktop><DockingPanel><Split orientation=\"0\">"
                + "<Split orientation=\"1\">" + dockable("ACTION_PANEL") + dockable("FOREIGN_TOO")
                + "</Split>" + dockable("NOTES") + "</Split></DockingPanel></DockingDesktop>";
        String filtered = filter(xml);
        assertFalse(filtered.contains("ACTION_PANEL"));
        assertFalse(filtered.contains("FOREIGN_TOO"));
        assertFalse("both splits collapse away", filtered.contains("<Split"));
        assertTrue(filtered.contains("NOTES"));
        assertEquals("exactly the one known dockable remains", 1,
                filtered.split("<Dockable", -1).length - 1);
    }
}
