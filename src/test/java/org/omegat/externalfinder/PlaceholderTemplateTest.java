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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.Test;

import org.omegat.core.data.EntryKey;
import org.omegat.core.data.PrepareTMXEntry;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.data.TMXEntry;
import org.omegat.core.data.TestTMXEntries;
import org.omegat.externalfinder.item.ExternalFinderItem;
import org.omegat.externalfinder.item.ExternalFinderValidationException;
import org.omegat.externalfinder.item.PlaceholderTemplate;
import org.omegat.externalfinder.item.PlaceholderTemplate.Context;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class PlaceholderTemplateTest {

    private static final UnaryOperator<String> RAW = s -> s;

    private static Context entryContext(String selection) {
        EntryKey key = new EntryKey("Strings.xlf", "Hello", "greeting", null, null, "/body/unit[1]");
        String[] props = { "note", "path=/src/A.m&line=12", "note", "second note", "resname", "GREETING" };
        SourceTextEntry entry = new SourceTextEntry(key, 7, props, null, Collections.emptyList());
        PrepareTMXEntry prepared = new PrepareTMXEntry();
        prepared.source = "Hello";
        prepared.translation = "Hallo";
        prepared.note = "check tone";
        prepared.changer = "alice";
        prepared.changeDate = 1767265445000L;
        prepared.creator = "bob";
        prepared.creationDate = 1767225600000L;
        TMXEntry translation = TestTMXEntries.create(prepared, false, TMXEntry.ExternalLinked.x100PC);
        return Context.of(selection, entry, translation);
    }

    @Test
    public void testTranslationRecordPlaceholders() {
        String template = "{source}|{translation}|{note}|{path}|{number}|{status}|{link}|{origin}|{author}|{date}"
                + "|{creator}|{created}|{duplicate}|{duplicates}|{alternative}|{paragraph}";
        assertEquals("Hello|Hallo|check tone|/body/unit[1]|7|translated|100pc||alice|2026-01-01T11:04:05Z"
                + "|bob|2026-01-01T00:00:00Z|none|0|true|true", PlaceholderTemplate.parse(template)
                        .resolve(entryContext(null), RAW));
    }

    @Test
    public void testEveryNameHasASampleAndARecordValue() {
        Context full = entryContext("sel");
        for (String name : PlaceholderTemplate.NAMES) {
            if (PlaceholderTemplate.PROP.equals(name)) {
                continue;
            }
            PlaceholderTemplate template = PlaceholderTemplate.parse("{" + name + "}");
            assertFalse("sample " + name, template.resolve(Context.sample(false), RAW).isEmpty());
            if (!PlaceholderTemplate.ORIGIN.equals(name)) {
                // origin travels as a TMX property; the record here has none.
                assertFalse("record " + name, template.resolve(full, RAW).isEmpty());
            }
        }
    }

    @Test
    public void testLinkNamesDropTheirPrefix() {
        for (TMXEntry.ExternalLinked linked : TMXEntry.ExternalLinked.values()) {
            assertTrue(linked.name(), linked.name().startsWith("x"));
        }
    }

    @Test
    public void testUntranslatedRecord() {
        EntryKey key = new EntryKey("a.txt", "Hello", null, null, null, null);
        SourceTextEntry entry = new SourceTextEntry(key, 1, null, null, Collections.emptyList());
        PrepareTMXEntry empty = new PrepareTMXEntry();
        empty.source = "Hello";
        TMXEntry untranslated = TestTMXEntries.create(empty, true, null);
        Context context = Context.of(null, entry, untranslated);
        assertEquals("|untranslated|||false",
                PlaceholderTemplate.parse("{translation}|{status}|{link}|{date}|{alternative}").resolve(context, RAW));
        assertEquals("||", PlaceholderTemplate.parse("{translation}|{status}|{id}").resolve(
                Context.of(null, entry, null), RAW));
    }

    @Test
    public void testLiteralOnly() {
        PlaceholderTemplate template = PlaceholderTemplate.parse("https://example.com/{notAPlaceholder");
        assertFalse(template.hasPlaceholders());
        assertEquals("https://example.com/{notAPlaceholder", template.resolve(entryContext("x"), RAW));
    }

    @Test
    public void testTarget() {
        PlaceholderTemplate template = PlaceholderTemplate.parse("https://e.com/?q={target}&x=1");
        assertTrue(template.usesSelection());
        assertEquals("https://e.com/?q=foo+bar&x=1",
                template.resolve(Context.ofSelection("foo bar"), ExternalFinderItem.ENCODING.DEFAULT::apply));
        assertEquals("https://e.com/?q=foo%20bar&x=1",
                template.resolve(Context.ofSelection("foo bar"), ExternalFinderItem.ENCODING.ESCAPE::apply));
    }

    @Test
    public void testEntryPlaceholders() {
        PlaceholderTemplate template = PlaceholderTemplate.parse("{file}|{id}|{comment}|{prop:resname}");
        assertFalse(template.usesSelection());
        assertEquals("Strings.xlf|greeting|path=/src/A.m&line=12\nsecond note\nGREETING|GREETING",
                template.resolve(entryContext(null), RAW));
    }

    @Test
    public void testMissingValuesAreEmpty() {
        PlaceholderTemplate template = PlaceholderTemplate.parse("[{comment}][{prop:nope}][{target}]");
        assertEquals("[][][]", template.resolve(Context.of(null, null, null), RAW));
        assertEquals("[][][]",
                template.resolve(Context.of(null, null, null), ExternalFinderItem.ENCODING.DEFAULT::apply));
    }

    @Test
    public void testRegexGroupAndWholeMatch() {
        assertEquals("12", PlaceholderTemplate.parse("{comment:{line=(\\d+)}}").resolve(entryContext(null), RAW));
        assertEquals("line=12",
                PlaceholderTemplate.parse("{comment:{line=\\d+}}").resolve(entryContext(null), RAW));
        assertEquals("/src/A.m",
                PlaceholderTemplate.parse("{prop:note:{path=([^&]+)}}").resolve(entryContext(null), RAW));
        assertEquals("", PlaceholderTemplate.parse("{comment:{nomatch}}").resolve(entryContext(null), RAW));
    }

    @Test
    public void testRegexWithBracesAndEscapes() {
        assertEquals("12", PlaceholderTemplate.parse("{comment:{line=(\\d{1,3})}}").resolve(entryContext(null), RAW));
        assertEquals("", PlaceholderTemplate.parse("{comment:{\\{x\\}}}").resolve(entryContext(null), RAW));
        assertEquals("GREETING", PlaceholderTemplate.parse("{prop:resname:{[A-Z]+}}").resolve(entryContext(null), RAW));
    }

    @Test
    public void testRegexDoesNotSplitArguments() {
        PlaceholderTemplate template = PlaceholderTemplate.parse("/usr/bin/open|x://{comment:{(a|path)=(\\S+)}}|{id}");
        List<String> arguments = template.resolveSplit(entryContext(null), RAW, "|");
        assertEquals(List.of("/usr/bin/open", "x://path", "greeting"), arguments);
    }

    @Test
    public void testSplitKeepsEmptyMiddleDropsTrailing() {
        assertEquals(List.of("a", "", "b"),
                PlaceholderTemplate.parse("a||b|{comment:{nomatch}}").resolveSplit(entryContext(null), RAW, "|"));
    }

    @Test
    public void testSelectionDetectionOnBrokenTemplate() {
        assertTrue(PlaceholderTemplate.usesSelection("{comment:{(}}"));
        assertFalse(PlaceholderTemplate.usesSelection("{comment}"));
        assertTrue(PlaceholderTemplate.usesSelection("{target}"));
    }

    @Test
    public void testErrors() {
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{bogus}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{prop}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{prop:}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{target:x}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{comment:{(}}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{comment:{a}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{comment:x}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{prop:a:b}"));
        assertThrows(ExternalFinderValidationException.class, () -> PlaceholderTemplate.parse("{prop:a:}"));
    }
}
