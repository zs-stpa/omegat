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
package org.omegat.gui.comments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;

import org.junit.Test;
import org.omegat.core.Core;
import org.omegat.core.TestCore;
import org.omegat.core.data.EntryKey;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.util.Preferences;
import org.omegat.util.gui.CharacterWrapEditorKit;
import org.omegat.util.gui.NoWrapEditorKit;

/**
 * The comments pane wraps long lines by default, breaking a token without
 * spaces at any character, and lets the pane menu switch to a horizontal
 * scroll bar instead.
 *
 * @author Stephan Pakebusch (stephan.pakebusch at zollsoft.de)
 */
public class CommentsTextAreaTest extends TestCore {

    private static final int WIDTH = 200;

    private static final String SENTENCE = "The quick brown fox jumps over the lazy dog and keeps running.";
    private static final String LONG_TOKEN = "/Users/someone/project/Client_0_Test/"
            + "VerwaltungWithOutlineViewWindowController.m:3085";
    private static final String TEXT = SENTENCE + "\n" + LONG_TOKEN + "\n";

    @Test
    public void testWrapsByDefault() throws Exception {
        CommentsTextArea pane = create();
        assertTrue(onEdt(() -> pane.getEditorKit() instanceof CharacterWrapEditorKit));
        layOut(pane, TEXT);

        JScrollPane scrollPane = scrollPaneOf(pane);
        assertTrue(onEdt(pane::getScrollableTracksViewportWidth));
        assertFalse("no sideways scrolling", onEdt(() -> scrollPane.getHorizontalScrollBar().isVisible()));
        assertEquals(onEdt(() -> scrollPane.getViewport().getWidth()), onEdt(pane::getWidth));

        List<String> sentenceRows = rows(pane, 0);
        assertTrue("prose must still wrap, got " + sentenceRows, sentenceRows.size() > 1);
        for (int i = 0; i < sentenceRows.size() - 1; i++) {
            assertTrue("prose breaks at spaces: " + sentenceRows, sentenceRows.get(i).endsWith(" "));
        }
        assertTrue("a long token breaks inside", rows(pane, 1).size() > 1);
    }

    @Test
    public void testPreferenceDisablesWrap() throws Exception {
        Preferences.setPreference(Preferences.COMMENTS_WRAP_LONG_LINES, false);
        CommentsTextArea pane = create();
        assertTrue(onEdt(() -> pane.getEditorKit() instanceof NoWrapEditorKit));
        layOut(pane, TEXT);

        JScrollPane scrollPane = scrollPaneOf(pane);
        assertFalse(onEdt(pane::getScrollableTracksViewportWidth));
        assertTrue("lines keep their length behind a scroll bar",
                onEdt(() -> scrollPane.getHorizontalScrollBar().isVisible()));
        assertTrue(onEdt(pane::getWidth) > onEdt(() -> scrollPane.getViewport().getWidth()));
        assertEquals(1, rows(pane, 0).size());
        assertEquals(1, rows(pane, 1).size());

        layOut(pane, "short");
        assertEquals("short text still fills the viewport", onEdt(() -> scrollPane.getViewport().getWidth()),
                onEdt(pane::getWidth));
    }

    @Test
    public void testSwitchingKeepsTextAndLinks() throws Exception {
        CommentsTextArea pane = create();
        pane.addCommentProvider(entry -> "See https://example.com/a%20b for details", 1);
        SourceTextEntry entry = new SourceTextEntry(new EntryKey("source.txt", "text", null, "", "", null), 1,
                null, null, new ArrayList<>());
        SwingUtilities.invokeAndWait(() -> pane.onEntryActivated(entry));
        String shown = settledText(pane);
        assertTrue(shown, shown.contains("https://example.com/a b"));
        assertTrue("the URL is a link", onEdt(() -> isLink(pane, shown.indexOf("https"))));

        SwingUtilities.invokeAndWait(() -> pane.setLineWrap(false));
        assertTrue(onEdt(() -> pane.getEditorKit() instanceof NoWrapEditorKit));
        assertEquals("switching renders the same text again", shown, settledText(pane));
        assertTrue(onEdt(() -> isLink(pane, shown.indexOf("https"))));

        SwingUtilities.invokeAndWait(() -> pane.setLineWrap(true));
        assertTrue(onEdt(() -> pane.getEditorKit() instanceof CharacterWrapEditorKit));
        assertEquals(shown, settledText(pane));
        assertTrue(onEdt(() -> isLink(pane, shown.indexOf("https"))));
    }

    /**
     * The pane's text once the linkifier is done: it restyles and decodes
     * links from a delayed timer, so wait until the text stops changing.
     */
    private static String settledText(CommentsTextArea pane) throws Exception {
        String last = onEdt(pane::getText);
        for (int attempt = 0; attempt < 20; attempt++) {
            Thread.sleep(100);
            String now = onEdt(pane::getText);
            if (now.equals(last) && attempt >= 3) {
                return now;
            }
            last = now;
        }
        return last;
    }

    private static CommentsTextArea create() throws Exception {
        return onEdt(() -> new CommentsTextArea(Core.getMainWindow()));
    }

    private static JScrollPane scrollPaneOf(CommentsTextArea pane) throws Exception {
        return onEdt(() -> (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, pane));
    }

    /** Show the text in a scroll pane of a fixed width and lay everything out. */
    private static void layOut(CommentsTextArea pane, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            pane.setText(text);
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, pane);
            scrollPane.setSize(WIDTH, 120);
            scrollPane.doLayout();
            scrollPane.getViewport().doLayout();
            pane.getUI().getRootView(pane).setSize(pane.getWidth(), pane.getHeight());
        });
    }

    /** The text of each row the given paragraph was broken into. */
    private static List<String> rows(CommentsTextArea pane, int paragraph) throws Exception {
        return onEdt(() -> {
            View section = pane.getUI().getRootView(pane).getView(0);
            View para = section.getView(paragraph);
            Document doc = pane.getDocument();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < para.getViewCount(); i++) {
                View row = para.getView(i);
                try {
                    result.add(doc.getText(row.getStartOffset(), row.getEndOffset() - row.getStartOffset()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return result;
        });
    }

    private static boolean isLink(CommentsTextArea pane, int offset) {
        return StyleConstants.isUnderline(pane.getStyledDocument().getCharacterElement(offset).getAttributes());
    }

    private static <T> T onEdt(java.util.function.Supplier<T> supplier) throws Exception {
        AtomicReference<T> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> holder.set(supplier.get()));
        return holder.get();
    }
}
