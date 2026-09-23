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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One configured row of the panel. The id is the row's identity for the
 * view, UI automation and the persisted file; it never shows and survives
 * renames, reordering and a changed action. The name is the user's label.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public record ActionRow(String id, String name, @Nullable String iconRef, @Nullable ActionSpec action,
        @Nullable String textColor, @Nullable String backgroundColor, @Nullable String borderColor) {

    /** A new row with a fresh id. */
    public ActionRow(String name, @Nullable String iconRef, @Nullable ActionSpec action) {
        this(newId(), name, iconRef, action, null, null, null);
    }

    /** A new row with a fresh id and colours. */
    public ActionRow(String name, @Nullable String iconRef, @Nullable ActionSpec action,
            @Nullable String textColor, @Nullable String backgroundColor, @Nullable String borderColor) {
        this(newId(), name, iconRef, action, textColor, backgroundColor, borderColor);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public ActionRow withName(String newName) {
        return new ActionRow(id, newName, iconRef, action, textColor, backgroundColor, borderColor);
    }

    public ActionRow withIconRef(@Nullable String newIconRef) {
        return new ActionRow(id, name, newIconRef, action, textColor, backgroundColor, borderColor);
    }

    public ActionRow withAction(@Nullable ActionSpec newAction) {
        return new ActionRow(id, name, iconRef, newAction, textColor, backgroundColor, borderColor);
    }

    /** The same row under a new identity: for duplicates and imports. */
    public ActionRow withFreshId() {
        return new ActionRow(newId(), name, iconRef, action, textColor, backgroundColor, borderColor);
    }

    public ActionRow withColor(int column, @Nullable String hex) {
        switch (column) {
        case 0:
            return new ActionRow(id, name, iconRef, action, hex, backgroundColor, borderColor);
        case 1:
            return new ActionRow(id, name, iconRef, action, textColor, hex, borderColor);
        default:
            return new ActionRow(id, name, iconRef, action, textColor, backgroundColor, hex);
        }
    }

    /**
     * The rows with every id unique: a later row repeating an earlier id (a
     * hand-edited file, an import of an export) gets a fresh one. Order is
     * kept; unchanged rows stay the same objects.
     */
    public static List<ActionRow> withUniqueIds(List<ActionRow> rows) {
        Set<String> seen = new HashSet<>();
        List<ActionRow> result = new ArrayList<>(rows.size());
        for (ActionRow row : rows) {
            ActionRow unique = row;
            while (!seen.add(unique.id())) {
                unique = unique.withFreshId();
            }
            result.add(unique);
        }
        return result;
    }
}
