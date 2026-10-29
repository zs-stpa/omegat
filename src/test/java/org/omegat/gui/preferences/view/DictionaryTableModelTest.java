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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.text.NumberFormat;
import java.util.List;

import javax.swing.SortOrder;

import org.junit.Before;
import org.junit.Test;

import org.omegat.core.spellchecker.DictionaryInfo;
import org.omegat.core.spellchecker.DictionaryManager.SpellingDictionaryEntry;
import org.omegat.core.spellchecker.SpellCheckDictionaryType;
import org.omegat.gui.preferences.view.DictionaryTableModel.Column;
import org.omegat.gui.preferences.view.DictionaryTableModel.SizeUnit;
import org.omegat.util.OStrings;

/**
 * Header clicks on the dictionary table: the size column cycles its unit,
 * other columns alternate between ascending and descending order; numbers
 * follow the GUI locale.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class DictionaryTableModelTest {

    private DictionaryTableModel model;
    private int unitChanges;
    private DictionaryTableModel.Sorter sorter;

    @Before
    public void setUp() {
        model = new DictionaryTableModel();
        model.setEntries(List.of(new SpellingDictionaryEntry("zz_BB", SpellCheckDictionaryType.HUNSPELL, false),
                new SpellingDictionaryEntry("zz_AA", SpellCheckDictionaryType.MORFOLOGIK, true)));
        model.setInfo(0, new DictionaryInfo("MIT", "https://example.org/zz", 2048L, 3L * 1024 * 1024));
        sorter = new DictionaryTableModel.Sorter(model, () -> unitChanges++);
    }

    @Test
    public void testSizeHeaderCyclesUnitInsteadOfSorting() {
        assertEquals(SizeUnit.BYTES, model.getSizeUnit());
        sorter.toggleSortOrder(Column.SIZE.ordinal());
        assertEquals(SizeUnit.KB, model.getSizeUnit());
        assertEquals(1, unitChanges);
        assertEquals("no sort key from the size header", 0, sorter.getSortKeys().size());
        sorter.toggleSortOrder(Column.SIZE.ordinal());
        sorter.toggleSortOrder(Column.SIZE.ordinal());
        assertEquals("back to bytes after three clicks", SizeUnit.BYTES, model.getSizeUnit());
        String header = model.getColumnName(Column.SIZE.ordinal());
        assertEquals(OStrings.getString("GUI_SPELLCHECKER_COL_SIZE", SizeUnit.BYTES.getLabel()), header);
    }

    @Test
    public void testOtherHeadersAlternateAscendingAndDescending() {
        int code = Column.CODE.ordinal();
        sorter.toggleSortOrder(code);
        assertEquals(SortOrder.ASCENDING, sorter.getSortKeys().get(0).getSortOrder());
        assertEquals("zz_AA", model.getValueAt(sorter.convertRowIndexToModel(0), code));
        sorter.toggleSortOrder(code);
        assertEquals(SortOrder.DESCENDING, sorter.getSortKeys().get(0).getSortOrder());
        assertEquals("zz_BB", model.getValueAt(sorter.convertRowIndexToModel(0), code));
        sorter.toggleSortOrder(code);
        assertEquals("never unsorted", SortOrder.ASCENDING, sorter.getSortKeys().get(0).getSortOrder());
        // Switching columns starts ascending again.
        sorter.toggleSortOrder(Column.ENTRIES.ordinal());
        assertEquals(SortOrder.ASCENDING, sorter.getSortKeys().get(0).getSortOrder());
        assertEquals(Column.ENTRIES.ordinal(), sorter.getSortKeys().get(0).getColumn());
    }

    @Test
    public void testUnitsFormatForTheGuiLocale() {
        long threeMegabytes = 3L * 1024 * 1024;
        NumberFormat integer = NumberFormat.getIntegerInstance();
        assertEquals(integer.format(threeMegabytes), SizeUnit.BYTES.format(threeMegabytes));
        NumberFormat oneDecimal = NumberFormat.getNumberInstance();
        oneDecimal.setMinimumFractionDigits(1);
        oneDecimal.setMaximumFractionDigits(1);
        assertEquals(oneDecimal.format(3072.0), SizeUnit.KB.format(threeMegabytes));
        assertEquals(oneDecimal.format(3.0), SizeUnit.MB.format(threeMegabytes));
    }

    @Test
    public void testEntriesTooltipNamesWhatIsCounted() {
        assertEquals(OStrings.getString("GUI_SPELLCHECKER_ENTRIES_HUNSPELL_TOOLTIP"),
                DictionaryTableModel.entriesTooltip(SpellCheckDictionaryType.HUNSPELL));
        assertEquals(OStrings.getString("GUI_SPELLCHECKER_ENTRIES_MORFOLOGIK_TOOLTIP"),
                DictionaryTableModel.entriesTooltip(SpellCheckDictionaryType.MORFOLOGIK));
    }

    @Test
    public void testValuesComeFromEntryAndInfo() {
        assertEquals("zz_BB", model.getValueAt(0, Column.CODE.ordinal()));
        assertEquals("MIT", model.getValueAt(0, Column.LICENSE.ordinal()));
        assertEquals(2048L, model.getValueAt(0, Column.ENTRIES.ordinal()));
        assertEquals(3L * 1024 * 1024, model.getValueAt(0, Column.SIZE.ordinal()));
        assertEquals("unknown facts show as empty", "", model.getValueAt(1, Column.LICENSE.ordinal()));
        assertNull(model.getValueAt(1, Column.ENTRIES.ordinal()));
        assertEquals(Long.class, model.getColumnClass(Column.SIZE.ordinal()));
        assertEquals(OStrings.getString("GUI_SPELLCHECKER_SOURCE_INSTALLED", "Morfologik"),
                model.getValueAt(1, Column.SOURCE.ordinal()));
    }
}
