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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.omegat.core.Core;
import org.omegat.core.data.ProjectFactory;
import org.omegat.core.data.ProjectProperties;
import org.omegat.util.Log;

/**
 * Boolean project settings as toggle actions. The flags are discovered
 * generically: every is-getter of ProjectProperties with a matching boolean
 * setter qualifies, so new project flags appear without a code change here.
 * Toggling edits the loaded project's properties and saves and reloads the
 * project; the toggle click itself counts as the confirmation.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ProjectFlagActions {

    private ProjectFlagActions() {
    }

    /** Property names (getter suffix) of all toggleable project flags. */
    public static List<String> listFlags() {
        List<String> flags = new ArrayList<>();
        for (Method method : ProjectProperties.class.getMethods()) {
            String name = method.getName();
            if (name.startsWith("is") && method.getParameterCount() == 0
                    && method.getReturnType() == boolean.class) {
                String property = name.substring(2);
                try {
                    ProjectProperties.class.getMethod("set" + property, boolean.class);
                    flags.add(property);
                } catch (NoSuchMethodException ignored) {
                    // Read-only flag: not toggleable.
                }
            }
        }
        flags.sort(String::compareTo);
        return flags;
    }

    /** Human-readable label derived from the property name. */
    public static String label(String property) {
        StringBuilder sb = new StringBuilder(property.length() + 8);
        for (int i = 0; i < property.length(); i++) {
            char c = property.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(property.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Current value of the flag in the loaded project; false without one. */
    public static boolean getValue(String property) {
        if (!Core.getProject().isProjectLoaded()) {
            return false;
        }
        try {
            return (boolean) ProjectProperties.class.getMethod("is" + property)
                    .invoke(Core.getProject().getProjectProperties());
        } catch (ReflectiveOperationException e) {
            Log.log(e);
            return false;
        }
    }

    /**
     * Toggle the flag, then save and reload the project (pattern of
     * ProjectUICommands.projectEditProperties, without its confirmation: the
     * toggle click itself is the user's explicit intent). Returns true when
     * the toggle was applied.
     */
    public static boolean toggleWithReload(String property) {
        if (!Core.getProject().isProjectLoaded()) {
            return false;
        }
        Core.getEditor().commitAndLeave();
        ProjectProperties props = Core.getProject().getProjectProperties();
        try {
            boolean current = (boolean) ProjectProperties.class.getMethod("is" + property).invoke(props);
            ProjectProperties.class.getMethod("set" + property, boolean.class).invoke(props, !current);
        } catch (ReflectiveOperationException e) {
            Log.log(e);
            return false;
        }
        int previousEntry = Core.getEditor().getCurrentEntryNumber();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Core.executeExclusively(true, () -> {
                    Core.getProject().saveProject(true);
                    ProjectFactory.closeProject();
                    ProjectFactory.loadProject(props, true);
                });
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    SwingUtilities.invokeLater(() -> {
                        Core.getEditor().gotoEntry(previousEntry);
                        Core.getEditor().requestFocus();
                    });
                } catch (Exception ex) {
                    Log.log(ex);
                }
            }
        }.execute();
        return true;
    }
}
