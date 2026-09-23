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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import com.vlsolutions.swing.docking.DockableState;
import com.vlsolutions.swing.docking.DockingDesktop;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.gui.main.DockableScrollPane;
import org.omegat.gui.main.IMainWindow;
import org.omegat.gui.preferences.PreferencesControllers;
import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.Log;
import org.omegat.util.Preferences;

/**
 * Bundled plugin entry point: a dockable panel with user-configurable action
 * buttons (SF RFE #642). The panel behaves like the other panes (gear menu,
 * pinnable) and starts minimized and unconfigured on a fresh installation.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ActionPanelModule {

    /** Dock key of the panel. */
    public static final String DOCK_KEY = "ACTION_PANEL";
    /** Preference flag: set once the panel was added to a layout. */
    public static final String INITIALIZED_PREFERENCE = "action_panel_initialized";
    /** Prefix of the positional shortcut keys, followed by the 1-based row position. */
    public static final String ROW_SHORTCUT_PREFIX = "actionPanelRow";
    /** Rows with a positional shortcut: {@code actionPanelRow1} to {@code actionPanelRow10}. */
    public static final int ROW_SHORTCUT_COUNT = 10;
    static final String SHORTCUTS_FILE = "/org/omegat/gui/actionpanel/ActionPanelShortcuts.properties";

    private static final ResourceBundle BUNDLE = ResourceBundle
            .getBundle("org.omegat.gui.actionpanel.Bundle");

    private static @Nullable IApplicationEventListener listener;
    private static @Nullable ActionPanelView view;

    private ActionPanelModule() {
    }

    public static String getString(String key) {
        return BUNDLE.getString(key);
    }

    /** Shortcut key of the row at a 1-based display position. */
    public static String rowShortcutKey(int position) {
        return ROW_SHORTCUT_PREFIX + position;
    }

    /** Label of a row shortcut key for the shortcuts page; null for other keys. */
    static @Nullable String shortcutLabel(String key) {
        if (!key.startsWith(ROW_SHORTCUT_PREFIX)) {
            return null;
        }
        return MessageFormat.format(getString("SHORTCUT_ROW_LABEL"), key.substring(ROW_SHORTCUT_PREFIX.length()));
    }

    public static void loadPlugins() {
        PreferencesControllers.addSupplier(ActionPanelPreferencesController::new);
        try {
            // Positional shortcuts join the editor set: listed and rebindable
            // on the shortcuts page, shown under the panel's own name.
            PropertiesShortcuts.getEditorShortcuts().contribute(SHORTCUTS_FILE,
                    ActionPanelModule.class.getClassLoader(), getString("ACTION_PANEL_TITLE"),
                    ActionPanelModule::shortcutLabel);
        } catch (IOException e) {
            Log.log(e);
        }
        listener = new IApplicationEventListener() {
            @Override
            public void onApplicationStartup() {
                createPanel();
            }

            @Override
            public void onApplicationShutdown() {
            }
        };
        CoreEvents.registerApplicationEventListener(listener);
    }

    public static void unloadPlugins() {
        PropertiesShortcuts.getEditorShortcuts().uncontribute(SHORTCUTS_FILE);
        if (listener != null) {
            CoreEvents.unregisterApplicationEventListener(listener);
            listener = null;
        }
    }

    private static void createPanel() {
        IMainWindow mainWindow = Core.getMainWindow();
        if (mainWindow == null || mainWindow.getDesktop() == null) {
            // Console mode has no docking desktop.
            return;
        }
        ActionPanelConfig.getInstance().load();
        if (view != null) {
            // A second startup (acceptance tests) replaces the previous view.
            view.dispose();
        }
        view = new ActionPanelView();
        DockableScrollPane pane = new DockableScrollPane(DOCK_KEY,
                getString("ACTION_PANEL_TITLE"), view, true);
        DockingDesktop desktop = mainWindow.getDesktop();
        // Register only: the main window reads its layout later in this same
        // startup pass and places the pane by key. A pane docked now instead
        // would keep a stale DOCKED location after the layout rebuild, and
        // re-adding it then fails inside VLDocking (NPE in replaceChild).
        desktop.registerDockable(pane);
        // After the layout pass: dock the pane if the layout had no place
        // for it, and minimize it on its very first appearance only, so a
        // user-chosen placement is never overridden afterwards.
        SwingUtilities.invokeLater(() -> {
            boolean freshlyDocked = pane.getDockKey().getLocation() == DockableState.Location.CLOSED;
            if (freshlyDocked) {
                desktop.addDockable(pane);
            }
            // Minimize only a pane the layout did not place: a position
            // restored from the layout is the user's and stays, even when
            // the preference got lost. The flag is written through to disk
            // at once - preferences are otherwise flushed only at project
            // saves, dialog confirmations and clean exit, so a session
            // killed before any of those re-minimized the pane on every
            // start.
            if (!Preferences.existsPreference(INITIALIZED_PREFERENCE)) {
                if (freshlyDocked) {
                    desktop.setAutoHide(pane, true);
                }
                Preferences.setPreference(INITIALIZED_PREFERENCE, true);
                Preferences.save();
            }
        });
    }
}
