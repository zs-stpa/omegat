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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import org.omegat.util.Log;
import org.omegat.util.StaticUtils;

/**
 * The live panel configuration: an ordered list of rows, loaded from and
 * saved to the configuration directory. Listeners are told after every
 * replacement so the panel can rebuild its buttons.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ActionPanelConfig {

    public static final String CONFIG_FILE_NAME = "actionpanel.xml";
    /** Folder below the configuration directory holding copied icon files. */
    public static final String ICON_FOLDER = "actionpanel/icons";

    private static final ActionPanelConfig INSTANCE = new ActionPanelConfig();

    private volatile List<ActionRow> rows = List.of();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ActionPanelConfig() {
    }

    public static ActionPanelConfig getInstance() {
        return INSTANCE;
    }

    public List<ActionRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * Replace the configuration, persist it, and notify listeners. Row ids
     * are made unique on the way in; the caller's list is not touched.
     */
    public void setRows(List<ActionRow> newRows) {
        rows = List.copyOf(ActionRow.withUniqueIds(newRows));
        try {
            ActionPanelXML.write(rows, getConfigFile());
        } catch (IOException e) {
            Log.log(e);
        }
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /**
     * Load the stored configuration; a missing file means an empty panel.
     * Rows that came without an id (or with a repeated one) are written
     * back at once, so their ids stay stable from here on.
     */
    public void load() {
        File file = getConfigFile();
        if (!file.isFile()) {
            rows = List.of();
            return;
        }
        try {
            ActionPanelXML.ReadResult result = ActionPanelXML.readWithReport(file);
            List<ActionRow> unique = ActionRow.withUniqueIds(result.rows());
            rows = List.copyOf(unique);
            if (result.idsAdded() || !unique.equals(result.rows())) {
                ActionPanelXML.write(rows, file);
            }
        } catch (IOException e) {
            Log.log(e);
        }
    }

    /**
     * Change one row, found by id (it may have been renamed or moved since
     * the caller saw it), persist and notify. False when the row is gone.
     */
    public boolean updateRow(String id, UnaryOperator<ActionRow> change) {
        List<ActionRow> updated = new ArrayList<>(rows);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(id)) {
                updated.set(i, change.apply(updated.get(i)));
                setRows(updated);
                return true;
            }
        }
        return false;
    }

    /** Notify listeners about a view option change without new rows. */
    public void notifyChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public void addChangeListener(Runnable listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    static File getConfigFile() {
        return new File(StaticUtils.getConfigDir(), CONFIG_FILE_NAME);
    }

    static File getIconFolder() {
        return new File(StaticUtils.getConfigDir(), ICON_FOLDER);
    }

    /** Resolve an icon reference: relative names live in the icon folder. */
    static File resolveIcon(String iconRef) {
        File file = new File(iconRef);
        return file.isAbsolute() ? file : new File(StaticUtils.getConfigDir(), iconRef);
    }
}
