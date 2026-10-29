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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.imageio.ImageIO;
import javax.swing.Icon;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Sample files are generated on the fly, so no binary fixtures are needed.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class IconLoaderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File raster(String format) throws IOException {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 64, 32);
        g.dispose();
        File file = new File(folder.getRoot(), "sample." + format);
        ImageIO.write(image, format, file);
        return file;
    }

    @Test
    public void testRasterFormats() throws IOException {
        for (String format : new String[] { "png", "gif", "jpg" }) {
            Icon icon = IconLoader.load(raster(format), IconLoader.PANEL_ICON_SIZE);
            assertNotNull(format, icon);
            assertEquals(IconLoader.PANEL_ICON_SIZE, icon.getIconWidth());
            assertEquals(IconLoader.PANEL_ICON_SIZE, icon.getIconHeight());
        }
    }

    @Test
    public void testSvg() throws IOException {
        File file = new File(folder.getRoot(), "sample.svg");
        Files.writeString(file.toPath(),
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\">"
                        + "<rect width=\"40\" height=\"20\" fill=\"#336699\"/></svg>",
                StandardCharsets.UTF_8);
        Icon icon = IconLoader.load(file, IconLoader.TABLE_ICON_SIZE);
        assertNotNull(icon);
        assertEquals(IconLoader.TABLE_ICON_SIZE, icon.getIconWidth());
    }

    @Test
    public void testPdf() throws IOException {
        File file = new File(folder.getRoot(), "sample.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(file);
        }
        Icon icon = IconLoader.load(file, IconLoader.PANEL_ICON_SIZE);
        assertNotNull(icon);
        assertEquals(IconLoader.PANEL_ICON_SIZE, icon.getIconWidth());
    }

    @Test
    public void testLowContrastIconIsInverted() throws IOException {
        BufferedImage white = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = white.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        assertEquals(true, IconLoader.lowContrast(white, Color.WHITE));
        assertEquals(false, IconLoader.lowContrast(white, Color.BLACK));
        BufferedImage inverted = IconLoader.invert(white);
        assertEquals(false, IconLoader.lowContrast(inverted, Color.WHITE));
        // Alpha survives the inversion.
        assertEquals(white.getRGB(0, 0) >>> 24, inverted.getRGB(0, 0) >>> 24);
    }

    @Test
    public void testCorruptFileYieldsNull() throws IOException {
        File file = new File(folder.getRoot(), "broken.png");
        Files.writeString(file.toPath(), "this is not an image", StandardCharsets.UTF_8);
        assertNull(IconLoader.load(file, IconLoader.PANEL_ICON_SIZE));
        File missing = new File(folder.getRoot(), "missing.svg");
        assertNull(IconLoader.load(missing, IconLoader.PANEL_ICON_SIZE));
    }
}
