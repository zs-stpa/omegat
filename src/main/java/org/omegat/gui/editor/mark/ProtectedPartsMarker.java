/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2013 Alex Buloichik
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

package org.omegat.gui.editor.mark;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import javax.swing.text.AttributeSet;
import javax.swing.text.Highlighter.HighlightPainter;

import org.apache.commons.text.StringEscapeUtils;

import org.omegat.core.Core;
import org.omegat.core.data.ProtectedPart;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.util.BiDiUtils;
import org.omegat.util.PatternConsts;
import org.omegat.util.Preferences;
import org.omegat.util.gui.Styles;

/**
 * Marker for SourceTextEntry.protectedParts and tags.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Aaron Madlon-Kay
 */
public class ProtectedPartsMarker implements IMarker {

    @Override
    public List<Mark> getMarksForEntry(SourceTextEntry ste, String sourceText, String translationText, boolean isActive)
            throws Exception {
        if (ste.getProtectedParts().length == 0) {
            return null;
        }
        if (sourceText == null && translationText == null) {
            return null;
        }

        // The attributes bind the foreground to the palette entry so the mark
        // is not shadowed by the bound state color of the segment text (see
        // Mark#attributes) and colour preference changes take effect without
        // restarting the application; the faint 0.2 alpha painter a
        // right-to-left target used to get left the tags barely legible (SF
        // bug #1122). The attribute is safe for a part whose direction
        // matches the editor orientation. A part of the other direction (the
        // source when the project languages differ in direction) keeps the
        // painter: a style run inside such an embedding splits the text into
        // separately laid out fragments and the spaces next to the tag
        // change sides.
        boolean sourceSafe = true;
        if (Core.getProject().isProjectLoaded()) {
            sourceSafe = attributesSafe(true, BiDiUtils.isSourceLangRtl(), BiDiUtils.isTargetLangRtl());
        }
        boolean targetSafe = true;
        AttributeSet attrs = Styles.createBoundAttributeSet(Styles.EditorColor.COLOR_PLACEHOLDER, null,
                null, null);
        HighlightPainter painter = new TransparentHighlightPainter(
                Styles.EditorColor.COLOR_PLACEHOLDER.getColor(), 0.2F);

        List<Mark> r = new ArrayList<Mark>();

        // find protected parts
        for (ProtectedPart pp : ste.getProtectedParts()) {
            if (sourceText != null) {
                int pos = -1;
                while ((pos = sourceText.indexOf(pp.getTextInSourceSegment(), pos + 1)) >= 0) {
                    Mark m = new Mark(Mark.ENTRY_PART.SOURCE, pos,
                            pos + pp.getTextInSourceSegment().length());
                    m.painter = sourceSafe ? null : painter;
                    m.attributes = sourceSafe ? attrs : null;
                    m.toolTipText = escapeHtml(pp.getDetailsFromSourceFile());
                    r.add(m);
                }
            }
            if (translationText != null) {
                int pos = -1;
                while ((pos = translationText.indexOf(pp.getTextInSourceSegment(), pos + 1)) >= 0) {
                    Mark m = new Mark(Mark.ENTRY_PART.TRANSLATION, pos, pos + pp.getTextInSourceSegment().length());
                    m.painter = targetSafe ? null : painter;
                    m.attributes = targetSafe ? attrs : null;
                    m.toolTipText = escapeHtml(pp.getDetailsFromSourceFile());
                    r.add(m);
                }
            }
        }

        return r;
    }

    /**
     * Whether a character attribute may be set on a tag of the given part
     * without disturbing the bidi layout. Each part is embedded in the
     * direction of its own language while the editor follows the target
     * language, so only a source of the other direction is embedded against
     * the editor orientation.
     */
    static boolean attributesSafe(boolean isSource, boolean sourceRtl, boolean targetRtl) {
        return !isSource || sourceRtl == targetRtl;
    }

    private String escapeHtml(String s) {
        boolean doSimplify = Preferences.isPreferenceDefault(Preferences.VIEW_OPTION_PPT_SIMPLIFY,
                Preferences.VIEW_OPTION_PPT_SIMPLIFY_DEFAULT);
        // See if tooltip is enclosed by tags. If so, either strip the tags (doSimplify == true)
        // or make the enclosed text bold (doSimplify != true).
        Matcher m = PatternConsts.PROTECTED_PARTS_PAIRED_TAG_DECOMPILE.matcher(s);
        if (m.find()) {
            s = s.replace("&", "&amp;");
            // paired tag
            StringBuilder text = new StringBuilder(s.length() * 2);
            if (!doSimplify) {
                text.append(m.group(1).replace("<", "&lt;").replace(">", "&gt;"));
                text.append("<b>");
            }
            text.append(m.group(2).replace("<", "&lt;").replace(">", "&gt;"));
            if (!doSimplify) {
                text.append("</b>");
                text.append(m.group(3).replace("<", "&lt;").replace(">", "&gt;"));
            }
            return text.toString();
        }
        if (doSimplify) {
            // See if the tooltip contains an equiv-text attribute. If so, use just the value.
            m = PatternConsts.EQUIV_TEXT_ATTRIBUTE_DECOMPILE.matcher(s);
            if (m.find()) {
                s = StringEscapeUtils.unescapeHtml4(m.group(1));
            }
        }
        // standalone tag
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
