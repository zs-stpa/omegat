/*
 *  OmegaT - Computer Assisted Translation (CAT) tool
 *           with fuzzy matching, translation memory, keyword search,
 *           glossaries, and translation leveraging into updated projects.
 *
 *  Copyright (C) 2007 Zoltan Bartko
 *                2008-2011 Didier Briel
 *                2012 Martin Fleurke, Didier Briel
 *                2015 Aaron Madlon-Kay
 *                2016 Aaron Madlon-Kay
 *                2026 Stephan Pakebusch
 *                Home page: https://www.omegat.org/
 *                Support center: https://omegat.org/support
 *
 *  This file is part of OmegaT.
 *
 *  OmegaT is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  OmegaT is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.omegat.gui.preferences.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.core.spellchecker.DictionaryInfo;
import org.omegat.core.spellchecker.DictionaryManager;
import org.omegat.core.spellchecker.DictionaryManager.SpellingDictionaryEntry;
import org.omegat.core.spellchecker.ISpellChecker;
import org.omegat.core.spellchecker.SpellCheckerManager;
import org.omegat.gui.preferences.BasePreferencesController;
import org.omegat.gui.preferences.view.DictionaryTableModel.Column;
import org.omegat.util.Language;
import org.omegat.util.Log;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.DesktopWrapper;
import org.omegat.util.gui.StaticUIUtils;

/**
 * @author Zoltan Bartko
 * @author Didier Briel
 * @author Martin Fleurke
 * @author Aaron Madlon-Kay
 * @author stephan.pakebusch at zollsoft.de
 */
public class SpellcheckerConfigurationController extends BasePreferencesController {

    private static final Color LINK_FALLBACK = new Color(0x00, 0x66, 0xCC);

    private SpellcheckerConfigurationPanel panel;
    private DictionaryTableModel tableModel;
    private @Nullable InfoWorker infoWorker;
    private static final int REFRESH_DELAY_MS = 300;
    private @Nullable Timer refresh;

    @Override
    public JComponent getGui() {
        if (panel == null) {
            initGui();
            initFromPrefs();
        }
        return panel;
    }

    @Override
    public String toString() {
        return OStrings.getString("PREFS_TITLE_SPELLCHECKER");
    }

