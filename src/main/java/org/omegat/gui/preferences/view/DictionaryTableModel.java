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

package org.omegat.gui.preferences.view;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import org.omegat.core.spellchecker.DictionaryInfo;
import org.omegat.core.spellchecker.DictionaryManager;
import org.omegat.core.spellchecker.DictionaryManager.SpellingDictionaryEntry;
import org.omegat.core.spellchecker.SpellCheckDictionaryType;
import org.omegat.util.OStrings;

/**
 * Rows of the dictionary table in the spell checker preferences: one per
 * dictionary, with the facts of {@link DictionaryInfo} filled in later.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
@SuppressWarnings("serial")
final class DictionaryTableModel extends AbstractTableModel {

    enum Column {
        CODE("GUI_SPELLCHECKER_COL_CODE"), LANGUAGE("GUI_SPELLCHECKER_COL_LANGUAGE"),
        SOURCE("GUI_SPELLCHECKER_COL_SOURCE"), LICENSE("GUI_SPELLCHECKER_COL_LICENSE"),
        ENTRIES("GUI_SPELLCHECKER_COL_ENTRIES"), SIZE("GUI_SPELLCHECKER_COL_SIZE");

        private final String key;

        Column(String key) {
            this.key = key;
        }
    }

    /** Unit the size column is shown in; a header click moves to the next. */
    enum SizeUnit {
        BYTES("GUI_SPELLCHECKER_UNIT_BYTES", 1, 0), KB("GUI_SPELLCHECKER_UNIT_KB", 1024, 1),
        MB("GUI_SPELLCHECKER_UNIT_MB", 1024 * 1024, 1);

        private final String key;
        private final long divisor;
        private final int fractionDigits;

        SizeUnit(String key, long divisor, int fractionDigits) {
            this.key = key;
            this.divisor = divisor;
            this.fractionDigits = fractionDigits;
        }

        String getLabel() {
            return OStrings.getString(key);
        }

        /** The size in this unit, formatted for the GUI locale. */
        String format(long bytes) {
            NumberFormat format = NumberFormat.getNumberInstance();
            format.setMinimumFractionDigits(fractionDigits);
            format.setMaximumFractionDigits(fractionDigits);
            return format.format(bytes / (double) divisor);
        }

        SizeUnit next() {
            SizeUnit[] units = values();
            return units[(ordinal() + 1) % units.length];
        }
    }

    private final List<SpellingDictionaryEntry> entries = new ArrayList<>();
    private final List<DictionaryInfo> infos = new ArrayList<>();
    private SizeUnit sizeUnit = SizeUnit.BYTES;

    void setEntries(Collection<SpellingDictionaryEntry> newEntries) {
        entries.clear();
        infos.clear();
        entries.addAll(newEntries);
        for (int i = 0; i < entries.size(); i++) {
            infos.add(DictionaryInfo.UNKNOWN);
        }
        fireTableDataChanged();
    }

    void setInfo(int row, DictionaryInfo info) {
        if (row < infos.size()) {
            infos.set(row, info);
            fireTableRowsUpdated(row, row);
        }
    }

    SpellingDictionaryEntry getEntry(int row) {
        return entries.get(row);
    }

    DictionaryInfo getInfo(int row) {
        return infos.get(row);
    }

    SizeUnit getSizeUnit() {
        return sizeUnit;
    }

    /**
     * What the entry count measures: Hunspell lists stems, a Morfologik
     * automaton every form the stems and affixes multiply out to.
     */
    static String entriesTooltip(SpellCheckDictionaryType type) {
        return OStrings.getString(type == SpellCheckDictionaryType.HUNSPELL
                ? "GUI_SPELLCHECKER_ENTRIES_HUNSPELL_TOOLTIP" : "GUI_SPELLCHECKER_ENTRIES_MORFOLOGIK_TOOLTIP");
    }

    void cycleSizeUnit() {
        sizeUnit = sizeUnit.next();
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }

    @Override
    public String getColumnName(int column) {
        Column col = Column.values()[column];
        return col == Column.SIZE ? OStrings.getString(col.key, sizeUnit.getLabel()) : OStrings.getString(col.key);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        Column col = Column.values()[column];
        return col == Column.ENTRIES || col == Column.SIZE ? Long.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        SpellingDictionaryEntry entry = entries.get(row);
        DictionaryInfo info = infos.get(row);
        return switch (Column.values()[column]) {
        case CODE -> entry.getLanguageCode();
        case LANGUAGE -> DictionaryManager.getLanguageDisplayName(entry.getLanguageCode());
        case SOURCE -> DictionaryManager.getSourceLabel(entry);
        case LICENSE -> info.license() == null ? "" : info.license();
        case ENTRIES -> info.entries();
        case SIZE -> info.sizeBytes();
        };
    }

    /**
     * Header clicks: the size column cycles its unit, every other column
     * alternates between ascending and descending order.
     */
    static final class Sorter extends TableRowSorter<DictionaryTableModel> {
        private final DictionaryTableModel model;
        private final Runnable onUnitChanged;

        Sorter(DictionaryTableModel model, Runnable onUnitChanged) {
            super(model);
            this.model = model;
            this.onUnitChanged = onUnitChanged;
            setSortable(Column.SIZE.ordinal(), false);
        }

        @Override
        public void toggleSortOrder(int column) {
            if (column == Column.SIZE.ordinal()) {
                model.cycleSizeUnit();
                onUnitChanged.run();
                return;
            }
            SortOrder next = SortOrder.ASCENDING;
            List<? extends RowSorter.SortKey> keys = getSortKeys();
            if (!keys.isEmpty() && keys.get(0).getColumn() == column
                    && keys.get(0).getSortOrder() == SortOrder.ASCENDING) {
                next = SortOrder.DESCENDING;
            }
            setSortKeys(List.of(new RowSorter.SortKey(column, next)));
        }
    }
}
