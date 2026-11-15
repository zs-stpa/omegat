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

import org.omegat.util.Preferences;
import org.omegat.util.StaticUtils;

/**
 * Folder conventions of the panel. Saved colour schemes and shortcut sets are
 * plain export files collected in dedicated folders below the configuration
 * directory; every file there is offered by name in the assignment menu. An
 * action may equally reference a file anywhere else by absolute path.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ActionPanelFolders {

    public static final String COLOR_SCHEME_FOLDER = "colorschemes";
    public static final String SHORTCUT_SET_FOLDER = "shortcutsets";

    private ActionPanelFolders() {
    }

    public static File getColorSchemeFolder() {
        return new File(StaticUtils.getConfigDir(), COLOR_SCHEME_FOLDER);
    }

    public static File getShortcutSetFolder() {
        return new File(StaticUtils.getConfigDir(), SHORTCUT_SET_FOLDER);
    }

    /** A bare name resolves in the scheme folder, anything else is a path. */
    public static File resolveColorScheme(String ref) {
        return resolve(ref, getColorSchemeFolder());
    }

    public static File resolveShortcutSet(String ref) {
        return resolve(ref, getShortcutSetFolder());
    }

    private static File resolve(String ref, File folder) {
        File file = new File(ref);
        if (file.isAbsolute() || ref.indexOf('/') >= 0 || ref.indexOf('\\') >= 0) {
            return file;
        }
        return new File(folder, ref.endsWith(".properties") ? ref : ref + ".properties");
    }

    /** The scripts folder as configured in the scripting window. */
    public static File getScriptsFolder() {
        String dir = Preferences.getPreferenceDefault(Preferences.SCRIPTS_DIRECTORY,
                new File(StaticUtils.installDir(), "scripts").getPath());
        return new File(dir);
    }
}
