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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

import javax.swing.plaf.UIResource;

import org.junit.Assume;
import org.junit.Test;

import org.omegat.util.gui.FontFallbackManager;

/**
 * @author stephan.pakebusch at zollsoft.de
 */
public class FallbackFontsTest {

    /** Regional indicator pair D+E, rendered as the German flag emoji. */
    private static final String FLAG_PAIR = "🇩🇪";

    @Test
    public void testDisplayableTextKeepsFont() {
        Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
        assertSame(font, FallbackFonts.withGlyphFallback(font, "Preset"));
        assertSame(font, FallbackFonts.withGlyphFallback(font, ""));
        assertSame(font, FallbackFonts.withGlyphFallback(font, null));
    }

    @Test
    public void testUndisplayableTextGetsFallbackFont() {
        Font base = physicalFontLackingFlagGlyphs();
        Assume.assumeTrue("no font without flag glyphs installed", base != null);
        base = base.deriveFont(14.5f);
        Font fallback = FallbackFonts.withGlyphFallback(base, FLAG_PAIR + " Preset");
        assertNotSame(base, fallback);
        assertFalse("Aqua replaces UIResource fonts on icon buttons with its 11 pt system font",
                fallback instanceof UIResource);
        assertTrue("layout attribute must route painting through the TextLayout pipeline",
                fallback.hasLayoutAttributes());
        assertEquals(base.getSize2D(), fallback.getSize2D(), 0f);
        assertEquals(base.getStyle(), fallback.getStyle());
        assertEquals(Font.DIALOG, fallback.getFamily());
        assertSame("repeated lookups must hit the cache",
                fallback, FallbackFonts.withGlyphFallback(base, FLAG_PAIR + " Preset"));
    }

    private static Font physicalFontLackingFlagGlyphs() {
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            Font font = new Font(family, Font.BOLD, 14);
            if (FontFallbackManager.canDisplayUpTo(font, FLAG_PAIR) != -1) {
                return font;
            }
        }
        return null;
    }
}
