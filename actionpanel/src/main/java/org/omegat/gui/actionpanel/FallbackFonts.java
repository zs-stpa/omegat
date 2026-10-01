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

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.omegat.util.gui.FontFallbackManager;

/**
 * Labels and buttons paint their text through a glyph pipeline that does no
 * font fallback for physical fonts, so glyphs the font lacks (flag emoji in
 * a row name, say) degrade to replacement glyphs, although the same string
 * renders fine while being edited in a text field.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
final class FallbackFonts {

    /**
     * Table cell renderers ask per paint, and on macOS the displayability
     * probe creates a glyph vector per character; the cache is confined to
     * the EDT like all Swing painting, so it needs no synchronization.
     */
    private static final Map<Map.Entry<Font, String>, Font> CACHE = new HashMap<>();
    private static final int CACHE_CAP = 256;

    private FallbackFonts() {
    }

    /**
     * The font to render the given text with: the font itself while it can
     * display every character, otherwise a logical Dialog font whose kerning
     * attribute routes painting through the TextLayout pipeline, where the
     * logical font's fallback chain covers such glyphs. The whole string
     * switches family then, not only the missing glyphs. The fallback is a
     * plain font, deliberately not a UIResource: the Aqua look and feel
     * swaps UIResource fonts on buttons with icons for its 11 pt system
     * font.
     */
    static Font withGlyphFallback(Font font, @Nullable String text) {
        if (text == null || text.isEmpty()) {
            return font;
        }
        Map.Entry<Font, String> key = Map.entry(font, text);
        Font cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font result;
        if (FontFallbackManager.canDisplayUpTo(font, text) == -1) {
            result = font;
        } else {
            result = new Font(Font.DIALOG, font.getStyle(), font.getSize())
                    .deriveFont(font.getSize2D())
                    .deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
        }
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear();
        }
        CACHE.put(key, result);
        return result;
    }
}
