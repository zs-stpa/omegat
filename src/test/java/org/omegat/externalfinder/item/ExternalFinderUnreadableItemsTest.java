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
package org.omegat.externalfinder.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.omegat.core.TestCore;
import org.omegat.externalfinder.item.ExternalFinderConfiguration.Entry;
import org.omegat.externalfinder.item.ExternalFinderItem.SCOPE;

/**
 * An item this OmegaT cannot read, say one written by a newer version with
 * placeholders this one does not know, survives loading and saving in place
 * and unchanged instead of being dropped.
 *
 * @author Stephan Pakebusch (stephan.pakebusch at zollsoft.de)
 */
public class ExternalFinderUnreadableItemsTest extends TestCore {

    /**
     * A placeholder no version knows: too new for this one, and unknown to a
     * version that reads placeholders by name. Either must keep the item.
     */
    private static final String FUTURE_URL = "shortcuts://run-shortcut?name=open&amp;text={future_token}";

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<items priority=\"40\">\n"
            + "    <item nopopup=\"true\">\n"
            + "        <name>Open in Xcode &amp; more</name>\n"
            + "        <url encoding=\"escape\">" + FUTURE_URL + "</url>\n"
            + "        <keystroke>shift ctrl X</keystroke>\n"
            + "    </item>\n"
            + "    <item>\n"
            + "        <name>Google</name>\n"
            + "        <url>https://www.google.com/search?q={target}</url>\n"
            + "    </item>\n"
            + "    <item>\n"
            + "        <name>Mixed</name>\n"
            + "        <url>https://example.com/{future_token}</url>\n"
            + "        <command>/usr/bin/open|dict://{target}</command>\n"
            + "    </item>\n"
            + "    <item/>\n"
            + "    <item>\n"
            + "        <name>Future keys</name>\n"
            + "        <url>https://example.com/?q={target}</url>\n"
            + "        <keystroke>hyper meta FUTURE_KEY</keystroke>\n"
            + "    </item>\n"
            + "</items>\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testUnreadableItemsSurviveInPlace() throws Exception {
        ExternalFinderConfiguration loaded = load(write("finder.xml", XML));
        assertEquals(40, loaded.getPriority());
        assertEquals(List.of("Google"), names(loaded.getItems()));
        List<String> kept = loaded.getUnreadableItems();
        assertEquals(4, kept.size());
        assertTrue(kept.get(0), kept.get(0).contains("Open in Xcode &amp; more"));
        // a single URL this version cannot read keeps the whole item as written
        assertTrue(kept.get(1), kept.get(1).contains("<name>Mixed</name>"));
        assertTrue(kept.get(1), kept.get(1).contains("dict://{target}"));
        assertTrue(kept.get(2), kept.get(2).contains("<item/>"));
        // a keystroke this version cannot parse keeps the whole item as well
        assertTrue(kept.get(3), kept.get(3).contains("hyper meta FUTURE_KEY"));
        assertFalse(loaded.isEmpty());

        File target = folder.newFile("finder-written.xml");
        new ExternalFinderXMLWriter(target).write(loaded);
        String written = read(target);
        assertTrue(written, written.contains(FUTURE_URL));
        assertTrue(written, written.contains("nopopup=\"true\""));
        assertTrue(written, written.contains("<keystroke>shift ctrl X</keystroke>"));
        assertTrue("file order is kept: " + written,
                written.indexOf("Open in Xcode") < written.indexOf("Google")
                        && written.indexOf("Google") < written.indexOf("Mixed"));

        // Saving again, and saving what a reload read, changes nothing.
        ExternalFinderConfiguration reloaded = load(target);
        assertEquals(loaded, reloaded);
        File again = folder.newFile("finder-again.xml");
        new ExternalFinderXMLWriter(again).write(reloaded);
        assertEquals(written, read(again));
    }

    @Test
    public void testEditedItemsKeepUnreadableOnesInPlace() throws Exception {
        ExternalFinderConfiguration loaded = load(write("finder.xml", XML));

        // What the preferences page produces after the user removed every
        // visible item: the invisible ones must still be there.
        ExternalFinderConfiguration cleared = loaded.withItems(10, Collections.emptyList());
        assertEquals(10, cleared.getPriority());
        assertTrue(cleared.getItems().isEmpty());
        assertEquals(loaded.getUnreadableItems(), cleared.getUnreadableItems());
        assertFalse("kept items are content, the project dialog must not delete the file", cleared.isEmpty());
        assertNotEquals(ExternalFinderConfiguration.empty(), cleared);

        // Saving the page untouched reproduces the file as it was.
        assertEquals(loaded, loaded.withItems(40, loaded.getItems()));

        // After every visible item was removed and one added again, the kept
        // ones are all still there; their place among the new items is best
        // effort.
        ExternalFinderConfiguration restored = cleared.withItems(40, loaded.getItems());
        assertEquals(loaded.getUnreadableItems(), restored.getUnreadableItems());
        assertEquals(List.of("Google"), names(restored.getItems()));
        assertEquals(5, restored.getEntries().size());
    }

    @Test
    public void testPlainConfigurationHasNoKeptEntries() throws Exception {
        ExternalFinderConfiguration plain = new ExternalFinderConfiguration(50, Collections.emptyList());
        assertTrue(plain.isEmpty());
        assertTrue(plain.getUnreadableItems().isEmpty());
        assertEquals(ExternalFinderConfiguration.empty(), plain);
        Entry kept = Entry.kept("<item><name>x</name></item>");
        assertTrue(kept.isKept());
        assertEquals(kept, Entry.kept("<item><name>x</name></item>"));
    }

    private File write(String name, String content) throws Exception {
        File file = folder.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static ExternalFinderConfiguration load(File file) throws Exception {
        return new ExternalFinderXMLLoader(file, SCOPE.GLOBAL).load();
    }

    private static List<String> names(List<ExternalFinderItem> items) {
        return items.stream().map(ExternalFinderItem::getName).collect(Collectors.toList());
    }
}
