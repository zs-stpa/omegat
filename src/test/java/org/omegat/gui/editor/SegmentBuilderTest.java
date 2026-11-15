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
package org.omegat.gui.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.awt.ComponentOrientation;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.omegat.core.Core;
import org.omegat.core.TestCore;
import org.omegat.core.TestCoreInitializer;
import org.omegat.core.data.EntryKey;
import org.omegat.core.data.IProject;
import org.omegat.core.data.NotLoadedProject;
import org.omegat.core.data.ProjectProperties;
import org.omegat.core.data.ProjectTMX;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.data.TMXEntry;
import org.omegat.core.data.TMXEntryFactoryForTest;
import org.omegat.gui.main.IMainWindow;
import org.omegat.util.BiDiUtils;
import org.omegat.util.Language;

public class SegmentBuilderTest extends TestCore {

    private static final String GERMAN = "In der %@-App gibt es ";
    private static final String ARABIC = "نص عربي ";
    private static final String FRENCH = "Dans l'app ";
    private static final String GERMAN_LONG = "Das ist ein langer deutscher Satz, der sicher umbrochen wird "
            + "und dann noch weiter geht und erst hier endet. ";
    private static final String ARABIC_LONG = "نص عربي طويل جدا يلتف على عدة أسطر في المحرر ويستمر حتى "
            + "النهاية، ثم يعود من جديد ويلتف مرة أخرى على سطر ثالث وينتهي هنا تماما. ";

    /** Width of the editor in the layout tests; the long texts wrap into three lines. */
    private static final int WIDTH = 300;
    private static final double RIGHT_HALF = WIDTH / 2.0;

    private EditorController editorController;
    private final Map<Language, ProjectTMX> otherLanguageTMs = new HashMap<>();

    @BeforeClass
    public static void setUpBeforeClass() {
        assumeFalse("Skipping test: headless environment", GraphicsEnvironment.isHeadless());
    }

    @Override
    protected void initEditor(IMainWindow mainWindow) {
        editorController = new EditorController(mainWindow);
        TestCoreInitializer.initEditor(editorController);
    }

    @Before
    public void setUpProject() {
        Core.setProject(new NotLoadedProject() {
            @Override
            public ProjectProperties getProjectProperties() {
                return new TestingProjectProperties();
            }

            @Override
            public Map<Language, ProjectTMX> getOtherTargetLanguageTMs() {
                return otherLanguageTMs;
            }
        });
    }

    @Test
    public void testInactiveSourceStartsWithItsDirectionMarker() throws Exception {
        assertEquals(BiDiUtils.BIDI_LRM + GERMAN + "\n",
                render(entry(GERMAN), IProject.AllTranslations.EMPTY_TRANSLATION, false, false, true));
        assertEquals(BiDiUtils.BIDI_RLM + ARABIC + "\n",
                render(entry(ARABIC), IProject.AllTranslations.EMPTY_TRANSLATION, false, true, false));
    }

    @Test
    public void testInactiveTranslationStartsWithItsDirectionMarker() throws Exception {
        assertEquals(BiDiUtils.BIDI_RLM + ARABIC + "\n",
                render(entry(GERMAN), translation(GERMAN, ARABIC), false, false, true));
        assertEquals(BiDiUtils.BIDI_LRM + GERMAN + "\n",
                render(entry(ARABIC), translation(ARABIC, GERMAN), false, true, false));
    }

    @Test
    public void testOtherLanguageLineStartsWithItsDirectionMarker() throws Exception {
        SourceTextEntry ste = entry(GERMAN);
        ProjectTMX french = new ProjectTMX();
        french.setTranslation(ste, translation(GERMAN, FRENCH), true);
        otherLanguageTMs.put(new Language("fr"), french);

        String[] lines = render(ste, IProject.AllTranslations.EMPTY_TRANSLATION, true, false, true).split("\n");
        assertEquals(BiDiUtils.BIDI_LRM + BiDiUtils.BIDI_LRE + "fr: " + BiDiUtils.BIDI_PDF + FRENCH, lines[1]);
    }

