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
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.events.IProjectEventListener;
import org.omegat.gui.actionpanel.ActionPanelViewOptions.DisplayMode;
import org.omegat.gui.actionpanel.ActionPanelViewOptions.LayoutMode;
import org.omegat.gui.actionpanel.ActionSpec.AutotextRefActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.EditorKeyActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.MenuActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.PreferenceActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SearchActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.SnippetActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.UnknownActionSpec;
import org.omegat.gui.preferences.PreferencesWindowController;
import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.Preferences;
import org.omegat.util.gui.FontUtil;
import org.omegat.util.gui.IPaneMenu;
import org.omegat.util.gui.StaticUIUtils;
import org.omegat.util.gui.Styles;

/**
 * The dockable panel: one control per configured row. The control type is
 * inferred from the action — checkbox menu items become labeled switches,
 * radio menu groups and enum preferences become labeled comboboxes, scalar
 * preferences become labeled sliders, everything else a button showing icon
 * and/or name according to the display mode. Rows flow line by line or fill
 * columns downwards, following the component orientation, optionally
 * reversed.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
@SuppressWarnings("serial")
public class ActionPanelView extends JPanel implements IPaneMenu, IProjectEventListener, Scrollable {

    private final transient MenuActionCatalog catalog = new MenuActionCatalog();
    private final transient Runnable configListener = () -> SwingUtilities.invokeLater(this::rebuild);
    /** Menu items we attached sync listeners to, cleared on every rebuild. */
    private final transient List<Runnable> itemListenerCleanups = new ArrayList<>();
    /** Rows in display order, parallel to {@link #rowControls}. */
    private final transient List<ActionRow> displayRows = new ArrayList<>();
    private final transient List<JComponent> rowControls = new ArrayList<>();
    private static final Cursor DELETE_CURSOR = createDeleteCursor();
    private static final Cursor MOVE_DRAG_CURSOR = createMoveCursor();
    /** Display index an active drag would drop at; -1 without a drag. */
    private transient int dropIndicator = -1;
    /** Control an outside drag would remove; marked with a painted cross. */
    private transient @Nullable JComponent dragRemoveMark;

    public ActionPanelView() {
        ActionPanelConfig.getInstance().addChangeListener(configListener);
        PropertiesShortcuts.getMainMenuShortcuts().addChangeListener(configListener);
        PropertiesShortcuts.getEditorShortcuts().addChangeListener(configListener);
        CoreEvents.registerProjectChangeListener(this);
        CoreEvents.registerFontChangedEventListener(font -> SwingUtilities.invokeLater(this::rebuild));
        CoreEvents.registerColorsChangedEventListener(() -> SwingUtilities.invokeLater(this::rebuild));
        rebuild();
    }

    @Override
    public void onProjectChanged(PROJECT_CHANGE_TYPE eventType) {
        SwingUtilities.invokeLater(this::rebuild);
    }

    /** Rebuild all controls from the current configuration and options. */
    void rebuild() {
        try {
            doRebuild();
        } catch (RuntimeException e) {
            // The EDT default handler prints to stderr, which is lost when
            // the app runs detached; keep failures visible in the log.
            org.omegat.util.Log.log(e);
        }
    }

    private void doRebuild() {
        // A rebuild mid-drag replaces the controls: drop the drag feedback so
        // no marker is painted against the new layout.
        dropIndicator = -1;
        dragRemoveMark = null;
        itemListenerCleanups.forEach(Runnable::run);
        itemListenerCleanups.clear();
        displayRows.clear();
        rowControls.clear();
        removeAll();
        applyComponentOrientation(ComponentOrientation.getOrientation(Locale.getDefault()));
        setLayout(ActionPanelViewOptions.getLayoutMode() == LayoutMode.COLUMNS ? new ColumnWrapLayout(4, 4)
                : new WrapLayout(FlowLayout.LEADING, 4, 4));
        catalog.rebuild();
        // The panel follows the global editor colours and font, like the
        // other dockable panes; per-row colours override below.
        setOpaque(true);
        Styles.applyColors(this);
        Font scaledFont = FontUtil.getScaledFont();
        // Plain copy, not the FontUIResource itself: the Aqua look and feel
        // swaps UIResource fonts on buttons with icons for its small system
        // font, freezing icon buttons at 11 pt.
        Font globalFont = scaledFont.deriveFont(scaledFont.getSize2D());
        setFont(globalFont);
        List<ActionRow> rows = new ArrayList<>(ActionPanelConfig.getInstance().getRows());
        if (ActionPanelViewOptions.isReverse()) {
            Collections.reverse(rows);
        }
        if (rows.isEmpty()) {
            JLabel hint = new JLabel(ActionPanelModule.getString("ACTION_PANEL_EMPTY_HINT"));
            hint.setEnabled(false);
            hint.setFont(globalFont);
            add(hint);
        }
        for (ActionRow row : rows) {
            JComponent control = createControl(row);
            applyGlobalStyle(control, globalFont);
            applyRowColors(row, control);
            installDragReorder(control);
            displayRows.add(row);
            rowControls.add(control);
            add(control);
        }
        revalidate();
        repaint();
    }

    /** Global font on every control; panel foreground on borderless text. */
    private void applyGlobalStyle(java.awt.Component component, Font font) {
        String text = component instanceof JLabel label ? label.getText()
                : component instanceof AbstractButton button ? button.getText() : null;
        component.setFont(FallbackFonts.withGlyphFallback(font, text));
        if (component instanceof JLabel || component instanceof JCheckBox) {
            component.setForeground(getForeground());
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                applyGlobalStyle(child, font);
            }
        }
    }

    /** Infer the control type from the assigned action. */
    private JComponent createControl(ActionRow row) {
        ActionSpec spec = row.action();
        JComponent control;
        MenuActionCatalog.MenuEntry entry = spec instanceof MenuActionSpec menu
                ? catalog.lookup(menu.actionCommand())
                : null;
        if (entry != null && entry.getItem() instanceof JCheckBoxMenuItem checkbox) {
            control = createToggle(row, checkbox);
        } else if (entry != null && entry.getItem() instanceof JRadioButtonMenuItem radio) {
            control = createRadioGroupCombo(row, radio);
        } else if (spec instanceof PreferenceActionSpec preference) {
            control = "combobox".equals(preference.kind()) ? createPreferenceCombo(row, preference)
                    : createPreferenceSlider(row, preference);
        } else if (spec instanceof ActionSpec.ProjectFlagActionSpec flag) {
            control = createProjectFlagToggle(row, flag);
        } else {
            control = createButton(row);
        }
        return control;
    }

    /** Checkbox menu item: a labeled switch mirroring the menu state. */
    private JComponent createToggle(ActionRow row, JCheckBoxMenuItem item) {
        JCheckBox toggle = new JCheckBox(row.name(), item.isSelected());
        toggle.setFocusable(false);
        toggle.setOpaque(false);
        toggle.setToolTipText(tooltip(row));
        java.awt.event.ItemListener sync = e -> toggle.setSelected(item.isSelected());
        item.addItemListener(sync);
        itemListenerCleanups.add(() -> item.removeItemListener(sync));
        toggle.addActionListener(e -> {
            if (item.isEnabled()) {
                item.doClick();
            }
            toggle.setSelected(item.isSelected());
        });
        toggle.setEnabled(item.isEnabled());
        return toggle;
    }

    /** Radio menu item: a labeled combobox over its sibling group. */
    private JComponent createRadioGroupCombo(ActionRow row, JRadioButtonMenuItem item) {
        List<JRadioButtonMenuItem> group = new ArrayList<>();
        if (item.getParent() instanceof JPopupMenu parent) {
            for (java.awt.Component sibling : parent.getComponents()) {
                if (sibling instanceof JRadioButtonMenuItem radio) {
                    group.add(radio);
                }
            }
        }
        if (group.isEmpty()) {
            group.add(item);
        }
        JComboBox<String> combo = new JComboBox<>(
                group.stream().map(JMenuItem::getText).toArray(String[]::new));
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i).isSelected()) {
                combo.setSelectedIndex(i);
            }
        }
        combo.setFocusable(false);
        combo.setToolTipText(tooltip(row));
        combo.addActionListener(e -> {
            int index = combo.getSelectedIndex();
            if (index >= 0 && group.get(index).isEnabled() && !group.get(index).isSelected()) {
                group.get(index).doClick();
            }
        });
        // Live in both directions: a change made in the menu itself moves
        // the combo selection along.
        for (int i = 0; i < group.size(); i++) {
            JRadioButtonMenuItem sibling = group.get(i);
            final int index = i;
            java.awt.event.ItemListener sync = e -> {
                if (sibling.isSelected() && combo.getSelectedIndex() != index) {
                    combo.setSelectedIndex(index);
                }
            };
            sibling.addItemListener(sync);
            itemListenerCleanups.add(() -> sibling.removeItemListener(sync));
        }
        return labeled(row, combo);
    }

    /** Boolean project setting: a toggle that saves and reloads the project. */
    private JComponent createProjectFlagToggle(ActionRow row, ActionSpec.ProjectFlagActionSpec flag) {
        JCheckBox toggle = new JCheckBox(row.name(), ProjectFlagActions.getValue(flag.property()));
        toggle.setFocusable(false);
        toggle.setOpaque(false);
        toggle.setToolTipText(tooltip(row));
        toggle.setEnabled(Core.getProject().isProjectLoaded());
        toggle.addActionListener(e -> {
            if (!ProjectFlagActions.toggleWithReload(flag.property())) {
                toggle.setSelected(ProjectFlagActions.getValue(flag.property()));
            }
        });
        return toggle;
    }

    /** Scalar preference: a labeled slider, applied on release. */
    private JComponent createPreferenceSlider(ActionRow row, PreferenceActionSpec spec) {
        int current = Preferences.getPreferenceDefault(spec.key(), spec.min());
        JSlider slider = new JSlider(spec.min(), spec.max(),
                Math.min(spec.max(), Math.max(spec.min(), current)));
        slider.setFocusable(false);
        slider.setOpaque(false);
        slider.setToolTipText(tooltip(row));
        slider.addChangeListener(e -> {
            if (!slider.getValueIsAdjusting()) {
                Preferences.setPreference(spec.key(), slider.getValue());
                applyPreferenceSideEffects(spec.key());
            }
        });
        // Live in both directions: edits elsewhere (preferences dialog,
        // another panel row) move the slider along.
        java.beans.PropertyChangeListener sync = e -> SwingUtilities.invokeLater(() -> {
            int value = Preferences.getPreferenceDefault(spec.key(), spec.min());
            if (slider.getValue() != value) {
                slider.setValue(Math.min(spec.max(), Math.max(spec.min(), value)));
            }
        });
        Preferences.addPropertyChangeListener(spec.key(), sync);
        itemListenerCleanups.add(() -> Preferences.removePropertyChangeListener(spec.key(), sync));
        return labeled(row, slider);
    }

    /** Enum preference: a labeled combobox over the declared values. */
    private JComponent createPreferenceCombo(ActionRow row, PreferenceActionSpec spec) {
        JComboBox<String> combo = new JComboBox<>(spec.values().toArray(new String[0]));
        combo.setSelectedItem(Preferences.getPreferenceDefault(spec.key(),
                spec.values().isEmpty() ? "" : spec.values().get(0)));
        combo.setFocusable(false);
        combo.setToolTipText(tooltip(row));
        combo.addActionListener(e -> {
            Object selected = combo.getSelectedItem();
            if (selected != null) {
                Preferences.setPreference(spec.key(), selected.toString());
                applyPreferenceSideEffects(spec.key());
            }
        });
        // Live in both directions, matching the slider behaviour.
        java.beans.PropertyChangeListener sync = e -> SwingUtilities.invokeLater(() -> {
            String value = Preferences.getPreferenceDefault(spec.key(),
                    spec.values().isEmpty() ? "" : spec.values().get(0));
            if (!value.equals(combo.getSelectedItem())) {
                combo.setSelectedItem(value);
            }
        });
        Preferences.addPropertyChangeListener(spec.key(), sync);
        itemListenerCleanups.add(() -> Preferences.removePropertyChangeListener(spec.key(), sync));
        return labeled(row, combo);
    }

    /** Some preferences take live effect only through their core event. */
    private void applyPreferenceSideEffects(String key) {
        if (Preferences.TF_SRC_FONT_SIZE.equals(key) || Preferences.TF_SRC_FONT_NAME.equals(key)) {
            CoreEvents.fireFontChanged(FontUtil.getScaledFont());
        }
    }

    private JComponent labeled(ActionRow row, JComponent control) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        wrapper.setOpaque(false);
        JLabel label = new JLabel(row.name());
        label.setLabelFor(control);
        wrapper.add(label);
        wrapper.add(control);
        return wrapper;
    }

    private JButton createButton(ActionRow row) {
        JButton button = new JButton();
        // Toolbar convention: clicking must not move the focus, so editor
        // actions land in the editor and not on the button itself.
        button.setFocusable(false);
        DisplayMode mode = ActionPanelViewOptions.getDisplayMode();
        Icon icon = null;
        if (row.iconRef() != null && mode != DisplayMode.NAME_ONLY) {
            File iconFile = ActionPanelConfig.resolveIcon(row.iconRef());
            // Contrast check against the button's effective background: a
            // configured row colour wins over the theme's button colour.
            Color effectiveBackground = decode(row.backgroundColor());
            if (effectiveBackground == null) {
                effectiveBackground = javax.swing.UIManager.getColor("Button.background");
            }
            if (effectiveBackground == null) {
                effectiveBackground = button.getBackground();
            }
            // Icons follow the global font size, so icon-only buttons resize
            // with the rest of the panel (24 px at the default 12 pt).
            int iconSize = Math.max(16, Math.round(getFont().getSize2D() * 2f));
            icon = IconLoader.load(iconFile, iconSize, effectiveBackground);
        }
        if (icon != null) {
            button.setIcon(icon);
        }
        if (icon == null || mode == DisplayMode.ICON_AND_NAME) {
            button.setText(row.name());
        }
        button.setToolTipText(tooltip(row));
        button.setEnabled(isActionAvailable(row.action()));
        button.getAccessibleContext().setAccessibleName(row.name());
        button.addActionListener(e -> {
            ActionSpec spec = row.action();
            if (spec != null) {
                catalog.rebuild();
                ActionInvoker.invoke(spec, catalog, e.getModifiers());
            }
        });
        return button;
    }

    /** Per-row text, background and border colours, where configured. */
    private void applyRowColors(ActionRow row, JComponent control) {
        Color foreground = decode(row.textColor());
        Color background = decode(row.backgroundColor());
        Color border = decode(row.borderColor());
        if (foreground != null) {
            control.setForeground(foreground);
            for (java.awt.Component child : control.getComponents()) {
                child.setForeground(foreground);
            }
        }
        if (background != null) {
            control.setBackground(background);
            control.setOpaque(true);
            if (control instanceof AbstractButton button) {
                button.setContentAreaFilled(true);
            }
        }
        if (border != null) {
            control.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            if (control instanceof AbstractButton button) {
                // Checkboxes do not paint borders unless told to.
                button.setBorderPainted(true);
            }
        }
    }

    static @Nullable Color decode(@Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The full action as tooltip: the name is already on the button. */
    private String tooltip(ActionRow row) {
        String description = ActionPanelTableModel.describeAction(row.action(), catalog);
        if (description.isEmpty()) {
            description = row.name();
        }
        String shortcut = currentShortcut(row.action());
        if (shortcut == null) {
            return description;
        }
        return MessageFormat.format(ActionPanelModule.getString("TOOLTIP_FORMAT"), description,
                shortcut);
    }

    private @Nullable String currentShortcut(@Nullable ActionSpec spec) {
        KeyStroke keyStroke = null;
        if (spec instanceof MenuActionSpec menu) {
            keyStroke = ShortcutLookup.find(PropertiesShortcuts.getMainMenuShortcuts(), menu.actionCommand());
        } else if (spec instanceof EditorKeyActionSpec editorKey) {
            keyStroke = ShortcutLookup.find(PropertiesShortcuts.getEditorShortcuts(), editorKey.shortcutKey());
        }
        return keyStroke == null ? null : StaticUIUtils.getKeyStrokeText(keyStroke);
    }

    private boolean isActionAvailable(@Nullable ActionSpec spec) {
        if (spec == null || spec instanceof UnknownActionSpec) {
            return false;
        }
        if (spec instanceof MenuActionSpec menu) {
            MenuActionCatalog.MenuEntry entry = catalog.lookup(menu.actionCommand());
            JMenuItem item = entry == null ? null : entry.getItem();
            return item != null && item.isEnabled();
        }
        if (spec instanceof EditorKeyActionSpec editorKey) {
            return Core.getProject().isProjectLoaded()
                    && ShortcutLookup.find(PropertiesShortcuts.getEditorShortcuts(),
                            editorKey.shortcutKey()) != null;
        }
        if (spec instanceof SnippetActionSpec || spec instanceof SearchActionSpec) {
            return Core.getProject().isProjectLoaded();
        }
        if (spec instanceof AutotextRefActionSpec ref) {
            return Core.getProject().isProjectLoaded()
                    && ActionInvoker.findAutotextItem(ref.source()) != null;
        }
        return true;
    }

    @Override
    public void populatePaneMenu(JPopupMenu menu) {
        JMenuItem settings = new JMenuItem(ActionPanelModule.getString("ACTION_PANEL_SETTINGS_MENU"));
        settings.addActionListener(e -> new PreferencesWindowController()
                .show(SwingUtilities.getWindowAncestor(this), ActionPanelPreferencesController.class));
        menu.add(settings);
        menu.addSeparator();

        // The same assignment tree as the settings' Add button, adding rows
        // to the live configuration directly.
        JMenu addMenu = new JMenu(ActionPanelModule.getString("BTN_ADD"));
        JPopupMenu assign = AssignMenuBuilder.build(this, catalog,
                (spec, label) -> appendRows(List.of(new ActionRow(label, null, spec))),
                entries -> appendRows(entries.stream()
                        .map(entry -> new ActionRow(entry.label(), null, entry.spec())).toList()));
        for (java.awt.Component component : assign.getComponents()) {
            addMenu.add(component);
        }
        menu.add(addMenu);

        JMenu displayMenu = new JMenu(ActionPanelModule.getString("MENU_DISPLAY_MODE"));
        ButtonGroup displayGroup = new ButtonGroup();
        addDisplayItem(displayMenu, displayGroup, "DISPLAY_MODE_ICON", DisplayMode.ICON_ONLY);
        addDisplayItem(displayMenu, displayGroup, "DISPLAY_MODE_NAME", DisplayMode.NAME_ONLY);
        addDisplayItem(displayMenu, displayGroup, "DISPLAY_MODE_BOTH", DisplayMode.ICON_AND_NAME);
        menu.add(displayMenu);

        JMenu layoutMenu = new JMenu(ActionPanelModule.getString("MENU_LAYOUT"));
        ButtonGroup layoutGroup = new ButtonGroup();
        addLayoutItem(layoutMenu, layoutGroup, "LAYOUT_FLOW", LayoutMode.FLOW);
        addLayoutItem(layoutMenu, layoutGroup, "LAYOUT_COLUMNS", LayoutMode.COLUMNS);
        layoutMenu.addSeparator();
        JCheckBoxMenuItem reverse = new JCheckBoxMenuItem(ActionPanelModule.getString("LAYOUT_REVERSE"),
                ActionPanelViewOptions.isReverse());
        reverse.addActionListener(e -> ActionPanelViewOptions.setReverse(reverse.isSelected()));
        layoutMenu.add(reverse);
        menu.add(layoutMenu);
    }

    private void addDisplayItem(JMenu menu, ButtonGroup group, String key, DisplayMode mode) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(ActionPanelModule.getString(key),
                ActionPanelViewOptions.getDisplayMode() == mode);
        item.addActionListener(e -> ActionPanelViewOptions.setDisplayMode(mode));
        group.add(item);
        menu.add(item);
    }

    private void addLayoutItem(JMenu menu, ButtonGroup group, String key, LayoutMode mode) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(ActionPanelModule.getString(key),
                ActionPanelViewOptions.getLayoutMode() == mode);
        item.addActionListener(e -> ActionPanelViewOptions.setLayoutMode(mode));
        group.add(item);
        menu.add(item);
    }

    /** Append rows to the live configuration (gear menu add). */
    private void appendRows(List<ActionRow> newRows) {
        List<ActionRow> rows = new ArrayList<>(ActionPanelConfig.getInstance().getRows());
        rows.addAll(newRows);
        ActionPanelConfig.getInstance().setRows(rows);
    }

    /**
     * Drag a control to reorder it in place; drop outside the panel to remove
     * it, after a confirmation unless Shift or Ctrl is held. Both gestures
     * change the stored configuration, not only the view. Labeled comboboxes
     * and sliders are dragged by their label.
     */
    private void installDragReorder(JComponent control) {
        DragReorderHandler handler = new DragReorderHandler(control);
        control.addMouseListener(handler);
        control.addMouseMotionListener(handler);
    }

    private final class DragReorderHandler extends MouseAdapter {
        /** Movement below this many pixels stays a plain click. */
        private static final int DRAG_THRESHOLD = 5;

        private final JComponent control;
        private @Nullable Point pressPoint;
        private boolean dragging;
        private @Nullable Cursor originalCursor;

        private DragReorderHandler(JComponent control) {
            this.control = control;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                pressPoint = e.getLocationOnScreen();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (pressPoint == null) {
                return;
            }
            if (!dragging && e.getLocationOnScreen().distance(pressPoint) > DRAG_THRESHOLD) {
                dragging = true;
                originalCursor = control.getCursor();
                if (control instanceof AbstractButton button) {
                    // The release must reorder, not fire the button action.
                    button.getModel().setArmed(false);
                    button.getModel().setPressed(false);
                }
            }
            if (dragging) {
                boolean inside = insidePanelArea(e);
                // macOS suppresses cursor changes while a button is held, so
                // the painted markers below are the primary feedback there.
                control.setCursor(inside ? MOVE_DRAG_CURSOR : DELETE_CURSOR);
                int indicator = inside ? displayDropIndex(SwingUtilities.convertPoint(e.getComponent(),
                        e.getPoint(), ActionPanelView.this)) : -1;
                JComponent removeMark = inside ? null : control;
                if (indicator != dropIndicator || removeMark != dragRemoveMark) {
                    dropIndicator = indicator;
                    dragRemoveMark = removeMark;
                    repaint();
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1) {
                return;
            }
            boolean wasDragging = dragging;
            dragging = false;
            pressPoint = null;
            dropIndicator = -1;
            dragRemoveMark = null;
            repaint();
            if (!wasDragging) {
                return;
            }
            control.setCursor(originalCursor);
            if (insidePanelArea(e)) {
                dropAt(e);
            } else {
                removeDragged(e);
            }
        }

        /** Inside = within the visible panel area (its viewport if any). */
        private boolean insidePanelArea(MouseEvent e) {
            java.awt.Container viewport = SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,
                    ActionPanelView.this);
            java.awt.Component area = viewport != null ? viewport : ActionPanelView.this;
            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), area);
            return p.x >= 0 && p.y >= 0 && p.x < area.getWidth() && p.y < area.getHeight();
        }

        private void dropAt(MouseEvent e) {
            int from = rowControls.indexOf(control);
            if (from < 0) {
                return;
            }
            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ActionPanelView.this);
            int to = displayDropIndex(p);
            List<ActionRow> reordered = new ArrayList<>(displayRows);
            ActionRow moved = reordered.remove(from);
            if (to > from) {
                to--;
            }
            reordered.add(Math.min(Math.max(to, 0), reordered.size()), moved);
            if (reordered.equals(displayRows)) {
                return;
            }
            if (ActionPanelViewOptions.isReverse()) {
                Collections.reverse(reordered);
            }
            ActionPanelConfig.getInstance().setRows(reordered);
        }

        private void removeDragged(MouseEvent e) {
            int index = rowControls.indexOf(control);
            if (index < 0) {
                return;
            }
            // Cmd counts too: Ctrl+drag is synthesised into a popup gesture
            // on macOS when held from the start.
            boolean skipConfirm = (e.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK
                    | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
            if (!skipConfirm && javax.swing.JOptionPane.showConfirmDialog(ActionPanelView.this,
                    MessageFormat.format(ActionPanelModule.getString("CONFIRM_REMOVE"),
                            displayRows.get(index).name()),
                    ActionPanelModule.getString("ACTION_PANEL_TITLE"),
                    javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) {
                return;
            }
            // The modal dialog pumps events: a rebuild meanwhile replaces the
            // lists, so resolve the control again before removing.
            index = rowControls.indexOf(control);
            if (index < 0) {
                return;
            }
            List<ActionRow> remaining = new ArrayList<>(displayRows);
            remaining.remove(index);
            if (ActionPanelViewOptions.isReverse()) {
                Collections.reverse(remaining);
            }
            ActionPanelConfig.getInstance().setRows(remaining);
        }
    }

    /**
     * Drag feedback painted into the panel: the insertion marker at the drop
     * position, and a red cross over the control an outside drop would
     * remove. Painted rather than cursor-only because macOS suppresses cursor
     * changes while a mouse button is held.
     */
    @Override
    protected void paintChildren(java.awt.Graphics g) {
        super.paintChildren(g);
        boolean indicator = dropIndicator >= 0 && !rowControls.isEmpty();
        if (!indicator && dragRemoveMark == null) {
            return;
        }
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            if (indicator) {
                Color accent = javax.swing.UIManager.getColor("List.selectionBackground");
                g2.setColor(accent != null ? accent : getForeground());
                boolean after = dropIndicator >= rowControls.size();
                Rectangle b = rowControls.get(after ? rowControls.size() - 1 : dropIndicator)
                        .getBounds();
                if (ActionPanelViewOptions.getLayoutMode() == LayoutMode.COLUMNS) {
                    int y = after ? b.y + b.height + 1 : b.y - 3;
                    g2.fillRoundRect(b.x, Math.max(0, y), b.width, 2, 2, 2);
                } else {
                    boolean ltr = getComponentOrientation().isLeftToRight();
                    int x = after == ltr ? b.x + b.width + 1 : b.x - 3;
                    g2.fillRoundRect(Math.max(0, x), b.y, 2, b.height, 2, 2);
                }
            }
            if (dragRemoveMark != null) {
                Rectangle b = dragRemoveMark.getBounds();
                int s = Math.max(12, Math.min(Math.min(b.width, b.height) - 4, 32));
                int x = b.x + (b.width - s) / 2;
                int y = b.y + (b.height - s) / 2;
                g2.setStroke(new java.awt.BasicStroke(Math.max(4f, s / 4f),
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(Color.WHITE);
                g2.drawLine(x, y, x + s, y + s);
                g2.drawLine(x + s, y, x, y + s);
                g2.setStroke(new java.awt.BasicStroke(Math.max(2f, s / 8f),
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(0xcc2222));
                g2.drawLine(x, y, x + s, y + s);
                g2.drawLine(x + s, y, x, y + s);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Display index the point drops in front of, layout and RTL aware. */
    private int displayDropIndex(Point p) {
        boolean columns = ActionPanelViewOptions.getLayoutMode() == LayoutMode.COLUMNS;
        boolean ltr = getComponentOrientation().isLeftToRight();
        for (int i = 0; i < rowControls.size(); i++) {
            Rectangle b = rowControls.get(i).getBounds();
            boolean before;
            if (columns) {
                boolean beforeColumn = ltr ? p.x < b.x : p.x >= b.x + b.width;
                boolean inColumn = p.x >= b.x && p.x < b.x + b.width;
                before = beforeColumn || (inColumn && p.y < b.y + b.height / 2);
            } else {
                boolean inLine = p.y >= b.y && p.y < b.y + b.height;
                before = p.y < b.y
                        || (inLine && (ltr ? p.x < b.x + b.width / 2 : p.x >= b.x + b.width / 2));
            }
            if (before) {
                return i;
            }
        }
        return rowControls.size();
    }

    /** A red cross backed in white, readable on any background. */
    private static Cursor createDeleteCursor() {
        // Class loading must survive headless test environments.
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return Cursor.getDefaultCursor();
        }
        try {
            return paintDeleteCursor();
        } catch (RuntimeException e) {
            // Class loading must never fail over a decorative cursor: decode()
            // and the settings table depend on this class.
            return Cursor.getDefaultCursor();
        }
    }

    private static Cursor paintDeleteCursor() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension size = toolkit.getBestCursorSize(24, 24);
        if (size.width <= 0 || size.height <= 0) {
            // Not DragSource.DefaultMoveNoDrop: its class initializer may
            // itself throw, as an Error the caller's fallback cannot catch.
            return Cursor.getDefaultCursor();
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size.width, size.height,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(size.width, size.height);
            g.setStroke(new java.awt.BasicStroke(Math.max(4f, s / 4f), java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            g.drawLine(4, 4, s - 5, s - 5);
            g.drawLine(s - 5, 4, 4, s - 5);
            g.setStroke(new java.awt.BasicStroke(Math.max(2f, s / 8f), java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xcc2222));
            g.drawLine(4, 4, s - 5, s - 5);
            g.drawLine(s - 5, 4, 4, s - 5);
        } finally {
            g.dispose();
        }
        return toolkit.createCustomCursor(image, new Point(size.width / 2, size.height / 2),
                "actionpanel-delete");
    }

    /**
     * Four-direction move arrows, white-backed like the delete cross. The
     * predefined MOVE_CURSOR renders as the plain arrow on macOS, so a drag
     * needs an own glyph to be visible at all.
     */
    private static Cursor createMoveCursor() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return Cursor.getDefaultCursor();
        }
        try {
            return paintMoveCursor();
        } catch (RuntimeException e) {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
    }

    private static Cursor paintMoveCursor() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension size = toolkit.getBestCursorSize(24, 24);
        if (size.width <= 0 || size.height <= 0) {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size.width, size.height,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(size.width, size.height);
            // Poor man's outline: the glyph in white at four offsets first.
            for (int[] offset : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                paintMoveGlyph(g, s, offset[0], offset[1], Color.WHITE);
            }
            paintMoveGlyph(g, s, 0, 0, new Color(0x222222));
        } finally {
            g.dispose();
        }
        return toolkit.createCustomCursor(image, new Point(size.width / 2, size.height / 2),
                "actionpanel-move");
    }

    private static void paintMoveGlyph(java.awt.Graphics2D g, int s, int dx, int dy, Color color) {
        g.translate(dx, dy);
        g.setColor(color);
        g.setStroke(new java.awt.BasicStroke(Math.max(2f, s / 10f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        int c = s / 2;
        int a = Math.max(3, s / 4);
        int h = a - 1;
        g.drawLine(a, c, s - 1 - a, c);
        g.drawLine(c, a, c, s - 1 - a);
        g.fillPolygon(new int[] { 1, 1 + a, 1 + a }, new int[] { c, c - h, c + h }, 3);
        g.fillPolygon(new int[] { s - 2, s - 2 - a, s - 2 - a }, new int[] { c, c - h, c + h }, 3);
        g.fillPolygon(new int[] { c, c - h, c + h }, new int[] { 1, 1 + a, 1 + a }, 3);
        g.fillPolygon(new int[] { c, c - h, c + h }, new int[] { s - 2, s - 2 - a, s - 2 - a }, 3);
        g.translate(-dx, -dy);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        // Roughly one control row, following the font-scaled icon size.
        return Math.max(IconLoader.PANEL_ICON_SIZE, Math.round(getFont().getSize2D() * 2f));
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return ActionPanelViewOptions.getLayoutMode() == LayoutMode.FLOW;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return ActionPanelViewOptions.getLayoutMode() == LayoutMode.COLUMNS;
    }
}
