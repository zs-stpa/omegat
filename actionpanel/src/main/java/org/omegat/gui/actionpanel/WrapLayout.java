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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * FlowLayout whose preferred size accounts for line wrapping, so a wrapping
 * button row inside a scroll pane grows vertically instead of forcing a
 * horizontal scroll bar. Based on the widely used public-domain WrapLayout by
 * Rob Camick (tips4java).
 *
 * @author stephan.pakebusch at zollsoft.de
 */
@SuppressWarnings("serial")
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                targetWidth = scrollPane.getSize().width;
            }
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + getHgap() * 2;
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dimension = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            int memberCount = target.getComponentCount();
            for (int i = 0; i < memberCount; i++) {
                Component member = target.getComponent(i);
                if (!member.isVisible()) {
                    continue;
                }
                Dimension size = preferred ? member.getPreferredSize() : member.getMinimumSize();
                if (rowWidth + size.width > maxWidth) {
                    addRow(dimension, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }
                rowWidth += size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            addRow(dimension, rowWidth, rowHeight);

            dimension.width += horizontalInsetsAndGap;
            dimension.height += insets.top + insets.bottom + getVgap() * 2;
            return dimension;
        }
    }

    private void addRow(Dimension dimension, int rowWidth, int rowHeight) {
        dimension.width = Math.max(dimension.width, rowWidth);
        if (dimension.height > 0) {
            dimension.height += getVgap();
        }
        dimension.height += rowHeight;
    }
}
