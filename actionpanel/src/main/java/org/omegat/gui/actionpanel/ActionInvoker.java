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

import java.awt.Color;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.search.SearchMode;
import org.omegat.gui.actionpanel.ActionSpec.AutotextRefActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ColorSchemeActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.EditorKeyActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.MenuActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ScriptActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SearchActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ShortcutSetActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SnippetActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.UnknownActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.UrlActionSpec;
import org.omegat.gui.editor.autotext.Autotext;
import org.omegat.gui.scripting.ScriptItem;
import org.omegat.gui.scripting.ScriptRunner;
import org.omegat.gui.search.SearchWindowManager;
import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.Log;
import org.omegat.util.Preferences;
import org.omegat.util.gui.DesktopWrapper;
import org.omegat.util.gui.Styles;

/**
 * Executes an {@link ActionSpec} on behalf of a panel button.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ActionInvoker {

    private ActionInvoker() {
    }

    public static void invoke(ActionSpec spec, MenuActionCatalog catalog, int modifiers) {
        if (spec instanceof MenuActionSpec menu) {
            invokeMenu(menu, catalog, modifiers);
        } else if (spec instanceof EditorKeyActionSpec editorKey) {
            invokeEditorKey(editorKey);
        } else if (spec instanceof ScriptActionSpec script) {
            invokeScript(script);
        } else if (spec instanceof SearchActionSpec search) {
            // Restore the captured search window options, then open and run.
            search.options().forEach(Preferences::setPreference);
            SearchWindowManager.createSearchWindow(
                    search.replace() ? SearchMode.REPLACE : SearchMode.SEARCH, search.query(), true);
        } else if (spec instanceof SnippetActionSpec snippet) {
            insertText(snippet.text());
        } else if (spec instanceof UrlActionSpec url) {
            try {
                DesktopWrapper.browse(java.net.URI.create(url.url()));
            } catch (IllegalArgumentException | UnsupportedOperationException | IOException e) {
                Log.log(e);
                showError("ERROR_URL_OPEN", url.url(),
                        e.getMessage() == null ? e.toString() : e.getMessage());
            }
        } else if (spec instanceof AutotextRefActionSpec ref) {
            Autotext.AutotextItem item = findAutotextItem(ref.source());
            if (item == null) {
                Toolkit.getDefaultToolkit().beep();
            } else {
                insertText(item.target);
            }
        } else if (spec instanceof ColorSchemeActionSpec scheme) {
            applyColorScheme(scheme.ref());
        } else if (spec instanceof ShortcutSetActionSpec set) {
            applyShortcutSet(set.ref());
        } else if (spec instanceof UnknownActionSpec) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    static Autotext.@Nullable AutotextItem findAutotextItem(String source) {
        for (Autotext.AutotextItem item : Autotext.getItems()) {
            if (item.source.equals(source)) {
                return item;
            }
        }
        return null;
    }

    private static void invokeMenu(MenuActionSpec spec, MenuActionCatalog catalog, int modifiers) {
        MenuActionCatalog.MenuEntry entry = catalog.lookup(spec.actionCommand());
        if (entry == null || !entry.getItem().isEnabled()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        // Checkbox and radio items carry their state in the widget; the
        // handlers read it from the event source. A real click is the only
        // dispatch that toggles and reports consistently.
        if (entry.getItem() instanceof JCheckBoxMenuItem
                || entry.getItem() instanceof JRadioButtonMenuItem) {
            entry.getItem().doClick();
            return;
        }
        Core.getMainWindow().getMainMenu().invokeAction(spec.actionCommand(), modifiers);
    }

    /**
     * Dispatch the keystroke currently mapped to the catalog key into the
     * editor, exactly as if the user pressed it there. Only currently mapped
     * actions are reachable by design.
     */
    private static void invokeEditorKey(EditorKeyActionSpec spec) {
        KeyStroke keyStroke = PropertiesShortcuts.getEditorShortcuts().getKeyStroke(spec.shortcutKey());
        if (keyStroke == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        Core.getEditor().requestFocus();
        // Two hops: requestFocus is asynchronous, the second invokeLater runs
        // after the focus transfer events queued by the first.
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner == null) {
                return;
            }
            long when = System.currentTimeMillis();
            owner.dispatchEvent(new KeyEvent(owner, KeyEvent.KEY_PRESSED, when, keyStroke.getModifiers(),
                    keyStroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED));
            owner.dispatchEvent(new KeyEvent(owner, KeyEvent.KEY_RELEASED, when, keyStroke.getModifiers(),
                    keyStroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED));
        }));
    }

    private static void invokeScript(ScriptActionSpec spec) {
        File file = new File(spec.fileName());
        if (!file.isAbsolute()) {
            file = new File(ActionPanelFolders.getScriptsFolder(), spec.fileName());
        }
        if (!file.isFile()) {
            showError("ERROR_SCRIPT_RUN", spec.fileName(), "file not found");
            return;
        }
        File scriptFile = file;
        new Thread(() -> {
            try {
                ScriptRunner.executeScript(new ScriptItem(scriptFile), Map.of());
            } catch (Exception e) {
                Log.log(e);
                SwingUtilities.invokeLater(
                        () -> showError("ERROR_SCRIPT_RUN", scriptFile.getName(), e.getMessage()));
            }
        }, "actionpanel-script").start();
    }

    private static void insertText(String text) {
        if (!Core.getProject().isProjectLoaded()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        Core.getEditor().insertText(text);
    }

    /**
     * Load a colour scheme in the export format of the colour preferences
     * (EditorColor name = #rrggbb per line) and apply it live.
     */
    private static void applyColorScheme(String ref) {
        File file = ActionPanelFolders.resolveColorScheme(ref);
        if (!file.isFile()) {
            showError("ERROR_SCHEME_LOAD", ref, "file not found");
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            Log.log(e);
            showError("ERROR_SCHEME_LOAD", ref, e.getMessage());
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key).trim();
            try {
                Styles.EditorColor color = Styles.EditorColor.valueOf(key);
                color.setColor(value.isEmpty() ? null : Color.decode(value));
            } catch (IllegalArgumentException e) {
                // Unknown colour names and unparseable values: skip, report.
                unknown.add(key);
            }
        }
        try {
            Preferences.save();
        } catch (Exception e) {
            Log.log(e);
        }
        // Live refresh: every pane listening for colour changes repaints.
        CoreEvents.fireColorsChanged();
        if (!unknown.isEmpty()) {
            Log.log("Colour scheme " + file.getName() + ": skipped unknown entries " + unknown);
        }
    }

    /**
     * Load a shortcut set (properties file with catalog keys of both scopes
     * mixed) and apply it live through the shortcut manager. The file is an
     * overlay: keys it does not mention keep their current binding, so a
     * complete switch needs complete set files.
     */
    private static void applyShortcutSet(String ref) {
        File file = ActionPanelFolders.resolveShortcutSet(ref);
        if (!file.isFile()) {
            showError("ERROR_SHORTCUTSET_LOAD", ref, "file not found");
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            Log.log(e);
            showError("ERROR_SHORTCUTSET_LOAD", ref, e.getMessage());
            return;
        }
        PropertiesShortcuts menuShortcuts = PropertiesShortcuts.getMainMenuShortcuts();
        PropertiesShortcuts editorShortcuts = PropertiesShortcuts.getEditorShortcuts();
        List<String> rejected = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key).trim();
            KeyStroke keyStroke = value.isEmpty() ? null : KeyStroke.getKeyStroke(value);
            if (!value.isEmpty() && keyStroke == null) {
                rejected.add(key);
                continue;
            }
            if (menuShortcuts.getKeys().contains(key)) {
                menuShortcuts.setShortcut(key, keyStroke);
            } else if (editorShortcuts.getKeys().contains(key)) {
                editorShortcuts.setShortcut(key, keyStroke);
            } else {
                rejected.add(key);
            }
        }
        try {
            menuShortcuts.save();
            editorShortcuts.save();
        } catch (IOException e) {
            Log.log(e);
            showError("ERROR_SHORTCUTSET_LOAD", ref, e.getMessage());
            return;
        }
        if (!rejected.isEmpty()) {
            showError("ERROR_SHORTCUTSET_LOAD", ref, "rejected keys: " + String.join(", ", rejected));
        }
    }

    private static void showError(String key, Object... params) {
        Core.getMainWindow()
                .showMessageDialog(MessageFormat.format(ActionPanelModule.getString(key), params));
    }
}
