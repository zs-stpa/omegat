/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2008 Alex Buloichik
               2012 Didier Briel
               2015 Aaron Madlon-Kay
               2023 Damien Rembert
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

import java.awt.ComponentOrientation;
import java.awt.font.TextAttribute;
import java.text.Bidi;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;

/**
 * @author Damien Rembert
 */
public final class BiDiUtils {

    public enum ORIENTATION {
        /** All text is left-to-right oriented. */
        ALL_LTR,
        /** All text is right-to-left oriented. */
        ALL_RTL,
        /**
         * different texts/segments have different orientation, depending on
         * language/locale.
         */
        DIFFER
    };

    public static final String BIDI_LRE = "\u202a";
    public static final String BIDI_RLE = "\u202b";
    public static final String BIDI_PDF = "\u202c";
    public static final String BIDI_LRI = "\u2066";
    public static final String BIDI_RLI = "\u2067";
    public static final String BIDI_PDI = "\u2069";
    public static final String BIDI_LRM = "\u200e";
    public static final String BIDI_RLM = "\u200f";
    public static final char BIDI_LRM_CHAR = '\u200e';
    public static final char BIDI_RLM_CHAR = '\u200f';

    private BiDiUtils() {
    }

    public static ORIENTATION getOrientationType() {
        ORIENTATION orientation;

        if (Core.getProject().isProjectLoaded()) {
            orientation = getOrientationFromProject();
        } else if (isLocaleRtl()) {
            // project not loaded, use locale
            orientation = ORIENTATION.ALL_RTL;
        } else {
            // project not loaded, default to LTR
            orientation = ORIENTATION.ALL_LTR;
        }
        return orientation;
    }

    /**
     * Decide what document orientation should be default for source/target
     * languages.
     */
    private static ORIENTATION getOrientationFromProject() {
        ORIENTATION orientation;

        boolean sourceLangIsRTL = isSourceLangRtl();
        boolean targetLangIsRTL = isTargetLangRtl();

        if (sourceLangIsRTL) {
            orientation = ORIENTATION.ALL_RTL;
        } else {
            orientation = ORIENTATION.ALL_LTR;
        }
        if (sourceLangIsRTL != targetLangIsRTL || sourceLangIsRTL != isLocaleRtl()) {
            orientation = ORIENTATION.DIFFER;
        }
        return orientation;
    }

    /**
     * Decide what document orientation should be default for source/target
     * languages.
     */
    public static ComponentOrientation getInitialOrientation() {
        return getOrientation(null);
    }

    /**
     * The direction a text is displayed in under the orientation mode: the
     * forced modes win, otherwise the text reads in its own direction.
     *
     * @param orientationType
     *            the orientation mode, null for the current one
     * @param textRtl
     *            does the text itself read right-to-left?
     */
    public static boolean isDisplayedRtl(@Nullable ORIENTATION orientationType, boolean textRtl) {
        if (orientationType == null) {
            orientationType = getOrientationType();
        }
        switch (orientationType) {
        case ALL_LTR:
            return false;
        case ALL_RTL:
            return true;
        default:
            return textRtl;
        }
    }

    /**
     * Decide what document orientation should be default for source/target
     * languages.
     */
    public static ComponentOrientation getOrientation(ORIENTATION orientationType) {

        ComponentOrientation targetOrientation = null;

        if (orientationType == null) {
            orientationType = getOrientationType();
        }

        switch (orientationType) {
        case ALL_LTR:
            targetOrientation = ComponentOrientation.LEFT_TO_RIGHT;
            break;
        case ALL_RTL:
            targetOrientation = ComponentOrientation.RIGHT_TO_LEFT;
            break;
        case DIFFER:
            if (isTargetLangRtl()) {
                // using target lang direction gives better result when user
                // starts editing.
                targetOrientation = ComponentOrientation.RIGHT_TO_LEFT;
            } else {
                targetOrientation = ComponentOrientation.LEFT_TO_RIGHT;
            }
            break;
        default:
            throw new IllegalArgumentException();
        }
        return targetOrientation;
    }

