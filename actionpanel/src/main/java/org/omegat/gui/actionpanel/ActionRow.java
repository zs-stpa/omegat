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

import org.jspecify.annotations.Nullable;

/**
 * One configured panel button. The row number shown to the user is the list
 * position and is not stored here. The icon reference is either a path
 * relative to the module's icon folder in the configuration directory, or an
 * absolute path to an external file.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public record ActionRow(String name, @Nullable String iconRef, @Nullable ActionSpec action,
        @Nullable String textColor, @Nullable String backgroundColor, @Nullable String borderColor) {

    public ActionRow(String name, @Nullable String iconRef, @Nullable ActionSpec action) {
        this(name, iconRef, action, null, null, null);
    }

    public ActionRow withName(String newName) {
        return new ActionRow(newName, iconRef, action, textColor, backgroundColor, borderColor);
    }

    public ActionRow withIconRef(@Nullable String newIconRef) {
        return new ActionRow(name, newIconRef, action, textColor, backgroundColor, borderColor);
    }

    public ActionRow withAction(@Nullable ActionSpec newAction) {
        return new ActionRow(name, iconRef, newAction, textColor, backgroundColor, borderColor);
    }

    public ActionRow withColor(int column, @Nullable String hex) {
        switch (column) {
        case 0:
            return new ActionRow(name, iconRef, action, hex, backgroundColor, borderColor);
        case 1:
            return new ActionRow(name, iconRef, action, textColor, hex, borderColor);
        default:
            return new ActionRow(name, iconRef, action, textColor, backgroundColor, hex);
        }
    }
}
