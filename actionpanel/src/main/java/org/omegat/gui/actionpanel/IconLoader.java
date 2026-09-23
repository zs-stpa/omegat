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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.jspecify.annotations.Nullable;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;

import org.omegat.util.Log;

/**
 * Loads user-chosen icon files. Raster formats go through ImageIO, PDF pages
 * through the application's pdfbox, SVG through the bundled jsvg renderer.
 * Results are cached per file and size and invalidated when the file changes;
 * a file that cannot be read yields null and the button falls back to its
 * name text.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class IconLoader {

    /** Icon edge length used on the panel buttons. */
    public static final int PANEL_ICON_SIZE = 24;
    /** Icon edge length used in the settings table. */
    public static final int TABLE_ICON_SIZE = 16;

    private record CacheKey(String path, long lastModified, int size, int backgroundRgb) {
    }

    /** Minimum luminance distance between icon and background (0-255). */
    private static final int CONTRAST_THRESHOLD = 64;

    private static final Map<CacheKey, Icon> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 512;

    private IconLoader() {
    }

    public static @Nullable Icon load(File file, int sizePx) {
        return load(file, sizePx, null);
    }

    /**
     * Load with a runtime contrast check against the given background: an
     * icon whose average luminance sits too close to the background is
     * inverted so light glyphs stay visible on light grounds and vice versa.
     */
    public static @Nullable Icon load(File file, int sizePx, @Nullable Color background) {
        if (!file.isFile()) {
            return null;
        }
        CacheKey key = new CacheKey(file.getAbsolutePath(), file.lastModified(), sizePx,
                background == null ? 0 : background.getRGB());
        Icon cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (CACHE.size() > CACHE_LIMIT) {
            // Stale (path, mtime) keys accumulate when icon files change;
            // dropping everything is cheap and self-healing.
            CACHE.clear();
        }
        Icon icon = null;
        try {
            BufferedImage image = render(file, sizePx);
            if (image != null) {
                if (background != null && lowContrast(image, background)) {
                    image = invert(image);
                }
                icon = new ImageIcon(image);
            }
        } catch (IOException | RuntimeException e) {
            Log.log(e);
        }
        if (icon != null) {
            CACHE.put(key, icon);
        }
        return icon;
    }

    private static @Nullable BufferedImage render(File file, int sizePx) throws IOException {
        String name = file.getName().toLowerCase(Locale.ENGLISH);
        if (name.endsWith(".svg")) {
            return renderSvg(file, sizePx);
        }
        if (name.endsWith(".pdf")) {
            return renderPdf(file, sizePx);
        }
        BufferedImage image = ImageIO.read(file);
        return image == null ? null : scale(image, sizePx);
    }

    private static @Nullable BufferedImage renderSvg(File file, int sizePx) throws IOException {
        SVGDocument document = new SVGLoader().load(file.toURI().toURL());
        if (document == null) {
            return null;
        }
        BufferedImage image = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float width = document.size().width;
            float height = document.size().height;
            if (width > 0 && height > 0) {
                float factor = sizePx / Math.max(width, height);
                g.translate((sizePx - width * factor) / 2, (sizePx - height * factor) / 2);
                g.scale(factor, factor);
            }
            document.render(null, g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static @Nullable BufferedImage renderPdf(File file, int sizePx) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            if (document.getNumberOfPages() == 0) {
                return null;
            }
            PDRectangle box = document.getPage(0).getMediaBox();
            float largest = Math.max(box.getWidth(), box.getHeight());
            float renderScale = largest > 0 ? sizePx / largest : 1f;
            BufferedImage page = new PDFRenderer(document).renderImage(0, renderScale);
            return scale(page, sizePx);
        }
    }

    /** Mean luminance of the opaque pixels vs. the background luminance. */
    static boolean lowContrast(BufferedImage image, Color background) {
        long sum = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) > 32) {
                    sum += luminance(argb);
                    count++;
                }
            }
        }
        if (count == 0) {
            return false;
        }
        long backgroundLuminance = luminance(background.getRGB());
        return Math.abs(sum / count - backgroundLuminance) < CONTRAST_THRESHOLD;
    }

    private static long luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    /** Invert the colours, keeping the alpha channel. */
    static BufferedImage invert(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int inverted = (argb & 0xff000000) | (~argb & 0x00ffffff);
                result.setRGB(x, y, inverted);
            }
        }
        return result;
    }

    /** Scale into a square of the target size, keeping the aspect ratio. */
    private static BufferedImage scale(BufferedImage source, int sizePx) {
        int width = source.getWidth();
        int height = source.getHeight();
        double factor = (double) sizePx / Math.max(width, height);
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));
        BufferedImage target = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, (sizePx - targetWidth) / 2, (sizePx - targetHeight) / 2, targetWidth,
                    targetHeight, null);
        } finally {
            g.dispose();
        }
        return target;
    }
}
