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

import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;

import org.jspecify.annotations.Nullable;

/**
 * Component names of everything the module builds, for UI automation and
 * acceptance tests. Rows are addressed by their stable id, never by their
 * user-editable name or their position: {@code actionpanel.row.<id>} is the
 * wrapper of a labeled control, {@code actionpanel.row.<id>.control} the
 * interactive element itself (button, checkbox, slider or combobox, also
 * when it stands alone without a wrapper), {@code .label}, {@code .icon} and
 * {@code .readout} its companions where present. Every name starts with
 * {@link #PREFIX}.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ComponentNames {

    public static final String PREFIX = "actionpanel.";

    /** The dockable panel itself. */
    public static final String PANEL = PREFIX + "panel";
    /** Hint label shown while no row is configured. */
    public static final String EMPTY_HINT = PREFIX + "empty_hint";

    public static final String ROW_PREFIX = PREFIX + "row.";
    public static final String CONTROL_SUFFIX = ".control";
    public static final String LABEL_SUFFIX = ".label";
    public static final String ICON_SUFFIX = ".icon";
    public static final String READOUT_SUFFIX = ".readout";
    public static final String MENU_SUFFIX = ".menu";

    private static final String PANE_MENU_PREFIX = PREFIX + "menu.";
    private static final String PREFS_PREFIX = PREFIX + "prefs.";
    private static final String ASSIGN_PREFIX = PREFIX + "assign.";

    /** Preferences page: the row table and its search field. */
    public static final String PREFS_TABLE = PREFS_PREFIX + "table";
    public static final String PREFS_SEARCH = PREFS_PREFIX + "search";

    /** Assignment popup: the branch mirroring the menu bar. */
    public static final String ASSIGN_MENU_BAR = ASSIGN_PREFIX + "menubar";

    /** Assignment popup: "add all" entry of a submenu, appended to its name. */
    public static final String ADD_ALL_SUFFIX = ".add_all";
    /** Assignment popup: file chooser entry of a folder submenu. */
    public static final String CHOOSE_FILE_SUFFIX = ".choose_file";
    /** Assignment popup: the two entries of an autotext submenu. */
    public static final String FREEZE_SUFFIX = ".freeze";
    public static final String REFERENCE_SUFFIX = ".reference";

    private ComponentNames() {
    }

    /** Wrapper of a labeled row control; absent for a bare button or checkbox. */
    public static String row(String id) {
        return ROW_PREFIX + id;
    }

    /** The interactive element of a row, wrapped or not. */
    public static String control(String id) {
        return row(id) + CONTROL_SUFFIX;
    }

    public static String label(String id) {
        return row(id) + LABEL_SUFFIX;
    }

    public static String icon(String id) {
        return row(id) + ICON_SUFFIX;
    }

    public static String readout(String id) {
        return row(id) + READOUT_SUFFIX;
    }

    /** Context menu of a row (right click), and its entries by bundle key. */
    public static String rowMenu(String id) {
        return row(id) + MENU_SUFFIX;
    }

    public static String rowMenuEntry(String id, String bundleKey) {
        return rowMenu(id) + "." + lower(bundleKey);
    }

    /** Gear menu of the pane: entries by their bundle key. */
    public static String paneMenu(String bundleKey) {
        return PANE_MENU_PREFIX + lower(bundleKey);
    }

    /** Preferences page: buttons, comboboxes and checkboxes by bundle key. */
    public static String prefs(String bundleKey) {
        return PREFS_PREFIX + lower(bundleKey);
    }

    /** A child entry of a named parent; null keeps an unnamed parent's children unnamed. */
    public static @Nullable String child(JComponent parent, String suffix) {
        return parent.getName() == null ? null : parent.getName() + suffix;
    }

    /**
     * Assignment popup: an entry that opens a dialog rather than assigning
     * directly (search wizard, free text, URL), by its bundle key like
     * {@link #assignBranch}.
     */
    public static String assignEntry(String bundleKey) {
        return assignBranch(bundleKey);
    }

    /**
     * Assignment popup: a branch by its bundle key, with the
     * {@code ASSIGN_MENU_} or {@code ASSIGN_} prefix dropped, so
     * {@code ASSIGN_MENU_EDITOR} becomes {@code actionpanel.assign.editor}.
     */
    public static String assignBranch(String bundleKey) {
        String key = lower(bundleKey);
        for (String prefix : new String[] { "assign_menu_", "assign_" }) {
            if (key.startsWith(prefix)) {
                key = key.substring(prefix.length());
                break;
            }
        }
        return ASSIGN_PREFIX + key;
    }

    /**
     * Assignment popup: a submenu mirroring the menu bar, by the component
     * names of the menus on its path (the menu bar names its menus after
     * their handler fields), e.g. {@code actionpanel.assign.menubar.viewMenu}.
     */
    public static String assignMenuBranch(List<String> menuNames) {
        return ASSIGN_MENU_BAR + "." + String.join(".", menuNames);
    }

    /**
     * Assignment popup: a settings page or group, by its bundle key, e.g.
     * {@code actionpanel.assign.preference.group.preferences_general}. The
     * catalog gives every page a key today; the localized title is the
     * fallback for a keyless group and is then locale-dependent.
     */
    public static String assignPreferenceGroup(@Nullable String titleKey, String title) {
        return assignBranch("ASSIGN_MENU_PREFERENCE") + ".group." + lower(titleKey == null ? title : titleKey);
    }

    /**
     * Assignment popup: an entry that assigns a ready action, by the action
     * type and its identity (see {@link ActionSpec#identity()}), e.g.
     * {@code actionpanel.assign.menu.projectNewMenuItem} or
     * {@code actionpanel.assign.preference.source_font_size}.
     */
    public static String assignLeaf(ActionSpec spec) {
        return ASSIGN_PREFIX + spec.type() + "." + spec.identity();
    }

    /** Assignment popup: an autotext entry's submenu, by the autotext source. */
    public static String assignAutotext(String source) {
        return assignBranch("ASSIGN_MENU_SNIPPET") + ".autotext." + source;
    }

    private static String lower(String key) {
        return key.toLowerCase(Locale.ROOT);
    }
}
