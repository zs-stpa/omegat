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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.Icon;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;

import org.jspecify.annotations.Nullable;

import org.omegat.gui.actionpanel.ActionSpec.AutotextRefActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ColorSchemeActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.EditorKeyActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.MenuActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.PreferenceActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ScriptActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SearchActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ShortcutSetActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SnippetActionSpec;
import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.gui.StaticUIUtils;

/**
 * Staged working copy of the panel rows shown in the settings table. All edits
 * happen here and reach {@link ActionPanelConfig} only on OK; Cancel discards
 * the copy. Reordering operates on contiguous or scattered selections and
 * reports the new selection range so the GUI can keep it.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
@SuppressWarnings("serial")
public class ActionPanelTableModel extends AbstractTableModel {

    public static final int COLUMN_NUMBER = 0;
    public static final int COLUMN_NAME = 1;
    public static final int COLUMN_ICON = 2;
    public static final int COLUMN_ACTION = 3;
    public static final int COLUMN_SHORTCUT = 4;
    public static final int COLUMN_TEXT_COLOR = 5;
    public static final int COLUMN_BACKGROUND_COLOR = 6;
    public static final int COLUMN_BORDER_COLOR = 7;

    private final List<ActionRow> rows;
    private final MenuActionCatalog catalog;

    public ActionPanelTableModel(List<ActionRow> initial, MenuActionCatalog catalog) {
        this.rows = new ArrayList<>(initial);
        this.catalog = catalog;
    }

    public List<ActionRow> getRows() {
        return new ArrayList<>(rows);
    }

    public ActionRow getRow(int index) {
        return rows.get(index);
    }

    public void setRow(int index, ActionRow row) {
        rows.set(index, row);
        fireTableRowsUpdated(index, index);
    }

    /** Append a fresh unnamed row and return its index. */
    public int addRow() {
        rows.add(new ActionRow(ActionPanelModule.getString("ROW_DEFAULT_NAME"), null, null));
        int index = rows.size() - 1;
        fireTableRowsInserted(index, index);
        return index;
    }

    /** Append copies of the given rows; returns the first new index. */
    public int duplicateRows(int[] indices) {
        int first = rows.size();
        for (int index : indices) {
            rows.add(rows.get(index));
        }
        fireTableRowsInserted(first, rows.size() - 1);
        return first;
    }

    public void removeRows(int[] indices) {
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        for (int i = sorted.length - 1; i >= 0; i--) {
            rows.remove(sorted[i]);
        }
        fireTableDataChanged();
    }

    /**
     * Move the selected rows one step up or down. Returns the resulting
     * selection indices, unchanged when the block already touches the edge.
     */
    public int[] moveRows(int[] indices, boolean up) {
        if (indices.length == 0) {
            return indices;
        }
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        if (up && sorted[0] == 0) {
            return indices;
        }
        if (!up && sorted[sorted.length - 1] == rows.size() - 1) {
            return indices;
        }
        int[] result = new int[sorted.length];
        if (up) {
            for (int i = 0; i < sorted.length; i++) {
                swap(sorted[i], sorted[i] - 1);
                result[i] = sorted[i] - 1;
            }
        } else {
            for (int i = sorted.length - 1; i >= 0; i--) {
                swap(sorted[i], sorted[i] + 1);
                result[i] = sorted[i] + 1;
            }
        }
        fireTableDataChanged();
        return result;
    }

    /**
     * Move the given model rows in front of the target model index (used by
     * drag and drop). Returns the new indices of the moved rows.
     */
    public int[] moveRowsTo(int[] indices, int target) {
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        List<ActionRow> moved = new ArrayList<>();
        for (int i = sorted.length - 1; i >= 0; i--) {
            moved.add(0, rows.remove(sorted[i]));
            if (sorted[i] < target) {
                target--;
            }
        }
        rows.addAll(target, moved);
        fireTableDataChanged();
        int[] result = new int[moved.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = target + i;
        }
        return result;
    }

    /** Replace the whole staged content, keeping table wiring intact. */
    public void replaceAll(List<ActionRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    /** Append imported rows (import merges, it does not replace). */
    public void appendRows(List<ActionRow> imported) {
        if (imported.isEmpty()) {
            return;
        }
        int first = rows.size();
        rows.addAll(imported);
        fireTableRowsInserted(first, rows.size() - 1);
    }

    private void swap(int a, int b) {
        ActionRow tmp = rows.get(a);
        rows.set(a, rows.get(b));
        rows.set(b, tmp);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 8;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
        case COLUMN_NUMBER:
            return ActionPanelModule.getString("COL_NUMBER");
        case COLUMN_NAME:
            return ActionPanelModule.getString("COL_NAME");
        case COLUMN_ICON:
            return ActionPanelModule.getString("COL_ICON");
        case COLUMN_ACTION:
            return ActionPanelModule.getString("COL_ACTION");
        case COLUMN_SHORTCUT:
            return ActionPanelModule.getString("COL_SHORTCUT");
        case COLUMN_TEXT_COLOR:
            return ActionPanelModule.getString("COL_TEXT_COLOR");
        case COLUMN_BACKGROUND_COLOR:
            return ActionPanelModule.getString("COL_BACKGROUND_COLOR");
        case COLUMN_BORDER_COLOR:
        default:
            return ActionPanelModule.getString("COL_BORDER_COLOR");
        }
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
        case COLUMN_NUMBER:
            return Integer.class;
        case COLUMN_ICON:
            return Icon.class;
        case COLUMN_TEXT_COLOR:
        case COLUMN_BACKGROUND_COLOR:
        case COLUMN_BORDER_COLOR:
            return java.awt.Color.class;
        default:
            return String.class;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == COLUMN_NAME;
    }

    @Override
    public @Nullable Object getValueAt(int rowIndex, int columnIndex) {
        ActionRow row = rows.get(rowIndex);
        switch (columnIndex) {
        case COLUMN_NUMBER:
            return rowIndex + 1;
        case COLUMN_NAME:
            return row.name();
        case COLUMN_ICON:
            String iconRef = row.iconRef();
            return iconRef == null ? null
                    : IconLoader.load(ActionPanelConfig.resolveIcon(iconRef), IconLoader.TABLE_ICON_SIZE);
        case COLUMN_ACTION:
            return describeAction(row.action());
        case COLUMN_SHORTCUT:
            return shortcutText(row.action());
        case COLUMN_TEXT_COLOR:
            return ActionPanelView.decode(row.textColor());
        case COLUMN_BACKGROUND_COLOR:
            return ActionPanelView.decode(row.backgroundColor());
        case COLUMN_BORDER_COLOR:
        default:
            return ActionPanelView.decode(row.borderColor());
        }
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex == COLUMN_NAME) {
            setRow(rowIndex, rows.get(rowIndex).withName(value == null ? "" : value.toString()));
        }
    }

    /** Short human-readable description of the assigned action. */
    String describeAction(@Nullable ActionSpec spec) {
        return describeAction(spec, catalog);
    }

    /** Shared with the panel view, whose tooltips show the full action. */
    static String describeAction(@Nullable ActionSpec spec, MenuActionCatalog catalog) {
        if (spec == null) {
            return "";
        }
        if (spec instanceof MenuActionSpec menu) {
            MenuActionCatalog.MenuEntry entry = catalog.lookup(menu.actionCommand());
            if (entry == null) {
                return menu.actionCommand();
            }
            return String.join(" > ", entry.getPath()) + " > " + entry.getLabel();
        }
        if (spec instanceof EditorKeyActionSpec editorKey) {
            return ActionPanelModule.getString("ASSIGN_MENU_EDITOR") + ": "
                    + MenuActionCatalog.editorKeyLabel(editorKey.shortcutKey());
        }
        if (spec instanceof ScriptActionSpec script) {
            return ActionPanelModule.getString("ASSIGN_MENU_SCRIPTS") + ": " + script.fileName();
        }
        if (spec instanceof SearchActionSpec search) {
            return ActionPanelModule
                    .getString(search.replace() ? "ASSIGN_MENU_REPLACE" : "ASSIGN_MENU_SEARCH") + ": "
                    + search.query();
        }
        if (spec instanceof SnippetActionSpec snippet) {
            String text = snippet.text().replace('\n', ' ');
            return ActionPanelModule.getString("ASSIGN_MENU_SNIPPET") + ": "
                    + (text.length() > 40 ? text.substring(0, 40) + "…" : text);
        }
        if (spec instanceof AutotextRefActionSpec ref) {
            return ActionPanelModule.getString("ASSIGN_MENU_SNIPPET") + ": " + ref.source();
        }
        if (spec instanceof ActionSpec.UrlActionSpec url) {
            return ActionPanelModule.getString("URL_LABEL") + ": " + url.url();
        }
        if (spec instanceof ColorSchemeActionSpec scheme) {
            return ActionPanelModule.getString("ASSIGN_MENU_COLORSCHEME") + ": " + scheme.ref();
        }
        if (spec instanceof ShortcutSetActionSpec set) {
            return ActionPanelModule.getString("ASSIGN_MENU_SHORTCUTSET") + ": " + set.ref();
        }
        if (spec instanceof PreferenceActionSpec preference) {
            return ActionPanelModule.getString("ASSIGN_MENU_PREFERENCE") + ": " + preference.key();
        }
        if (spec instanceof ActionSpec.ProjectFlagActionSpec flag) {
            return ActionPanelModule.getString("ASSIGN_MENU_PROJECT") + ": "
                    + ProjectFlagActions.label(flag.property());
        }
        // Unknown and any future variant: never throw from a renderer path.
        return spec.type();
    }

    private String shortcutText(@Nullable ActionSpec spec) {
        KeyStroke keyStroke = null;
        if (spec instanceof MenuActionSpec menu) {
            keyStroke = ShortcutLookup.find(PropertiesShortcuts.getMainMenuShortcuts(), menu.actionCommand());
        } else if (spec instanceof EditorKeyActionSpec editorKey) {
            keyStroke = ShortcutLookup.find(PropertiesShortcuts.getEditorShortcuts(), editorKey.shortcutKey());
        }
        return keyStroke == null ? "" : StaticUIUtils.getKeyStrokeText(keyStroke);
    }
}
