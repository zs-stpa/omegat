/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2008 Alex Buloichik
               2012 Didier Briel
               2015 Aaron Madlon-Kay
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.awt.ComponentOrientation;
import java.awt.font.TextAttribute;
import java.util.Locale;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;

import org.omegat.core.Core;
import org.omegat.core.data.NotLoadedProject;
import org.omegat.core.data.ProjectProperties;

public class BiDiUtilsTest {
    final Language LTR_LANGUAGE = new Language("pl");
    final Language RTL_LANGUAGE = new Language("ar");
    final Locale LTR_LOCALE = LTR_LANGUAGE.getLocale();
    final Locale RTL_LOCALE = RTL_LANGUAGE.getLocale();
    final Locale INITIAL_LOCALE = Locale.getDefault();

    @After
    public void resetInitialLocale() {
        Locale.setDefault(INITIAL_LOCALE);
    }

    @Test
    public void testAlignParagraphsToText() throws Exception {
        String[] paragraphs = { "Kommentar:", // label in the UI language
                "\u0646\u0635 \u0639\u0631\u0628\u064a %@", // Arabic with a tag
                "Siehe \u0646\u0635 im Original.", // German quoting an Arabic word
                "\u0661\u0662 - %@", // digits and neutrals only
                "\uD83A\uDD00\uD83A\uDD01", // Adlam, outside the BMP
                "" }; // empty
        // null: no strong character, the paragraph follows the document
        Boolean[] ltr = { true, false, true, null, false, null };

        for (boolean docLtr : new boolean[] { true, false }) {
            DefaultStyledDocument doc = new DefaultStyledDocument();
            doc.putProperty(TextAttribute.RUN_DIRECTION,
                    docLtr ? TextAttribute.RUN_DIRECTION_LTR : TextAttribute.RUN_DIRECTION_RTL);
            doc.insertString(0, String.join("\n", paragraphs), null);

            BiDiUtils.alignParagraphsToText(doc);

            Element root = doc.getDefaultRootElement();
            assertEquals(paragraphs.length, root.getElementCount());
            String[] lines = doc.getText(0, doc.getLength()).split("\n", -1);
            for (int i = 0; i < paragraphs.length; i++) {
                String where = "paragraph " + i + " in " + (docLtr ? "LTR" : "RTL") + " document";
                boolean expectLtr = ltr[i] == null ? docLtr : ltr[i];
                assertEquals(where, expectLtr ? StyleConstants.ALIGN_LEFT : StyleConstants.ALIGN_RIGHT,
                        StyleConstants.getAlignment(root.getElement(i).getAttributes()));
                String expectedLine = expectLtr == docLtr ? paragraphs[i]
                        : (expectLtr ? BiDiUtils.BIDI_LRI : BiDiUtils.BIDI_RLI) + paragraphs[i] + BiDiUtils.BIDI_PDI;
                assertEquals(where, expectedLine, lines[i]);
            }
        }

        // a single paragraph without a final newline, against the document
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.putProperty(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        doc.insertString(0, "Hallo.", null);
        BiDiUtils.alignParagraphsToText(doc);
        assertEquals(BiDiUtils.BIDI_LRI + "Hallo." + BiDiUtils.BIDI_PDI, doc.getText(0, doc.getLength()));
    }

    @Test
    public void testGetOrientationType_noProjectLocaleLtr_allLtr() {
        Locale.setDefault(LTR_LOCALE);
        setupProjectNotLoaded();
        assertEquals(BiDiUtils.ORIENTATION.ALL_LTR, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_noProjectLocaleRtl_allRtl() {
        Locale.setDefault(RTL_LOCALE);
        setupProjectNotLoaded();
        assertEquals(BiDiUtils.ORIENTATION.ALL_RTL, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_allLtrProjectAndRtlLocale_differ() {
        Locale.setDefault(RTL_LOCALE);
        setupAllLtrProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_allRtlProjectAndLtrLocale_differ() {
        Locale.setDefault(Locale.ENGLISH);
        setupAllRtlProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_allLtrProjectAndLtrLocale_allLtr() {
        Locale.setDefault(LTR_LOCALE);
        setupAllLtrProject();
        assertEquals(BiDiUtils.ORIENTATION.ALL_LTR, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_allRtlProjectAndRtlLocale_allRtl() {
        Locale.setDefault(RTL_LOCALE);
        setupAllRtlProject();
        assertEquals(BiDiUtils.ORIENTATION.ALL_RTL, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_ltrToRtlProjectAndLtrLocale_differ() {
        Locale.setDefault(LTR_LOCALE);
        setupLtrToRtlProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_ltrToRtlProjectAndRtlLocale_differ() {
        Locale.setDefault(RTL_LOCALE);
        setupLtrToRtlProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_rtlToLtrProjectAndLtrLocale_differ() {
        Locale.setDefault(LTR_LOCALE);
        setupRtlToLtrProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetOrientationType_rtlToLtrProjectAndRtlLocale_differ() {
        Locale.setDefault(RTL_LOCALE);
        setupRtlToLtrProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
    }

    @Test
    public void testGetInitialOrientation_notNull() {
        Locale.setDefault(RTL_LOCALE);
        setupRtlToLtrProject();
        assertNotNull(BiDiUtils.getInitialOrientation());
    }

    @Test
    public void testGetOrientation_nullParam_notNull() {
        Locale.setDefault(RTL_LOCALE);
        setupRtlToLtrProject();
        assertNotNull(BiDiUtils.getOrientation(null));
    }

    @Test
    public void testGetOrientation_allLtrTargetIsLtr_Ltr() {
        setupAllLtrProject();
        ComponentOrientation orientation = BiDiUtils.getOrientation(BiDiUtils.ORIENTATION.ALL_LTR);
        assertEquals(ComponentOrientation.LEFT_TO_RIGHT, orientation);
    }

    @Test
    public void testGetOrientation_allRtlTargetIsRtl_Rtl() {
        setupAllRtlProject();
        ComponentOrientation orientation = BiDiUtils.getOrientation(BiDiUtils.ORIENTATION.ALL_RTL);
        assertEquals(ComponentOrientation.RIGHT_TO_LEFT, orientation);
    }

    @Test
    public void testAddRtlBidiAround() {
        String input = "everything";
        String output = BiDiUtils.addRtlBidiAround(input);
        assertEquals("\u202beverything\u202c", output);
    }

    @Test
    public void testAddLtrBidiAround() {
        String input = "everything";
        String output = BiDiUtils.addLtrBidiAround(input);
        assertEquals("\u202aeverything\u202c", output);
    }

    @Test
    public void testIsSourceLangRtl_RtlSource_true() {
        setupRtlToLtrProject();
        assertTrue(BiDiUtils.isSourceLangRtl());
    }

    @Test
    public void testIsSourceLangRtl_LtrSource_false() {
        setupLtrToRtlProject();
        assertFalse(BiDiUtils.isSourceLangRtl());
    }

    @Test
    public void testIsTargetLangRtl_RtlTarget_true() {
        setupLtrToRtlProject();
        assertTrue(BiDiUtils.isTargetLangRtl());
    }

    @Test
    public void testIsTargetLangRtl_LtrTarget_false() {
        setupRtlToLtrProject();
        assertFalse(BiDiUtils.isTargetLangRtl());
    }

    @Test
    public void testIsLocaleRtl_RtlLocale_true() {
        Locale.setDefault(RTL_LOCALE);
        assertTrue(BiDiUtils.isLocaleRtl());
    }

    @Test
    public void testIsLocaleRtl_LtrLocale_false() {
        Locale.setDefault(LTR_LOCALE);
        assertFalse(BiDiUtils.isLocaleRtl());
    }

    @Test
    public void testIsRtl_RtlLocale_true() {
        assertTrue(BiDiUtils.isRtl(RTL_LANGUAGE.getLanguage()));
    }

    @Test
    public void testIsRtl_LtrLocale_false() {
        assertFalse(BiDiUtils.isRtl(LTR_LANGUAGE.getLanguage()));
    }

    @Test
    public void testIsMixedOrientationProject_orientationAllLtr_false() {
        Locale.setDefault(LTR_LOCALE);
        setupProjectNotLoaded();
        assertEquals(BiDiUtils.ORIENTATION.ALL_LTR, BiDiUtils.getOrientationType());
        assertFalse(BiDiUtils.isMixedOrientationProject());
    }

    @Test
    public void testIsMixedOrientationProject_orientationAllRtl_false() {
        Locale.setDefault(RTL_LOCALE);
        setupProjectNotLoaded();
        assertEquals(BiDiUtils.ORIENTATION.ALL_RTL, BiDiUtils.getOrientationType());
        assertFalse(BiDiUtils.isMixedOrientationProject());
    }

    @Test
    public void testIsMixedOrientationProject_orientationDiffer_true() {
        Locale.setDefault(RTL_LOCALE);
        setupAllLtrProject();
        assertEquals(BiDiUtils.ORIENTATION.DIFFER, BiDiUtils.getOrientationType());
        assertTrue(BiDiUtils.isMixedOrientationProject());
    }

    // Helper methods

    private void setupAllLtrProject() {
        setupProject(LTR_LANGUAGE, LTR_LANGUAGE);
    }

    private void setupAllRtlProject() {
        setupProject(RTL_LANGUAGE, RTL_LANGUAGE);
    }

    private void setupRtlToLtrProject() {
        setupProject(RTL_LANGUAGE, LTR_LANGUAGE);
    }

    private void setupLtrToRtlProject() {
        setupProject(LTR_LANGUAGE, RTL_LANGUAGE);
    }

    private void setupProject(Language sourceLanguage, Language targetLanguage) {
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

    private void setupProjectNotLoaded() {
        Core.setProject(new NotLoadedProject() {
            @Override
            public boolean isProjectLoaded() {
                return false;
            }
        });
    }
}
