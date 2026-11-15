/*
 *  OmegaT - Computer Assisted Translation (CAT) tool
 *           with fuzzy matching, translation memory, keyword search,
 *           glossaries, and translation leveraging into updated projects.
 *
 *  Copyright (C) 2024 Hiroshi Miura.
 *                Home page: https://www.omegat.org/
 *                Support center: https://omegat.org/support
 *
 *  This file is part of OmegaT.
 *
 *  OmegaT is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  OmegaT is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.omegat.gui.editor.mark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.omegat.core.Core;
import org.omegat.core.TestCoreInitializer;
import org.omegat.core.data.EntryKey;
import org.omegat.core.data.NotLoadedProject;
import org.omegat.core.data.ProjectProperties;
import org.omegat.core.data.ProtectedPart;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.util.Language;
import org.omegat.util.PatternConsts;
import org.omegat.util.TagUtil;

public class ProtectedPartsMarkerTest extends MarkerTestBase {

    @Before
    public void preUp() {
        TestCoreInitializer.initEditor(editor);
    }

    @Test
    public void testMarkerProtectedParts() throws Exception {
        IMarker marker = new ProtectedPartsMarker();
        String sourceText = "source %s text.";
        List<ProtectedPart> protectedParts = TagUtil.applyCustomProtectedParts(sourceText, PatternConsts.PRINTF_VARS,
                null);
        EntryKey key = new EntryKey("file", sourceText, "id", "prev", "next", "path");
        SourceTextEntry ste = new SourceTextEntry(key, 1, new String[0], sourceText, protectedParts);
        List<Mark> result = marker.getMarksForEntry(ste, sourceText, null, true);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(7, result.get(0).startOffset);
        assertEquals(9, result.get(0).endOffset);
        assertEquals("%s", result.get(0).toolTipText);
        assertEquals("SOURCE", result.get(0).entryPart.toString());
    }

    /**
     * In a project whose languages differ in direction the editor follows
     * the target. Tags of the target keep the foreground attribute (SF bug
     * #1122); tags of the source, which is embedded against the editor
     * orientation, keep the painter: a style run inside such an embedding
     * makes the spaces next to the tag change sides.
     */
    @Test
    public void testMarkerAttributesFollowEditorOrientation() throws Exception {
        setupProject(new Language("de"), new Language("ar"));
        try {
            IMarker marker = new ProtectedPartsMarker();
            String sourceText = "source %s text.";
            String translationText = "\u0646\u0635 %s";
            List<ProtectedPart> protectedParts = TagUtil.applyCustomProtectedParts(sourceText,
                    PatternConsts.PRINTF_VARS, null);
            EntryKey key = new EntryKey("file", sourceText, "id", "prev", "next", "path");
            SourceTextEntry ste = new SourceTextEntry(key, 1, new String[0], sourceText, protectedParts);

            List<Mark> result = marker.getMarksForEntry(ste, sourceText, translationText, true);

            assertNotNull(result);
            assertEquals(2, result.size());
            Mark source = result.get(0);
            Mark target = result.get(1);
            assertEquals(Mark.ENTRY_PART.SOURCE, source.entryPart);
            assertNull("Source against the editor orientation must not carry attributes", source.attributes);
            assertNotNull("Source against the editor orientation keeps the painter", source.painter);
            assertEquals(Mark.ENTRY_PART.TRANSLATION, target.entryPart);
            assertNotNull("Target tags must carry a foreground colour attribute", target.attributes);
            assertNull("Target tags must not fall back to a faint highlight painter", target.painter);
        } finally {
            Core.setProject(new NotLoadedProject());
        }
    }

    @Test
    public void testAttributesSafe() {
        // the target always follows the editor orientation
        assertTrue(ProtectedPartsMarker.attributesSafe(false, false, true));
        assertTrue(ProtectedPartsMarker.attributesSafe(false, true, false));
        // the source only when it shares the target direction
        assertTrue(ProtectedPartsMarker.attributesSafe(true, false, false));
        assertTrue(ProtectedPartsMarker.attributesSafe(true, true, true));
        assertFalse(ProtectedPartsMarker.attributesSafe(true, false, true));
        assertFalse(ProtectedPartsMarker.attributesSafe(true, true, false));
    }

    private static void setupProject(Language sourceLanguage, Language targetLanguage) {
        Core.setProject(new NotLoadedProject() {
            @Override
            public boolean isProjectLoaded() {
                return true;
            }

            @Override
            public ProjectProperties getProjectProperties() {
                return new ProjectProperties() {
                    @Override
                    public Language getSourceLanguage() {
                        return sourceLanguage;
                    }

                    @Override
                    public Language getTargetLanguage() {
                        return targetLanguage;
                    }
                };
            }
        });
    }
}
