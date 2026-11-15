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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import org.jspecify.annotations.Nullable;

import org.omegat.gui.actionpanel.PanelPackage.Contents;
import org.omegat.gui.actionpanel.PanelPackage.Decision;
import org.omegat.gui.actionpanel.PanelPackage.Entry;
import org.omegat.gui.actionpanel.PanelPackage.Kind;
import org.omegat.gui.actionpanel.PanelPackage.Resource;
import org.omegat.util.gui.StaticUIUtils;

/**
 * The two decisions around a panel package, each as one table the keyboard
 * can work through: what to pack on export, what to take on import. Every
 * cell is a plain table cell, so screen readers announce column and value.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
final class PackageDialogs {

    private PackageDialogs() {
    }

    /**
     * Let the user pick the resources to pack. Shipped scripts and
     * resources that are not present start unchecked; autotext is listed for
     * information only. Returns the chosen resources, or null when cancelled.
     */
    static @Nullable List<Resource> chooseForExport(Component parent, List<Resource> resources) {
        ExportModel model = new ExportModel(resources);
        JTable table = createTable(model, ComponentNames.EXPORT_TABLE);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        String intro = MessageFormat.format(ActionPanelModule.getString("EXPORT_INTRO"), resources.size());
        if (!showDialog(parent, ActionPanelModule.getString("EXPORT_TITLE"), intro, table, "EXPORT_CONFIRM")) {
            return null;
        }
        List<Resource> chosen = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            if (model.included[i]) {
                chosen.add(resources.get(i));
            }
        }
        return chosen;
    }

    /**
     * Let the user decide per carried resource. New resources default to
     * import, existing ones to keeping the local copy. Returns the decisions,
     * or null when cancelled.
     */
    static @Nullable Map<Entry, Decision> chooseForImport(Component parent, Contents contents) {
        ImportModel model = new ImportModel(contents);
        JTable table = createTable(model, ComponentNames.IMPORT_TABLE);
        TableColumn decisionColumn = table.getColumnModel().getColumn(3);
        JComboBox<String> decisions = new JComboBox<>(new String[] {
                ActionPanelModule.getString("IMPORT_DECISION_IMPORT"),
                ActionPanelModule.getString("IMPORT_DECISION_KEEP"),
                ActionPanelModule.getString("IMPORT_DECISION_SKIP") });
        decisions.setName(ComponentNames.IMPORT_DECISION_EDITOR);
        decisionColumn.setCellEditor(new DefaultCellEditor(decisions));
        String intro = MessageFormat.format(ActionPanelModule.getString("IMPORT_INTRO"), contents.rows().size(),
                contents.entries().size(), contents.unresolved().size());
        if (!showDialog(parent, ActionPanelModule.getString("IMPORT_TITLE"), intro, table, "IMPORT_CONFIRM")) {
            return null;
        }
        Map<Entry, Decision> result = new LinkedHashMap<>();
        for (int i = 0; i < contents.entries().size(); i++) {
            result.put(contents.entries().get(i), model.decisions[i]);
        }
        return result;
    }

    private static JTable createTable(AbstractTableModel model, String name) {
        JTable table = new JTable(model);
        table.setName(name);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setPreferredScrollableViewportSize(new Dimension(560, Math.min(320, 24 * (model.getRowCount() + 2))));
        table.getAccessibleContext().setAccessibleDescription(ActionPanelModule.getString("PACKAGE_TABLE_A11Y"));
        return table;
    }

    /** Intro label, table, OK/Cancel; the default button confirms, Escape cancels. */
    private static boolean showDialog(Component parent, String title, String intro, JTable table,
            String okKey) {
        AtomicBoolean confirmed = new AtomicBoolean();
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setName(ComponentNames.PACKAGE_DIALOG);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel introLabel = new JLabel(intro);
        introLabel.setLabelFor(table);
        content.add(introLabel, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        JButton ok = new JButton();
        setTextWithMnemonic(ok, ActionPanelModule.getString(okKey));
        ok.setName(ComponentNames.child(dialog.getName(), "ok"));
        ok.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            confirmed.set(true);
            dialog.dispose();
        });
        JButton cancel = new JButton();
        setTextWithMnemonic(cancel, ActionPanelModule.getString("SEARCH_WIZARD_CANCEL"));
        cancel.setName(ComponentNames.child(dialog.getName(), "cancel"));
        cancel.addActionListener(e -> dialog.dispose());
        buttons.add(ok);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        StaticUIUtils.setEscapeClosable(dialog);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(table::requestFocusInWindow);
        dialog.setVisible(true);
        return confirmed.get();
    }

    /** Bundle texts mark the mnemonic with an ampersand, as the main menu does. */
    static void setTextWithMnemonic(AbstractButton button, String text) {
        int marker = text.indexOf('&');
        if (marker >= 0 && marker + 1 < text.length()) {
            button.setText(text.substring(0, marker) + text.substring(marker + 1));
            button.setMnemonic(Character.toUpperCase(text.charAt(marker + 1)));
            button.setDisplayedMnemonicIndex(marker);
        } else {
            button.setText(text);
        }
    }

    /** Export table: include, kind, name, note. */
    private static final class ExportModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<Resource> resources;
        private final boolean[] included;

        ExportModel(List<Resource> resources) {
            this.resources = resources;
            this.included = new boolean[resources.size()];
            for (int i = 0; i < resources.size(); i++) {
                Resource resource = resources.get(i);
                included[i] = resource.packable() && resource.present() && !resource.shipped();
            }
        }

        @Override
        public int getRowCount() {
            return resources.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return ActionPanelModule.getString(switch (column) {
            case 0 -> "PACKAGE_COL_INCLUDE";
            case 1 -> "PACKAGE_COL_KIND";
            case 2 -> "PACKAGE_COL_NAME";
            default -> "PACKAGE_COL_NOTE";
            });
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && resources.get(row).packable() && resources.get(row).present();
        }

        @Override
        public Object getValueAt(int row, int column) {
            Resource resource = resources.get(row);
            return switch (column) {
            case 0 -> included[row];
            case 1 -> resource.kind().label();
            case 2 -> resource.name();
            default -> note(resource);
            };
        }

        private static String note(Resource resource) {
            if (resource.kind() == Kind.AUTOTEXT) {
                return ActionPanelModule.getString("PACKAGE_NOTE_AUTOTEXT");
            }
            if (!resource.present()) {
                return ActionPanelModule.getString("PACKAGE_NOTE_MISSING");
            }
            if (resource.shipped()) {
                return ActionPanelModule.getString("PACKAGE_NOTE_SHIPPED");
            }
            return "";
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && value instanceof Boolean include) {
                included[row] = include;
                fireTableCellUpdated(row, column);
            }
        }
    }

    /** Import table: kind, name, status, decision; unresolved references follow as read-only rows. */
    private static final class ImportModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final Contents contents;
        private final Decision[] decisions;

        ImportModel(Contents contents) {
            this.contents = contents;
            this.decisions = new Decision[contents.entries().size()];
            for (int i = 0; i < decisions.length; i++) {
                decisions[i] = PanelPackage.exists(contents.entries().get(i)) ? Decision.KEEP_EXISTING
                        : Decision.IMPORT;
            }
        }

        @Override
        public int getRowCount() {
            return contents.entries().size() + contents.unresolved().size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return ActionPanelModule.getString(switch (column) {
            case 0 -> "PACKAGE_COL_KIND";
            case 1 -> "PACKAGE_COL_NAME";
            case 2 -> "PACKAGE_COL_STATUS";
            default -> "PACKAGE_COL_DECISION";
            });
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 3 && row < decisions.length;
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (row < decisions.length) {
                Entry entry = contents.entries().get(row);
                return switch (column) {
                case 0 -> entry.kind().label();
                case 1 -> entry.name();
                case 2 -> ActionPanelModule
                        .getString(PanelPackage.exists(entry) ? "PACKAGE_STATUS_EXISTS" : "PACKAGE_STATUS_NEW");
                default -> label(decisions[row]);
                };
            }
            Resource unresolved = contents.unresolved().get(row - decisions.length);
            return switch (column) {
            case 0 -> unresolved.kind().label();
            case 1 -> unresolved.name();
            case 2 -> ActionPanelModule.getString("PACKAGE_STATUS_UNRESOLVED");
            default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 3 && row < decisions.length && value instanceof String chosen) {
                for (Decision decision : Decision.values()) {
                    if (label(decision).equals(chosen)) {
                        decisions[row] = decision;
                    }
                }
                fireTableCellUpdated(row, column);
            }
        }

        private static String label(Decision decision) {
            return ActionPanelModule.getString(switch (decision) {
            case IMPORT -> "IMPORT_DECISION_IMPORT";
            case KEEP_EXISTING -> "IMPORT_DECISION_KEEP";
            case SKIP -> "IMPORT_DECISION_SKIP";
            });
        }
    }
}
