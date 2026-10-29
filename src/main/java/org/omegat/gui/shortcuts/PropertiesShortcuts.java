/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2015 Alex Buloichik, Yu Tang
               2017 Aaron Madlon-Kay
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
package org.omegat.gui.shortcuts;

import java.awt.Component;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.InputMap;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import org.omegat.util.Platform;
import org.omegat.util.StaticUtils;
import org.omegat.util.StringUtil;

/**
 * The <code>PropertiesShortcuts</code> class represents a persistent set of shortcut.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Yu Tang
 * @author Aaron Madlon-Kay
 */
public class PropertiesShortcuts {

    private static final Logger LOGGER = Logger.getLogger(PropertiesShortcuts.class.getName());

    private static final String BUNDLED_ROOT = "/org/omegat/gui/main/";
    private static final String MAIN_MENU_SHORTCUTS_FILE = "MainMenuShortcuts.properties";
    private static final String EDITOR_SHORTCUTS_FILE = "EditorShortcuts.properties";

    private static class LoadedShortcuts {
        static final PropertiesShortcuts MAIN_MENU_SHORTCUTS = loadBundled(BUNDLED_ROOT, MAIN_MENU_SHORTCUTS_FILE);
        static final PropertiesShortcuts EDITOR_SHORTCUTS = loadBundled(BUNDLED_ROOT, EDITOR_SHORTCUTS_FILE);
    }

    public static PropertiesShortcuts getMainMenuShortcuts() {
        return LoadedShortcuts.MAIN_MENU_SHORTCUTS;
    }

    public static PropertiesShortcuts getEditorShortcuts() {
        return LoadedShortcuts.EDITOR_SHORTCUTS;
    }

    /** Bundled default values, from the classpath. */
    private final Map<String, String> defaults = new HashMap<>();
    /**
     * Per-key user overrides, from the file in the config dir and from
     * {@link #setShortcut}. An entry equal to its default is never held here.
     */
    private final Map<String, String> userOverrides = new HashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    /** Defaults modules added to this set, re-applied on reload. */
    private final List<Contribution> contributions = new CopyOnWriteArrayList<>();
    /** Base name of the user file in the config dir; null for ad-hoc sets. */
    private @Nullable String userFileName;
    /** Classpath location of the bundled defaults; null for ad-hoc sets. */
    private @Nullable String classpathPath;

    /**
     * Creates shortcut list with the specified defaults and user shortcuts.
     * Look for specified file in these places in this order:
     * <ol>
     * <li>Stream in classpath
     * <li>File of same name in the user's config dir
     * </ol>
     * For each shortcut, user shortcuts have priority, then defaults (for
     * Mac-specific or others).
     *
     * @param classpathRoot
     *            the path to the file on the classpath. Should include a
     *            trailing slash.
     * @param filename
     *            name of file to load
     */
    static PropertiesShortcuts loadBundled(String classpathRoot, String filename) {
        PropertiesShortcuts result = new PropertiesShortcuts();
        result.userFileName = filename;
        result.classpathPath = classpathRoot + filename;
        try {
            result.loadFromClasspath(classpathRoot + filename);
            result.loadFromFile(new File(StaticUtils.getConfigDir(), filename));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load shortcuts properties file", ex);
        }
        return result;
    }

    public void loadFromClasspath(String propertiesFile) throws IOException {
        loadFromClasspath(propertiesFile, getClass().getClassLoader(), defaults);
    }

    /** Platform-aware load through a class loader into a target map; false when neither variant exists. */
    private boolean loadFromClasspath(String propertiesFile, ClassLoader loader, Map<String, String> target)
            throws IOException {
        boolean loaded = false;
        if (Platform.isMacOSX()) {
            loaded = loadFromClasspathImpl(getMacProperties(propertiesFile), loader, target);
        }
        if (!loaded) {
            loaded = loadFromClasspathImpl(propertiesFile, loader, target);
        }
        return loaded;
    }

    /**
     * A module's bundled defaults: their classpath file, the keys it holds,
     * the scope shown for them on the preferences page, and their labels.
     */
    private record Contribution(String classpathPath, ClassLoader loader, Set<String> keys, String scopeName,
            Function<String, @Nullable String> labels) {
    }

    /**
     * Add a module's shortcut defaults to this set: a bundled properties file
     * read through the module's class loader (plugin jars are not on the
     * application's; a .mac variant is preferred on macOS, like the set's
     * own), the scope name the preferences page shows for its keys (they
     * bind window-wide, so they clash with menu accelerators), and a label
     * function for the keys. User overrides for these keys are honoured, the
     * contribution survives {@link #reload()}, and contributing the same
     * file again replaces the earlier contribution.
     *
     * @throws java.io.FileNotFoundException
     *             when the loader finds no such file
     */
    public void contribute(String classpathPath, ClassLoader loader, String scopeName,
            Function<String, @Nullable String> labels) throws IOException {
        Map<String, String> loaded = new HashMap<>();
        if (!loadFromClasspath(classpathPath, loader, loaded)) {
            throw new java.io.FileNotFoundException(classpathPath);
        }
        uncontribute(classpathPath);
        contributions.add(new Contribution(classpathPath, loader, Set.copyOf(loaded.keySet()), scopeName, labels));
        defaults.putAll(loaded);
        pruneDefaultOverrides();
    }

