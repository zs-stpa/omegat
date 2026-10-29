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

package org.omegat.core.spellchecker;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import org.omegat.filters2.master.PluginUtils;
import org.omegat.util.Log;

/**
 * Hunspell dictionary provider for language modules that ship plain
 * dictionary files without LanguageTool.
 *
 * A module stores its pair as {@code org/omegat/dictionaries/hunspell/<stem>.aff}
 * and {@code .dic} and calls {@link #register(String, String...)} from its
 * {@code loadPlugins()}; this class then serves the pair for every registered
 * language code. Language modules share one class loader, so the resources
 * of all modules are visible from here. Modules need no dictionary class of
 * their own, which keeps them to a one-line plugin class.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class BundledHunspellDictionary extends AbstractHunspellDictionary {

    /** Resource folder of the bundled pairs, with trailing slash. */
    public static final String RESOURCE_BASE = "/org/omegat/dictionaries/hunspell/";

    private static final String PROVIDER = BundledHunspellDictionary.class.getName();

    /** Language code (de_AT, de) to file stem (de_AT). */
    private static final Map<String, String> STEMS = Collections.synchronizedMap(new TreeMap<>());

    /**
     * Registers a bundled pair for the given codes.
     *
     * @param stem
     *            file name of the pair without extension, e.g. {@code tr_TR}
     * @param codes
     *            language codes the pair serves, e.g. {@code tr_TR}, {@code tr};
     *            the stem itself and its language part are always registered
     */
    public static void register(final String stem, final String... codes) {
        putVariants(stem, stem, true);
        for (String code : codes) {
            putVariants(code, stem, true);
        }
        int sep = stem.indexOf('_');
        if (sep > 0) {
            // The bare language falls back to this pair unless a pair was
            // registered for it explicitly (sr serves Cyrillic, sr_Latn Latin).
            put(stem.substring(0, sep), stem, false);
        }
    }

    /**
     * The spell checker asks for the project language as OmegaT spells it:
     * {@code sr_LATN} and {@code sr-LATN} for a file stem {@code sr_Latn}.
     * Register the stem's own spelling and both of those.
     */
    private static void putVariants(final String code, final String stem, final boolean explicit) {
        String underscore = code.replace('-', '_');
        put(underscore, stem, explicit);
        int sep = underscore.indexOf('_');
        if (sep > 0) {
            String omegat = underscore.substring(0, sep).toLowerCase(Locale.ENGLISH) + "_"
                    + underscore.substring(sep + 1).toUpperCase(Locale.ENGLISH);
            put(omegat, stem, explicit);
            put(omegat.replace('_', '-'), stem, explicit);
        }
    }

    private static void put(final String code, final String stem, final boolean explicit) {
        synchronized (STEMS) {
            if (!explicit && STEMS.containsKey(code)) {
                return;
            }
            STEMS.put(code, stem);
        }
        SpellCheckerManager.registerSpellCheckerDictionaryProvider(code, SpellCheckDictionaryType.HUNSPELL,
                PROVIDER);
    }

    /** Registered language codes, for tests and diagnostics. */
    public static Map<String, String> getRegisteredStems() {
        return Collections.unmodifiableMap(new TreeMap<>(STEMS));
    }

    @Override
    protected String[] getDictionaries() {
        synchronized (STEMS) {
            return STEMS.values().stream().distinct().sorted().toArray(String[]::new);
        }
    }

    @Override
    protected @Nullable String getDictionary(final String language) {
        String stem = STEMS.get(language);
        if (stem == null) {
            stem = STEMS.get(language.replace('-', '_'));
        }
        if (stem == null) {
            synchronized (STEMS) {
                stem = STEMS.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(language.replace('-', '_')))
                        .map(Map.Entry::getValue).findFirst().orElse(null);
            }
        }
        if (stem == null) {
            // Last resort like the LanguageTool based modules: a pair whose
            // stem starts with the requested code.
            int sep = language.indexOf('_');
            String base = sep > 0 ? language.substring(0, sep) : language;
            stem = STEMS.get(base.toLowerCase(Locale.ENGLISH));
        }
        return stem;
    }

    @Override
    protected @Nullable URL getResource(final String resource) {
        String path = RESOURCE_BASE.substring(1) + resource;
        ClassLoader modules = PluginUtils.getClassLoader(PluginUtils.PluginType.LANGUAGE);
        URL url = modules == null ? null : modules.getResource(path);
        return url != null ? url : BundledHunspellDictionary.class.getClassLoader().getResource(path);
    }

    /**
     * Adds licence and upstream from the {@code <stem>.properties} the
     * artifact build writes next to the pair (keys {@code license} and
     * {@code upstream}); the manifest does not survive merging the artifact
     * into the module jar.
     */
    @Override
    public DictionaryInfo getDictionaryInfo(final String language) {
        DictionaryInfo info = super.getDictionaryInfo(language);
        String stem = getDictionary(language);
        if (stem == null) {
            return info;
        }
        try (InputStream in = getResourceAsStream(stem + ".properties")) {
            if (in == null) {
                return info;
            }
            return DictionaryResources.originFrom(info, in);
        } catch (IOException ex) {
            Log.log(ex);
            return info;
        }
    }

    @Override
    protected @Nullable InputStream getResourceAsStream(final String resource) {
        String path = RESOURCE_BASE.substring(1) + resource;
        ClassLoader modules = PluginUtils.getClassLoader(PluginUtils.PluginType.LANGUAGE);
        InputStream in = modules == null ? null : modules.getResourceAsStream(path);
        if (in == null) {
            // Unit tests and the IDE have the module on the plain class path.
            in = BundledHunspellDictionary.class.getClassLoader().getResourceAsStream(path);
        }
        return in;
    }
}