    @Test
    public void testLeftToRightLinesStayFlushInARightToLeftEditor() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        Document3 doc = new Document3(editorController, true);
        SegmentBuilder builder = build(doc, entry(GERMAN_LONG), IProject.AllTranslations.EMPTY_TRANSLATION,
                false, false, true);
        List<int[]> lines = layout(editor, doc);
        double leftEdge = editor.getInsets().left;
        assertTrue("the text wraps", lines.size() >= 3);
        for (int[] line : lines) {
            assertEquals("a line starts at the left edge", leftEdge, x(editor, line[0] == 0 ? 1 : line[0]), 0.5);
            assertTrue("the space at a line end stays at the end", x(editor, line[1]) > x(editor, line[1] - 1));
        }
        SwingUtilities.invokeAndWait(builder::resetTextAttributes);
        assertEquals("also after the marker attributes were reset", leftEdge, x(editor, 1), 0.5);
    }

    @Test
    public void testAnOrientationChangeKeepsTheParagraphDirections() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        Document3 doc = new Document3(editorController, true);
        build(doc, entry(GERMAN_LONG), IProject.AllTranslations.EMPTY_TRANSLATION, false, false, true);
        layout(editor, doc);
        // the project switches to a right-to-left editor while the document is in place
        SwingUtilities.invokeAndWait(() -> editor.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT));
        List<int[]> lines = lines(editor, doc);
        assertTrue("the text wraps", lines.size() >= 3);
        for (int[] line : lines) {
            assertEquals("a line starts at the left edge", editor.getInsets().left,
                    x(editor, line[0] == 0 ? 1 : line[0]), 0.5);
        }
    }

    @Test
    public void testRightToLeftLinesStayFlushInALeftToRightEditor() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        Document3 doc = new Document3(editorController, true);
        build(doc, entry(ARABIC_LONG), IProject.AllTranslations.EMPTY_TRANSLATION, false, true, false);
        List<int[]> lines = layout(editor, doc);
        assertTrue("the text wraps", lines.size() >= 3);
        for (int[] line : lines) {
            int first = line[0] == 0 ? 1 : line[0];
            assertTrue("a line starts at the right edge", x(editor, first) > RIGHT_HALF);
            assertTrue("the space at a line end stays at the end", x(editor, line[1]) < x(editor, line[1] - 1));
        }
    }

    @Test
    public void testATranslationStartingWithALatinWordReadsRightToLeft() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        Document3 doc = new Document3(editorController, true);
        String latin = "iPhone ";
        build(doc, entry(GERMAN), translation(GERMAN, latin + ARABIC), false, false, true);
        layout(editor, doc);
        assertTrue("the Latin word sits right of the Arabic text", x(editor, 1) > x(editor, 1 + latin.length()));
    }

    @Test
    public void testANumberOnlyTranslationReadsRightToLeft() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        Document3 doc = new Document3(editorController, true);
        build(doc, entry(GERMAN), translation(GERMAN, "123"), false, false, true);
        layout(editor, doc);
        assertTrue("the number sits at the right edge", x(editor, 1) > RIGHT_HALF);
        assertTrue("the line ends at the left", x(editor, 4) < x(editor, 3));
    }

    @Test
    public void testAPlainDocumentKeepsTheEditorDirection() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        Document3 doc = new Document3(editorController, false);
        editorController.currentOrientation = BiDiUtils.ORIENTATION.ALL_LTR;
        // the space before the Latin word keeps the two runs apart
        SourceTextEntry ste = entry(ARABIC.trim() + " Text");
        SegmentBuilder builder = new SegmentBuilder(editorController, doc, editorController.getSettings(), ste,
                1, false);
        SwingUtilities.invokeAndWait(() -> builder.createSegmentElement(false,
                IProject.AllTranslations.EMPTY_TRANSLATION));
        layout(editor, doc);
        assertEquals("the editor direction stays on the document", TextAttribute.RUN_DIRECTION_LTR,
                doc.getProperty(TextAttribute.RUN_DIRECTION));
        assertTrue("a segment starting with an Arabic word still reads left-to-right",
                x(editor, ARABIC.length()) > x(editor, 0));
    }

    @Test
    public void testTheSegmentMarkFollowsTheTranslation() throws Exception {
        EditorTextArea3 editor = editorController.editor;
        editor.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        Document3 doc = new Document3(editorController, true);
        build(doc, entry(GERMAN), translation(GERMAN, ARABIC), true, false, true);
        layout(editor, doc);
        assertTrue("the mark of an Arabic translation stands to its left",
                x(editor, doc.getTranslationEnd() + 1) < x(editor, doc.getTranslationStart()));

        doc = new Document3(editorController, true);
        build(doc, entry(ARABIC), translation(ARABIC, GERMAN), true, true, false);
        layout(editor, doc);
        assertTrue("the mark of a German translation stands to its right, also in a right-to-left editor",
                x(editor, doc.getTranslationEnd() + 1) > x(editor, doc.getTranslationStart()));
    }

    /** Shows the document in the editor; returns the first and last offset of each line. */
    private static List<int[]> layout(EditorTextArea3 editor, Document3 doc) throws Exception {
        SwingUtilities.invokeAndWait(() -> editor.setDocument(doc));
        return lines(editor, doc);
    }

    /** Lays the editor out at the test width; returns the first and last offset of each line. */
    private static List<int[]> lines(EditorTextArea3 editor, Document3 doc) throws Exception {
        List<int[]> lines = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                editor.setSize(WIDTH, 400);
                editor.getUI().getRootView(editor).setSize(WIDTH, 400);
                int len = doc.getLength();
                double lastY = -1;
                int lineStart = -1;
                for (int i = 0; i < len; i++) {
                    double y = editor.modelToView2D(i).getY();
                    if (y != lastY) {
                        if (lineStart >= 0) {
                            lines.add(new int[] { lineStart, i - 1 });
                        }
                        lastY = y;
                        lineStart = i;
                    }
                }
                lines.add(new int[] { lineStart, len - 1 });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return lines;
    }

    /** The x position of the character at the offset. */
    private static double x(EditorTextArea3 editor, int offset) throws Exception {
        Rectangle2D r = editor.modelToView2D(offset);
        return r.getX();
    }

    private static SourceTextEntry entry(String source) {
        return new SourceTextEntry(new EntryKey("file.txt", source, null, "", "", null), 1, null, null,
                new ArrayList<>());
    }

    private static TMXEntry translation(String source, String translation) {
        return TMXEntryFactoryForTest.createTMXEntry(source, translation, "tester", 0, true);
    }

    /** The document text of one segment under the given language directions. */
    private String render(SourceTextEntry ste, TMXEntry trans, boolean active, boolean sourceRtl,
            boolean targetRtl) throws Exception {
        Document3 doc = new Document3(editorController, true);
        SegmentBuilder builder = build(doc, ste, trans, active, sourceRtl, targetRtl);
        if (builder.getStartSourcePosition() >= 0) {
            assertEquals("the source text keeps its position", ste.getSrcText(),
                    doc.getText(builder.getStartSourcePosition(), builder.posSourceLength));
        }
        if (builder.getStartTranslationPosition() >= 0) {
            assertEquals("the translation keeps its position", trans.translation,
                    doc.getText(builder.getStartTranslationPosition(), builder.posTranslationLength));
        }
        return doc.getText(0, doc.getLength());
    }

    private SegmentBuilder build(Document3 doc, SourceTextEntry ste, TMXEntry trans, boolean active,
            boolean sourceRtl, boolean targetRtl) throws Exception {
        editorController.sourceLangIsRTL = sourceRtl;
        editorController.targetLangIsRTL = targetRtl;
        editorController.currentOrientation = BiDiUtils.ORIENTATION.DIFFER;
        SegmentBuilder builder = new SegmentBuilder(editorController, doc, editorController.getSettings(), ste,
                1, true);
        SwingUtilities.invokeAndWait(() -> builder.createSegmentElement(active, trans));
        return builder;
    }
}