    /** Withdraw a contribution: its keys leave the defaults, user overrides stay. */
    public void uncontribute(String classpathPath) {
        for (Contribution contribution : contributions) {
            if (contribution.classpathPath().equals(classpathPath)) {
                contributions.remove(contribution);
                contribution.keys().forEach(defaults::remove);
            }
        }
    }

    /** Label a contributor gave the key; null for the set's own keys. */
    public @Nullable String contributedLabel(String key) {
        Contribution contribution = contributionOf(key);
        return contribution == null ? null : contribution.labels().apply(key);
    }

    /** Scope name of the contribution holding the key; null for the set's own keys. */
    public @Nullable String contributedScope(String key) {
        Contribution contribution = contributionOf(key);
        return contribution == null ? null : contribution.scopeName();
    }

    private @Nullable Contribution contributionOf(String key) {
        for (Contribution contribution : contributions) {
            if (contribution.keys().contains(key)) {
                return contribution;
            }
        }
        return null;
    }

    /**
     * The properties-file value for a keystroke, parseable by
     * {@link KeyStroke#getKeyStroke(String)}; the empty string (= explicitly
     * unbound) for null.
     */
    public static String toPropertyValue(@Nullable KeyStroke ks) {
        return ks == null ? "" : ks.toString();
    }

