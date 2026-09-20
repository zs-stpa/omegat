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
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.events.IColorsChangedEventListener;
import org.omegat.core.events.IFontChangedEventListener;
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
    private final transient IFontChangedEventListener fontListener = font -> SwingUtilities
            .invokeLater(this::rebuild);
    private final transient IColorsChangedEventListener colorsListener = () -> SwingUtilities
            .invokeLater(this::rebuild);
    /** Menu items we attached sync listeners to, cleared on every rebuild. */
    private final transient List<Runnable> itemListenerCleanups = new ArrayList<>();
    /** Rows in display order, parallel to {@link #rowControls}. */
    private final transient List<ActionRow> displayRows = new ArrayList<>();
    private final transient List<JComponent> rowControls = new ArrayList<>();
    private static final Cursor DELETE_CURSOR = createDeleteCursor();
    private static final Cursor MOVE_DRAG_CURSOR = createMoveCursor(false);
    private static final Cursor COPY_DRAG_CURSOR = createMoveCursor(true);
    private static final Cursor NO_DROP_CURSOR = createNoDropCursor();
    /** Display index an active drag would drop at; -1 without a drag. */
    private transient int dropIndicator = -1;
    /** Control an outside drag would remove; marked with a painted cross. */
    private transient @Nullable JComponent dragRemoveMark;
    /** What the current drag would do, for the painted markers. */
    private transient DragEffect dragEffect = DragEffect.MOVE;
    /** Modifier watcher of the running drag, so feedback follows the keys. */
    private transient @Nullable KeyEventDispatcher dragKeys;

    /**
     * What releasing a drag does, from the drop side and the modifiers:
     * inside the panel Shift copies, otherwise moves; outside Shift does
     * nothing (a copy has nowhere to go), Ctrl or Cmd removes without
     * asking, otherwise removal asks first. On macOS Ctrl held at the press
     * opens the context menu instead; press it after the drag started, or
     * use Cmd.
     */
    enum DragEffect {
        MOVE, COPY, REMOVE, REMOVE_SILENT, NONE;

        static DragEffect of(boolean inside, int modifiersEx) {
            boolean shift = (modifiersEx & InputEvent.SHIFT_DOWN_MASK) != 0;
            if (inside) {
                return shift ? COPY : MOVE;
            }
            if (shift) {
                return NONE;
            }
            boolean silent = (modifiersEx & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
            return silent ? REMOVE_SILENT : REMOVE;
        }
    }

    public ActionPanelView() {
        setName(ComponentNames.PANEL);
        ActionPanelConfig.getInstance().addChangeListener(configListener);
        PropertiesShortcuts.getMainMenuShortcuts().addChangeListener(configListener);
        PropertiesShortcuts.getEditorShortcuts().addChangeListener(configListener);
        CoreEvents.registerProjectChangeListener(this);
        CoreEvents.registerFontChangedEventListener(fontListener);
        CoreEvents.registerColorsChangedEventListener(colorsListener);
        rebuild();
    }

    /**
     * Let go of every global listener, so a replaced view (the acceptance
     * tests start the application once per test) stops rebuilding itself.
     */
    void dispose() {
        ActionPanelConfig.getInstance().removeChangeListener(configListener);
        PropertiesShortcuts.getMainMenuShortcuts().removeChangeListener(configListener);
        PropertiesShortcuts.getEditorShortcuts().removeChangeListener(configListener);
        CoreEvents.unregisterProjectChangeListener(this);
        CoreEvents.unregisterFontChangedEventListener(fontListener);
        CoreEvents.unregisterColorsChangedEventListener(colorsListener);
        itemListenerCleanups.forEach(Runnable::run);
        itemListenerCleanups.clear();
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
        uninstallDragKeys();
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
            hint.setName(ComponentNames.EMPTY_HINT);
            hint.setEnabled(false);
            hint.setFont(globalFont);
            add(hint);
        }
        for (ActionRow row : rows) {
            JComponent control = createControl(row);
            applyGlobalStyle(control, globalFont);
            applyRowColors(row, control);
            installDragReorder(control);
            installContextMenu(row, control);
            displayRows.add(row);
            rowControls.add(control);
            add(control);
        }
        revalidate();
        repaint();
    }

    /** Global font on every control; panel foreground on borderless text. */
    private void applyGlobalStyle(Component component, Font font) {
        String text = component instanceof JLabel label ? label.getText()
                : component instanceof AbstractButton button ? button.getText() : null;
        component.setFont(FallbackFonts.withGlyphFallback(font, text));
        if (component instanceof JLabel || component instanceof JCheckBox) {
            component.setForeground(getForeground());
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
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
        } else if (spec instanceof PreferenceActionSpec stored) {
            // The row owns its name; kind, range and default are the
            // catalog's business and follow later versions.
            PreferenceActionSpec preference = PreferenceCatalog.resolve(stored);
            control = switch (preference.kind()) {
            case PreferenceCatalog.KIND_COMBOBOX -> createPreferenceCombo(row, preference);
            case PreferenceCatalog.KIND_TOGGLE -> createPreferenceToggle(row, preference);
            case PreferenceCatalog.KIND_SLIDER -> createPreferenceSlider(row, preference);
            // A kind from a newer version: a disabled button, never a guess.
            default -> createButton(row);
            };
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
        return withIcon(row, toggle);
    }

    /** Radio menu item: a labeled combobox over its sibling group. */
    private JComponent createRadioGroupCombo(ActionRow row, JRadioButtonMenuItem item) {
        List<JRadioButtonMenuItem> group = new ArrayList<>();
        if (item.getParent() instanceof JPopupMenu parent) {
            for (Component sibling : parent.getComponents()) {
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
        return withIcon(row, toggle);
    }

    /** Boolean preference: a switch, live in both directions. */
    private JComponent createPreferenceToggle(ActionRow row, PreferenceActionSpec spec) {
        JCheckBox toggle = new JCheckBox(row.name(), PreferenceCatalog.isSelected(spec));
        toggle.setFocusable(false);
        toggle.setOpaque(false);
        toggle.setToolTipText(tooltip(row));
        toggle.addActionListener(e -> {
            Preferences.setPreference(spec.key(), toggle.isSelected());
            PreferenceCatalog.applySideEffects(spec.key());
        });
        java.beans.PropertyChangeListener sync = e -> SwingUtilities.invokeLater(() -> {
            boolean value = PreferenceCatalog.isSelected(spec);
            if (toggle.isSelected() != value) {
                toggle.setSelected(value);
            }
        });
        Preferences.addPropertyChangeListener(spec.key(), sync);
        itemListenerCleanups.add(() -> Preferences.removePropertyChangeListener(spec.key(), sync));
        return withIcon(row, toggle);
    }

    /** Scalar preference: a labeled slider, applied on release. */
    private JComponent createPreferenceSlider(ActionRow row, PreferenceActionSpec spec) {
        int current = PreferenceCatalog.intValue(spec);
        JSlider slider = new JSlider(spec.min(), spec.max(),
                Math.min(spec.max(), Math.max(spec.min(), current)));
        slider.setFocusable(false);
        slider.setOpaque(false);
        slider.setToolTipText(tooltip(row));
        // Readout after the knob: current value and range, e.g. "12 [8\u201332]".
        // Reserves the width of the widest value in the current font (digits
        // are tabular in UI fonts), so the row neither jitters while dragging
        // nor truncates after the panel font grows (the font size slider
        // changes it live).
        String range = " [" + spec.min() + "\u2013" + spec.max() + "]";
        int widest = Math.max(Integer.toString(spec.min()).length(), Integer.toString(spec.max()).length());
        String widestText = "0".repeat(widest) + range;
        JLabel readout = new JLabel(slider.getValue() + range) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.width = Math.max(size.width, getFontMetrics(getFont()).stringWidth(widestText)
                        + getInsets().left + getInsets().right);
                return size;
            }
        };
        readout.setName(ComponentNames.readout(row.id()));
        readout.setToolTipText(tooltip(row));
        slider.addChangeListener(e -> {
            readout.setText(slider.getValue() + range);
            if (!slider.getValueIsAdjusting()) {
                Preferences.setPreference(spec.key(), slider.getValue());
                PreferenceCatalog.applySideEffects(spec.key());
            }
        });
        // Live in both directions: edits elsewhere (preferences dialog,
        // another panel row) move the slider along.
        java.beans.PropertyChangeListener sync = e -> SwingUtilities.invokeLater(() -> {
            int value = PreferenceCatalog.intValue(spec);
            if (slider.getValue() != value) {
                slider.setValue(Math.min(spec.max(), Math.max(spec.min(), value)));
            }
        });
        Preferences.addPropertyChangeListener(spec.key(), sync);
        itemListenerCleanups.add(() -> Preferences.removePropertyChangeListener(spec.key(), sync));
        JComponent wrapper = labeled(row, slider);
        wrapper.add(readout);
        return wrapper;
    }

    /** Enum preference: a labeled combobox over the declared values. */
    private JComponent createPreferenceCombo(ActionRow row, PreferenceActionSpec spec) {
        JComboBox<String> combo = new JComboBox<>(spec.values().toArray(new String[0]));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, @Nullable Object value, int index,
                    boolean selected, boolean focused) {
                Object shown = value == null ? null : PreferenceCatalog.valueLabel(spec.key(), value.toString());
                return super.getListCellRendererComponent(list, shown, index, selected, focused);
            }
        });
        selectComboValue(combo, spec);
        combo.setFocusable(false);
        combo.setToolTipText(tooltip(row));
        combo.addActionListener(e -> {
            Object selected = combo.getSelectedItem();
            if (selected != null) {
                Preferences.setPreference(spec.key(), selected.toString());
                PreferenceCatalog.applySideEffects(spec.key());
            }
        });
        // Live in both directions, matching the slider behaviour.
        java.beans.PropertyChangeListener sync = e -> SwingUtilities.invokeLater(() -> {
            selectComboValue(combo, spec);
        });
        Preferences.addPropertyChangeListener(spec.key(), sync);
        itemListenerCleanups.add(() -> Preferences.removePropertyChangeListener(spec.key(), sync));
        return labeled(row, combo);
    }

    /**
     * Show the stored value even when the catalog no longer lists it (a
     * renamed enum constant, a foreign omegat.prefs): it is appended as an
     * extra item rather than silently displayed as the first entry.
     */
    private static void selectComboValue(JComboBox<String> combo, PreferenceActionSpec spec) {
        String value = Preferences.getPreferenceDefault(spec.key(),
                spec.values().isEmpty() ? "" : spec.values().get(0));
        if (value.equals(combo.getSelectedItem())) {
            return;
        }
        boolean listed = false;
        for (int i = 0; i < combo.getItemCount(); i++) {
            listed |= value.equals(combo.getItemAt(i));
        }
        if (!listed) {
            combo.addItem(value);
        }
        combo.setSelectedItem(value);
    }

    /**
     * Label plus control; the label carries the row icon, if any. Names
     * (see {@link ComponentNames}) are given here, where the parts are built.
     */
    private JComponent labeled(ActionRow row, JComponent control) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        wrapper.setName(ComponentNames.row(row.id()));
        wrapper.setOpaque(false);
        control.setName(ComponentNames.control(row.id()));
        Icon icon = loadRowIcon(row, null);
        JLabel label = new JLabel(
                icon != null && ActionPanelViewOptions.getDisplayMode() == DisplayMode.ICON_ONLY ? "" : row.name());
        label.setName(ComponentNames.label(row.id()));
        if (icon != null) {
            label.setIcon(icon);
            label.setToolTipText(tooltip(row));
        }
        label.setLabelFor(control);
        wrapper.add(label);
        wrapper.add(control);
        return wrapper;
    }

    /**
     * The row's icon at panel size, or null when none is configured or the
     * display mode hides icons. The contrast check runs against the control's
     * effective background: a configured row colour wins over the theme's.
     */
    private @Nullable Icon loadRowIcon(ActionRow row, @Nullable Color themeBackground) {
        if (row.iconRef() == null || ActionPanelViewOptions.getDisplayMode() == DisplayMode.NAME_ONLY) {
            return null;
        }
        File iconFile = ActionPanelConfig.resolveIcon(row.iconRef());
        Color effectiveBackground = RowEditing.decode(row.backgroundColor());
        if (effectiveBackground == null) {
            effectiveBackground = themeBackground;
        }
        if (effectiveBackground == null) {
            effectiveBackground = getBackground();
        }
        // Icons follow the global font size, so icon-only buttons resize
        // with the rest of the panel (24 px at the default 12 pt).
        int iconSize = Math.max(16, Math.round(getFont().getSize2D() * 2f));
        return IconLoader.load(iconFile, iconSize, effectiveBackground);
    }

    /**
     * A checkbox keeps its tick, so the row icon goes into a label in front
     * of it; in icon-only mode the name moves into the tooltip.
     */
    private JComponent withIcon(ActionRow row, JCheckBox toggle) {
        Icon icon = loadRowIcon(row, null);
        toggle.setName(ComponentNames.control(row.id()));
        toggle.getAccessibleContext().setAccessibleName(row.name());
        if (icon == null) {
            toggle.setText(row.name());
            return toggle;
        }
        toggle.setText(ActionPanelViewOptions.getDisplayMode() == DisplayMode.ICON_ONLY ? "" : row.name());
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        wrapper.setName(ComponentNames.row(row.id()));
        wrapper.setOpaque(false);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setName(ComponentNames.icon(row.id()));
        iconLabel.setToolTipText(tooltip(row));
        wrapper.add(iconLabel);
        wrapper.add(toggle);
        return wrapper;
    }

    private JButton createButton(ActionRow row) {
        JButton button = new JButton();
        button.setName(ComponentNames.control(row.id()));
        // Toolbar convention: clicking must not move the focus, so editor
        // actions land in the editor and not on the button itself.
        button.setFocusable(false);
        Icon icon = loadRowIcon(row, javax.swing.UIManager.getColor("Button.background"));
        if (icon != null) {
            button.setIcon(icon);
        }
        if (icon == null || ActionPanelViewOptions.getDisplayMode() == DisplayMode.ICON_AND_NAME) {
            button.setText(row.name());
        }
        button.setToolTipText(tooltip(row));
        button.setEnabled(isActionAvailable(row.action()));
        button.getAccessibleContext().setAccessibleName(row.name());
        button.addActionListener(e -> {
            ActionSpec spec = row.action();
            if (spec != null) {
                catalog.rebuild();
                ActionInvoker.invoke(spec, catalog, e.getModifiers(), ActionPanelView.this,
                        updated -> updateRowAction(row, updated));
            }
        });
        return button;
    }

    /** Store a revised action back into the row's configuration entry. */
    private void updateRowAction(ActionRow row, ActionSpec updated) {
        changeRow(row.id(), r -> r.withAction(updated));
    }

    /** Per-row text, background and border colours, where configured. */
    private void applyRowColors(ActionRow row, JComponent control) {
        Color foreground = RowEditing.decode(row.textColor());
        Color background = RowEditing.decode(row.backgroundColor());
        Color border = RowEditing.decode(row.borderColor());
        if (foreground != null) {
            control.setForeground(foreground);
            for (Component child : control.getComponents()) {
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
                // Bare checkboxes do not paint borders unless told to;
                // icon-bearing ones sit in a wrapper that paints its own.
                button.setBorderPainted(true);
            }
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
        if (spec instanceof PreferenceActionSpec preference) {
            return PreferenceCatalog.isKnownKind(PreferenceCatalog.resolve(preference).kind());
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
        settings.setName(ComponentNames.paneMenu("ACTION_PANEL_SETTINGS_MENU"));
        settings.addActionListener(e -> openSettings());
        menu.add(settings);
        menu.addSeparator();

        // The same assignment tree as the settings' Add button, adding rows
        // to the live configuration directly.
        JMenu addMenu = new JMenu(ActionPanelModule.getString("BTN_ADD"));
        addMenu.setName(ComponentNames.paneMenu("BTN_ADD"));
        JPopupMenu assign = AssignMenuBuilder.build(this, catalog,
                (spec, label) -> appendRows(List.of(new ActionRow(label, null, spec))),
                entries -> appendRows(entries.stream()
                        .map(entry -> new ActionRow(entry.label(), null, entry.spec())).toList()));
        for (Component component : assign.getComponents()) {
            addMenu.add(component);
        }
        menu.add(addMenu);

        JMenu displayMenu = new JMenu(ActionPanelModule.getString("MENU_DISPLAY_MODE"));
        displayMenu.setName(ComponentNames.paneMenu("MENU_DISPLAY_MODE"));
        ButtonGroup displayGroup = new ButtonGroup();
        addDisplayItem(displayMenu, displayGroup, "DISPLAY_MODE_ICON", DisplayMode.ICON_ONLY);
        addDisplayItem(displayMenu, displayGroup, "DISPLAY_MODE_NAME", DisplayMode.NAME_ONLY);
        addDisplayItem(displayMenu, displayGroup, "DISPLAY_MODE_BOTH", DisplayMode.ICON_AND_NAME);
        menu.add(displayMenu);

        JMenu layoutMenu = new JMenu(ActionPanelModule.getString("MENU_LAYOUT"));
        layoutMenu.setName(ComponentNames.paneMenu("MENU_LAYOUT"));
        ButtonGroup layoutGroup = new ButtonGroup();
        addLayoutItem(layoutMenu, layoutGroup, "LAYOUT_FLOW", LayoutMode.FLOW);
        addLayoutItem(layoutMenu, layoutGroup, "LAYOUT_COLUMNS", LayoutMode.COLUMNS);
        layoutMenu.addSeparator();
        JCheckBoxMenuItem reverse = new JCheckBoxMenuItem(ActionPanelModule.getString("LAYOUT_REVERSE"),
                ActionPanelViewOptions.isReverse());
        reverse.setName(ComponentNames.paneMenu("LAYOUT_REVERSE"));
        reverse.addActionListener(e -> ActionPanelViewOptions.setReverse(reverse.isSelected()));
        layoutMenu.add(reverse);
        menu.add(layoutMenu);
    }

    private void addDisplayItem(JMenu menu, ButtonGroup group, String key, DisplayMode mode) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(ActionPanelModule.getString(key),
                ActionPanelViewOptions.getDisplayMode() == mode);
        item.setName(ComponentNames.paneMenu(key));
        item.addActionListener(e -> ActionPanelViewOptions.setDisplayMode(mode));
        group.add(item);
        menu.add(item);
    }

    private void addLayoutItem(JMenu menu, ButtonGroup group, String key, LayoutMode mode) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(ActionPanelModule.getString(key),
                ActionPanelViewOptions.getLayoutMode() == mode);
        item.setName(ComponentNames.paneMenu(key));
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
     * The row's context menu as Swing component popup: reachable by right
     * click on the control or any child (labels, the checkbox inside its
     * wrapper, the combobox itself) and by the keyboard's context menu key.
     * Entries refresh their enabled state each time the menu opens.
     */
    private void installContextMenu(ActionRow row, JComponent control) {
        control.setComponentPopupMenu(buildRowMenu(row));
        inheritPopupMenu(control);
    }

    private static void inheritPopupMenu(JComponent parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComponent component) {
                component.setInheritsPopupMenu(true);
                inheritPopupMenu(component);
            }
        }
    }

    /**
     * The context menu of a row: entries specific to its action type first,
     * then the generic ones (rename, icon, colours, duplicate, remove) and
     * the panel settings, so a row is fully configurable in place. Entries
     * act on the live row looked up by id when clicked, and re-evaluate
     * their enabled state each time the menu opens, so a menu built before
     * a rebuild still does the right thing.
     */
    private JPopupMenu buildRowMenu(ActionRow row) {
        JPopupMenu menu = new JPopupMenu();
        menu.setName(ComponentNames.rowMenu(row.id()));
        List<Runnable> refreshers = new ArrayList<>();
        addTypeSpecificEntries(menu, row, refreshers);
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        addGenericEntries(menu, row, refreshers);
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                refreshers.forEach(Runnable::run);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        return menu;
    }

    private void addTypeSpecificEntries(JPopupMenu menu, ActionRow row, List<Runnable> refreshers) {
        if (row.action() instanceof SearchActionSpec search) {
            JMenuItem filter = rowMenuItem(row, "ROW_MENU_APPLY_FILTER");
            filter.addActionListener(e -> ActionInvoker.applySearchFilter(search, this,
                    updated -> updateRowAction(row, updated)));
            refreshers.add(() -> filter
                    .setEnabled(Core.getProject().isProjectLoaded() && !search.query().isEmpty()));
            menu.add(filter);
            JMenuItem edit = rowMenuItem(row, "ROW_MENU_EDIT_SEARCH");
            edit.addActionListener(e -> {
                ActionSpec updated = SearchActionWizard.show(this, search.replace(), search);
                if (updated != null) {
                    updateRowAction(row, updated);
                }
            });
            menu.add(edit);
        }
    }

    private void addGenericEntries(JPopupMenu menu, ActionRow row, List<Runnable> refreshers) {
        String id = row.id();
        ActionPanelConfig config = ActionPanelConfig.getInstance();
        JMenuItem rename = rowMenuItem(row, "ROW_MENU_RENAME");
        rename.addActionListener(e -> {
            ActionRow live = config.findRow(id);
            if (live == null) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            Object name = JOptionPane.showInputDialog(this, ActionPanelModule.getString("ROW_MENU_RENAME_PROMPT"),
                    ActionPanelModule.getString("ACTION_PANEL_TITLE"), JOptionPane.PLAIN_MESSAGE, null, null,
                    live.name());
            if (name != null && !name.toString().isBlank()) {
                changeRow(id, r -> r.withName(name.toString().trim()));
            }
        });
        menu.add(rename);

        JMenuItem icon = rowMenuItem(row, "ROW_MENU_ICON");
        icon.addActionListener(e -> {
            String iconRef = RowEditing.chooseIcon(this);
            if (iconRef != null) {
                changeRow(id, r -> r.withIconRef(iconRef));
            }
        });
        menu.add(icon);
        // Drops the reference only; a file copied into the icon folder stays
        // there, other rows may use it.
        JMenuItem removeIcon = rowMenuItem(row, "ROW_MENU_ICON_REMOVE");
        removeIcon.addActionListener(e -> changeRow(id, r -> r.withIconRef(null)));
        refreshers.add(() -> {
            ActionRow live = config.findRow(id);
            removeIcon.setEnabled(live != null && live.iconRef() != null);
        });
        menu.add(removeIcon);

        JMenu colors = new JMenu(ActionPanelModule.getString("ROW_MENU_COLORS"));
        colors.setName(ComponentNames.rowMenuEntry(id, "ROW_MENU_COLORS"));
        String[] colorKeys = { "COL_TEXT_COLOR", "COL_BACKGROUND_COLOR", "COL_BORDER_COLOR" };
        for (int i = 0; i < colorKeys.length; i++) {
            final int column = i;
            JMenuItem item = rowMenuItem(row, colorKeys[i]);
            item.addActionListener(e -> {
                ActionRow live = config.findRow(id);
                if (live == null) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }
                String[] current = { live.textColor(), live.backgroundColor(), live.borderColor() };
                String hex = RowEditing.chooseColor(this, item.getText(), RowEditing.decode(current[column]));
                if (hex != null) {
                    changeRow(id, r -> r.withColor(column, hex));
                }
            });
            colors.add(item);
        }
        colors.addSeparator();
        JMenuItem reset = rowMenuItem(row, "COLOR_RESET");
        reset.addActionListener(e -> changeRow(id, r -> r.withColor(0, null).withColor(1, null).withColor(2, null)));
        refreshers.add(() -> {
            ActionRow live = config.findRow(id);
            reset.setEnabled(live != null
                    && (live.textColor() != null || live.backgroundColor() != null || live.borderColor() != null));
        });
        colors.add(reset);
        menu.add(colors);

        menu.addSeparator();
        JMenuItem duplicate = rowMenuItem(row, "BTN_DUPLICATE");
        duplicate.addActionListener(e -> {
            if (!config.duplicateRow(id)) {
                Toolkit.getDefaultToolkit().beep();
            }
        });
        menu.add(duplicate);
        JMenuItem remove = rowMenuItem(row, "BTN_REMOVE");
        remove.addActionListener(e -> {
            ActionRow live = config.findRow(id);
            if (live != null && confirmRemove(live) && !config.removeRow(id)) {
                Toolkit.getDefaultToolkit().beep();
            }
        });
        menu.add(remove);

        menu.addSeparator();
        JMenuItem settings = rowMenuItem(row, "ACTION_PANEL_SETTINGS_MENU");
        settings.addActionListener(e -> openSettings());
        menu.add(settings);
    }

    /** Change the live row by id; a beep when it was edited away meanwhile. */
    private static void changeRow(String id, java.util.function.UnaryOperator<ActionRow> change) {
        if (!ActionPanelConfig.getInstance().updateRow(id, change)) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void openSettings() {
        new PreferencesWindowController().show(SwingUtilities.getWindowAncestor(this),
                ActionPanelPreferencesController.class);
    }

    private boolean confirmRemove(ActionRow row) {
        return JOptionPane.showConfirmDialog(this,
                MessageFormat.format(ActionPanelModule.getString("CONFIRM_REMOVE"), row.name()),
                ActionPanelModule.getString("ACTION_PANEL_TITLE"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void addTypeSpecificEntries(JPopupMenu menu, ActionRow row) {
        if (row.action() instanceof SearchActionSpec search) {
            JMenuItem filter = rowMenuItem(row, "ROW_MENU_APPLY_FILTER");
            filter.addActionListener(e -> ActionInvoker.applySearchFilter(search, this,
                    updated -> updateRowAction(row, updated)));
            menu.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    filter.setEnabled(Core.getProject().isProjectLoaded() && !search.query().isEmpty());
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {
                }
            });
            menu.add(filter);
            JMenuItem edit = rowMenuItem(row, "ROW_MENU_EDIT_SEARCH");
            edit.addActionListener(e -> {
                ActionSpec updated = SearchActionWizard.show(this, search.replace(), search);
                if (updated != null) {
                    updateRowAction(row, updated);
                }
            });
            menu.add(edit);
        }
    }

    private static JMenuItem rowMenuItem(ActionRow row, String bundleKey) {
        JMenuItem item = new JMenuItem(ActionPanelModule.getString(bundleKey));
        item.setName(ComponentNames.rowMenuEntry(row.id(), bundleKey));
        return item;
    }

    /**
     * Drag a control to reorder it in place; with Shift held at release, a
     * copy lands at the drop position and the original stays. Drop outside
     * the panel to remove the row, after a confirmation unless Ctrl or Cmd
     * is held; with Shift an outside drop does nothing, so an overshot copy
     * never deletes. Cursor and painted markers follow the modifiers live,
     * also without mouse motion. All gestures change the stored
     * configuration, not only the view. Labeled comboboxes, sliders and
     * icon-bearing toggles are dragged by their label.
     */
    private void installDragReorder(JComponent control) {
        DragReorderHandler handler = new DragReorderHandler(control);
        control.addMouseListener(handler);
        control.addMouseMotionListener(handler);
        // Labels inside a wrapper carry tooltips, and a tooltip registers a
        // mouse listener, so the press would stop there instead of reaching
        // the wrapper. The handler converts coordinates from the event
        // source, so it can sit on the labels directly.
        for (Component child : control.getComponents()) {
            if (child instanceof JLabel) {
                child.addMouseListener(handler);
                child.addMouseMotionListener(handler);
            }
        }
    }

    /**
     * While a drag runs, modifier key presses and releases re-evaluate the
     * feedback at once, so the cursor and the markers show what the release
     * would do before the mouse moves again.
     */
    private void installDragKeys(DragReorderHandler handler) {
        uninstallDragKeys();
        dragKeys = e -> {
            int code = e.getKeyCode();
            if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_META
                    || code == KeyEvent.VK_ALT) {
                handler.updateFeedback(e.getModifiersEx());
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dragKeys);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        uninstallDragKeys();
    }

    private void uninstallDragKeys() {
        if (dragKeys != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dragKeys);
            dragKeys = null;
        }
    }

    private final class DragReorderHandler extends MouseAdapter {
        /** Movement below this many pixels stays a plain click. */
        private static final int DRAG_THRESHOLD = 5;

        private final JComponent control;
        private @Nullable Point pressPoint;
        private boolean dragging;
        private @Nullable Cursor originalCursor;
        /** Last drag position in panel coordinates, and whether it was inside. */
        private @Nullable Point lastPanelPoint;
        private boolean lastInside;

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
                installDragKeys(this);
            }
            if (dragging) {
                lastInside = insidePanelArea(e);
                lastPanelPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ActionPanelView.this);
                updateFeedback(e.getModifiersEx());
            }
        }

        /**
         * Cursor and painted markers for the current position and modifiers;
         * called on every drag step and whenever a modifier key changes.
         * macOS suppresses cursor changes while a button is held, so the
         * painted markers are the primary feedback there.
         */
        void updateFeedback(int modifiersEx) {
            if (!dragging || lastPanelPoint == null) {
                return;
            }
            DragEffect effect = DragEffect.of(lastInside, modifiersEx);
            control.setCursor(switch (effect) {
            case COPY -> COPY_DRAG_CURSOR;
            case MOVE -> MOVE_DRAG_CURSOR;
            case NONE -> NO_DROP_CURSOR;
            case REMOVE, REMOVE_SILENT -> DELETE_CURSOR;
            });
            int indicator = lastInside ? displayDropIndex(lastPanelPoint) : -1;
            JComponent mark = lastInside ? null : control;
            if (indicator != dropIndicator || mark != dragRemoveMark || effect != dragEffect) {
                dropIndicator = indicator;
                dragRemoveMark = mark;
                dragEffect = effect;
                repaint();
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
            lastPanelPoint = null;
            dropIndicator = -1;
            dragRemoveMark = null;
            uninstallDragKeys();
            repaint();
            if (!wasDragging) {
                return;
            }
            control.setCursor(originalCursor);
            switch (DragEffect.of(insidePanelArea(e), e.getModifiersEx())) {
            case COPY -> dropAt(e, true);
            case MOVE -> dropAt(e, false);
            case REMOVE -> removeDragged(true);
            case REMOVE_SILENT -> removeDragged(false);
            default -> {
                // NONE: Shift means copy; outside the panel there is nowhere
                // to copy to, and it must never turn into a delete.
            }
            }
        }

        /** Inside = within the visible panel area (its viewport if any). */
        private boolean insidePanelArea(MouseEvent e) {
            java.awt.Container viewport = SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,
                    ActionPanelView.this);
            Component area = viewport != null ? viewport : ActionPanelView.this;
            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), area);
            return p.x >= 0 && p.y >= 0 && p.x < area.getWidth() && p.y < area.getHeight();
        }

        private void dropAt(MouseEvent e, boolean copy) {
            int from = rowControls.indexOf(control);
            if (from < 0) {
                return;
            }
            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ActionPanelView.this);
            int to = displayDropIndex(p);
            List<ActionRow> reordered = new ArrayList<>(displayRows);
            if (copy) {
                // A copy lands at the drop position, the original stays.
                reordered.add(Math.min(Math.max(to, 0), reordered.size()),
                        RowEditing.copyOf(displayRows.get(from)));
                if (ActionPanelViewOptions.isReverse()) {
                    Collections.reverse(reordered);
                }
                ActionPanelConfig.getInstance().setRows(reordered);
                return;
            }
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

        private void removeDragged(boolean confirm) {
            int index = rowControls.indexOf(control);
            if (index < 0) {
                return;
            }
            if (confirm && !confirmRemove(displayRows.get(index))) {
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
                int badgeX;
                int badgeY;
                if (ActionPanelViewOptions.getLayoutMode() == LayoutMode.COLUMNS) {
                    int y = after ? b.y + b.height + 1 : b.y - 3;
                    g2.fillRoundRect(b.x, Math.max(0, y), b.width, 2, 2, 2);
                    badgeX = b.x + b.width / 2;
                    badgeY = Math.max(0, y) + 1;
                } else {
                    boolean ltr = getComponentOrientation().isLeftToRight();
                    int x = after == ltr ? b.x + b.width + 1 : b.x - 3;
                    g2.fillRoundRect(Math.max(0, x), b.y, 2, b.height, 2, 2);
                    badgeX = Math.max(0, x) + 1;
                    badgeY = b.y + b.height / 2;
                }
                if (dragEffect == DragEffect.COPY) {
                    // A plus badge on the insertion marker: this drop copies.
                    // Kept inside the panel: a drop before the first row puts
                    // the marker at the edge.
                    int margin = BADGE_RADIUS + 2;
                    paintPlusBadge(g2, Math.min(Math.max(badgeX, margin), getWidth() - margin),
                            Math.min(Math.max(badgeY, margin), getHeight() - margin), BADGE_RADIUS, g2.getColor());
                }
            }
            if (dragRemoveMark != null) {
                Rectangle b = dragRemoveMark.getBounds();
                int s = Math.max(12, Math.min(Math.min(b.width, b.height) - 4, 32));
                int x = b.x + (b.width - s) / 2;
                int y = b.y + (b.height - s) / 2;
                if (dragEffect == DragEffect.NONE) {
                    // Shift outside: nothing will happen, shown as a grey no-drop sign.
                    paintNoDropSign(g2, x, y, s);
                } else {
                    paintCross(g2, x, y, s);
                }
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
            paintCross(g, 4, 4, s - 9);
        } finally {
            g.dispose();
        }
        return toolkit.createCustomCursor(image, new Point(size.width / 2, size.height / 2),
                "actionpanel-delete");
    }

    /** Half length of the plus badge's arms on the panel. */
    private static final int BADGE_RADIUS = 5;

    /** White-outlined plus with arms of length r, centred on (cx, cy); the copy badge and cursor mark. */
    private static void paintPlusBadge(java.awt.Graphics2D g, int cx, int cy, int r, Color color) {
        g.setStroke(new java.awt.BasicStroke(Math.max(3f, r * 0.8f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.drawLine(cx - r, cy, cx + r, cy);
        g.drawLine(cx, cy - r, cx, cy + r);
        g.setStroke(new java.awt.BasicStroke(Math.max(1.5f, r * 0.4f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(color);
        g.drawLine(cx - r, cy, cx + r, cy);
        g.drawLine(cx, cy - r, cx, cy + r);
    }

    /** White-backed red cross of size s at (x, y): the remove mark and cursor. */
    private static void paintCross(java.awt.Graphics2D g, int x, int y, int s) {
        g.setStroke(new java.awt.BasicStroke(Math.max(4f, s / 4f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.drawLine(x, y, x + s, y + s);
        g.drawLine(x + s, y, x, y + s);
        g.setStroke(new java.awt.BasicStroke(Math.max(2f, s / 8f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xcc2222));
        g.drawLine(x, y, x + s, y + s);
        g.drawLine(x + s, y, x, y + s);
    }

    /** Grey circle with a slash: this drop does nothing. */
    private static void paintNoDropSign(java.awt.Graphics2D g, int x, int y, int s) {
        g.setStroke(new java.awt.BasicStroke(Math.max(4f, s / 4f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.drawOval(x, y, s, s);
        g.drawLine(x + s / 5, y + s / 5, x + s - s / 5, y + s - s / 5);
        g.setStroke(new java.awt.BasicStroke(Math.max(2f, s / 8f), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x666666));
        g.drawOval(x, y, s, s);
        g.drawLine(x + s / 5, y + s / 5, x + s - s / 5, y + s - s / 5);
    }

    private static Cursor createNoDropCursor() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return Cursor.getDefaultCursor();
        }
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension size = toolkit.getBestCursorSize(24, 24);
            if (size.width <= 0 || size.height <= 0) {
                return Cursor.getDefaultCursor();
            }
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size.width, size.height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                int s = Math.min(size.width, size.height);
                paintNoDropSign(g, 3, 3, s - 7);
            } finally {
                g.dispose();
            }
            return toolkit.createCustomCursor(image, new Point(size.width / 2, size.height / 2),
                    "actionpanel-nodrop");
        } catch (RuntimeException e) {
            return Cursor.getDefaultCursor();
        }
    }

    /**
     * Four-direction move arrows, white-backed like the delete cross. The
     * predefined MOVE_CURSOR renders as the plain arrow on macOS, so a drag
     * needs an own glyph to be visible at all. With copy, a plus badge sits
     * in the lower right corner.
     */
    private static Cursor createMoveCursor(boolean copy) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return Cursor.getDefaultCursor();
        }
        try {
            return paintMoveCursor(copy);
        } catch (RuntimeException e) {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
    }

    private static Cursor paintMoveCursor(boolean copy) {
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
            if (copy) {
                // Badge on a white disc in the corner, clear of the arrowheads
                // and inside the image whatever the cursor size.
                int r = Math.max(3, s / 8);
                int cx = s - r - 3;
                g.setColor(Color.WHITE);
                g.fillOval(cx - r - 2, cx - r - 2, 2 * r + 4, 2 * r + 4);
                paintPlusBadge(g, cx, cx, r, new Color(0x1a7f37));
            }
        } finally {
            g.dispose();
        }
        return toolkit.createCustomCursor(image, new Point(size.width / 2, size.height / 2),
                copy ? "actionpanel-copy" : "actionpanel-move");
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
