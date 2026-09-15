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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;

import org.jspecify.annotations.Nullable;

import org.omegat.gui.preferences.BasePreferencesController;
import org.omegat.util.Log;
import org.omegat.util.Preferences;
import org.omegat.util.gui.TableColumnSizer;
import org.omegat.util.gui.TableSearchField;

/**
 * Settings page of the action panel, injected into the main Preferences
 * dialog through the plugin supplier hook. All edits are staged in the table
 * model and reach the live configuration only in {@link #persist()}.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class ActionPanelPreferencesController extends BasePreferencesController {

    /** Preference key remembering the last icon chooser folder. */
    static final String ICON_DIRECTORY_PREFERENCE = "action_panel_icon_directory";
    /** Preference key remembering the last export/import folder. */
    static final String EXPORT_DIRECTORY_PREFERENCE = "action_panel_export_directory";

    private @Nullable JPanel panel;
    private @Nullable JTable table;
    private @Nullable ActionPanelTableModel model;
    private @Nullable TableRowSorter<ActionPanelTableModel> sorter;
    private final MenuActionCatalog catalog = new MenuActionCatalog();

    @Override
    public JComponent getGui() {
        if (panel == null) {
            initGui();
        }
        return panel;
    }

    @Override
    public String toString() {
        return ActionPanelModule.getString("PREFS_TITLE_ACTION_PANEL");
    }

    @Override
    protected void initFromPrefs() {
        // Handled in initGui: the staged model is created from the live
        // configuration when the page is first shown.
    }

    @Override
    public void undoChanges() {
        if (model != null) {
            stopEditing();
            // Replace the content in place: table, sorter, renderers and the
            // search field keep their wiring.
            model.replaceAll(ActionPanelConfig.getInstance().getRows());
        }
    }

    @Override
    public void persist() {
        if (model != null) {
            stopEditing();
            ActionPanelConfig.getInstance().setRows(model.getRows());
        }
    }

    @Override
    public void restoreDefaults() {
        if (model != null) {
            stopEditing();
            model.removeRows(allModelIndices());
        }
    }

    @Override
    public boolean requiresEditorRefresh() {
        return false;
    }

    private int[] allModelIndices() {
        int[] indices = new int[model.getRowCount()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        return indices;
    }

    private void initGui() {
        catalog.rebuild();
        model = new ActionPanelTableModel(ActionPanelConfig.getInstance().getRows(), catalog);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDragEnabled(true);
        table.setDropMode(DropMode.INSERT_ROWS);
        table.setTransferHandler(new RowMoveHandler());
        table.setFillsViewportHeight(true);
        table.getAccessibleContext().setAccessibleName(toString());
        installSorter();
        // Column widths follow the colour table convention: sized to the
        // content, never clipping, never stretched by surplus table width.
        TableColumnSizer sizer = TableColumnSizer.autoSize(table,
                ActionPanelTableModel.COLUMN_ACTION, false);
        sizer.freezeCurrentWidthsAsMinimum();
        sizer.capWidthToContent(ActionPanelTableModel.COLUMN_NUMBER);
        sizer.capWidthToContent(ActionPanelTableModel.COLUMN_SHORTCUT);
        // The icon cell must never truncate its "assign..." placeholder, even
        // when the width calculation ran while every row had an icon.
        java.awt.FontMetrics metrics = table.getFontMetrics(table.getFont());
        int padding = table.getIntercellSpacing().width + 16;
        int iconMinimum = Math.max(
                metrics.stringWidth(ActionPanelModule.getString("ICON_ASSIGN_PLACEHOLDER")),
                IconLoader.TABLE_ICON_SIZE + 8) + padding;
        sizer.setMinimumFloor(ActionPanelTableModel.COLUMN_ICON, iconMinimum);
        // Colour cells hold a fixed-size swatch: give the columns exactly
        // that width, capped so surplus width flows into the action column.
        int swatchWidth = 24 + padding;
        for (int column : new int[] { ActionPanelTableModel.COLUMN_TEXT_COLOR,
                ActionPanelTableModel.COLUMN_BACKGROUND_COLOR,
                ActionPanelTableModel.COLUMN_BORDER_COLOR }) {
            int width = Math.max(swatchWidth, metrics.stringWidth(model.getColumnName(column)) + padding);
            sizer.setMinimumFloor(column, width);
            sizer.capWidthToContent(column);
            table.getColumnModel().getColumn(column).setPreferredWidth(width);
        }
        table.getTableHeader().setReorderingAllowed(false);
        installCellClickHandling();

        JPanel content = new JPanel(new BorderLayout(6, 6));
        TableSearchField searchField = new TableSearchField(table, sorter);
        // The search field sits above the table only, so its hit counter
        // ends at the table edge, not at the window edge.
        JPanel tablePane = new JPanel(new BorderLayout(6, 6));
        tablePane.add(searchField, BorderLayout.NORTH);
        tablePane.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(tablePane, BorderLayout.CENTER);

        JPanel movePanel = new JPanel();
        movePanel.setLayout(new BoxLayout(movePanel, BoxLayout.Y_AXIS));
        JButton upButton = new JButton(ActionPanelModule.getString("BTN_UP"));
        upButton.addActionListener(e -> moveSelection(true));
        JButton downButton = new JButton(ActionPanelModule.getString("BTN_DOWN"));
        downButton.addActionListener(e -> moveSelection(false));
        movePanel.add(upButton);
        movePanel.add(Box.createVerticalStrut(4));
        movePanel.add(downButton);
        content.add(movePanel, BorderLayout.EAST);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton addButton = new JButton(ActionPanelModule.getString("BTN_ADD"));
        addButton.addActionListener(e -> onAdd(addButton));
        buttons.add(addButton);
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(createButton("BTN_DUPLICATE", this::onDuplicate));
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(createButton("BTN_REMOVE", this::onRemove));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(createButton("BTN_EXPORT", this::onExport));
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(createButton("BTN_IMPORT", this::onImport));
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(buttons);
        south.add(Box.createVerticalStrut(6));
        JPanel displayRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 4, 0));
        JLabel displayLabel = new JLabel(ActionPanelModule.getString("DISPLAY_MODE_LABEL"));
        JComboBox<String> displayCombo = new JComboBox<>(
                new String[] { ActionPanelModule.getString("DISPLAY_MODE_ICON"),
                        ActionPanelModule.getString("DISPLAY_MODE_NAME"),
                        ActionPanelModule.getString("DISPLAY_MODE_BOTH") });
        displayLabel.setLabelFor(displayCombo);
        displayCombo.setSelectedIndex(ActionPanelViewOptions.getDisplayMode().ordinal());
        displayCombo.addActionListener(e -> ActionPanelViewOptions.setDisplayMode(
                ActionPanelViewOptions.DisplayMode.values()[displayCombo.getSelectedIndex()]));
        displayRow.add(displayLabel);
        displayRow.add(displayCombo);
        displayRow.add(Box.createHorizontalStrut(12));
        JLabel layoutLabel = new JLabel(ActionPanelModule.getString("LAYOUT_MODE_LABEL"));
        JComboBox<String> layoutCombo = new JComboBox<>(
                new String[] { ActionPanelModule.getString("LAYOUT_FLOW"),
                        ActionPanelModule.getString("LAYOUT_COLUMNS") });
        layoutLabel.setLabelFor(layoutCombo);
        layoutCombo.setSelectedIndex(ActionPanelViewOptions.getLayoutMode().ordinal());
        layoutCombo.addActionListener(e -> ActionPanelViewOptions.setLayoutMode(
                ActionPanelViewOptions.LayoutMode.values()[layoutCombo.getSelectedIndex()]));
        displayRow.add(layoutLabel);
        displayRow.add(layoutCombo);
        javax.swing.JCheckBox reverseBox = new javax.swing.JCheckBox(
                ActionPanelModule.getString("LAYOUT_REVERSE"), ActionPanelViewOptions.isReverse());
        reverseBox.addActionListener(e -> ActionPanelViewOptions.setReverse(reverseBox.isSelected()));
        displayRow.add(reverseBox);
        south.add(displayRow);
        content.add(south, BorderLayout.SOUTH);

        panel = new JPanel(new BorderLayout());
        panel.add(content, BorderLayout.CENTER);
    }

    private void installSorter() {
        // The sorter filters for the search field and sorts on header
        // clicks; the manual order stays the model order underneath.
        sorter = new TableRowSorter<>(model);
        sorter.setSortable(ActionPanelTableModel.COLUMN_ICON, false);
        sorter.setSortable(ActionPanelTableModel.COLUMN_TEXT_COLOR, false);
        sorter.setSortable(ActionPanelTableModel.COLUMN_BACKGROUND_COLOR, false);
        sorter.setSortable(ActionPanelTableModel.COLUMN_BORDER_COLOR, false);
        table.setRowSorter(sorter);
    }

    private JButton createButton(String key, Runnable action) {
        JButton button = new JButton(ActionPanelModule.getString(key));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void installCellClickHandling() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewColumn < 0) {
                    return;
                }
                int modelRow = table.convertRowIndexToModel(viewRow);
                int modelColumn = table.convertColumnIndexToModel(viewColumn);
                // Colour cells open the chooser on a single click, like the
                // colours table; the other cells need a double click so a
                // single click still selects rows.
                if (modelColumn >= ActionPanelTableModel.COLUMN_TEXT_COLOR
                        && modelColumn <= ActionPanelTableModel.COLUMN_BORDER_COLOR) {
                    onAssignColor(modelRow, modelColumn);
                } else if (e.getClickCount() == 2) {
                    if (modelColumn == ActionPanelTableModel.COLUMN_ICON) {
                        onAssignIcon(modelRow);
                    } else if (modelColumn == ActionPanelTableModel.COLUMN_ACTION) {
                        onAssignAction(modelRow, e);
                    } else if (modelColumn == ActionPanelTableModel.COLUMN_SHORTCUT) {
                        onEditShortcut(modelRow);
                    }
                }
            }
        });
        // Keyboard path: Enter activates the lead cell, Delete clears the
        // icon or colour of the lead cell.
        javax.swing.InputMap inputMap = table
                .getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "actionpanel-activate");
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("DELETE"), "actionpanel-clear");
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("BACK_SPACE"), "actionpanel-clear");
        table.getActionMap().put("actionpanel-activate", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                activateLeadCell();
            }
        });
        table.getActionMap().put("actionpanel-clear", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearLeadCell();
            }
        });
        table.getAccessibleContext()
                .setAccessibleDescription(ActionPanelModule.getString("TABLE_A11Y_DESC"));
        // The icon column shows a textual placeholder while empty.
        table.getColumnModel().getColumn(ActionPanelTableModel.COLUMN_ICON)
                .setCellRenderer(new IconCellRenderer());
        table.getColumnModel().getColumn(ActionPanelTableModel.COLUMN_NAME)
                .setCellRenderer(new NameCellRenderer());
        ColorCellRenderer colorRenderer = new ColorCellRenderer();
        table.getColumnModel().getColumn(ActionPanelTableModel.COLUMN_TEXT_COLOR)
                .setCellRenderer(colorRenderer);
        table.getColumnModel().getColumn(ActionPanelTableModel.COLUMN_BACKGROUND_COLOR)
                .setCellRenderer(colorRenderer);
        table.getColumnModel().getColumn(ActionPanelTableModel.COLUMN_BORDER_COLOR)
                .setCellRenderer(colorRenderer);
    }

    /** Click on a colour cell: open the chooser directly. */
    private void onAssignColor(int modelRow, int modelColumn) {
        int colorIndex = modelColumn - ActionPanelTableModel.COLUMN_TEXT_COLOR;
        java.awt.Color initial = (java.awt.Color) model.getValueAt(modelRow, modelColumn);
        java.awt.Color chosen = javax.swing.JColorChooser.showDialog(panel,
                model.getColumnName(modelColumn), initial != null ? initial : java.awt.Color.GRAY);
        if (chosen != null) {
            String hex = String.format("#%02x%02x%02x", chosen.getRed(), chosen.getGreen(),
                    chosen.getBlue());
            model.setRow(modelRow, model.getRow(modelRow).withColor(colorIndex, hex));
        }
    }

    /** Enter on the lead cell mirrors the mouse gestures of each column. */
    private void activateLeadCell() {
        int viewRow = table.getSelectionModel().getLeadSelectionIndex();
        int viewColumn = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (viewRow < 0 || viewRow >= table.getRowCount() || viewColumn < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        switch (modelColumn) {
        case ActionPanelTableModel.COLUMN_NAME:
            table.editCellAt(viewRow, viewColumn);
            Component editor = table.getEditorComponent();
            if (editor != null) {
                editor.requestFocusInWindow();
            }
            break;
        case ActionPanelTableModel.COLUMN_ICON:
            onAssignIcon(modelRow);
            break;
        case ActionPanelTableModel.COLUMN_ACTION:
            java.awt.Rectangle cell = table.getCellRect(viewRow, viewColumn, true);
            JPopupMenu popup = AssignMenuBuilder.build(panel, catalog,
                    (spec, label) -> model.setRow(modelRow, model.getRow(modelRow).withAction(spec)));
            popup.show(table, cell.x, cell.y + cell.height);
            break;
        case ActionPanelTableModel.COLUMN_SHORTCUT:
            onEditShortcut(modelRow);
            break;
        case ActionPanelTableModel.COLUMN_TEXT_COLOR:
        case ActionPanelTableModel.COLUMN_BACKGROUND_COLOR:
        case ActionPanelTableModel.COLUMN_BORDER_COLOR:
            onAssignColor(modelRow, modelColumn);
            break;
        default:
            break;
        }
    }

    /** Delete on the lead cell clears its icon or colour. */
    private void clearLeadCell() {
        int viewRow = table.getSelectionModel().getLeadSelectionIndex();
        int viewColumn = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (viewRow < 0 || viewRow >= table.getRowCount() || viewColumn < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        if (modelColumn == ActionPanelTableModel.COLUMN_ICON) {
            model.setRow(modelRow, model.getRow(modelRow).withIconRef(null));
        } else if (modelColumn >= ActionPanelTableModel.COLUMN_TEXT_COLOR
                && modelColumn <= ActionPanelTableModel.COLUMN_BORDER_COLOR) {
            model.setRow(modelRow, model.getRow(modelRow)
                    .withColor(modelColumn - ActionPanelTableModel.COLUMN_TEXT_COLOR, null));
        }
    }

    /**
     * Double click or Enter on the shortcut cell: edit the binding of the
     * underlying catalog action through the shared shortcut store. The
     * shortcuts preferences page reflects the change automatically; conflict
     * resolution lives there, this path only warns.
     */
    private void onEditShortcut(int modelRow) {
        ActionSpec spec = model.getRow(modelRow).action();
        org.omegat.gui.shortcuts.PropertiesShortcuts shortcuts;
        String key;
        if (spec instanceof ActionSpec.MenuActionSpec menu) {
            shortcuts = org.omegat.gui.shortcuts.PropertiesShortcuts.getMainMenuShortcuts();
            key = menu.actionCommand();
        } else if (spec instanceof ActionSpec.EditorKeyActionSpec editorKey) {
            shortcuts = org.omegat.gui.shortcuts.PropertiesShortcuts.getEditorShortcuts();
            key = editorKey.shortcutKey();
        } else {
            return;
        }
        javax.swing.KeyStroke current = ShortcutLookup.find(shortcuts, key);
        org.omegat.gui.dialogs.ShortcutEditorDialog dialog =
                new org.omegat.gui.dialogs.ShortcutEditorDialog(current);
        if (!dialog.show(javax.swing.SwingUtilities.getWindowAncestor(panel),
                model.getRow(modelRow).name())) {
            return;
        }
        javax.swing.KeyStroke result = dialog.getResult();
        if (result != null) {
            String conflictKey = findSameScopeConflictKey(shortcuts, key, result);
            if (conflictKey != null) {
                Object[] options = { ActionPanelModule.getString("SHORTCUT_CONFLICT_OVERRIDE"),
                        ActionPanelModule.getString("SHORTCUT_CONFLICT_KEEP_BOTH"),
                        ActionPanelModule.getString("SHORTCUT_CONFLICT_CANCEL") };
                int choice = JOptionPane.showOptionDialog(panel,
                        java.text.MessageFormat.format(
                                ActionPanelModule.getString("SHORTCUT_CONFLICT_WARN"),
                                org.omegat.util.gui.StaticUIUtils.getKeyStrokeText(result),
                                conflictLabel(conflictKey)),
                        model.getColumnName(ActionPanelTableModel.COLUMN_SHORTCUT),
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
                        options[0]);
                if (choice == 0) {
                    // Override: the other action loses its binding.
                    shortcuts.setShortcut(conflictKey, null);
                } else if (choice != 1) {
                    return;
                }
            }
        }
        shortcuts.setShortcut(key, result);
        try {
            shortcuts.save();
        } catch (IOException ex) {
            Log.log(ex);
        }
        model.fireTableRowsUpdated(modelRow, modelRow);
    }

    private @Nullable String findSameScopeConflictKey(
            org.omegat.gui.shortcuts.PropertiesShortcuts shortcuts, String ownKey,
            javax.swing.KeyStroke candidate) {
        for (String other : shortcuts.getKeys()) {
            if (!other.equals(ownKey) && candidate.equals(ShortcutLookup.find(shortcuts, other))) {
                return other;
            }
        }
        return null;
    }

    private String conflictLabel(String key) {
        MenuActionCatalog.MenuEntry entry = catalog.lookup(key);
        return entry != null ? entry.getLabel() : MenuActionCatalog.editorKeyLabel(key);
    }

    /** Colour swatch plus its hex value, or an em dash while unset. */
    @SuppressWarnings("serial")
    private static final class ColorCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(t, null, isSelected, hasFocus,
                    row, column);
            if (value instanceof java.awt.Color color) {
                String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(),
                        color.getBlue());
                label.setIcon(new SwatchIcon(color));
                label.setText(null);
                label.setEnabled(true);
                label.setToolTipText(hex);
                label.getAccessibleContext().setAccessibleName(hex);
            } else {
                label.setIcon(null);
                label.setText("\u2014");
                label.setEnabled(false);
                label.setToolTipText(null);
                label.getAccessibleContext()
                        .setAccessibleName(ActionPanelModule.getString("COLOR_RESET"));
            }
            label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            return label;
        }
    }

    /** Small filled rectangle with a neutral outline. */
    private record SwatchIcon(java.awt.Color color) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, getIconWidth(), getIconHeight());
            g.setColor(java.awt.Color.GRAY);
            g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    /** Name cells in a fallback-capable font where the table font lacks glyphs. */
    @SuppressWarnings("serial")
    private static final class NameCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row,
                    column);
            c.setFont(FallbackFonts.withGlyphFallback(t.getFont(),
                    value == null ? null : value.toString()));
            return c;
        }
    }

    @SuppressWarnings("serial")
    private static final class IconCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(t, null, isSelected, hasFocus,
                    row, column);
            if (value instanceof javax.swing.Icon icon) {
                label.setIcon(new ChipIcon(icon));
                label.setText(null);
                label.setEnabled(true);
            } else {
                label.setIcon(null);
                label.setText(ActionPanelModule.getString("ICON_ASSIGN_PLACEHOLDER"));
                label.setEnabled(false);
            }
            return label;
        }
    }

    /**
     * An open cell editor must never survive a structural table change: its
     * commit would land on a shifted row and leave the view broken. Stop it
     * (keeping the typed value) before every mutating operation.
     */
    private void stopEditing() {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
            table.getCellEditor().cancelCellEditing();
        }
    }

    /**
     * Add opens the assignment menu right at the button: a leaf adds one
     * prefilled row, an "add all" node adds every action of that branch as
     * its own row, names and actions prefilled.
     */
    private void onAdd(JButton addButton) {
        stopEditing();
        JPopupMenu popup = AssignMenuBuilder.build(panel, catalog,
                (spec, label) -> appendNewRows(List.of(new ActionRow(label, null, spec))),
                entries -> appendNewRows(entries.stream()
                        .map(entry -> new ActionRow(entry.label(), null, entry.spec())).toList()));
        popup.show(addButton, 0, addButton.getHeight());
    }

    private void appendNewRows(List<ActionRow> rows) {
        stopEditing();
        int first = model.getRowCount();
        model.appendRows(rows);
        table.clearSelection();
        for (int i = first; i < model.getRowCount(); i++) {
            int viewRow = table.convertRowIndexToView(i);
            if (viewRow >= 0) {
                table.addRowSelectionInterval(viewRow, viewRow);
            }
        }
        scrollToModelRow(model.getRowCount() - 1);
    }

    private void onDuplicate() {
        stopEditing();
        int[] selection = selectedModelIndices();
        if (selection.length > 0) {
            selectModelRow(model.duplicateRows(selection));
        }
    }

    private void onRemove() {
        stopEditing();
        int[] selection = selectedModelIndices();
        if (selection.length > 0) {
            model.removeRows(selection);
        }
    }

    private void moveSelection(boolean up) {
        stopEditing();
        int[] selection = selectedModelIndices();
        if (selection.length == 0) {
            return;
        }
        int[] moved = model.moveRows(selection, up);
        table.clearSelection();
        for (int index : moved) {
            int viewIndex = table.convertRowIndexToView(index);
            if (viewIndex >= 0) {
                table.addRowSelectionInterval(viewIndex, viewIndex);
            }
        }
        if (moved.length > 0) {
            scrollToModelRow(moved[0]);
        }
    }

    private void onAssignIcon(int modelRow) {
        JFileChooser chooser = new JFileChooser(
                Preferences.getPreferenceDefault(ICON_DIRECTORY_PREFERENCE, null));
        chooser.setDialogTitle(ActionPanelModule.getString("ICON_CHOOSER_TITLE"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                ActionPanelModule.getString("ICON_FILTER_DESC"), "svg", "png", "jpg", "jpeg", "gif",
                "pdf"));
        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        Preferences.setPreference(ICON_DIRECTORY_PREFERENCE, chosen.getParent());
        Object[] options = { ActionPanelModule.getString("ICON_COPY"),
                ActionPanelModule.getString("ICON_REFERENCE") };
        int mode = JOptionPane.showOptionDialog(panel, ActionPanelModule.getString("ICON_COPY_QUESTION"),
                ActionPanelModule.getString("ICON_CHOOSER_TITLE"), JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (mode == JOptionPane.CLOSED_OPTION) {
            return;
        }
        String iconRef;
        if (mode == 0) {
            try {
                iconRef = copyIntoIconFolder(chosen);
            } catch (IOException ex) {
                Log.log(ex);
                JOptionPane.showMessageDialog(panel, ex.getLocalizedMessage(), toString(),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            iconRef = chosen.getAbsolutePath();
        }
        model.setRow(modelRow, model.getRow(modelRow).withIconRef(iconRef));
    }

    /** Copy into the icon folder, dodging name collisions with a suffix. */
    static String copyIntoIconFolder(File chosen) throws IOException {
        File folder = ActionPanelConfig.getIconFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create " + folder);
        }
        String name = chosen.getName();
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        File target = new File(folder, name);
        for (int i = 2; target.exists(); i++) {
            target = new File(folder, base + "-" + i + extension);
        }
        Files.copy(chosen.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return ActionPanelConfig.ICON_FOLDER + "/" + target.getName();
    }

    private void onAssignAction(int modelRow, MouseEvent e) {
        JPopupMenu popup = AssignMenuBuilder.build(panel, catalog,
                (spec, label) -> model.setRow(modelRow, model.getRow(modelRow).withAction(spec)));
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void onExport() {
        JFileChooser chooser = exportChooser();
        chooser.setDialogTitle(ActionPanelModule.getString("EXPORT_TITLE"));
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().contains(".")) {
            file = new File(file.getParentFile(), file.getName() + ".xml");
        }
        Preferences.setPreference(EXPORT_DIRECTORY_PREFERENCE, file.getParent());
        try {
            ActionPanelXML.write(model.getRows(), file);
        } catch (IOException ex) {
            Log.log(ex);
            JOptionPane.showMessageDialog(panel, ex.getLocalizedMessage(), toString(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onImport() {
        JFileChooser chooser = exportChooser();
        chooser.setDialogTitle(ActionPanelModule.getString("IMPORT_TITLE"));
        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Preferences.setPreference(EXPORT_DIRECTORY_PREFERENCE, chooser.getSelectedFile().getParent());
        try {
            List<ActionRow> imported = ActionPanelXML.read(chooser.getSelectedFile());
            stopEditing();
            model.appendRows(imported);
        } catch (IOException ex) {
            Log.log(ex);
            JOptionPane.showMessageDialog(panel, ex.getLocalizedMessage(), toString(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private JFileChooser exportChooser() {
        JFileChooser chooser = new JFileChooser(
                Preferences.getPreferenceDefault(EXPORT_DIRECTORY_PREFERENCE, null));
        chooser.setFileFilter(new FileNameExtensionFilter("*.xml", "xml"));
        return chooser;
    }

    private int[] selectedModelIndices() {
        int[] viewRows = table.getSelectedRows();
        int[] modelRows = new int[viewRows.length];
        for (int i = 0; i < viewRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(viewRows[i]);
        }
        return modelRows;
    }

    private void selectModelRow(int modelRow) {
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            table.setRowSelectionInterval(viewRow, viewRow);
            scrollToModelRow(modelRow);
        }
    }

    private void scrollToModelRow(int modelRow) {
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            Rectangle rect = table.getCellRect(viewRow, 0, true);
            table.scrollRectToVisible(rect);
        }
    }

    /**
     * Neutral mid-tone chip behind an icon so light glyphs stay visible on
     * light table rows and dark glyphs on dark ones.
     */
    private record ChipIcon(javax.swing.Icon icon) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(new java.awt.Color(127, 127, 127, 70));
            g.fillRoundRect(x - 2, y - 2, getIconWidth() + 4, getIconHeight() + 4, 6, 6);
            icon.paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth() {
            return icon.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return icon.getIconHeight();
        }
    }

    /** Drag and drop row reordering, complementing the Up/Down buttons. */
    @SuppressWarnings("serial")
    private final class RowMoveHandler extends TransferHandler {

        private final DataFlavor flavor = new DataFlavor(int[].class, "row indices");

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected @Nullable Transferable createTransferable(JComponent c) {
            int[] indices = selectedModelIndices();
            if (indices.length == 0) {
                return null;
            }
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { flavor };
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor f) {
                    return flavor.equals(f);
                }

                @Override
                public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                    if (!flavor.equals(f)) {
                        throw new UnsupportedFlavorException(f);
                    }
                    return indices;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(flavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                int[] indices = (int[]) support.getTransferable().getTransferData(flavor);
                JTable.DropLocation drop = (JTable.DropLocation) support.getDropLocation();
                int viewRow = drop.getRow();
                int target = viewRow >= table.getRowCount() ? model.getRowCount()
                        : table.convertRowIndexToModel(viewRow);
                int[] moved = model.moveRowsTo(indices, target);
                table.clearSelection();
                for (int index : moved) {
                    int movedView = table.convertRowIndexToView(index);
                    if (movedView >= 0) {
                        table.addRowSelectionInterval(movedView, movedView);
                    }
                }
                return true;
            } catch (UnsupportedFlavorException | IOException ex) {
                Log.log(ex);
                return false;
            }
        }
    }
}
