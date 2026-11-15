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

package org.omegat.gui.glossary;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openide.awt.Mnemonics;

import org.omegat.core.Core;
import org.omegat.core.data.IProject;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.gui.editor.IEditorFilter;
import org.omegat.util.Log;
import org.omegat.util.OStrings;

/**
 * Editor filter showing only the segments that have glossary matches under
 * the current glossary settings, optionally restricted by translation
 * state. Activated from the glossary pane menu; the matching segments are
 * collected once at activation, like the search filter does.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
@NullMarked
public final class GlossaryMatchesFilter implements IEditorFilter {

    /** Translation-state restriction of the filter. */
    public enum Scope {
        UNTRANSLATED_ONLY, ALL_SEGMENTS, TRANSLATED_ONLY;

        /** Localized menu and filter bar label of the scope. */
        String label() {
            // The bundle keys follow the enum names:
            // GUI_GLOSSARYWINDOW_FILTER_UNTRANSLATED_ONLY etc.
            return OStrings.getString("GUI_GLOSSARYWINDOW_FILTER_" + name());
        }
    }

    /** Guards against a second collection while one is running. */
    private static final AtomicBoolean COLLECTING = new AtomicBoolean(false);

    /** Filter over the matches of all local glossaries. */
    public static void apply(Scope scope) {
        apply(scope, null);
    }

    /**
     * Collects the matching segments in the background and installs the
     * filter, after confirming when another editor filter is active.
     * Dismissing the confirmation keeps the active filter; a second
     * invocation while a collection runs is ignored.
     *
     * @param originPath
     *            restricts the matches to entries of the glossary with
     *            this origin path; null means all local glossaries
     */
    public static void apply(Scope scope, @Nullable String originPath) {
        if (COLLECTING.get()) {
            // Answering the replace question would silently do nothing
            // while a collection runs; don't ask it in the first place.
            return;
        }
        if (Core.getEditor().getFilter() != null) {
            int answer = JOptionPane.showConfirmDialog(Core.getMainWindow().getApplicationFrame(),
                    OStrings.getString("GUI_GLOSSARYWINDOW_FILTER_REPLACE_QUESTION"),
                    OStrings.getString("GUI_GLOSSARYWINDOW_FILTER_MENU"), JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (!COLLECTING.compareAndSet(false, true)) {
            return;
        }
        IProject project = Core.getProject();
        Component frame = Core.getMainWindow().getApplicationFrame();
        Cursor oldCursor = frame.getCursor();
        try {
            // Commit the open segment first, so its translation state is
            // what the collection classifies.
            Core.getEditor().commitAndDeactivate();
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } catch (RuntimeException ex) {
            // Never leave the gate closed for good.
            frame.setCursor(oldCursor);
            COLLECTING.set(false);
            throw ex;
        }
        new SwingWorker<Set<Integer>, Void>() {
            @Override
            protected Set<Integer> doInBackground() {
                return collectEntries(project, Core.getGlossaryManager(), scope, originPath);
            }

            @Override
            protected void done() {
                frame.setCursor(oldCursor);
                COLLECTING.set(false);
                try {
                    Set<Integer> entries = get();
                    if (Core.getProject() != project || !project.isProjectLoaded()) {
                        // The collection ran against a project that has been
                        // closed or replaced meanwhile; its entry numbers
                        // mean nothing to the current one.
                        return;
                    }
                    Core.getEditor().commitAndDeactivate();
                    String originName = originPath == null ? null : new File(originPath).getName();
                    Core.getEditor().setFilter(new GlossaryMatchesFilter(scope, originName, entries));
                } catch (Exception ex) {
                    Log.log(ex);
                }
            }
        }.execute();
    }

    /**
     * Numbers of the segments the filter shows: glossary matches under the
     * current glossary settings, restricted to the scope's translation
     * state as of now and, when originPath is given, to matches carrying
     * an entry of that glossary.
     */
    static Set<Integer> collectEntries(IProject project, GlossaryManager manager, Scope scope,
            @Nullable String originPath) {
        Set<Integer> entries = new HashSet<>();
        for (SourceTextEntry ste : project.getAllEntries()) {
            boolean translated = project.getTranslationInfo(ste).isTranslated();
            if (!included(translated, scope)) {
                continue;
            }
            // Local glossaries only: external providers may go over the
            // network, which must not happen once per segment.
            List<GlossaryEntry> matches = manager.searchSourceLocalMatches(ste);
            if (originPath != null) {
                // The searcher may merge entries of several glossaries; a
                // merged match counts when any of its origins is the
                // requested glossary. Exact duplicates across glossaries
                // keep only one origin (like the pane display), so such a
                // match counts for the surviving glossary only.
                matches = matches.stream().filter(entry -> Arrays.asList(entry.getOrigins(true))
                        .contains(originPath)).collect(Collectors.toList());
            }
            if (!matches.isEmpty()) {
                entries.add(ste.entryNum());
            }
        }
        return entries;
    }

    /** Whether a segment of the given translation state falls in scope. */
    static boolean included(boolean translated, Scope scope) {
        switch (scope) {
        case UNTRANSLATED_ONLY:
            return !translated;
        case TRANSLATED_ONLY:
            return translated;
        default:
            return true;
        }
    }

    private final Set<Integer> entries;
    private final JPanel controlComponent;

    GlossaryMatchesFilter(Scope scope, @Nullable String originName, Set<Integer> entries) {
        this.entries = entries;
        controlComponent = new JPanel(new FlowLayout(FlowLayout.LEFT));
        Border border = UIManager.getBorder("OmegaTEditorFilter.border");
        if (border != null) {
            controlComponent.setBorder(border);
        }
        String barText = originName == null
                ? OStrings.getString("GUI_GLOSSARYWINDOW_FILTER_BAR", scope.label(), entries.size())
                : OStrings.getString("GUI_GLOSSARYWINDOW_FILTER_BAR_ORIGIN", originName, scope.label(),
                        entries.size());
        controlComponent.add(new JLabel(barText));
        JButton remove = new JButton();
        Mnemonics.setLocalizedText(remove, OStrings.getString("BUTTON_FILTER_REMOVE_FILTER"));
        remove.addActionListener(e -> {
            // Make sure that any change done in the current segment is not
            // lost
            Core.getEditor().commitAndDeactivate();
            Core.getEditor().removeFilter();
        });
        controlComponent.add(remove);
    }

    @Override
    public boolean allowed(@Nullable SourceTextEntry ste) {
        return ste != null && entries.contains(ste.entryNum());
    }

    @Override
    public Component getControlComponent() {
        return controlComponent;
    }

    @Override
    public boolean isSourceAsEmptyTranslation() {
        return false;
    }
}