    /** Keys of all known shortcutable functions, defaults and overrides. */
    public Set<String> getKeys() {
        Set<String> keys = new TreeSet<>(defaults.keySet());
        keys.addAll(userOverrides.keySet());
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Raw current value of the key ("" = explicitly unbound), or null when
     * the key is unknown.
     */
    public @Nullable String getShortcutValue(String key) {
        String override = userOverrides.get(key);
        return override != null ? override : defaults.get(key);
    }

    /** Raw bundled default of the key, or null when the key is unknown. */
    public @Nullable String getDefaultValue(String key) {
        return defaults.get(key);
    }

    /** Whether the key currently differs from its bundled default. */
    public boolean isModified(String key) {
        return userOverrides.containsKey(key);
    }

    /**
     * Sets the shortcut of the key for this session; null unbinds it
     * explicitly. A value equal to the bundled default removes the override
     * instead. Takes effect in consumers on the next (re)bind; persistent
     * only after {@link #save()}.
     */
    public void setShortcut(String key, @Nullable KeyStroke ks) {
        // Equality of the keystrokes, not of the raw strings: the canonical
        // format ("ctrl pressed D") differs from the hand-written file
        // syntax ("ctrl D") for the same keystroke.
        if (defaults.containsKey(key) && Objects.equals(ks, parse(defaults.get(key)))) {
            userOverrides.remove(key);
        } else {
            userOverrides.put(key, toPropertyValue(ks));
        }
    }

    private static @Nullable KeyStroke parse(@Nullable String value) {
        return value == null || value.isEmpty() ? null : KeyStroke.getKeyStroke(value);
    }

    /** Restores the bundled default of the key for this session. */
    public void clearUserOverride(String key) {
        userOverrides.remove(key);
    }

    /**
     * Writes the current overrides to the user file in the config dir (on
     * macOS the .mac.properties variant, matching the load preference) and
     * notifies the change listeners. Keys at their bundled default are not
     * written, so later default changes reach the user.
     */
    public void save() throws IOException {
        String name = userFileName;
        if (name == null) {
            throw new IllegalStateException("This shortcut set is not backed by a user file");
        }
        if (Platform.isMacOSX()) {
            name = getMacProperties(name);
        }
        File file = new File(StaticUtils.getConfigDir(), name);
        try (BufferedWriter out = Files.newBufferedWriter(file.toPath(), StandardCharsets.ISO_8859_1)) {
            out.write("# Shortcut overrides written by the OmegaT preferences dialog.");
            out.newLine();
            out.write("# Keys absent here follow the application defaults; comments are not preserved.");
            out.newLine();
            for (Map.Entry<String, String> entry : new TreeMap<>(userOverrides).entrySet()) {
                out.write(escapeKey(entry.getKey()) + "=" + entry.getValue());
                out.newLine();
            }
        }
        // Consumers rebind Swing components, so they are notified on the EDT
        // regardless of the calling thread (the preferences dialog saves
        // from a worker).
        SwingUtilities.invokeLater(() -> changeListeners.forEach(Runnable::run));
    }

    /** Escapes the properties-format metacharacters of a key. */
    private static String escapeKey(String key) {
        return key.replace("\\", "\\\\").replace(" ", "\\ ").replace("=", "\\=")
                .replace(":", "\\:").replace("#", "\\#").replace("!", "\\!");
    }

    /**
     * Discards unsaved session changes by reloading the bundled defaults and
     * the user file.
     */
    public void reload() throws IOException {
        String name = userFileName;
        String classpath = classpathPath;
        if (name == null || classpath == null) {
            throw new IllegalStateException("This shortcut set is not backed by a user file");
        }
        defaults.clear();
        userOverrides.clear();
        loadFromClasspath(classpath);
        for (Contribution contribution : contributions) {
            loadFromClasspath(contribution.classpathPath(), contribution.loader(), defaults);
        }
        loadFromFile(new File(StaticUtils.getConfigDir(), name));
    }

    /** Registers a listener notified after {@link #save()}, on the EDT. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private String getMacProperties(String properties) {
        return properties.replaceAll("\\.properties$", ".mac.properties");
    }

    /**
     * Load the properties file from the classpath
     *
     * @param path
     * @return whether the file was loaded (<code>false</code> if not present,
     *         etc.)
     * @throws IOException
     */
    private static boolean loadFromClasspathImpl(String path, ClassLoader loader, Map<String, String> target)
            throws IOException {
        // Class loaders take resource names without the leading slash.
        String name = path.startsWith("/") ? path.substring(1) : path;
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in != null) {
                loadProperties(in, target);
                return true;
            }
        }
        return false;
    }

    public void loadFromFile(File file) throws IOException {
        boolean loaded = false;
        if (Platform.isMacOSX()) {
            File macSpecific = new File(getMacProperties(file.getPath()));
            if (macSpecific.isFile()) {
                loadFromFileImpl(macSpecific);
                loaded = true;
            }
        }
        if (!loaded && file.isFile()) {
            loadFromFileImpl(file);
        }
    }

    private void loadFromFileImpl(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            loadProperties(fis, userOverrides);
        }
        pruneDefaultOverrides();
    }

    /**
     * Entries at their default are not overrides; without this, a saved file
     * from an older default set would freeze those keys forever. Compared as
     * keystrokes, so a differently spelled equal value does not count as an
     * override either.
     */
    private void pruneDefaultOverrides() {
        userOverrides.entrySet().removeIf(e -> defaults.containsKey(e.getKey())
                && Objects.equals(parse(e.getValue()), parse(defaults.get(e.getKey()))));
    }

    private static void loadProperties(InputStream in, Map<String, String> target) throws IOException {
        Properties props = new Properties();
        props.load(in);
        props.forEach((k, v) -> target.put(k.toString(), v.toString()));
    }

    public KeyStroke getKeyStroke(String key) {
        String shortcut = getShortcutValue(key);
        if (shortcut == null) {
            throw new IllegalArgumentException("Keyboard shortcut not defined. Key=" + key);
        }
        KeyStroke result = KeyStroke.getKeyStroke(shortcut);
        if (!shortcut.isEmpty() && result == null) {
            LOGGER.warning("Keyboard shortcut is invalid: " + key + "=" + shortcut);
        }
        return result;
    }

    public void bindKeyStrokes(JMenuBar menu) {
        applyTo(menu.getComponents());
    }

    /**
     * Travel by all submenus for setup shortcuts.
     *
     * @param menu
     *            menu or menu item
     */
    private void applyTo(final Component[] items) {
        for (Component c : items) {
            if (c instanceof JMenuItem) {
                bindKeyStrokes((JMenuItem) c);
            }
        }
    }

    public void bindKeyStrokes(final JMenuItem item) {
        if (item instanceof JMenu) {
            // setAccelerator() is not defined for JMenu.
            applyTo(((JMenu) item).getMenuComponents());
        } else {
            String shortcut = item.getActionCommand();
            if (!StringUtil.isEmpty(shortcut)) {
                try {
                item.setAccelerator(getKeyStroke(shortcut));
                } catch (Exception ex) {
                    // Eat exception silently
                }
            }
        }
    }

    /**
     * Bind each key's current keystroke in the input map. Earlier keystrokes
     * of the key are removed first, so a rebind never leaves the old one
     * active beside the new.
     */
    public void bindKeyStrokes(InputMap inputMap, String... keys) {
        for (String key : keys) {
            try {
                removeEntries(inputMap, key);
                KeyStroke keyStroke = getKeyStroke(key);
                if (keyStroke != null) {
                    inputMap.put(keyStroke, key);
                }
            } catch (Exception ex) {
                // Eat exception silently
            }
        }
    }

    private static void removeEntries(InputMap inputMap, String keyToBeRemoved) {
        KeyStroke[] strokes = inputMap.keys();
        if (strokes == null) {
            return;
        }
        for (KeyStroke ks : strokes) {
            if (keyToBeRemoved.equals(inputMap.get(ks))) {
                inputMap.remove(ks);
            }
        }
    }

    public boolean isEmpty() {
        return defaults.isEmpty() && userOverrides.isEmpty();
    }

    /**
     * For testing purposes
     *
     * @return Unmodifiable merged view of the data held by this instance
     */
    Map<String, String> getData() {
        Map<String, String> merged = new HashMap<>(defaults);
        merged.putAll(userOverrides);
        return Collections.unmodifiableMap(merged);
    }
}
