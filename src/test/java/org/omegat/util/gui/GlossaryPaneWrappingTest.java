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

package org.omegat.util.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.junit.Test;

/**
 * The glossary pane sporadically stopped word-wrapping: one over-long
 * unbreakable token pushes a plain JTextPane's minimum width past the
 * viewport, which switches wrapping off for the whole pane, and the
 * linkifier's full attribute reset re-layouts (and de-styles) the document
 * on every insert even when no link is involved.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class GlossaryPaneWrappingTest {

    private static final String LONG_TOKEN = "https://example.org/"
            + "averyveryverylongpaththatnobodycanbreakanywhere".repeat(8);

    @Test
    public void testForcedWrappingKitKeepsMinimumWidthAtZero() throws Exception {
        onEdt(() -> {
            JTextPane plain = new JTextPane();
            plain.setText(LONG_TOKEN);
            JTextPane wrapping = new JTextPane();
            wrapping.setEditorKit(new ForcedWrappingEditorKit());
            wrapping.setText(LONG_TOKEN);
            // thresholds are far apart on purpose: the token is hundreds
            // of characters wide in any font, the wrapped minimum is only
            // insets plus one character
            int plainMin = plain.getUI().getMinimumSize(plain).width;
            int wrappingMin = wrapping.getUI().getMinimumSize(wrapping).width;
            assertTrue("a plain pane cannot shrink below the longest token: " + plainMin,
                    plainMin > 400);
            assertTrue("the wrapping kit must not propagate the token width: " + wrappingMin,
                    wrappingMin < 50);
        });
    }

    @Test
    public void testLinkifierLeavesLinkFreeDocumentsAlone() throws Exception {
        JTextPane pane = new JTextPane();
        onEdt(() -> {
            JTextPaneLinkifier.linkify(pane, false);
            SimpleAttributeSet bold = new SimpleAttributeSet();
            StyleConstants.setBold(bold, true);
            try {
                pane.getStyledDocument().insertString(0, "no link here, styled by the renderer", bold);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
        flushEdt();
        onEdt(() -> assertTrue("renderer styling must survive a link-free refresh",
                StyleConstants.isBold(
                        pane.getStyledDocument().getCharacterElement(3).getAttributes())));
    }

    @Test
    public void testLinkifierStillLinksUrls() throws Exception {
        JTextPane pane = new JTextPane();
        onEdt(() -> {
            JTextPaneLinkifier.linkify(pane, false);
            try {
                pane.getStyledDocument().insertString(0, "see https://omegat.org for details", null);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
        flushEdt();
        onEdt(() -> {
            StyledDocument doc = pane.getStyledDocument();
            int at = "see ".length() + 2;
            assertTrue("URL must be styled as a link",
                    StyleConstants.isUnderline(doc.getCharacterElement(at).getAttributes()));
            assertFalse("plain text must stay unstyled",
                    StyleConstants.isUnderline(doc.getCharacterElement(1).getAttributes()));
            assertEquals("text unchanged", "see https://omegat.org for details",
                    getText(doc));
        });
    }

    /**
     * The glossary pane swaps in prebuilt documents (SF bug #981) instead
     * of inserting: the linkifier's document listener must pick the swap up
     * and style links in the arriving document.
     */
    @Test
    public void testLinkifierHandlesSwappedInDocuments() throws Exception {
        JTextPane pane = new JTextPane();
        onEdt(() -> {
            JTextPaneLinkifier.linkify(pane, false);
            DefaultStyledDocument doc = new DefaultStyledDocument();
            try {
                doc.insertString(0, "swapped: https://omegat.org inside", null);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            pane.setStyledDocument(doc);
        });
        flushEdt();
        onEdt(() -> assertTrue("links in a swapped-in document must be styled",
                StyleConstants.isUnderline(pane.getStyledDocument()
                        .getCharacterElement("swapped: ".length() + 2).getAttributes())));
    }

    private static String getText(StyledDocument doc) {
        try {
            return doc.getText(0, doc.getLength());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void onEdt(Runnable r) throws Exception {
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof AssertionError) {
                throw (AssertionError) ex.getCause();
            }
            throw ex;
        }
    }

    /** Let pending invokeLater rounds (the linkifier's refresh) run. */
    private static void flushEdt() throws Exception {
        for (int i = 0; i < 3; i++) {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
