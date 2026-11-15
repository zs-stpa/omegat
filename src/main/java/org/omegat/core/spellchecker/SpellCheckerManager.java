/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2023 Hiroshi Miura
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.omegat.filters2.master.PluginUtils;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.StaticUtils;

public class SpellCheckerManager {

    private ISpellChecker spellChecker;

    public static File getDefaultDictionaryDir() {
        return new File(StaticUtils.getConfigDir(), OConsts.SPELLING_DICT_DIR);
    }

    /**
     * Get a spell checker engine used currently.
     * 
     * @return spell checker engine.
     */
    public ISpellChecker getCurrentSpellChecker() {
        if (spellChecker != null) {
            return spellChecker;
        }
        // Try to use a custom spell checker if one is available.
        for (Class<?> customSpellChecker : PluginUtils.getSpellCheckClasses()) {
            try {
                spellChecker = (ISpellChecker) customSpellChecker.getDeclaredConstructor().newInstance();
                if (spellChecker.initialize()) {
                    return spellChecker;
                } else {
                    spellChecker = null;
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException
                    | InstantiationException ex) {
                Log.log(ex);
            }
        }
        spellChecker = new SpellCheckerDummy();
        Log.logRB("CORE_SPELLCHECKER_NO_ENGINE");
        return spellChecker;
    }

    // Plugins register at start-up; the preferences worker reads from another thread.
    private static final Map<String, String> MORFOLOGIK_DICTIONARY_PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, String> HUNSPELL_DICTIONARY_PROVIDERS = new ConcurrentHashMap<>();

    public static Set<String> getMorfologikDictionaryLanguages() {
        return MORFOLOGIK_DICTIONARY_PROVIDERS.keySet();
    }

    public static Set<String> getHunspellDictionaryLanguages() {
        return HUNSPELL_DICTIONARY_PROVIDERS.keySet();
    }

    /**
     * The entry point to register spell checker dictionary class.
     * 
     * @param lang
     *            supported language e.g., en, en_US
     * @param type
     *            enum type of dictionary.
     * @param dictionaryProvider
     *            full qualified class path of the dictionary provider.
     */
    public static void registerSpellCheckerDictionaryProvider(String lang, SpellCheckDictionaryType type,
            String dictionaryProvider) {
        if (Objects.requireNonNull(type) == SpellCheckDictionaryType.HUNSPELL) {
            HUNSPELL_DICTIONARY_PROVIDERS.put(lang, dictionaryProvider);
        } else if (type == SpellCheckDictionaryType.MORFOLOGIK) {
            MORFOLOGIK_DICTIONARY_PROVIDERS.put(lang, dictionaryProvider);
        }
    }

    /**
     * Install hunspell dictionary into the specified directory.
     * 
     * @param dictionaryDir
     *            target directory.
     * @param language
     *            target dictionary language.
     * @return Path of the dictionary installed.
     */
    public static Path installHunspellDictionary(String dictionaryDir, String language) {
        String className = HUNSPELL_DICTIONARY_PROVIDERS.get(language);
        if (className == null) {
            return null;
        }
        ISpellCheckerDictionary dictionaryProvider = getSpellCheckerDictionary(className);
        return dictionaryProvider.installHunspellDictionary(Paths.get(dictionaryDir), language);
    }

    /**
     * Provide the Hunspell dictionary object.
     * 
     * @param language
     *            target language e.g, en, en_US
     * @return Lucene hunspell Dictionary object.
     */
    public static org.apache.lucene.analysis.hunspell.Dictionary getHunspellDictionary(String language) {
        String className = HUNSPELL_DICTIONARY_PROVIDERS.get(language);
        if (className == null) {
            return null;
        }
        ISpellCheckerDictionary dictionaryProvider = getSpellCheckerDictionary(className);
        return dictionaryProvider.getHunspellDictionary(language);
    }

    /**
     * Provide the Mofologik dictionary object.
     * 
     * @param language
     *            target language, e.g., en, en_US
     * @return Morfologik Dictionary object.
     */
    public static morfologik.stemming.Dictionary getMorfologikDictionary(String language) {
        String className = MORFOLOGIK_DICTIONARY_PROVIDERS.get(language);
        if (className == null) {
            return null;
        }
        ISpellCheckerDictionary dictionaryProvider = getSpellCheckerDictionary(className);
        return dictionaryProvider.getMorfologikDictionary(language);
    }

    /**
     * Facts about the bundled dictionary registered for the language and
     * engine; {@link DictionaryInfo#UNKNOWN} when none is registered.
     */
    public static DictionaryInfo getDictionaryInfo(String language, SpellCheckDictionaryType type) {
        String className = getProviders(type).get(language);
        if (className == null) {
            return DictionaryInfo.UNKNOWN;
        }
        try (ISpellCheckerDictionary provider = getSpellCheckerDictionary(className)) {
            DictionaryInfo info = provider.getDictionaryInfo(language);
            return DictionaryResources.completeOrigin(info, DictionaryResources.jarOf(provider.getClass()),
                    provider.getDictionaryStem(language), type);
        } catch (IOException | RuntimeException ex) {
            Log.log(ex);
            return DictionaryInfo.UNKNOWN;
        }
    }

    /**
     * Copies the bundled dictionary registered for the language and engine
     * into the directory.
     *
     * @return the main dictionary file, or null when none is registered
     */
    public static @Nullable Path extractDictionary(String language, SpellCheckDictionaryType type,
            Path targetDir) {
        String className = getProviders(type).get(language);
        if (className == null) {
            return null;
        }
        try (ISpellCheckerDictionary provider = getSpellCheckerDictionary(className)) {
            return provider.extractDictionary(targetDir, language);
        } catch (IOException | RuntimeException ex) {
            Log.log(ex);
            return null;
        }
    }

    private static Map<String, String> getProviders(SpellCheckDictionaryType type) {
        return type == SpellCheckDictionaryType.HUNSPELL ? HUNSPELL_DICTIONARY_PROVIDERS
                : MORFOLOGIK_DICTIONARY_PROVIDERS;
    }

    private static ISpellCheckerDictionary getSpellCheckerDictionary(String className) {
        try {
            ClassLoader modules = PluginUtils.getClassLoader(PluginUtils.PluginType.LANGUAGE);
            if (modules == null) {
                // No language module loaded (unit tests, IDE): the provider
                // is on the plain class path.
                modules = SpellCheckerManager.class.getClassLoader();
            }
            Class<?> aClass = modules.loadClass(className);
            Constructor<?> constructor = aClass.getConstructor();
            return (ISpellCheckerDictionary) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class '" + className + " could not be found in classpath", e);
        } catch (Exception e) {
            throw new RuntimeException("Object for class '" + className + " could not be created", e);
        }
    }
}
