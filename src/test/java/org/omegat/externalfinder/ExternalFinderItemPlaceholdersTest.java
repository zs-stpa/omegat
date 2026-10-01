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

package org.omegat.externalfinder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import org.omegat.core.data.EntryKey;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.externalfinder.item.ExternalFinderItem;
import org.omegat.externalfinder.item.ExternalFinderItemCommand;
import org.omegat.externalfinder.item.ExternalFinderItemURL;
import org.omegat.externalfinder.item.ExternalFinderValidationException;
import org.omegat.externalfinder.item.PlaceholderTemplate.Context;

/**
 * URL and command items with placeholders beyond {target}.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class ExternalFinderItemPlaceholdersTest {

    private static Context context() {
        EntryKey key = new EntryKey("Strings.xlf", "Hello", "greeting", null, null, null);
        String[] props = { "note", "path=/src/A.m&line=12" };
        return Context.of(null, new SourceTextEntry(key, 1, props, null, Collections.emptyList()));
    }

    @Test
    public void testUrlWithoutTargetValidatesAndRuns() throws Exception {
        ExternalFinderItemURL.Builder builder = new ExternalFinderItemURL.Builder()
                .setURL("shortcuts://run-shortcut?name=open&input=text&text={comment}");
        assertEquals("shortcuts://run-shortcut?name=open&input=text&text=Sample+comment",
                builder.validate().toString());
        ExternalFinderItemURL url = builder.build();
        assertFalse(url.usesSelection());
        assertEquals("shortcuts://run-shortcut?name=open&input=text&text=path%3D%2Fsrc%2FA.m%26line%3D12",
                url.generateURL(context()).toString());
        // Old entry point keeps working for plugins.
        assertEquals("shortcuts://run-shortcut?name=open&input=text&text=",
                url.generateURL("ignored").toString());
    }

    @Test
    public void testUrlRequiresAPlaceholder() {
        ExternalFinderItemURL.Builder builder = new ExternalFinderItemURL.Builder().setURL("https://e.com/");
        assertThrows(ExternalFinderValidationException.class, builder::validate);
        builder.setURL("https://e.com/?q={bogus}");
        assertThrows(ExternalFinderValidationException.class, builder::validate);
    }

    @Test
    public void testItemUsesSelection() {
        ExternalFinderItemURL target = new ExternalFinderItemURL.Builder().setURL("https://e.com/?q={target}").build();
        ExternalFinderItemURL comment = new ExternalFinderItemURL.Builder().setURL("https://e.com/?q={comment}")
                .build();
        assertTrue(item("a").addURL(target).build().usesSelection());
        assertFalse(item("b").addURL(comment).build().usesSelection());
        assertTrue(item("c").addURL(comment).addURL(target).build().usesSelection());
    }

    private static ExternalFinderItem.Builder item(String name) {
        return new ExternalFinderItem.Builder().setName(name).setScope(ExternalFinderItem.SCOPE.GLOBAL);
    }

    @Test
    public void testCommandWithRegexContainingDelimiter() {
        ExternalFinderItemCommand command = new ExternalFinderItemCommand.Builder()
                .setCommand("/usr/bin/open|x://{comment:{(a|path)=(\\S+)}}|{id}").build();
        assertFalse(command.usesSelection());
        assertArrayEquals(new String[] { "/usr/bin/open", "x://path", "greeting" },
                command.generateCommand(context()));
        assertArrayEquals(new String[] { "/usr/bin/open", "x://", "sample-id" },
                new ExternalFinderItemCommand.Builder()
                        .setCommand("/usr/bin/open|x://{comment:{(a|path)=(\\S+)}}|{id}").validate());
    }

    @Test
    public void testCommandEncodingAppliesToEveryPlaceholder() throws Exception {
        ExternalFinderItemCommand command = new ExternalFinderItemCommand.Builder()
                .setCommand("/usr/bin/open|dict://{target} {comment}")
                .setEncoding(ExternalFinderItem.ENCODING.DEFAULT).build();
        assertArrayEquals(new String[] { "/usr/bin/open", "dict://foo+bar " },
                command.generateCommand("foo bar"));
    }
}
