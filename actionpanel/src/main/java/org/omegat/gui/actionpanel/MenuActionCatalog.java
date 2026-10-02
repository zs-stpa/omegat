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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.OStrings;

/**
 * Enumerates the invokable actions of the running application. Main-menu
 * actions are harvested from the live menu bar (so plugin-added items and all
 * view toggles are included); editor and autocompleter actions come from the
 * shortcut catalog, restricted to keys that currently have a keystroke.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class MenuActionCatalog {

    /** One harvested menu item: its action command, label, path and widget. */
    public static final class MenuEntry {
        private final String actionCommand;
        private final String label;
        private final List<String> path;
        private final List<String> menuNames;
        private final JMenuItem item;

        MenuEntry(String actionCommand, String label, List<String> path, List<String> menuNames,
                JMenuItem item) {
            this.actionCommand = actionCommand;
            this.label = label;
            this.path = List.copyOf(path);
            this.menuNames = List.copyOf(menuNames);
            this.item = item;
        }

        /**
         * Component names of the menus on the path, parallel to
         * {@link #getPath()}; a menu without a name contributes its label.
         */
        public List<String> getMenuNames() {
            return menuNames;
        }

        public String getActionCommand() {
            return actionCommand;
        }

        public String getLabel() {
            return label;
        }

        /** Menu titles from the top level down to the item's parent menu. */
        public List<String> getPath() {
            return path;
        }

        public JMenuItem getItem() {
            return item;
        }
    }

    private final Map<String, MenuEntry> menuEntries = new LinkedHashMap<>();

    /** Re-harvest the live menu bar; cheap enough to run per popup. */
    public void rebuild() {
        menuEntries.clear();
        JMenuBar menuBar = Core.getMainWindow().getMainMenu().getMenuBar();
        if (menuBar == null) {
            return;
        }
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null) {
                harvest(menu, new ArrayList<>(List.of(menu.getText())), new ArrayList<>(List.of(nameOf(menu))));
            }
        }
    }

    /** Menus of the menu bar are named after their fields; a nameless one (a plugin's) falls back to its label. */
    private static String nameOf(JMenu menu) {
        return menu.getName() == null || menu.getName().isEmpty() ? menu.getText() : menu.getName();
    }

    private void harvest(JMenu menu, List<String> path, List<String> names) {
        for (java.awt.Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu submenu) {
                path.add(submenu.getText());
                names.add(nameOf(submenu));
                harvest(submenu, path, names);
                path.remove(path.size() - 1);
                names.remove(names.size() - 1);
            } else if (component instanceof JMenuItem item) {
                String command = item.getActionCommand();
                // Items without an explicit command echo their label; only
                // commands set from the handler field names are invokable.
                if (command != null && !command.isEmpty() && !command.equals(item.getText())) {
                    menuEntries.put(command, new MenuEntry(command, item.getText(), path, names, item));
                }
            }
        }
    }

    public Map<String, MenuEntry> getMenuEntries() {
        return menuEntries;
    }

    public @Nullable MenuEntry lookup(String actionCommand) {
        return menuEntries.get(actionCommand);
    }

    /** Editor and autocompleter catalog keys that currently have a keystroke. */
    public static List<String> getMappedEditorKeys() {
        List<String> keys = new ArrayList<>();
        PropertiesShortcuts shortcuts = PropertiesShortcuts.getEditorShortcuts();
        for (String key : shortcuts.getKeys()) {
            String value = shortcuts.getShortcutValue(key);
            if (value != null && !value.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Display label of an editor catalog key: the core bundle carries
     * SHORTCUT_KEY_* labels; an unknown key falls back to the raw key.
     */
    public static String editorKeyLabel(String key) {
        try {
            return OStrings.getString("SHORTCUT_KEY_" + key);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
