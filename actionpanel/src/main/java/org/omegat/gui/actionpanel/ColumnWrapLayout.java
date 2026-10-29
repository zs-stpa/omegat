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
import java.awt.Insets;
import java.awt.LayoutManager;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Lays components out in columns: fill downwards, then start the next column.
 * Columns advance left to right in LTR orientation and right to left in RTL,
 * so the panel reads naturally in both directions.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class ColumnWrapLayout implements LayoutManager {

    private final int hgap;
    private final int vgap;

    public ColumnWrapLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            int maxHeight = availableHeight(parent);
            Insets insets = parent.getInsets();
            int x = 0;
            int columnWidth = 0;
            int y = 0;
            int usedHeight = 0;
            for (Component component : parent.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                if (y > 0 && y + size.height > maxHeight) {
                    x += columnWidth + hgap;
                    columnWidth = 0;
                    y = 0;
                }
                y += size.height + vgap;
                usedHeight = Math.max(usedHeight, y);
                columnWidth = Math.max(columnWidth, size.width);
            }
            return new Dimension(x + columnWidth + insets.left + insets.right + hgap * 2,
                    usedHeight + insets.top + insets.bottom + vgap);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
            int maxHeight = availableHeight(parent);
            Insets insets = parent.getInsets();
            int x = insets.left + hgap;
            int y = insets.top + vgap;
            int columnWidth = 0;
            // First pass per column to know its width before placing when
            // running right to left; simpler: place LTR, then mirror.
            java.util.List<Component> visible = new java.util.ArrayList<>();
            java.util.List<Integer> xs = new java.util.ArrayList<>();
            java.util.List<Integer> ys = new java.util.ArrayList<>();
            for (Component component : parent.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                if (y > insets.top + vgap && y + size.height > maxHeight) {
                    x += columnWidth + hgap;
                    columnWidth = 0;
                    y = insets.top + vgap;
                }
                visible.add(component);
                xs.add(x);
                ys.add(y);
                y += size.height + vgap;
                columnWidth = Math.max(columnWidth, size.width);
            }
            int totalWidth = x + columnWidth + hgap + insets.right;
            for (int i = 0; i < visible.size(); i++) {
                Component component = visible.get(i);
                Dimension size = component.getPreferredSize();
                int componentX = xs.get(i);
                if (!leftToRight) {
                    int reference = Math.max(totalWidth, parent.getWidth());
                    componentX = reference - componentX - size.width;
                }
                component.setBounds(componentX, ys.get(i), size.width, size.height);
            }
        }
    }

    private int availableHeight(Container parent) {
        Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, parent);
        int height = scrollPane != null ? ((JScrollPane) scrollPane).getViewport().getHeight()
                : parent.getHeight();
        if (height <= 0) {
            height = Integer.MAX_VALUE;
        }
        return height;
    }
}
