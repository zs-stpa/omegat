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

package org.omegat.core.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import gen.core.filters.Filter;
import gen.core.filters.Filters;
import org.omegat.core.Core;
import org.omegat.core.segmentation.SRX;
import org.omegat.core.segmentation.SRXManager;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.util.Log;
import org.omegat.util.OStrings;
import org.omegat.util.PatternConsts;
import org.omegat.util.Preferences;
import org.omegat.util.TagPatternsStorage;

/**
 * The built-in file-backed team settings: the project's file filter
 * configuration ({@code omegat/filters.xml}), segmentation rules
 * ({@code omegat/segmentation.srx}, legacy {@code segmentation.conf}) and
 * tag definitions ({@code omegat/tag_patterns.xml}). A team sync used to
 * overwrite local changes to these files silently and OmegaT never
 * committed them itself; registered as {@link TeamSetting}, the generic
 * negotiation asks instead and offers to distribute a change to the team.
 * The raw value is the canonical serialized content, so differently
 * formatted files with equal rules do not count as diverged. Each of
 * these settings falls back to the user configuration when the project
 * carries none, so a team project without the file runs on every member's
 * own configuration; the user fallback readers expose that configuration
 * in the project's raw form when it differs from the installed defaults,
 * and the load reports the setting as unmanaged so the user can
 * distribute it - unless the team recorded in {@link #USER_LEVEL} that
 * the setting is left to each member.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class TeamSettingFiles {

    /** Registry key of the file filter configuration setting. */
    public static final String FILTERS_KEY = "filters";

    /** Registry key of the segmentation rules setting. */
    public static final String SEGMENTATION_KEY = "segmentation";

    /** Registry key of the tag definitions setting. */
    public static final String TAG_PATTERNS_KEY = "tag_patterns";

    /** File filter configuration as a team setting. */
    public static final TeamSetting FILTERS = TeamSetting.ofStoredFile(FILTERS_KEY,
            "TEAM_SETTING_NAME_FILTERS", TeamSettingFiles::readSessionFilters,
            TeamSettingFiles::applySessionFilters, TeamSettingFiles::describeFile,
            new FiltersStorage()).withUserFallback(TeamSettingFiles::readUserFilters);

    /** Segmentation rules as a team setting. */
    public static final TeamSetting SEGMENTATION = TeamSetting.ofStoredFile(SEGMENTATION_KEY,
            "TEAM_SETTING_NAME_SEGMENTATION", TeamSettingFiles::readSessionSegmentation,
            TeamSettingFiles::applySessionSegmentation, TeamSettingFiles::describeFile,
            new SegmentationStorage()).withUserFallback(TeamSettingFiles::readUserSegmentation);

    /**
     * Project-specific custom tag and removed-text expressions as a team
     * setting.
     */
    public static final TeamSetting TAG_PATTERNS = TeamSetting.ofStoredFile(TAG_PATTERNS_KEY,
            "TEAM_SETTING_NAME_TAG_PATTERNS", TeamSettingFiles::readSessionTagPatterns,
            TeamSettingFiles::applySessionTagPatterns, TeamSettingFiles::describeTagPatterns,
            new TagPatternsFileStorage()).withUserFallback(TeamSettingFiles::readUserTagPatterns);

    /** Registry key of the settings the team leaves to each member. */
    public static final String USER_LEVEL_KEY = "user_level_settings";

    /**
     * The keys of the settings the team decided to leave to each member's
     * user configuration, as a sidecar team setting: a comma-separated
     * sorted key list, committed with the project settings file, so the
     * hint about a missing team value stays away for every member once the
     * team made that decision.
     */
    public static final TeamSetting USER_LEVEL = TeamSetting.of(USER_LEVEL_KEY,
            "TEAM_SETTING_NAME_USER_LEVEL", config -> joinKeys(config.getUserLevelTeamSettings()),
            (config, raw) -> config.setUserLevelTeamSettings(parseKeys(raw)),
            TeamSettingFiles::describeUserLevel, raw -> {
                Set<String> keys = parseKeys(raw);
                return keys.isEmpty() ? null : joinKeys(keys);
            });

    private static final Object REGISTER_LOCK = new Object();

    private TeamSettingFiles() {
    }

    /** Registers the settings when absent; safe under concurrent calls. */
    public static void ensureRegistered() {
        synchronized (REGISTER_LOCK) {
            for (TeamSetting setting : List.of(FILTERS, SEGMENTATION, TAG_PATTERNS, USER_LEVEL)) {
                if (TeamSettingsRegistry.byKey(setting.getKey()) == null) {
                    TeamSettingsRegistry.register(setting);
                }
            }
        }
    }

    private static @Nullable String readSessionFilters(ProjectProperties config) {
        Filters filters = config.getProjectFilters();
        if (filters == null) {
            return null;
        }
        try {
            return FilterMaster.writeConfigToString(canonical(filters));
        } catch (IOException ex) {
            Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
            return null;
        }
    }

    /**
     * The canonical form includes the completion with this installation's
     * available filters that loading a file applies anyway: without it, a
     * configuration from a team repository or an older OmegaT version
     * would differ from every locally (re)written file, reading as a
     * divergence on each open although nobody changed anything.
     */
    private static Filters canonical(Filters filters) {
        return FilterMaster.completeWithAvailableFilters(filters);
    }

    private static void applySessionFilters(ProjectProperties config, @Nullable String raw) {
        if (raw == null) {
            config.setProjectFilters(null);
        } else {
            try {
                config.setProjectFilters(canonical(FilterMaster.loadConfigFromString(raw)));
            } catch (IOException ex) {
                // Unparseable content: keep the current session
                // configuration instead of silently falling back to global
                // defaults.
                Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
                return;
            }
        }
        // Answering the divergence question must take effect like flipping
        // a value setting does; entries loaded before the swap keep their
        // parse until the next reload, as with any mid-session change.
        if (appliesToLoadedProject(config)) {
            Core.setFilterMaster(new FilterMaster(
                    Optional.ofNullable(config.getProjectFilters()).orElse(Preferences.getFilters())));
        }
    }

    /**
     * The user-level file filter configuration in the project's raw form -
     * what a project without its own filters.xml runs on - or null when it
     * equals the installed defaults in substance: defaults have nothing of
     * this member's own to distribute, and distributing them would override
     * the other members' user configurations with a project file. Known
     * limit: option values the filter options dialog persisted explicitly
     * count as a deviation even when they equal the filter's defaults.
     */
    private static @Nullable String readUserFilters(ProjectProperties config) {
        try {
            Filters user = Preferences.getFilters();
            if (comparableForm(user).equals(comparableForm(FilterMaster.createDefaultFiltersConfig()))) {
                return null;
            }
            return FilterMaster.writeConfigToString(canonical(user));
        } catch (IOException ex) {
            Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
            return null;
        }
    }

    /**
     * The canonical form of a sorted copy, for comparing configurations in
     * substance: the order of filters and options does not count, because
     * an older user file carries the filters added since at its end, and
     * the defaults list them in installation order.
     */
    static String comparableForm(Filters filters) throws IOException {
        Filters sorted = FilterMaster.cloneConfig(canonical(filters));
        sorted.getFilters().sort(Comparator.comparing(Filter::getClassName));
        for (Filter filter : sorted.getFilters()) {
            filter.getOption().sort(Comparator.comparing(Filter.Option::getName));
        }
        return FilterMaster.writeConfigToString(sorted);
    }

    private static @Nullable String readSessionSegmentation(ProjectProperties config) {
        SRX srx = config.getProjectSRX();
        if (srx == null) {
            return null;
        }
        try {
            return SRXManager.writeToString(srx);
        } catch (IOException ex) {
            Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
            return null;
        }
    }

    private static void applySessionSegmentation(ProjectProperties config, @Nullable String raw) {
        if (raw == null) {
            config.setProjectSRX(null);
        } else {
            try {
                config.setProjectSRX(SRXManager.loadSrxFromString(raw));
            } catch (IOException ex) {
                Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
                return;
            }
        }
        if (appliesToLoadedProject(config)) {
            Core.setSegmenter(new Segmenter(
                    Optional.ofNullable(config.getProjectSRX()).orElse(Preferences.getSRX())));
        }
    }

    /**
     * The user-level segmentation rules in the project's raw form, or null
     * when they equal the built-in default rules. A rule set that an older
     * OmegaT version saved in full and that was migrated since counts as a
     * deviation: it is one, the rules differ from today's defaults.
     */
    private static @Nullable String readUserSegmentation(ProjectProperties config) {
        try {
            String user = SRXManager.writeToString(Preferences.getSRX());
            String defaults = SRXManager.writeToString(SRX.getDefault());
            return user.equals(defaults) ? null : user;
        } catch (IOException ex) {
            Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
            return null;
        }
    }

    private static @Nullable String readSessionTagPatterns(ProjectProperties config) {
        TagPatternsStorage.TagPatterns patterns = new TagPatternsStorage.TagPatterns();
        patterns.setCustomTagPattern(config.getCustomTagPattern());
        patterns.setRemoveTextPattern(config.getRemoveTextPattern());
        if (patterns.isEmpty()) {
            return null;
        }
        try {
            return TagPatternsStorage.writeToString(patterns);
        } catch (IOException ex) {
            Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
            return null;
        }
    }

    /**
     * The effective user-level tag expressions in the project's raw form,
     * resolved as the tag validation does (the custom tag expression has a
     * built-in default when the preference was never set, the removed-text
     * expression is empty, i.e. disabled, then), or null when both equal
     * those defaults.
     */
    private static @Nullable String readUserTagPatterns(ProjectProperties config) {
        TagPatternsStorage.TagPatterns patterns = new TagPatternsStorage.TagPatterns();
        patterns.setCustomTagPattern(Preferences.existsPreference(Preferences.CHECK_CUSTOM_PATTERN)
                ? Preferences.getPreference(Preferences.CHECK_CUSTOM_PATTERN)
                : PatternConsts.CHECK_CUSTOM_PATTERN_DEFAULT);
        patterns.setRemoveTextPattern(Preferences.getPreference(Preferences.CHECK_REMOVE_PATTERN));
        if (PatternConsts.CHECK_CUSTOM_PATTERN_DEFAULT.equals(patterns.getCustomTagPattern())
                && "".equals(patterns.getRemoveTextPattern())) {
            return null;
        }
        try {
            return TagPatternsStorage.writeToString(patterns);
        } catch (IOException ex) {
            Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
            return null;
        }
    }

    private static void applySessionTagPatterns(ProjectProperties config, @Nullable String raw) {
        if (raw == null) {
            config.setCustomTagPattern(null);
            config.setRemoveTextPattern(null);
        } else {
            TagPatternsStorage.TagPatterns patterns;
            try {
                patterns = TagPatternsStorage.loadFromString(raw);
            } catch (IOException ex) {
                // Unparseable content: keep the current session expressions
                // instead of silently falling back to global defaults.
                Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
                return;
            }
            config.setCustomTagPattern(patterns.getCustomTagPattern());
            config.setRemoveTextPattern(patterns.getRemoveTextPattern());
        }
        if (appliesToLoadedProject(config)) {
            PatternConsts.applyProjectPatterns(config.getCustomTagPattern(),
                    config.getRemoveTextPattern());
        }
    }

    /**
     * The two expressions are short enough to show, so unlike the other
     * file-backed settings the description names them instead of a
     * fingerprint.
     */
    private static String describeTagPatterns(@Nullable String raw) {
        if (raw == null) {
            return OStrings.getString("TEAM_SETTING_VALUE_FILE_NONE");
        }
        try {
            TagPatternsStorage.TagPatterns patterns = TagPatternsStorage.loadFromString(raw);
            return OStrings.getString("TEAM_SETTING_VALUE_TAG_PATTERNS",
                    describePattern(patterns.getCustomTagPattern()),
                    describePattern(patterns.getRemoveTextPattern()));
        } catch (IOException ex) {
            return describeFile(raw);
        }
    }

    private static String describePattern(@Nullable String pattern) {
        if (pattern == null) {
            return OStrings.getString("TEAM_SETTING_VALUE_TAG_PATTERNS_GLOBAL");
        }
        if (pattern.isEmpty()) {
            return OStrings.getString("TEAM_SETTING_VALUE_TAG_PATTERNS_OFF");
        }
        return '"' + pattern + '"';
    }

    /** The keys in a raw user-level value, sorted and without duplicates. */
    static Set<String> parseKeys(@Nullable String raw) {
        Set<String> keys = new TreeSet<>();
        if (raw != null) {
            for (String key : raw.split(",")) {
                if (!key.trim().isEmpty()) {
                    keys.add(key.trim());
                }
            }
        }
        return keys;
    }

    static String joinKeys(Set<String> keys) {
        return String.join(",", new TreeSet<>(keys));
    }

    /**
     * The raw user-level value that also leaves the given settings to the
     * members, for recording a team decision taken in the dialog.
     */
    public static String withKeysLeftToMembers(@Nullable String currentRaw, Collection<String> keys) {
        Set<String> all = parseKeys(currentRaw);
        all.addAll(keys);
        return joinKeys(all);
    }

    private static String describeUserLevel(@Nullable String raw) {
        Set<String> keys = parseKeys(raw);
        if (keys.isEmpty()) {
            return OStrings.getString("TEAM_SETTING_VALUE_USER_LEVEL_NONE");
        }
        List<String> names = new ArrayList<>();
        for (String key : keys) {
            TeamSetting setting = TeamSettingsRegistry.byKey(key);
            names.add(setting == null ? key : setting.getDisplayName());
        }
        return OStrings.getString("TEAM_SETTING_VALUE_USER_LEVEL", String.join(", ", names));
    }

    /**
     * Whether the given properties belong to the loaded project, so the
     * apply must also rewire the live filter master or segmenter. False
     * during a project load - the load wires them itself right after the
     * settings resolution - and for detached copies like the properties
     * dialog working set.
     */
    private static boolean appliesToLoadedProject(ProjectProperties config) {
        return Core.getProject().isProjectLoaded()
                && Core.getProject().getProjectProperties() == config;
    }

    /**
     * Dialogs cannot show whole files, so the description names the state
     * and fingerprints the content, letting the user tell two project
     * versions apart.
     */
    private static String describeFile(@Nullable String raw) {
        if (raw == null) {
            return OStrings.getString("TEAM_SETTING_VALUE_FILE_NONE");
        }
        return OStrings.getString("TEAM_SETTING_VALUE_FILE_CUSTOM", fingerprint(raw));
    }

    static String fingerprint(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class FiltersStorage implements TeamSetting.Storage {

        @Override
        public @Nullable String loadFrom(ProjectProperties config,
                Function<String, @Nullable File> fileResolver) {
            File file = fileResolver.apply(pathsUnderRoot(config).get(0));
            if (file == null || !file.isFile()) {
                return null;
            }
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (content.startsWith("\uFEFF")) {
                    // the byte-stream loader accepted a BOM, so must we
                    content = content.substring(1);
                }
                return FilterMaster
                        .writeConfigToString(canonical(FilterMaster.loadConfigFromString(content)));
            } catch (IOException ex) {
                Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
                return null;
            }
        }

        @Override
        public void save(ProjectProperties config, @Nullable String raw) throws IOException {
            FilterMaster.saveConfig(
                    raw == null ? null : canonical(FilterMaster.loadConfigFromString(raw)),
                    new File(config.getProjectInternal(), FilterMaster.FILE_FILTERS));
        }

        @Override
        public List<String> pathsUnderRoot(ProjectProperties config) {
            return List.of(config.getProjectInternalRelative() + FilterMaster.FILE_FILTERS);
        }
    }

    private static final class TagPatternsFileStorage implements TeamSetting.Storage {

        @Override
        public @Nullable String loadFrom(ProjectProperties config,
                Function<String, @Nullable File> fileResolver) {
            File file = fileResolver.apply(pathsUnderRoot(config).get(0));
            if (file == null || !file.isFile()) {
                return null;
            }
            try {
                TagPatternsStorage.TagPatterns patterns = TagPatternsStorage.load(file);
                return patterns == null || patterns.isEmpty() ? null
                        : TagPatternsStorage.writeToString(patterns);
            } catch (IOException ex) {
                Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
                return null;
            }
        }

        @Override
        public void save(ProjectProperties config, @Nullable String raw) throws IOException {
            if (raw == null && config.isTagPatternsLoadFailed()) {
                // An unreadable file is not represented by the session's
                // null value: leave it in place for repair instead of
                // deleting it on a routine save.
                return;
            }
            TagPatternsStorage.save(raw == null ? null : TagPatternsStorage.loadFromString(raw),
                    new File(config.getProjectInternal(), TagPatternsStorage.FILE_TAG_PATTERNS));
            // A successful write of real content replaces a possibly broken
            // file, so the repair protection may end.
            config.resetTagPatternsLoadFailed();
        }

        @Override
        public List<String> pathsUnderRoot(ProjectProperties config) {
            return List.of(config.getProjectInternalRelative() + TagPatternsStorage.FILE_TAG_PATTERNS);
        }
    }

    private static final class SegmentationStorage implements TeamSetting.Storage {

        @Override
        public @Nullable String loadFrom(ProjectProperties config,
                Function<String, @Nullable File> fileResolver) {
            List<String> paths = pathsUnderRoot(config);
            File srxFile = fileResolver.apply(paths.get(0));
            File confFile = fileResolver.apply(paths.get(1));
            SRX srx;
            if (srxFile != null && srxFile.isFile()) {
                srx = SRXManager.loadSrxFile(srxFile.toURI());
            } else if (confFile != null && confFile.isFile()) {
                srx = SRXManager.loadConfFileNoMigrate(confFile);
            } else {
                return null;
            }
            if (srx == null) {
                return null;
            }
            try {
                return SRXManager.writeToString(srx);
            } catch (IOException ex) {
                Log.logErrorRB(ex, "TEAM_SETTING_APPLY_ERROR");
                return null;
            }
        }

        @Override
        public void save(ProjectProperties config, @Nullable String raw) throws IOException {
            SRXManager.saveToSrx(raw == null ? null : SRXManager.loadSrxFromString(raw),
                    new File(config.getProjectInternal()));
        }

        @Override
        public List<String> pathsUnderRoot(ProjectProperties config) {
            // Both formats, srx first: sharing mirrors the sharer's
            // checkout, which after the srx migration no longer holds a
            // conf file, so the repository copy of the legacy file goes
            // away with it.
            return List.of(config.getProjectInternalRelative() + SRXManager.SRX_SENTSEG,
                    config.getProjectInternalRelative() + SRXManager.CONF_SENTSEG);
        }
    }
}