    private void initGui() {
        panel = new SpellcheckerConfigurationPanel();

        panel.autoSpellcheckCheckBox.addActionListener(e -> updateDetailPanel());

        panel.directoryTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                directoryChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                directoryChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                directoryChanged();
            }
        });

        panel.directoryTextField.addActionListener(e -> updateLanguageList());

        panel.directoryChooserButton.addActionListener(e -> chooseDirectory());

        initTable();
        Font label = UIManager.getFont("Label.font");
        panel.summaryTextArea.setFont(label.deriveFont(label.getSize2D() - 2f));
        panel.summaryTextArea.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    }

    private void initTable() {
        tableModel = new DictionaryTableModel();
        JTable table = panel.languageTable;
        table.setModel(tableModel);
        table.setRowSorter(new DictionaryTableModel.Sorter(tableModel, this::sizeUnitChanged));
        table.setFillsViewportHeight(true);
        table.setAutoCreateColumnsFromModel(false);

        NumberFormat integers = NumberFormat.getIntegerInstance();
        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(Column.ENTRIES.ordinal()).setCellRenderer(new NumberRenderer(integers::format) {
            @Override
            public Component getTableCellRendererComponent(JTable t, @Nullable Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setToolTipText(DictionaryTableModel
                        .entriesTooltip(tableModel.getEntry(t.convertRowIndexToModel(row)).getType()));
                return this;
            }
        });
        columns.getColumn(Column.SIZE.ordinal())
                .setCellRenderer(new NumberRenderer(bytes -> tableModel.getSizeUnit().format(bytes)));
        columns.getColumn(Column.LICENSE.ordinal()).setCellRenderer(new LicenseRenderer());
        int narrow = table.getFontMetrics(table.getFont()).stringWidth("00.000.000");
        columns.getColumn(Column.CODE.ordinal()).setPreferredWidth(narrow);
        columns.getColumn(Column.LICENSE.ordinal()).setPreferredWidth(narrow);
        columns.getColumn(Column.ENTRIES.ordinal()).setPreferredWidth(narrow);
        columns.getColumn(Column.SIZE.ordinal()).setPreferredWidth(narrow);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                tableClicked(e);
            }
        });
        table.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean link = upstreamAt(e.getPoint()) != null;
                table.setCursor(Cursor.getPredefinedCursor(link ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
    }

    /** The upstream link under the point when it is on a licence cell. */
    private @Nullable String upstreamAt(Point point) {
        JTable table = panel.languageTable;
        int viewRow = table.rowAtPoint(point);
        int viewColumn = table.columnAtPoint(point);
        if (viewRow < 0 || viewColumn < 0
                || table.convertColumnIndexToModel(viewColumn) != Column.LICENSE.ordinal()) {
            return null;
        }
        return tableModel.getInfo(table.convertRowIndexToModel(viewRow)).upstream();
    }

    /**
     * A single click on a licence link opens the upstream page; a double
     * click anywhere else on a row opens the dictionary file.
     */
    private void tableClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        JTable table = panel.languageTable;
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            return;
        }
        String upstream = upstreamAt(e.getPoint());
        if (upstream != null) {
            if (e.getClickCount() == 1) {
                browse(upstream);
            }
            return;
        }
        if (e.getClickCount() == 2) {
            openDictionary(tableModel.getEntry(table.convertRowIndexToModel(viewRow)));
        }
    }

    private void browse(String url) {
        try {
            DesktopWrapper.browse(URI.create(url));
        } catch (IOException | RuntimeException ex) {
            Log.log(ex);
        }
    }

    private void openDictionary(SpellingDictionaryEntry entry) {
        File dir = getDictDir();
        try {
            Path file = dir == null ? null : new DictionaryManager(dir).locate(entry);
            if (file == null) {
                throw new IOException("No dictionary file for " + entry.getLanguageCode());
            }
            DesktopWrapper.edit(file.toFile());
        } catch (IOException | RuntimeException ex) {
            Log.log(ex);
            JOptionPane.showMessageDialog(panel,
                    OStrings.getString("GUI_SPELLCHECKER_OPEN_FAILED", entry.getLanguageCode()),
                    OStrings.getString("ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sizeUnitChanged() {
        JTable table = panel.languageTable;
        int viewColumn = table.convertColumnIndexToView(Column.SIZE.ordinal());
        if (viewColumn >= 0) {
            table.getColumnModel().getColumn(viewColumn)
                    .setHeaderValue(tableModel.getColumnName(Column.SIZE.ordinal()));
        }
        table.getTableHeader().repaint();
        table.repaint();
    }

    private void chooseDirectory() {
        // open a dialog box to choose the directory
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle(OStrings.getString("GUI_SPELLCHECKER_FILE_CHOOSER_TITLE"));
        if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            // we should write the result into the directory text field
            File file = fileChooser.getSelectedFile();
            panel.directoryTextField.setText(file.getAbsolutePath());
        }
    }

    private @Nullable File getDictDir() {
        String dirName = panel.directoryTextField.getText();

        if (StringUtil.isEmpty(dirName)) {
            return null;
        }

        File dir = new File(dirName);
        if (dir.isFile() || (dir.exists() && !dir.canRead())) {
            return null;
        }

        return dir;
    }

    /** Typing in the directory field restarts the table once the typing pauses. */
    private void directoryChanged() {
        if (refresh == null) {
            refresh = new Timer(REFRESH_DELAY_MS, e -> updateLanguageList());
            refresh.setRepeats(false);
        }
        refresh.restart();
    }

    /**
     * Updates the state of the detail panel based on the check box state
     */
    private void updateDetailPanel() {
        StaticUIUtils.setHierarchyEnabled(panel.detailPanel, panel.autoSpellcheckCheckBox.isSelected());
        directoryChanged();
    }

    /**
     * Updates the dictionary table and its summary based on the directory
     * text field. Entry counts and sizes arrive row by row from a worker.
     */
    private void updateLanguageList() {
        if (infoWorker != null) {
            infoWorker.cancel(true);
            infoWorker = null;
        }
        File dir = getDictDir();
        if (dir == null) {
            tableModel.setEntries(List.of());
            panel.summaryTextArea.setText("");
            return;
        }

        DictionaryManager dicMan = new DictionaryManager(dir);
        List<SpellingDictionaryEntry> entries = new ArrayList<>(dicMan.getLocalDictionaryEntries());
        entries.sort(DictionaryManager.DISPLAY_ORDER);
        tableModel.setEntries(entries);
        panel.summaryTextArea.setText(DictionaryManager.summarize(entries));
        panel.summaryTextArea.setCaretPosition(0);
        infoWorker = new InfoWorker(dicMan, entries);
        infoWorker.execute();
    }

    protected Language getCurrentLanguage() {
        if (Core.getProject().isProjectLoaded()) {
            return Core.getProject().getProjectProperties().getTargetLanguage();
        } else {
            return new Language(Preferences.getPreference(Preferences.TARGET_LOCALE));
        }
    }

    @Override
    protected void initFromPrefs() {
        panel.autoSpellcheckCheckBox
                .setSelected(Preferences.isPreferenceDefault(Preferences.ALLOW_AUTO_SPELLCHECKING, true));
        // initialize things from the preferences
        updateDetailPanel();
        directoryChanged();

        String dictDirPath = Preferences.getPreferenceDefault(Preferences.SPELLCHECKER_DICTIONARY_DIRECTORY,
                SpellCheckerManager.getDefaultDictionaryDir().getPath());
        panel.directoryTextField.setText(dictDirPath);

        // Create dict dir if it doesn't exist, so user can install immediately
        File dictDir = new File(dictDirPath);
        if (!dictDir.exists()) {
            dictDir.mkdirs();
        }
    }

    @Override
    public void restoreDefaults() {
        panel.autoSpellcheckCheckBox.setSelected(true);
        directoryChanged();
        File dictDir = SpellCheckerManager.getDefaultDictionaryDir();
        panel.directoryTextField.setText(dictDir.getPath());
        // Create dict dir if it doesn't exist, so user can install immediately
        if (!dictDir.exists()) {
            dictDir.mkdirs();
        }
    }

    @Override
    public void persist() {
        boolean isNeedToSpell = panel.autoSpellcheckCheckBox.isSelected();
        Preferences.setPreference(Preferences.ALLOW_AUTO_SPELLCHECKING, isNeedToSpell);
        Preferences.setPreference(Preferences.SPELLCHECKER_DICTIONARY_DIRECTORY,
                panel.directoryTextField.getText());
        if (isNeedToSpell && Core.getProject().isProjectLoaded()) {
            ISpellChecker sc = Core.getSpellChecker();
            sc.destroy();
            sc.initialize();
        }
        SwingUtilities.invokeLater(() -> Core.getEditor().getSettings().setAutoSpellChecking(isNeedToSpell));
    }

    /** Reads entry counts and sizes off the EDT, one row at a time. */
    private final class InfoWorker extends SwingWorker<Void, InfoWorker.RowInfo> {
        private record RowInfo(int row, DictionaryInfo info) {
        }

        private final DictionaryManager manager;
        private final List<SpellingDictionaryEntry> entries;

        InfoWorker(DictionaryManager manager, List<SpellingDictionaryEntry> entries) {
            this.manager = manager;
            this.entries = entries;
        }

        @Override
        protected @Nullable Void doInBackground() {
            for (int i = 0; i < entries.size() && !isCancelled(); i++) {
                publish(new RowInfo(i, manager.describe(entries.get(i))));
            }
            return null;
        }

        @Override
        protected void process(List<RowInfo> chunks) {
            if (infoWorker != this) {
                return;
            }
            for (RowInfo chunk : chunks) {
                tableModel.setInfo(chunk.row(), chunk.info());
            }
        }
    }

    /** Numbers trail (right in left-to-right locales), empty when unknown. */
    @SuppressWarnings("serial")
    private static class NumberRenderer extends DefaultTableCellRenderer {
        private final Function<Long, String> format;

        NumberRenderer(Function<Long, String> format) {
            this.format = format;
            setHorizontalAlignment(SwingConstants.TRAILING);
        }

        @Override
        protected void setValue(@Nullable Object value) {
            setText(value instanceof Long bytes ? format.apply(bytes) : "");
        }
    }

    /** The licence as a link to the upstream when one is known. */
    @SuppressWarnings("serial")
    private final class LicenseRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            DictionaryInfo info = tableModel.getInfo(table.convertRowIndexToModel(row));
            String license = info.license() == null ? "" : info.license();
            if (info.upstream() == null || license.isEmpty()) {
                setText(license);
                setToolTipText(null);
                // The link colour of an earlier cell sticks to the renderer.
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                return this;
            }
            setText("<html><u>" + license.replace("&", "&amp;").replace("<", "&lt;") + "</u></html>");
            setToolTipText(info.upstream());
            if (!isSelected) {
                Color link = UIManager.getColor("Component.linkColor");
                setForeground(link != null ? link : LINK_FALLBACK);
            }
            return this;
        }
    }
}
