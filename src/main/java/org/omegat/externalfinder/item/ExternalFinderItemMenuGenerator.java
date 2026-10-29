/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2016 Chihiro Hio
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

package org.omegat.externalfinder.item;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.jspecify.annotations.Nullable;

import org.omegat.util.StringUtil;
import org.omegat.externalfinder.ExternalFinder;
import org.omegat.externalfinder.item.ExternalFinderItem.SCOPE;
import org.omegat.externalfinder.item.PlaceholderTemplate.Context;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.gui.DesktopWrapper;
import org.openide.awt.Mnemonics;

public class ExternalFinderItemMenuGenerator implements IExternalFinderItemMenuGenerator {

    /**
     * Prefix of the action command of generated menu items, followed by the
     * item name. Lets menu harvesters address an item without clashing with
     * the main menu's handler names; the item is triggered by clicking it.
     */
    public static final String ACTION_COMMAND_PREFIX = "externalFinder:";

    private final ExternalFinderItem.TARGET target;
    private final boolean popup;
    private final boolean hasSelection;

    public ExternalFinderItemMenuGenerator(ExternalFinderItem.TARGET target, boolean popup) {
        this(target, popup, true);
    }

    /**
     * @param hasSelection
     *            whether editor text is selected; without a selection, items
     *            that read it are left out
     */
    public ExternalFinderItemMenuGenerator(ExternalFinderItem.TARGET target, boolean popup,
            boolean hasSelection) {
        this.target = target;
        this.popup = popup;
        this.hasSelection = hasSelection;
    }

    @Override
    public List<JMenuItem> generate() {
        List<ExternalFinderItem> finderItems = ExternalFinder.getItems();
        if (finderItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<JMenuItem> menuItems = new ArrayList<>();

        // generate menu
        for (ExternalFinderItem finderItem : finderItems) {
            if (popup && finderItem.isNopopup()) {
                continue;
            }
            if (!hasSelection && finderItem.usesSelection()) {
                continue;
            }
            if (target == ExternalFinderItem.TARGET.ASCII_ONLY
                    && finderItem.isNonAsciiOnly()) {
                continue;
            } else if (target == ExternalFinderItem.TARGET.NON_ASCII_ONLY
                    && finderItem.isAsciiOnly()) {
                continue;
            }

            JMenuItem item = new JMenuItem();
            Mnemonics.setLocalizedText(item, finderItem.getName());
            item.setName(finderItem.getName());
            item.setActionCommand(ACTION_COMMAND_PREFIX + finderItem.getName());

            // set keyboard shortcut
            if (!popup) {
                item.setAccelerator(finderItem.getKeystroke());
            }
            item.addActionListener(new ExternalFinderItemActionListener(finderItem));

            menuItems.add(item);
        }
        return menuItems;
    }

    /**
     * Run a search set against the editor's current state: the selection and
     * the current segment. A set that reads the selection does nothing while
     * nothing is selected.
     *
     * @return whether the set was run
     */
    public static boolean run(ExternalFinderItem finderItem) {
        final Context context = Context.ofEditor();
        final String selection = context.getSelection();
        if (selection == null && finderItem.usesSelection()) {
            return false;
        }
        new ExternalFinderItemActionListener(finderItem).run(context, selection);
        return true;
    }

    private static class ExternalFinderItemActionListener implements ActionListener {

        private final SCOPE scope;
        private final ExternalFinderItem finderItem;
        private final List<ExternalFinderItemURL> urls;
        private final List<ExternalFinderItemCommand> commands;

        ExternalFinderItemActionListener(ExternalFinderItem finderItem) {
            this.finderItem = finderItem;
            this.urls = finderItem.getURLs();
            this.commands = finderItem.getCommands();
            this.scope = finderItem.getScope();
        }

        public void actionPerformed(ActionEvent e) {
            ExternalFinderItemMenuGenerator.run(finderItem);
        }

        private void run(Context context, @Nullable String selection) {
            // Without a selection the ASCII filters have nothing to judge.
            final @Nullable Boolean isASCII = selection == null ? null : ExternalFinderItem.isASCII(selection);

            new Thread(() -> {
                for (ExternalFinderItemURL url : urls) {
                    if (skip(url.getTarget(), isASCII)) {
                        continue;
                    }

                    try {
                        DesktopWrapper.browse(url.generateURL(context));
                    } catch (Exception ex) {
                        Logger.getLogger(ExternalFinderItemMenuGenerator.class.getName()).log(Level.SEVERE,
                                null, ex);
                        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), StringUtil.describeException(ex),
                                OStrings.getString("ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            }).start();

            new Thread(() -> {
                for (ExternalFinderItemCommand command : commands) {
                    if (skip(command.getTarget(), isASCII)) {
                        continue;
                    }
                    if (scope == SCOPE.PROJECT && !Preferences
                            .isPreference(Preferences.EXTERNAL_FINDER_ALLOW_PROJECT_COMMANDS)) {
                        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                OStrings.getString("EXTERNALFINDER_PROJECT_COMMANDS_DISALLOWED_MESSAGE"),
                                OStrings.getString("ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    try {
                        Runtime.getRuntime().exec(command.generateCommand(context));
                    } catch (Exception ex) {
                        Logger.getLogger(ExternalFinderItemMenuGenerator.class.getName()).log(Level.SEVERE,
                                null, ex);
                        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), StringUtil.describeException(ex),
                                OStrings.getString("ERROR_TITLE"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            }).start();
        }

        private static boolean skip(ExternalFinderItem.TARGET target, @Nullable Boolean isASCII) {
            if (isASCII == null) {
                return false;
            }
            return (isASCII && target == ExternalFinderItem.TARGET.NON_ASCII_ONLY)
                    || (!isASCII && target == ExternalFinderItem.TARGET.ASCII_ONLY);
        }
    }
}
