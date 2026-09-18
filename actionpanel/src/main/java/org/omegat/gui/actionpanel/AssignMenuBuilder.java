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

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jspecify.annotations.Nullable;

import org.omegat.gui.actionpanel.ActionSpec.AutotextRefActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ColorSchemeActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.EditorKeyActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.MenuActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ScriptActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ShortcutSetActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SnippetActionSpec;
import org.omegat.gui.editor.autotext.Autotext;
import org.omegat.gui.scripting.ScriptRunner;

/**
 * Builds the multi-level assignment popup out of every available action
 * source: the live menu bar, the currently mapped editor and autocompleter
 * shortcuts, the scripts folder, a preset search, text snippets, and the
 * settings tree of the preferences dialog (including saved colour schemes
 * and shortcut sets).
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class AssignMenuBuilder {

    /** Client property carrying the ready ActionSpec of a leaf menu item. */
    private static final String SPEC_PROPERTY = "actionpanel.spec";

    /** A chosen action plus the label to prefill as the row name. */
    public record AssignEntry(ActionSpec spec, String label) {
    }

    /** Receives a single chosen action with its display label. */
    @FunctionalInterface
    public interface AssignTarget {
        void assign(ActionSpec spec, String label);
    }

    /** Receives all leaf actions of a chosen intermediate node at once. */
    @FunctionalInterface
    public interface BulkTarget {
        void addAll(List<AssignEntry> entries);
    }

    private AssignMenuBuilder() {
    }

    public static JPopupMenu build(Component parent, MenuActionCatalog catalog, AssignTarget onAssign) {
        return build(parent, catalog, onAssign, null);
    }

    /**
     * With a bulk target, every submenu holding more than one ready action
     * gets a leading "add all" entry that, after a confirmation, hands all
     * its leaf actions over at once — names and actions prefilled.
     */
    public static JPopupMenu build(Component parent, MenuActionCatalog catalog, AssignTarget onAssign,
            @Nullable BulkTarget bulk) {
        JPopupMenu popup = new JPopupMenu();
        popup.add(buildMenuBranch(catalog, onAssign));
        popup.add(buildEditorBranch(onAssign));
        popup.add(buildScriptsBranch(onAssign));
        popup.add(buildSearchItem(parent, onAssign, false));
        popup.add(buildSearchItem(parent, onAssign, true));
        popup.add(buildSnippetBranch(parent, onAssign));
        popup.add(buildPreferenceBranch(parent, onAssign));
        if (bulk != null) {
            for (Component component : popup.getComponents()) {
                if (component instanceof JMenu menu) {
                    insertBulkEntries(menu, parent, bulk);
                }
            }
        }
        return popup;
    }

    /** Leaf item: carries its spec for bulk collection, assigns on click. */
    private static JMenuItem leaf(String label, ActionSpec spec, AssignTarget onAssign) {
        JMenuItem item = new JMenuItem(label);
        item.putClientProperty(SPEC_PROPERTY, spec);
        item.addActionListener(e -> onAssign.assign(spec, label));
        return item;
    }

    private static void collectLeaves(JMenu menu, List<AssignEntry> entries) {
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu submenu) {
                collectLeaves(submenu, entries);
            } else if (component instanceof JMenuItem item
                    && item.getClientProperty(SPEC_PROPERTY) instanceof ActionSpec spec) {
                entries.add(new AssignEntry(spec, item.getText()));
            }
        }
    }

    /** Recursively add "add all" entries to submenus with several actions. */
    private static void insertBulkEntries(JMenu menu, Component parent, BulkTarget bulk) {
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu submenu) {
                insertBulkEntries(submenu, parent, bulk);
            }
        }
        List<AssignEntry> entries = new ArrayList<>();
        collectLeaves(menu, entries);
        if (entries.size() < 2) {
            return;
        }
        JMenuItem addAll = new JMenuItem(java.text.MessageFormat
                .format(ActionPanelModule.getString("ADD_ALL_CHILDREN"), entries.size()));
        addAll.addActionListener(e -> {
            // Collect again at click time: submenus below were extended too.
            List<AssignEntry> confirmed = new ArrayList<>();
            collectLeaves(menu, confirmed);
            if (JOptionPane.showConfirmDialog(parent,
                    java.text.MessageFormat.format(ActionPanelModule.getString("CONFIRM_ADD_ALL"),
                            confirmed.size()),
                    menu.getText(), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
                bulk.addAll(confirmed);
            }
        });
        menu.insert(addAll, 0);
        menu.insertSeparator(1);
    }

    /** Mirror the live menu bar as a submenu tree. */
    private static JMenu buildMenuBranch(MenuActionCatalog catalog, AssignTarget onAssign) {
        JMenu root = new JMenu(ActionPanelModule.getString("ASSIGN_MENU_ROOT_MENU"));
        catalog.rebuild();
        Map<List<String>, JMenu> menus = new HashMap<>();
        for (MenuActionCatalog.MenuEntry entry : catalog.getMenuEntries().values()) {
            JMenu parent = menuForPath(root, menus, entry.getPath());
            parent.add(leaf(entry.getLabel(), new MenuActionSpec(entry.getActionCommand()), onAssign));
        }
        return root;
    }

    private static JMenu menuForPath(JMenu root, Map<List<String>, JMenu> menus, List<String> path) {
        if (path.isEmpty()) {
            return root;
        }
        JMenu existing = menus.get(path);
        if (existing != null) {
            return existing;
        }
        JMenu parent = menuForPath(root, menus, path.subList(0, path.size() - 1));
        JMenu menu = new JMenu(path.get(path.size() - 1));
        parent.add(menu);
        menus.put(List.copyOf(path), menu);
        return menu;
    }

    private static JMenu buildEditorBranch(AssignTarget onAssign) {
        JMenu menu = new JMenu(ActionPanelModule.getString("ASSIGN_MENU_EDITOR"));
        for (String key : MenuActionCatalog.getMappedEditorKeys()) {
            menu.add(leaf(MenuActionCatalog.editorKeyLabel(key), new EditorKeyActionSpec(key), onAssign));
        }
        menu.setEnabled(menu.getItemCount() > 0);
        return menu;
    }

    private static JMenu buildScriptsBranch(AssignTarget onAssign) {
        JMenu menu = new JMenu(ActionPanelModule.getString("ASSIGN_MENU_SCRIPTS"));
        File folder = ActionPanelFolders.getScriptsFolder();
        List<String> extensions = ScriptRunner.getAvailableScriptExtensions();
        File[] files = folder.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File file : files) {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ENGLISH);
                if (file.isFile() && extensions.contains(extension)) {
                    menu.add(leaf(name, new ScriptActionSpec(name), onAssign));
                }
            }
        }
        menu.setEnabled(menu.getItemCount() > 0);
        return menu;
    }

    private static JMenuItem buildSearchItem(Component parent, AssignTarget onAssign,
            boolean replace) {
        JMenuItem item = new JMenuItem(
                ActionPanelModule.getString(replace ? "ASSIGN_MENU_REPLACE" : "ASSIGN_MENU_SEARCH"));
        item.addActionListener(e -> {
            ActionSpec spec = SearchActionWizard.show(parent, replace);
            if (spec != null) {
                onAssign.assign(spec, item.getText());
            }
        });
        return item;
    }

    private static JMenu buildSnippetBranch(Component parent, AssignTarget onAssign) {
        JMenu menu = new JMenu(ActionPanelModule.getString("ASSIGN_MENU_SNIPPET"));
        JMenuItem freeText = new JMenuItem(ActionPanelModule.getString("ASSIGN_SNIPPET_FREETEXT"));
        freeText.addActionListener(e -> {
            String text = JOptionPane.showInputDialog(parent,
                    ActionPanelModule.getString("ASSIGN_SNIPPET_FREETEXT"));
            if (text != null && !text.isEmpty()) {
                onAssign.assign(new SnippetActionSpec(text), abbreviate(text));
            }
        });
        menu.add(freeText);
        JMenuItem urlItem = new JMenuItem(ActionPanelModule.getString("ASSIGN_MENU_URL"));
        urlItem.addActionListener(e -> {
            String url = JOptionPane.showInputDialog(parent,
                    ActionPanelModule.getString("ASSIGN_URL_PROMPT"));
            if (url != null && !url.isBlank()) {
                url = url.trim();
                // A bare host is meant as a web address.
                if (!url.contains("://") && !url.toLowerCase(Locale.ENGLISH).startsWith("mailto:")) {
                    url = "https://" + url;
                }
                try {
                    new java.net.URI(url);
                } catch (java.net.URISyntaxException ex) {
                    JOptionPane.showMessageDialog(parent,
                            java.text.MessageFormat.format(
                                    ActionPanelModule.getString("ERROR_URL_OPEN"), url,
                                    ex.getReason()),
                            urlItem.getText(), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                onAssign.assign(new ActionSpec.UrlActionSpec(url),
                        abbreviate(url.replaceFirst("^(?i)[a-z+.-]+://(www\\.)?", "")));
            }
        });
        menu.add(urlItem);
        List<Autotext.AutotextItem> items = Autotext.getItems();
        if (!items.isEmpty()) {
            menu.addSeparator();
        }
        for (Autotext.AutotextItem autotext : items) {
            // Each autotext entry offers both storage modes: freeze the
            // current target text, or follow the entry by reference.
            JMenu entry = new JMenu(autotext.source + " \u2014 " + autotext.target);
            JMenuItem freeze = new JMenuItem(ActionPanelModule.getString("ASSIGN_SNIPPET_FREEZE"));
            freeze.addActionListener(
                    e -> onAssign.assign(new SnippetActionSpec(autotext.target), autotext.source));
            entry.add(freeze);
            JMenuItem reference = new JMenuItem(ActionPanelModule.getString("ASSIGN_SNIPPET_REF"));
            reference.addActionListener(
                    e -> onAssign.assign(new AutotextRefActionSpec(autotext.source), autotext.source));
            entry.add(reference);
            menu.add(entry);
        }
        return menu;
    }

    /**
     * Settings, arranged like the pages of the preferences dialog (see
     * {@link PreferenceCatalog}). The Colours and Keyboard Shortcuts pages
     * hold the saved colour schemes and shortcut sets; boolean project
     * settings, which live in the project properties rather than the
     * dialog, close the list.
     */
    private static JMenu buildPreferenceBranch(Component parent, AssignTarget onAssign) {
        JMenu menu = new JMenu(ActionPanelModule.getString("ASSIGN_MENU_PREFERENCE"));
        for (PreferenceCatalog.Group group : PreferenceCatalog.groups()) {
            menu.add(buildPreferenceGroup(group, parent, onAssign));
        }
        menu.addSeparator();
        JMenu projectMenu = new JMenu(ActionPanelModule.getString("ASSIGN_MENU_PROJECT"));
        for (String property : ProjectFlagActions.listFlags()) {
            projectMenu.add(leaf(ProjectFlagActions.label(property),
                    new ActionSpec.ProjectFlagActionSpec(property), onAssign));
        }
        menu.add(projectMenu);
        return menu;
    }

    private static JMenu buildPreferenceGroup(PreferenceCatalog.Group group, Component parent,
            AssignTarget onAssign) {
        JMenu menu;
        if (group.folder() == PreferenceCatalog.Folder.COLOR_SCHEMES) {
            menu = buildFileFolderBranch(group.title(), ActionPanelFolders.getColorSchemeFolder(), parent,
                    onAssign, ColorSchemeActionSpec::new);
        } else if (group.folder() == PreferenceCatalog.Folder.SHORTCUT_SETS) {
            menu = buildFileFolderBranch(group.title(), ActionPanelFolders.getShortcutSetFolder(), parent,
                    onAssign, ShortcutSetActionSpec::new);
        } else {
            menu = new JMenu(group.title());
        }
        for (PreferenceCatalog.Entry entry : group.entries()) {
            menu.add(leaf(entry.label(), entry.spec(), onAssign));
        }
        if (!group.children().isEmpty()) {
            if (!group.entries().isEmpty()) {
                menu.addSeparator();
            }
            for (PreferenceCatalog.Group child : group.children()) {
                menu.add(buildPreferenceGroup(child, parent, onAssign));
            }
        }
        return menu;
    }

    /** Saved .properties files of a folder, plus a chooser for any other file. */
    private static JMenu buildFileFolderBranch(String title, File folder, Component parent,
            AssignTarget onAssign, java.util.function.Function<String, ActionSpec> factory) {
        JMenu menu = new JMenu(title);
        List<String> names = listPropertiesFiles(folder);
        for (String name : names) {
            menu.add(leaf(name, factory.apply(name), onAssign));
        }
        if (!names.isEmpty()) {
            menu.addSeparator();
        }
        JMenuItem choose = new JMenuItem(ActionPanelModule.getString("ASSIGN_CHOOSE_FILE"));
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(folder.isDirectory() ? folder : null);
            chooser.setFileFilter(new FileNameExtensionFilter("*.properties", "properties"));
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                File chosen = chooser.getSelectedFile();
                onAssign.assign(factory.apply(chosen.getAbsolutePath()), chosen.getName());
            }
        });
        menu.add(choose);
        return menu;
    }

    /** First line of a snippet, shortened, as a default row name. */
    private static String abbreviate(String text) {
        String firstLine = text.split("\\R", 2)[0];
        return firstLine.length() > 24 ? firstLine.substring(0, 24) + "\u2026" : firstLine;
    }

    /** Bare scheme/set names (file name without extension) in the folder. */
    static List<String> listPropertiesFiles(@Nullable File folder) {
        List<String> names = new ArrayList<>();
        File[] files = folder == null ? null : folder.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File file : files) {
                String name = file.getName();
                if (file.isFile() && name.endsWith(".properties")) {
                    names.add(name.substring(0, name.length() - ".properties".length()));
                }
            }
        }
        return names;
    }
}
