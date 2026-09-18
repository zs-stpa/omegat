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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a panel button does when clicked. Each variant carries a stable tag
 * used by the XML persistence; unknown tags survive load/save untouched so
 * configurations from newer versions are not destroyed.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public sealed interface ActionSpec {

    /** Stable persistence tag of this variant. */
    String type();

    /** Attributes to persist besides the tag. Keys are XML attribute names. */
    Map<String, String> attributes();

    /** A main-menu action, addressed by its action command (= field name). */
    record MenuActionSpec(String actionCommand) implements ActionSpec {
        @Override
        public String type() {
            return "menu";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("command", actionCommand);
        }
    }

    /**
     * An editor or autocompleter action, addressed by its shortcut catalog
     * key. Invoked through the keystroke currently mapped to the key; an
     * unmapped key makes the action unavailable.
     */
    record EditorKeyActionSpec(String shortcutKey) implements ActionSpec {
        @Override
        public String type() {
            return "editorkey";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("key", shortcutKey);
        }
    }

    /** A script file, relative to the scripts folder. */
    record ScriptActionSpec(String fileName) implements ActionSpec {
        @Override
        public String type() {
            return "script";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("file", fileName);
        }
    }

    /**
     * A preset search: the query plus a snapshot of every search window
     * option (all SEARCHWINDOW_* preferences at capture time). Applying the
     * action restores the snapshot, opens the Search window and runs the
     * search — so every present and future option travels with the preset.
     */
    record SearchActionSpec(String query, boolean replace, Map<String, String> options)
            implements ActionSpec {
        @Override
        public String type() {
            return "search";
        }

        @Override
        public Map<String, String> attributes() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("query", query);
            m.put("replace", Boolean.toString(replace));
            m.putAll(options);
            return m;
        }
    }

    /** A literal text snippet inserted into the editor. */
    record SnippetActionSpec(String text) implements ActionSpec {
        @Override
        public String type() {
            return "snippet";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("text", text);
        }
    }

    /** A URL opened in the system browser. */
    record UrlActionSpec(String url) implements ActionSpec {
        @Override
        public String type() {
            return "url";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("url", url);
        }
    }

    /**
     * A live reference to an autotext entry by its source shortcut; follows
     * later edits of the entry and becomes unavailable when it is deleted.
     */
    record AutotextRefActionSpec(String source) implements ActionSpec {
        @Override
        public String type() {
            return "autotextref";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("source", source);
        }
    }

    /**
     * Applies a saved colour scheme. The reference is either a bare scheme
     * name resolved in the colour scheme folder, or a file path.
     */
    record ColorSchemeActionSpec(String ref) implements ActionSpec {
        @Override
        public String type() {
            return "colorscheme";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("ref", ref);
        }
    }

    /**
     * Applies a saved shortcut set. The reference is either a bare set name
     * resolved in the shortcut set folder, or a file path.
     */
    record ShortcutSetActionSpec(String ref) implements ActionSpec {
        @Override
        public String type() {
            return "shortcutset";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("ref", ref);
        }
    }

    /**
     * A typed application preference rendered as a control instead of a
     * button: kind "toggle" switches a boolean (the single listed value is
     * the coded default), kind "slider" edits an integer preference between
     * min and max, kind "combobox" chooses one of the listed values.
     */
    record PreferenceActionSpec(String key, String kind, int min, int max, java.util.List<String> values)
            implements ActionSpec {
        @Override
        public String type() {
            return "preference";
        }

        @Override
        public Map<String, String> attributes() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("kind", kind);
            m.put("min", Integer.toString(min));
            m.put("max", Integer.toString(max));
            m.put("values", String.join(",", values));
            return m;
        }
    }

    /**
     * A boolean project setting rendered as a toggle; switching it saves and
     * reloads the project after the standard confirmation.
     */
    record ProjectFlagActionSpec(String property) implements ActionSpec {
        @Override
        public String type() {
            return "projectflag";
        }

        @Override
        public Map<String, String> attributes() {
            return Map.of("property", property);
        }
    }

    /**
     * An action of a type this version does not know. Kept verbatim so that
     * saving does not destroy it; rendered as a disabled button.
     */
    record UnknownActionSpec(String type, Map<String, String> attributes) implements ActionSpec {
    }

    /**
     * Recreate a spec from its persisted form. Unknown tags come back as
     * {@link UnknownActionSpec}.
     */
    static ActionSpec of(String type, Map<String, String> attrs) {
        switch (type) {
        case "menu":
            return new MenuActionSpec(attrs.getOrDefault("command", ""));
        case "editorkey":
            return new EditorKeyActionSpec(attrs.getOrDefault("key", ""));
        case "script":
            return new ScriptActionSpec(attrs.getOrDefault("file", ""));
        case "search":
            Map<String, String> options = new LinkedHashMap<>(attrs);
            String query = options.remove("query");
            boolean replace = Boolean.parseBoolean(options.remove("replace"));
            return new SearchActionSpec(query == null ? "" : query, replace, options);
        case "snippet":
            return new SnippetActionSpec(attrs.getOrDefault("text", ""));
        case "url":
            return new UrlActionSpec(attrs.getOrDefault("url", ""));
        case "autotextref":
            return new AutotextRefActionSpec(attrs.getOrDefault("source", ""));
        case "colorscheme":
            return new ColorSchemeActionSpec(attrs.getOrDefault("ref", ""));
        case "shortcutset":
            return new ShortcutSetActionSpec(attrs.getOrDefault("ref", ""));
        case "projectflag":
            return new ProjectFlagActionSpec(attrs.getOrDefault("property", ""));
        case "preference":
            String values = attrs.getOrDefault("values", "");
            return new PreferenceActionSpec(attrs.getOrDefault("key", ""),
                    attrs.getOrDefault("kind", "slider"),
                    Integer.parseInt(attrs.getOrDefault("min", "0")),
                    Integer.parseInt(attrs.getOrDefault("max", "100")),
                    values.isEmpty() ? java.util.List.of() : java.util.List.of(values.split(",")));
        default:
            return new UnknownActionSpec(type, Map.copyOf(attrs));
        }
    }
}
