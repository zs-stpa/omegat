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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.matching.NearString;
import org.omegat.core.spellchecker.ISpellChecker;
import org.omegat.gui.actionpanel.ActionSpec.PreferenceActionSpec;
import org.omegat.gui.editor.ModificationInfoManager;
import org.omegat.gui.editor.SegmentBuilder;
import org.omegat.gui.main.ProjectUICommands;
import org.omegat.util.OConsts;
import org.omegat.util.OStrings;
import org.omegat.util.PatternConsts;
import org.omegat.util.Preferences;
import org.omegat.util.gui.FontUtil;

/**
 * Application preferences that can live on the panel as a control, arranged
 * like the pages of the preferences dialog and labelled with the very
 * strings of that dialog. OmegaT preferences are untyped strings, so each
 * entry declares kind, range and coded default here; the coded defaults
 * are the ones the dialog controllers read with. Toggles and sliders carry
 * their default as the single declared value.
 *
 * Kinds: "toggle" for booleans, "slider" for integers between min and max,
 * "combobox" for one of the declared values.
 *
 * Left out on purpose: free-text preferences (templates, patterns, paths,
 * author name), coupled radio triples (printf checking), theme and menu
 * class names.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class PreferenceCatalog {

    public static final String KIND_TOGGLE = "toggle";
    public static final String KIND_SLIDER = "slider";
    public static final String KIND_COMBOBOX = "combobox";

    /** Saved-file folders that appear as pages in the preferences dialog. */
    public enum Folder {
        COLOR_SCHEMES, SHORTCUT_SETS
    }

    /** One assignable preference with its localized dialog label. */
    public record Entry(PreferenceActionSpec spec, String label) {
    }

    /** One preferences page: its entries, sub pages, and an optional folder. */
    public record Group(@Nullable String titleKey, String title, List<Entry> entries, List<Group> children,
            @Nullable Folder folder) {
    }

    /** Locale-free definition; labels are resolved when a tree is requested. */
    private record Def(PreferenceActionSpec spec, List<String> labelKeys) {
        String label() {
            StringBuilder sb = new StringBuilder();
            for (String key : labelKeys) {
                if (sb.length() > 0) {
                    sb.append(": ");
                }
                sb.append(text(key));
            }
            return sb.toString();
        }
    }

    private record GroupDef(@Nullable String titleKey, List<Def> defs, List<GroupDef> children,
            @Nullable Folder folder) {
    }

    private static final List<GroupDef> PAGES = definePages();
    private static final Map<String, Def> BY_KEY = new LinkedHashMap<>();

    // A duplicate key here is a programming error; the module tests build
    // the tree first, so it never reaches a user as a broken panel.
    static {
        index(PAGES);
    }

    private PreferenceCatalog() {
    }

    /** The page tree in dialog order, labels in the current UI language. */
    public static List<Group> groups() {
        List<Group> groups = new ArrayList<>(PAGES.size());
        for (GroupDef page : PAGES) {
            groups.add(localize(page));
        }
        return groups;
    }

    /**
     * The catalog's current definition of a stored row: kind, range, values
     * and default follow this version of the catalog, so a row saved by an
     * older version renders like a freshly assigned one. Only a key this
     * version does not know keeps its stored definition.
     */
    public static PreferenceActionSpec resolve(PreferenceActionSpec stored) {
        Def def = BY_KEY.get(stored.key());
        return def == null ? stored : def.spec();
    }

    /** Whether this version offers the key on the panel. */
    public static boolean isCatalogued(String key) {
        return BY_KEY.containsKey(key);
    }

    /** Whether a kind is one this version can render. */
    public static boolean isKnownKind(String kind) {
        return KIND_TOGGLE.equals(kind) || KIND_SLIDER.equals(kind) || KIND_COMBOBOX.equals(kind);
    }

    /** Dialog label of a catalogued preference key; the key itself otherwise. */
    public static String label(String key) {
        Def def = BY_KEY.get(key);
        return def == null ? key : labelOf(def);
    }

    /** Display text of a combobox value; the raw value when none is known. */
    public static String valueLabel(String key, String value) {
        if (Preferences.EXT_TMX_SORT_KEY.equals(key)) {
            return text("EXT_TMX_SORT_KEY_" + value);
        }
        if (Preferences.THEME_COLOR_MODE.equals(key)) {
            switch (value) {
            case "dark":
                return text("MW_OPTIONMENU_APPEARANCE_DARK_THEME_LABEL");
            case "sync":
                return text("MW_OPTIONMENU_APPEARANCE_SYNC_WITH_OS_COLOR");
            default:
                return text("MW_OPTIONMENU_APPEARANCE_LIGHT_THEME_LABEL");
            }
        }
        return value;
    }

    /**
     * Current value of a toggle. The coded default comes from the catalog,
     * so rows saved earlier follow later corrections; the value stored in the
     * row only serves keys this version no longer knows.
     */
    public static boolean isSelected(PreferenceActionSpec spec) {
        Def def = BY_KEY.get(spec.key());
        List<String> values = def != null && KIND_TOGGLE.equals(def.spec().kind()) ? def.spec().values()
                : spec.values();
        boolean fallback = !values.isEmpty() && Boolean.parseBoolean(values.get(0));
        return Preferences.isPreferenceDefault(spec.key(), fallback);
    }

    /** Current value of a slider, honouring the declared coded default. */
    public static int intValue(PreferenceActionSpec spec) {
        Def def = BY_KEY.get(spec.key());
        List<String> values = def != null && KIND_SLIDER.equals(def.spec().kind()) ? def.spec().values()
                : spec.values();
        int fallback = spec.min();
        if (!values.isEmpty()) {
            try {
                fallback = Integer.parseInt(values.get(0));
            } catch (NumberFormatException e) {
                // Foreign or hand-edited row: the range minimum has to do.
            }
        }
        return Preferences.getPreferenceDefault(spec.key(), fallback);
    }

    /** Coded default of a catalogued toggle, for tests and callers. */
    static @Nullable Boolean codedDefault(String key) {
        Def def = BY_KEY.get(key);
        if (def == null || !KIND_TOGGLE.equals(def.spec().kind())) {
            return null;
        }
        return Boolean.parseBoolean(def.spec().values().get(0));
    }

    /** Coded default of a catalogued slider, for tests and callers. */
    static @Nullable Integer codedIntDefault(String key) {
        Def def = BY_KEY.get(key);
        if (def == null || !KIND_SLIDER.equals(def.spec().kind())) {
            return null;
        }
        return Integer.parseInt(def.spec().values().get(0));
    }

    /**
     * What the preferences dialog does after saving a page, so a change made
     * on the panel takes effect the same way. Each case names the persist
     * method it mirrors; when that method grows a new call, this one must
     * follow (nothing checks it automatically). Runs on the EDT; safe without
     * a project.
     */
    public static void applySideEffects(String key) {
        switch (key) {
        case Preferences.USE_TAB_TO_ADVANCE:
            // GeneralOptionsController.persist
            Core.getEditor().getSettings().setUseTabForAdvance(Preferences.isPreference(key));
            break;
        case Preferences.TF_SRC_FONT_SIZE:
        case Preferences.DICTIONARY_USE_FONT:
            // FontSelectionController.persist: with the shared font on, the
            // dictionary size follows.
            if (Preferences.isPreferenceDefault(Preferences.DICTIONARY_USE_FONT, true)) {
                Preferences.setPreference(Preferences.TF_DICTIONARY_FONT_SIZE,
                        Preferences.getPreferenceDefault(Preferences.TF_SRC_FONT_SIZE, 12));
            }
            CoreEvents.fireFontChanged(FontUtil.getScaledFont());
            break;
        case Preferences.TF_SRC_FONT_NAME:
        case Preferences.TF_DICTIONARY_FONT_SIZE:
        case Preferences.PROJECT_FILES_USE_FONT:
            CoreEvents.fireFontChanged(FontUtil.getScaledFont());
            break;
        case Preferences.THEME_COLOR_MODE:
            // AppearanceController.setRestartRequired
            Core.getMainWindow().showTimedStatusMessageRB("PREFERENCES_WARNING_NEEDS_RESTART");
            break;
        case Preferences.VIEW_OPTION_SOURCE_ALL_BOLD:
        case Preferences.VIEW_OPTION_SOURCE_ACTIVE_BOLD:
        case Preferences.VIEW_OPTION_UNIQUE_FIRST:
        case Preferences.VIEW_OPTION_PPT_SIMPLIFY:
        case Preferences.VIEW_OPTION_TEMPLATE_ACTIVE:
            // ViewOptionsController.persist
            ModificationInfoManager.reset();
            Core.getEditor().getSettings().updateViewPreferences();
            break;
        case Preferences.ALLOW_AUTO_SPELLCHECKING:
            // SpellcheckerConfigurationController.persist
            boolean spell = Preferences.isPreferenceDefault(key, true);
            if (spell && Core.getProject().isProjectLoaded()) {
                ISpellChecker checker = Core.getSpellChecker();
                checker.destroy();
                checker.initialize();
            }
            Core.getEditor().getSettings().setAutoSpellChecking(spell);
            break;
        case Preferences.CHECK_JAVA_PATTERN_TAGS:
            // TagProcessingOptionsController.persist
            PatternConsts.updatePlaceholderPattern();
            Core.getEditor().getSettings().updateTagValidationPreferences();
            break;
        case Preferences.ALLOW_TAG_EDITING:
        case Preferences.TAG_VALIDATE_ON_LEAVE:
        case Preferences.LOOSE_TAG_ORDERING:
        case Preferences.TAGS_VALID_REQUIRED:
            // TagProcessingOptionsController.persist, EditingBehaviorController.persist
            Core.getEditor().getSettings().updateTagValidationPreferences();
            break;
        case Preferences.AC_SHOW_SUGGESTIONS_AUTOMATICALLY:
        case Preferences.AC_SWITCH_VIEWS_WITH_LR:
            // AutoCompleterController.persist
            Core.getEditor().getAutoCompleter().resetKeys();
            break;
        case Preferences.EXT_TMX_KEEP_FOREIGN_MATCH:
            // TMMatchesPreferencesController.persist: setReloadRequired
            if (Core.getProject().isProjectLoaded()) {
                ProjectUICommands.promptReload();
            }
            break;
        default:
            break;
        }
    }

    // ---- definition -------------------------------------------------------

    private static List<GroupDef> definePages() {
        List<GroupDef> root = new ArrayList<>();
        root.add(page("PREFS_TITLE_GENERAL",
                List.of(toggle(Preferences.ALWAYS_CONFIRM_QUIT, true, "MW_OPTIONSMENU_ALWAYS_CONFIRM_QUIT"),
                        toggle(Preferences.USE_TAB_TO_ADVANCE, false, "TF_MENU_DISPLAY_ADVANCE")),
                List.of(page("PREFS_TITLE_SOURCE_FILES", List.of(toggle(Preferences.PROJECT_FILES_SHOW_PROGRESS,
                        Preferences.PROJECT_FILES_SHOW_PROGRESS_DEFAULT, "PREFS_SHOW_PROJECT_FILES_PROGRESS"))))));
        root.add(page("PREFS_TITLE_MACHINE_TRANSLATION",
                List.of(toggle(Preferences.MT_AUTO_FETCH, false, "PREFS_MT_AUTO_FETCH"),
                        toggle(Preferences.MT_ONLY_UNTRANSLATED, false, "PREFS_MT_ONLY_UNTRANSLATED"))));
        root.add(page("PREFS_TITLE_GLOSSARY", List.of(
                toggle(Preferences.GLOSSARY_TBX_DISPLAY_CONTEXT, Preferences.GLOSSARY_TBX_DISPLAY_CONTEXT_DEFAULT,
                        "PREFS_GLOSSARY_TBX_DISPLAY_CONTEXT"),
                toggle(Preferences.GLOSSARY_NOT_EXACT_MATCH, Preferences.GLOSSARY_NOT_EXACT_MATCH_DEFAULT,
                        "PREFS_GLOSSARY_EXACT_MATCH"),
                toggle(Preferences.GLOSSARY_STEMMING, Preferences.GLOSSARY_STEMMING_DEFAULT,
                        "PREFS_GLOSSARY_STEMMING"),
                toggle(Preferences.GLOSSARY_STEMMING_FULL, false, "PREFS_GLOSSARY_STEMMING_FULL"),
                toggle(Preferences.GLOSSARY_REPLACE_ON_INSERT, false, "PREFS_GLOSSARY_REPLACE_ON_INSERT"),
                toggle(Preferences.GLOSSARY_REQUIRE_SIMILAR_CASE, Preferences.GLOSSARY_REQUIRE_SIMILAR_CASE_DEFAULT,
                        "PREFS_GLOSSARY_REQUIRE_SIMILAR_CASE"),
                toggle(Preferences.GLOSSARY_MERGE_ALTERNATE_DEFINITIONS,
                        Preferences.GLOSSARY_MERGE_ALTERNATE_DEFINITIONS_DEFAULT,
                        "PREFS_GLOSSARY_MERGE_ALTERNATE_DEFINITIONS"),
                toggle(Preferences.GLOSSARY_SORT_BY_LENGTH, false, "PREFS_GLOSSARY_SORT_BY_LENGTH"),
                toggle(Preferences.GLOSSARY_SORT_BY_SRC_LENGTH, false, "PREFS_GLOSSARY_SORT_BY_SRC_LENGTH"))));
        root.add(page("PREFS_TITLE_DICTIONARY",
                List.of(toggle(Preferences.DICTIONARY_AUTO_SEARCH, false, "PREFS_DICTIONARY_AUTO_SEARCH"),
                        toggle(Preferences.DICTIONARY_CONDENSED_VIEW, false, "PREFS_DICTIONARY_CONDENSED"),
                        toggle(Preferences.DICTIONARY_FUZZY_MATCHING, false, "PREFS_DICTIONARY_FUZZY"))));
        root.add(page("PREFS_TITLE_APPEARANCE",
                List.of(combobox(Preferences.THEME_COLOR_MODE, List.of("default", "dark", "sync"),
                        "MW_OPTIONMENU_APPEARANCE_THEME_LABEL")),
                List.of(page("PREFS_TITLE_FONT", List.of(
                        slider(Preferences.TF_SRC_FONT_SIZE, 8, 32, Preferences.TF_FONT_SIZE_DEFAULT,
                                "PREFS_TITLE_FONT", "TF_SELECT_FONTSIZE"),
                        toggle(Preferences.PROJECT_FILES_USE_FONT, false, "TF_APPLY_TO_PROJECT_FILES"),
                        toggle(Preferences.DICTIONARY_USE_FONT, true, "TF_APPLY_TO_DICTIONARY"),
                        slider(Preferences.TF_DICTIONARY_FONT_SIZE, 8, 32, Preferences.TF_FONT_SIZE_DEFAULT,
                                "PREFS_TITLE_DICTIONARY", "TF_SELECT_FONTSIZE_DICTIONARY"))),
                        new GroupDef("PREFS_TITLE_COLORS", List.of(), List.of(), Folder.COLOR_SCHEMES))));
        root.add(page("PREFS_TITLE_AUTOCOMPLETER",
                List.of(toggle(Preferences.AC_SHOW_SUGGESTIONS_AUTOMATICALLY, false,
                        "PREFS_AUTOCOMPLETE_SHOW_AUTOMATICALLY"),
                        toggle(Preferences.AC_SWITCH_VIEWS_WITH_LR, false, "PREFS_AUTOCOMPLETE_SWITCH_VIEWS_LR")),
                List.of(page("PREFS_TITLE_AUTOCOMPLETER_GLOSSARY", List.of(
                        toggle(Preferences.AC_GLOSSARY_ENABLED, Preferences.AC_GLOSSARY_ENABLED_DEFAULT,
                                "AC_GLOSSARY_ENABLED"),
                        toggle(Preferences.AC_GLOSSARY_SHOW_SOURCE, false, "AC_OPTIONS_DISPLAY_SOURCE"),
                        toggle(Preferences.AC_GLOSSARY_SHOW_TARGET_BEFORE_SOURCE, false, "AC_OPTIONS_TARGET_FIRST"),
                        toggle(Preferences.AC_GLOSSARY_SORT_BY_LENGTH, false, "AC_OPTIONS_SORT_BY_LENGTH"),
                        toggle(Preferences.AC_GLOSSARY_SORT_ALPHABETICALLY, false,
                                "AC_OPTIONS_SORT_TARGET_ALPHABETICALLY"))),
                        page("PREFS_TITLE_AUTOCOMPLETER_AUTOTEXT", List.of(
                                toggle(Preferences.AC_AUTOTEXT_ENABLED, Preferences.AC_AUTOTEXT_ENABLED_DEFAULT,
                                        "AC_AUTOTEXT_ENABLED"),
                                toggle(Preferences.AC_AUTOTEXT_SORT_BY_LENGTH, false, "AC_AUTOTEXT_SORT_BY_LENGTH"),
                                toggle(Preferences.AC_AUTOTEXT_SORT_ALPHABETICALLY, false,
                                        "AC_AUTOTEXT_ALPHABETICALLY"),
                                toggle(Preferences.AC_AUTOTEXT_SORT_FULL_TEXT, false,
                                        "AC_AUTOTEXT_SORT_FULL_TEXT"))),
                        page("PREFS_TITLE_AUTOCOMPLETER_CHAR_TABLE", List.of(
                                toggle(Preferences.AC_CHARTABLE_ENABLED, Preferences.AC_CHARTABLE_ENABLED_DEFAULT,
                                        "AC_CHARTABLE_ENABLED"),
                                toggle(Preferences.AC_CHARTABLE_USE_CUSTOM_CHARS, false, "AC_CHARTABLE_CUSTOM"),
                                toggle(Preferences.AC_CHARTABLE_UNIQUE_CUSTOM_CHARS, false,
                                        "AC_CHARTABLE_CUSTOM_UNIQUE"))),
                        page("PREFS_TITLE_AUTOCOMPLETE_HISTORY", List.of(
                                toggle(Preferences.AC_HISTORY_COMPLETION_ENABLED, true,
                                        "PREFS_AUTOCOMPLETE_HISTORY_COMPLETION_ENABLED"),
                                toggle(Preferences.AC_HISTORY_PREDICTION_ENABLED, true,
                                        "PREFS_AUTOCOMPLETE_HISTORY_PREDICTION_ENABLED"))))));
        root.add(page("PREFS_TITLE_SPELLCHECKER", List.of(toggle(Preferences.ALLOW_AUTO_SPELLCHECKING, true,
                "GUI_SPELLCHECKER_AUTOSPELLCHECKCHECKBOX"))));
        root.add(page("PREFS_TITLE_EDITING_BEHAVIOR", List.of(
                toggle(Preferences.DONT_INSERT_SOURCE_TEXT, SegmentBuilder.DONT_INSERT_SOURCE_TEXT_DEFAULT,
                        "WF_OPTION_INSERT_NOTHTHING"),
                toggle(Preferences.BEST_MATCH_INSERT, true, "WF_OPTION_INSERT_FUZZY_MATCH"),
                slider(Preferences.BEST_MATCH_MINIMAL_SIMILARITY, 0, 100,
                        Preferences.BEST_MATCH_MINIMAL_SIMILARITY_DEFAULT, "GUI_WORKFLOW_OPTION_Minimal_Similarity"),
                toggle(Preferences.CONVERT_NUMBERS, true, "WF_OPTION_REPLACE_NUMBERS"),
                toggle(Preferences.ALLOW_TRANS_EQUAL_TO_SRC, true, "WF_OPTION_ALLOW_TRANS_EQ_TO_SRC"),
                toggle(Preferences.EXPORT_CURRENT_SEGMENT, false, "WF_OPTION_EXPORT__CURRENT_SEGMENT"),
                toggle(Preferences.STOP_ON_ALTERNATIVE_TRANSLATION, false, "WF_OPTION_GOTO_NEXT_UNTRANSLATED"),
                toggle(Preferences.ALLOW_TAG_EDITING, false, "WF_TAG_EDITING"),
                toggle(Preferences.TAG_VALIDATE_ON_LEAVE, false, "WG_TAG_VALIDATE_ON_LEAVE"),
                toggle(Preferences.SAVE_AUTO_STATUS, false, "WG_SAVE_AUTO_STATUS"),
                toggle(Preferences.SAVE_ORIGIN, false, "WG_SAVE_ORIGIN"),
                toggle(Preferences.SINGLE_CLICK_SEGMENT_ACTIVATION, false, "WF_OPTION_ALLOW_SELECT_SINGLE_CLICK"))));
        root.add(new GroupDef("PREFS_TITLE_SHORTCUTS", List.of(), List.of(), Folder.SHORTCUT_SETS));
        root.add(page("PREFS_TITLE_TAG_PROCESSING",
                List.of(toggle(Preferences.CHECK_JAVA_PATTERN_TAGS, false, "TV_OPTION_JAVA_PATTERN"),
                        toggle(Preferences.LOOSE_TAG_ORDERING, false, "TV_OPTION_LOOSE_TAG_ORDER"),
                        toggle(Preferences.TAGS_VALID_REQUIRED, false, "TV_OPTION_TAGS_VALID_REQUIRED"))));
        root.add(page("PREFS_TITLE_TM_MATCHES", List.of(
                combobox(Preferences.EXT_TMX_SORT_KEY, enumNames(NearString.SORT_KEY.values()), "EXT_TMX_SORT_KEY"),
                toggle(Preferences.EXT_TMX_SHOW_LEVEL2, false, "EXT_TMX_SHOW_LEVEL2"),
                toggle(Preferences.EXT_TMX_USE_SLASH, false, "EXT_TMX_USE_XML"),
                toggle(Preferences.EXT_TMX_KEEP_FOREIGN_MATCH, false, "EXT_TMX_KEEP_FOREIGN_MATCHES"),
                slider(Preferences.PENALTY_FOR_FOREIGN_MATCHES, 0, 100, Preferences.PENALTY_FOR_FOREIGN_MATCHES_DEFAULT,
                        "EXT_TMX_PENALTY_FOR_FOREIGN_MATCHES"),
                slider(Preferences.EXT_TMX_FUZZY_MATCH_THRESHOLD, 0, 100, OConsts.FUZZY_MATCH_THRESHOLD,
                        "EXT_TMX_FUZZY_THRESHOLD_KEY"),
                toggle(Preferences.PARAGRAPH_MATCH_FROM_SEGMENT_TMX, true, "PARAGRAPH_MATCH_FROM_SEGMENT_TMX"))));
        root.add(page("PREFS_TITLE_VIEW_OPTIONS", List.of(
                toggle(Preferences.VIEW_OPTION_SOURCE_ALL_BOLD, Preferences.VIEW_OPTION_SOURCE_ALL_BOLD_DEFAULT,
                        "VIEW_OPTION_SOURCE"),
                toggle(Preferences.VIEW_OPTION_SOURCE_ACTIVE_BOLD, Preferences.VIEW_OPTION_SOURCE_ACTIVE_BOLD_DEFAULT,
                        "VIEW_OPTION_ACTIVE_SOURCE"),
                toggle(Preferences.VIEW_OPTION_UNIQUE_FIRST, false, "VIEW_OPTION_UNIQUE"),
                toggle(Preferences.VIEW_OPTION_PPT_SIMPLIFY, Preferences.VIEW_OPTION_PPT_SIMPLIFY_DEFAULT,
                        "VIEW_OPTION_PPT_SIMPLIFY"),
                // Core declares VIEW_OPTION_TEMPLATE_ACTIVE_DEFAULT = true, but
                // both the dialog and SegmentBuilder read the key without it;
                // the panel follows the behaviour, not the unused constant.
                toggle(Preferences.VIEW_OPTION_TEMPLATE_ACTIVE, false, "MOD_INFO_TEMPLATE_ACTIVATOR"))));
        root.add(page("PREFS_TITLE_SAVING_AND_OUTPUT", List.of(
                // Seconds, as stored; the dialog's minute+second spinners
                // have no slider equivalent. 10 s is the dialog's minimum.
                new Def(new PreferenceActionSpec(Preferences.AUTO_SAVE_INTERVAL, KIND_SLIDER, 10, 600,
                        List.of(Integer.toString(Preferences.AUTO_SAVE_DEFAULT))), List.of()),
                toggle(Preferences.ALLOW_PROJECT_EXTERN_CMD, false, "ALLOW_PROJECT_EXTERN_CMD"))));
        root.add(page("PREFS_TITLE_VERSION_CHECK", List.of(toggle(Preferences.VERSION_CHECK_AUTOMATIC,
                Preferences.VERSION_CHECK_AUTOMATIC_DEFAULT, "PREFS_VERSION_CHECK_AUTO_CHECK"))));
        return root;
    }

    private static void index(List<GroupDef> pages) {
        for (GroupDef page : pages) {
            for (Def def : page.defs()) {
                if (BY_KEY.put(def.spec().key(), def) != null) {
                    throw new IllegalStateException("duplicate preference " + def.spec().key());
                }
            }
            index(page.children());
        }
    }

    private static Group localize(GroupDef page) {
        List<Entry> entries = new ArrayList<>(page.defs().size());
        for (Def def : page.defs()) {
            entries.add(new Entry(def.spec(), labelOf(def)));
        }
        List<Group> children = new ArrayList<>(page.children().size());
        for (GroupDef child : page.children()) {
            children.add(localize(child));
        }
        return new Group(page.titleKey(), page.titleKey() == null ? "" : text(page.titleKey()), entries, children,
                page.folder());
    }

    /**
     * Entries without dialog label keys are named by the module bundle under
     * PREF_ plus the upper-cased preference key (PREF_AUTO_SAVE_INTERVAL).
     */
    private static String labelOf(Def def) {
        if (def.labelKeys().isEmpty()) {
            return ActionPanelModule.getString("PREF_" + def.spec().key().toUpperCase(java.util.Locale.ENGLISH));
        }
        return def.label();
    }

    private static GroupDef page(String titleKey, List<Def> defs) {
        return new GroupDef(titleKey, defs, List.of(), null);
    }

    private static GroupDef page(String titleKey, List<Def> defs, List<GroupDef> children) {
        return new GroupDef(titleKey, defs, children, null);
    }

    private static Def toggle(String key, boolean codedDefault, String labelKey) {
        return new Def(new PreferenceActionSpec(key, KIND_TOGGLE, 0, 0, List.of(Boolean.toString(codedDefault))),
                List.of(labelKey));
    }

    private static Def slider(String key, int min, int max, int codedDefault, String... labelKeys) {
        return new Def(new PreferenceActionSpec(key, KIND_SLIDER, min, max, List.of(Integer.toString(codedDefault))),
                List.of(labelKeys));
    }

    private static Def combobox(String key, List<String> values, String labelKey) {
        return new Def(new PreferenceActionSpec(key, KIND_COMBOBOX, 0, 0, values), List.of(labelKey));
    }

    private static List<String> enumNames(Enum<?>[] values) {
        List<String> names = new ArrayList<>(values.length);
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return names;
    }

    /**
     * Dialog string without its mnemonic marker and trailing colon. Like
     * Mnemonics.setLocalizedText: the first single ampersand marks the
     * mnemonic, a doubled one is a literal ampersand.
     */
    static String text(String bundleKey) {
        String s = OStrings.getString(bundleKey).replace("&&", "\u0000").replaceFirst("&", "")
                .replace('\u0000', '&').trim();
        return s.endsWith(":") ? s.substring(0, s.length() - 1).trim() : s;
    }
}
