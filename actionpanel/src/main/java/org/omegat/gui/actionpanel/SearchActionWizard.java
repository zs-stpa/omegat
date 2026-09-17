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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.jspecify.annotations.Nullable;

import org.omegat.core.search.SearchMode;
import org.omegat.gui.actionpanel.ActionSpec.SearchActionSpec;
import org.omegat.gui.search.SearchWindowController;
import org.omegat.util.Log;
import org.omegat.util.Preferences;
import org.omegat.util.gui.StaticUIUtils;

/**
 * Captures a preset search or replace action through the real search window,
 * wrapped in a small wizard: an explanation page first, then the embedded
 * window itself, behaving like the real thing (including live results while a
 * project is open). Finishing stores the query and a snapshot of every search
 * window option; the user's own sticky search options are restored on cancel.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class SearchActionWizard {

    private SearchActionWizard() {
    }

    /** Runs the modal wizard; null when the user cancelled. */
    public static @Nullable SearchActionSpec show(Component parent, boolean replace) {
        return show(parent, replace, null);
    }

    /**
     * With a preset: the embedded window opens prefilled with the preset's
     * stored query and options, so the user can revise and re-save it. Only
     * the search field is prefilled — a replacement term is not part of the
     * stored spec.
     */
    public static @Nullable SearchActionSpec show(Component parent, boolean replace,
            @Nullable SearchActionSpec preset) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        // The embedded search window needs a loaded project to preview and
        // to enumerate its scopes; without one the capture cannot work.
        if (!org.omegat.core.Core.getProject().isProjectLoaded()) {
            javax.swing.JOptionPane.showMessageDialog(owner,
                    ActionPanelModule.getString("SEARCH_WIZARD_NEEDS_PROJECT"),
                    ActionPanelModule.getString(
                            replace ? "SEARCH_WIZARD_TITLE_REPLACE" : "SEARCH_WIZARD_TITLE_SEARCH"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        JDialog dialog = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setTitle(ActionPanelModule
                .getString(replace ? "SEARCH_WIZARD_TITLE_REPLACE" : "SEARCH_WIZARD_TITLE_SEARCH"));
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        StaticUIUtils.setEscapeClosable(dialog);

        // The user's sticky search options must survive a cancelled wizard.
        Map<String, String> before = captureSearchOptions();
        if (preset != null) {
            // Before the controller reads them: its controls initialize from
            // the preferences at construction time.
            applyPresetOptions(preset.options(), before.keySet());
        }

        SearchWindowController controller = new SearchWindowController(
                replace ? SearchMode.REPLACE : SearchMode.SEARCH);
        if (preset != null && !preset.query().isEmpty()) {
            controller.setSearchText(preset.query());
        }

        CardLayout cards = new CardLayout();
        JPanel cardPanel = new JPanel(cards);
        JTextArea intro = new JTextArea(ActionPanelModule.getString("SEARCH_WIZARD_INTRO"));
        intro.setEditable(false);
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setOpaque(false);
        intro.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 16, 16));
        intro.setFocusable(true);
        intro.getAccessibleContext()
                .setAccessibleName(ActionPanelModule.getString("SEARCH_WIZARD_INTRO"));
        cardPanel.add(intro, "intro");
        cardPanel.add(controller.getWindowContent(), "search");

        JButton backButton = new JButton(ActionPanelModule.getString("SEARCH_WIZARD_BACK"));
        JButton nextButton = new JButton(ActionPanelModule.getString("SEARCH_WIZARD_NEXT"));
        JButton cancelButton = new JButton(ActionPanelModule.getString("SEARCH_WIZARD_CANCEL"));
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 12, 12));
        buttons.add(cancelButton);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(backButton);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(nextButton);

        final boolean[] onIntroPage = { true };
        final boolean[] finished = { false };
        Runnable updateButtons = () -> {
            backButton.setEnabled(!onIntroPage[0]);
            nextButton.setText(ActionPanelModule
                    .getString(onIntroPage[0] ? "SEARCH_WIZARD_NEXT" : "SEARCH_WIZARD_FINISH"));
        };
        backButton.addActionListener(e -> {
            onIntroPage[0] = true;
            cards.show(cardPanel, "intro");
            updateButtons.run();
        });
        nextButton.addActionListener(e -> {
            if (onIntroPage[0]) {
                onIntroPage[0] = false;
                cards.show(cardPanel, "search");
                updateButtons.run();
            } else {
                finished[0] = true;
                dialog.dispose();
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());
        updateButtons.run();

        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(cardPanel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.setSize(900, 700);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);

        // Modal: from here the wizard is closed.
        String query = controller.getSearchText();
        if (finished[0]) {
            controller.saveOptions();
        }
        controller.dispose();
        if (!finished[0]) {
            before.forEach(Preferences::setPreference);
            return null;
        }
        Map<String, String> options = captureSearchOptions();
        before.forEach(Preferences::setPreference);
        return new SearchActionSpec(query, replace, options);
    }

    /**
     * Apply stored preset options as the complete search window state: stored
     * keys get their snapshot value, current keys the preset does not know
     * are emptied so the window falls back to its coded default instead of
     * whatever sticky value the user last used.
     */
    static void applyPresetOptions(Map<String, String> options, java.util.Set<String> currentKeys) {
        options.forEach(Preferences::setPreference);
        for (String key : currentKeys) {
            if (!options.containsKey(key)) {
                Preferences.setPreference(key, "");
            }
        }
    }

    /**
     * Snapshot every SEARCHWINDOW_* preference, discovered reflectively so
     * options added to the search window later are captured automatically.
     * Geometry and history keys are skipped.
     */
    static Map<String, String> captureSearchOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Field field : Preferences.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getName().startsWith("SEARCHWINDOW_")
                    || field.getName().contains("GEOMETRY") || field.getName().contains("HISTORY")
                    || field.getType() != String.class) {
                continue;
            }
            try {
                String key = (String) field.get(null);
                options.put(key, Preferences.getPreference(key));
            } catch (IllegalAccessException e) {
                Log.log(e);
            }
        }
        return options;
    }
}