    /**
     * Lets every paragraph of a document read in the direction of its own
     * text, decided by its first strong character as in the Unicode bidi
     * algorithm; a paragraph without one keeps the document direction. For
     * panes that show free text whose language is not known in advance,
     * such as segment comments: a German comment stays left-to-right in a
     * project with an Arabic target, an Arabic one reads right-to-left,
     * each paragraph on its own.
     *
     * Swing lays every line out in the document direction, which follows
     * the component orientation; a paragraph attribute only moves the
     * alignment. So a paragraph that reads against the document is wrapped
     * in isolate controls, LRI or RLI up to PDI, much as the editor puts
     * embedding controls around segment text, and every paragraph is aligned
     * to the side it starts on. Call it once, right after the text is set:
     * the controls stay in the document, so whoever reads its text has to
     * strip them, see EditorUtils.removeDirectionChars.
     *
     * @param doc
     *            the document to adjust, on the event dispatch thread
     */
    public static void alignParagraphsToText(StyledDocument doc) {
        boolean docLtr = !TextAttribute.RUN_DIRECTION_RTL.equals(doc.getProperty(TextAttribute.RUN_DIRECTION));
        int defaultDirection = docLtr ? Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                : Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT;
        Element root = doc.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            Element paragraph = root.getElement(i);
            int start = paragraph.getStartOffset();
            // The last paragraph ends on the implicit newline past the text.
            int end = Math.min(paragraph.getEndOffset(), doc.getLength());
            try {
                String text = doc.getText(start, end - start);
                boolean ltr = new Bidi(text, defaultDirection).baseIsLeftToRight();
                if (ltr != docLtr) {
                    int contentEnd = text.endsWith("\n") ? end - 1 : end;
                    doc.insertString(contentEnd, BIDI_PDI, null);
                    doc.insertString(start, ltr ? BIDI_LRI : BIDI_RLI, null);
                    end += 2;
                }
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setAlignment(attrs, ltr ? StyleConstants.ALIGN_LEFT : StyleConstants.ALIGN_RIGHT);
                doc.setParagraphAttributes(start, end - start, attrs, false);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static String addRtlBidiAround(String string) {
        return BIDI_RLE + string + BIDI_PDF;
    }

    public static String addLtrBidiAround(String string) {
        return BIDI_LRE + string + BIDI_PDF;
    }

    public static boolean isSourceLangRtl() {
        return isRtl(getSourceLanguage());
    }

    public static boolean isTargetLangRtl() {
        return isRtl(getTargetLanguage());
    }

    private static String getSourceLanguage() {
        return Core.getProject().getProjectProperties().getSourceLanguage().getLanguageCode();
    }

    private static String getTargetLanguage() {
        return Core.getProject().getProjectProperties().getTargetLanguage().getLanguageCode();
    }

    /**
     * Check if locale is Right-To-Left oriented.
     * 
     * @return true if locale is Right-To-Left oriented.
     */
    public static boolean isLocaleRtl() {
        String language = Language.getLowerCaseLanguageFromLocale();
        return isRtl(language);
    }

    /**
     * Check if language is Right-To-Left oriented.
     *
     * @param language
     *            ISO-639-2 language code
     * @return true if language is RTL
     */
    public static boolean isRtl(final String language) {
        return "ar".equalsIgnoreCase(language) || "iw".equalsIgnoreCase(language)
                || "he".equalsIgnoreCase(language) || "fa".equalsIgnoreCase(language)
                || "ur".equalsIgnoreCase(language) || "ug".equalsIgnoreCase(language)
                || "ji".equalsIgnoreCase(language) || "yi".equalsIgnoreCase(language);
    }

    public static boolean isMixedOrientationProject() {
        return (BiDiUtils.getOrientationType() == ORIENTATION.DIFFER);
    }
}
