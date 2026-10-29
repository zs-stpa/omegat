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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;

import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jspecify.annotations.Nullable;

import org.omegat.util.Log;
import org.omegat.util.Preferences;

/**
 * Dialogs and value helpers for editing a row, shared by the preferences
 * page and the panel's context menu so both offer the same gestures.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
final class RowEditing {

    static final String ICON_DIRECTORY_PREFERENCE = "action_panel_icon_directory";

    private RowEditing() {
    }

    /**
     * Pick an icon file and decide whether to copy it into the icon folder
     * or to reference it in place. Null when the user cancels or the copy
     * fails (reported in a dialog).
     */
    static @Nullable String chooseIcon(Component parent) {
        JFileChooser chooser = new JFileChooser(Preferences.getPreferenceDefault(ICON_DIRECTORY_PREFERENCE, null));
        chooser.setDialogTitle(ActionPanelModule.getString("ICON_CHOOSER_TITLE"));
        chooser.setFileFilter(new FileNameExtensionFilter(ActionPanelModule.getString("ICON_FILTER_DESC"), "svg",
                "png", "jpg", "jpeg", "gif", "pdf"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File chosen = chooser.getSelectedFile();
        Preferences.setPreference(ICON_DIRECTORY_PREFERENCE, chosen.getParent());
        Object[] options = { ActionPanelModule.getString("ICON_COPY"), ActionPanelModule.getString("ICON_REFERENCE") };
        int mode = JOptionPane.showOptionDialog(parent, ActionPanelModule.getString("ICON_COPY_QUESTION"),
                ActionPanelModule.getString("ICON_CHOOSER_TITLE"), JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (mode == JOptionPane.CLOSED_OPTION) {
            return null;
        }
        if (mode != 0) {
            return chosen.getAbsolutePath();
        }
        try {
            return copyIntoIconFolder(chosen);
        } catch (IOException ex) {
            Log.log(ex);
            JOptionPane.showMessageDialog(parent, ex.getLocalizedMessage(),
                    ActionPanelModule.getString("ICON_CHOOSER_TITLE"), JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /** Copy into the icon folder, dodging name collisions with a suffix. */
    static String copyIntoIconFolder(File chosen) throws IOException {
        File folder = ActionPanelConfig.getIconFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create " + folder);
        }
        String name = chosen.getName();
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        File target = new File(folder, name);
        for (int i = 2; target.exists(); i++) {
            target = new File(folder, base + "-" + i + extension);
        }
        Files.copy(chosen.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return ActionPanelConfig.ICON_FOLDER + "/" + target.getName();
    }

    /** Colour chooser seeded with the current colour; hex or null when cancelled. */
    static @Nullable String chooseColor(Component parent, String title, @Nullable Color current) {
        Color chosen = JColorChooser.showDialog(parent, title, current != null ? current : Color.GRAY);
        return chosen == null ? null : toHex(chosen);
    }

    static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    static @Nullable Color decode(@Nullable String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A copy of the row under its own id, named as a copy ("Name (Copy)"). */
    static ActionRow copyOf(ActionRow row) {
        return row.withFreshId()
                .withName(MessageFormat.format(ActionPanelModule.getString("ROW_COPY_NAME"), row.name()));
    }
}
