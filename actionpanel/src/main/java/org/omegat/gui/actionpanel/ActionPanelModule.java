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

    private static final ResourceBundle BUNDLE = ResourceBundle
            .getBundle("org.omegat.gui.actionpanel.Bundle");

    private static @Nullable IApplicationEventListener listener;
    private static @Nullable ActionPanelView view;

    private ActionPanelModule() {
    }

    public static String getString(String key) {
        return BUNDLE.getString(key);
    }

    public static void loadPlugins() {
        PreferencesControllers.addSupplier(ActionPanelPreferencesController::new);
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
            if (pane.getDockKey().getLocation() == DockableState.Location.CLOSED) {
                desktop.addDockable(pane);
            }
            if (!Preferences.existsPreference(INITIALIZED_PREFERENCE)) {
                desktop.setAutoHide(pane, true);
                Preferences.setPreference(INITIALIZED_PREFERENCE, true);
            }
        });
    }
}
