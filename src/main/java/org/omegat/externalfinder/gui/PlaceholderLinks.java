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

package org.omegat.externalfinder.gui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import org.omegat.externalfinder.item.PlaceholderTemplate;
import org.omegat.util.OStrings;
import org.omegat.util.gui.Styles;

/**
 * Row of clickable placeholder names for the URL and command editors; a click
 * inserts the placeholder at the caret of the template field.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
final class PlaceholderLinks {

    /** Component name prefix of the links, followed by the placeholder name. */
    static final String NAME_PREFIX = "externalfinder.placeholder.";

    private PlaceholderLinks() {
    }

    /** Insertable text per link, in display order. The tooltip key is the suffix after the name. */
    static Map<String, String> links() {
        Map<String, String> links = new LinkedHashMap<>();
        for (String name : PlaceholderTemplate.NAMES) {
            links.put(name, "{" + name + (PlaceholderTemplate.PROP.equals(name) ? ":key}" : "}"));
        }
        links.put("regex", "{" + PlaceholderTemplate.COMMENT + ":{regex}}");
        return Collections.unmodifiableMap(links);
    }

    /** Links per row of the grid. */
    private static final int COLUMNS = 6;

    static JComponent create(JTextComponent field) {
        JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 8, 2));
        grid.setOpaque(false);
        grid.setAlignmentX(0.0F);
        for (Map.Entry<String, String> link : links().entrySet()) {
            grid.add(createLink(field, link.getKey(), link.getValue()));
        }
        // Keep the grid at its natural height; the box layout would otherwise
        // hand it a share of any extra dialog height.
        grid.setMaximumSize(new Dimension(Short.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private static JLabel createLink(JTextComponent field, String name, String text) {
        JLabel label = new JLabel(text);
        label.setName(NAME_PREFIX + name);
        label.setToolTipText(OStrings.getString("EXTERNALFINDER_PLACEHOLDER_TIP_" + name.toUpperCase(Locale.ENGLISH)));
        label.setForeground(Styles.EditorColor.COLOR_HYPERLINK.getColor());
        label.setFont(label.getFont().deriveFont(
                Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int start = field.getSelectionStart();
                field.replaceSelection(text);
                field.requestFocusInWindow();
                // Leave the part to fill in selected, so typing replaces it.
                int sample = Math.max(text.indexOf("key}"), text.indexOf("regex}"));
                if (sample >= 0) {
                    int end = text.indexOf('}', sample);
                    field.select(start + sample, start + end);
                }
            }
        });
        return label;
    }
}
