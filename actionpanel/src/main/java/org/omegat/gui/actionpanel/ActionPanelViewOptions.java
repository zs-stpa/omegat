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

import org.omegat.util.Preferences;

/**
 * View options of the panel (display mode and layout), stored as preferences
 * and applied immediately from both the gear menu and the settings page.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ActionPanelViewOptions {

    public enum DisplayMode {
        ICON_ONLY, NAME_ONLY, ICON_AND_NAME
    }

    public enum LayoutMode {
        FLOW, COLUMNS
    }

    static final String DISPLAY_MODE_PREFERENCE = "action_panel_display_mode";
    static final String LAYOUT_PREFERENCE = "action_panel_layout";
    static final String REVERSE_PREFERENCE = "action_panel_reverse";

    private ActionPanelViewOptions() {
    }

    public static DisplayMode getDisplayMode() {
        try {
            return DisplayMode
                    .valueOf(Preferences.getPreferenceDefault(DISPLAY_MODE_PREFERENCE, DisplayMode.ICON_ONLY.name()));
        } catch (IllegalArgumentException e) {
            return DisplayMode.ICON_ONLY;
        }
    }

    public static void setDisplayMode(DisplayMode mode) {
        Preferences.setPreference(DISPLAY_MODE_PREFERENCE, mode.name());
        ActionPanelConfig.getInstance().notifyChanged();
    }

    public static LayoutMode getLayoutMode() {
        try {
            return LayoutMode
                    .valueOf(Preferences.getPreferenceDefault(LAYOUT_PREFERENCE, LayoutMode.FLOW.name()));
        } catch (IllegalArgumentException e) {
            return LayoutMode.FLOW;
        }
    }

    public static void setLayoutMode(LayoutMode mode) {
        Preferences.setPreference(LAYOUT_PREFERENCE, mode.name());
        ActionPanelConfig.getInstance().notifyChanged();
    }

    /** Explicitly reversed fill order, on top of the natural direction. */
    public static boolean isReverse() {
        return Preferences.isPreferenceDefault(REVERSE_PREFERENCE, false);
    }

    public static void setReverse(boolean reverse) {
        Preferences.setPreference(REVERSE_PREFERENCE, reverse);
        ActionPanelConfig.getInstance().notifyChanged();
    }
}
