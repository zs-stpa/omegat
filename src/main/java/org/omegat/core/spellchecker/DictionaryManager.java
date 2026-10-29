/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2007 Zoltan Bartko
               2009-2011 Didier Briel
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;

import org.jspecify.annotations.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import org.omegat.util.HttpConnectionUtils;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.OStrings;
import org.omegat.util.PatternConsts;
import org.omegat.util.Preferences;

/**
 * Dictionary manager. Spell checking dictionaries' utility functions.
 *
 * @author Zoltan Bartko - bartkozoltan@bartkozoltan.com
 * @author Didier Briel
 */
public class DictionaryManager {

    /** the directory string */
    private final File dir;

    /**
     * Creates a new instance of DictionaryManager.
     *
     * @param dir
     *            : the directory where the spell checking dictionary files
     *            (*.(aff|dic) are available locally
     */
    public DictionaryManager(File dir) {
        this.dir = dir;
    }

    /**
     * returns the dictionary directory
     */
    public String getDirectory() {
        return dir.getAbsolutePath();
    }

    /**
     * returns a list of full names of dictionaries from a dictionary code list
     */
    public List<String> getDictionaryNameList(List<String> aList) {
        List<String> result = new ArrayList<>();

        for (String dic : aList) {
            result.add(dic + " - " + getLanguageDisplayName(dic));
        }

        return result;
    }

    /**
     * Localized language name for a dictionary code in the xx_YY form; only
     * the first two code parts are used.
     */
    public static String getLanguageDisplayName(String code) {
        // Codes arrive as xx_YY, xx-YY and with script subtags (sr_Latn);
        // BCP 47 parsing names them all.
        Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
        if (locale.getLanguage().isEmpty()) {
            String[] parts = code.split("_");
            locale = parts.length == 1 ? Locale.of(parts[0]) : Locale.of(parts[0], parts[1]);
        }
        return locale.getDisplayName();
    }

    /**
     * return a list of full names of the local dictionaries
     *
     * @deprecated the flat list repeats a language once per dictionary
     *             source with identical labels; use
     *             {@link #getLocalDictionaryDisplayList()} instead.
     */
    @Deprecated
    public List<String> getLocalDictionaryNameList() {
        return getDictionaryNameList(getLocalDictionaryCodeList());
    }

    /**
     * One display line per available dictionary, sorted by language code
     * with installed files before bundled ones: "code - name: engine
     * (origin)". The flat per-source list showed the same language up to
     * three times with identical labels (bundled Hunspell, bundled
     * Morfologik, installed file); the origin suffix keeps every line
     * distinguishable.
     */
    public List<String> getLocalDictionaryDisplayList() {
        List<SpellingDictionaryEntry> entries = new ArrayList<>(getLocalDictionaryEntries());
        entries.sort(Comparator.comparing(SpellingDictionaryEntry::getLanguageCode)
                .thenComparing(entry -> !entry.isLocalFile()).thenComparing(SpellingDictionaryEntry::getType));
        List<String> result = new ArrayList<>(entries.size());
        for (SpellingDictionaryEntry entry : entries) {
            String engine = switch (entry.getType()) {
            case HUNSPELL -> "Hunspell";
            case MORFOLOGIK -> "Morfologik";
            };
            String source = OStrings.getString(entry.isLocalFile() ? "GUI_SPELLCHECKER_SOURCE_INSTALLED"
                    : "GUI_SPELLCHECKER_SOURCE_BUNDLED", engine);
            result.add(OStrings.getString("GUI_SPELLCHECKER_LANG_WITH_SOURCES", entry.getLanguageCode(),
                    getLanguageDisplayName(entry.getLanguageCode()), source));
        }
        return result;
    }

    /**
     * returns a list of available dictionaries in the xx_YY form
     */
    public List<String> getLocalDictionaryCodeList() {
        List<String> result = new ArrayList<>();
        for (SpellingDictionaryEntry entry : getLocalDictionaryEntries()) {
            result.add(entry.getLanguageCode());
        }
        return result;
    }

    /**
     * returns a list of available dictionaries.
     */
    public List<SpellingDictionaryEntry> getLocalDictionaryEntries() {
        List<SpellingDictionaryEntry> result = new ArrayList<>();

        // get all affix files
        String[] affixFiles = dir.list((d, name) -> name.endsWith(OConsts.SC_AFFIX_EXTENSION));

        // get all dictionary files
        String[] dictionaryFiles = dir.list((d, name) -> name.endsWith(OConsts.SC_DICTIONARY_EXTENSION));

        // match them
        if (affixFiles != null && dictionaryFiles != null) {
            for (String affixFile : affixFiles) {
                boolean match = false;

                // get the affix file name
                String affixName = FilenameUtils.getBaseName(affixFile);
                if (affixName == null || affixName.isEmpty()) {
                    continue;
                }
                // cycle through the dictionary names
                for (String dictionaryFile : dictionaryFiles) {
                    // get the dic file name
                    String dicName = FilenameUtils.getBaseName(dictionaryFile);
                    if (dicName == null || dicName.isEmpty()) {
                        continue;
                    }
                    if (affixName.equals(dicName)) {
                        match = true;
                        break;
                    }
                }

                if (match) {
                    result.add(
                            new SpellingDictionaryEntry(affixName, SpellCheckDictionaryType.HUNSPELL, true));
                }
            }
        }

        String[] morfologikFiles = dir.list((d, name) -> name.endsWith(OConsts.SC_MORFOLOGIK_EXTENSION));
        if (morfologikFiles != null) {
            for (String morfologikFile : morfologikFiles) {
                String baseName = FilenameUtils.getBaseName(morfologikFile);
                result.add(new SpellingDictionaryEntry(baseName, SpellCheckDictionaryType.MORFOLOGIK, true));
            }
        }

        addBundled(result, SpellCheckerManager.getHunspellDictionaryLanguages(), SpellCheckDictionaryType.HUNSPELL);
        addBundled(result, SpellCheckerManager.getMorfologikDictionaryLanguages(),
                SpellCheckDictionaryType.MORFOLOGIK);
        return result;
    }

    /**
     * Modules register one dictionary under several spellings of its code
     * (sr_Latn, sr_LATN, sr-LATN); list it once.
     */
    private static void addBundled(List<SpellingDictionaryEntry> result, Set<String> codes,
            SpellCheckDictionaryType type) {
        Map<String, String> spellings = new HashMap<>();
        for (String code : codes) {
            spellings.merge(code.replace('-', '_').toLowerCase(Locale.ENGLISH), code,
                    DictionaryManager::preferredSpelling);
        }
        for (String code : spellings.values()) {
            result.add(new SpellingDictionaryEntry(code, type, false));
        }
    }

    /**
     * Of two spellings of one code, the underscore form with the fewest
     * capitals: sr_Latn over sr_LATN over sr-LATN.
     */
    static String preferredSpelling(String first, String second) {
        boolean firstHyphen = first.indexOf('-') >= 0;
        if (firstHyphen != (second.indexOf('-') >= 0)) {
            return firstHyphen ? second : first;
        }
        long firstCapitals = first.chars().filter(Character::isUpperCase).count();
        long secondCapitals = second.chars().filter(Character::isUpperCase).count();
        if (firstCapitals != secondCapitals) {
            return firstCapitals < secondCapitals ? first : second;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    /** Table order: language code, installed files before bundled, engine. */
    public static final Comparator<SpellingDictionaryEntry> DISPLAY_ORDER = Comparator
            .comparing(SpellingDictionaryEntry::getLanguageCode).thenComparing(entry -> !entry.isLocalFile())
            .thenComparing(SpellingDictionaryEntry::getType);

    /** Engine name as shown to the user. */
    public static String getEngineName(SpellCheckDictionaryType type) {
        return switch (type) {
        case HUNSPELL -> "Hunspell";
        case MORFOLOGIK -> "Morfologik";
        };
    }

    /** Engine and origin as shown to the user, e.g. "Hunspell (bundled)". */
    public static String getSourceLabel(SpellingDictionaryEntry entry) {
        return OStrings.getString(
                entry.isLocalFile() ? "GUI_SPELLCHECKER_SOURCE_INSTALLED" : "GUI_SPELLCHECKER_SOURCE_BUNDLED",
                getEngineName(entry.getType()));
    }

    /**
     * Summary for the preferences panel: the number of dictionaries, then
     * one line with the distinct languages (code before any region or
     * script subtag) and their number.
     */
    public static String summarize(Collection<SpellingDictionaryEntry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        Set<String> languages = new TreeSet<>();
        for (SpellingDictionaryEntry entry : entries) {
            languages.add(baseLanguage(entry.getLanguageCode()));
        }
        return OStrings.getString("GUI_SPELLCHECKER_SUMMARY_DICTIONARIES", entries.size()) + "\n"
                + OStrings.getString("GUI_SPELLCHECKER_SUMMARY_LANGUAGES", languages.size(),
                        String.join(", ", languages));
    }

    /** The language subtag of a dictionary code: de for de_AT, sr for sr_Latn. */
    static String baseLanguage(String code) {
        int end = code.length();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '_' || c == '-') {
                end = i;
                break;
            }
        }
        return code.substring(0, end).toLowerCase(Locale.ENGLISH);
    }

    /** Facts about the entry's dictionary, read from its files or module. */
    public DictionaryInfo describe(SpellingDictionaryEntry entry) {
        if (!entry.isLocalFile()) {
            return SpellCheckerManager.getDictionaryInfo(entry.getLanguageCode(), entry.getType());
        }
        File main = mainFile(entry);
        File companion = companionFile(entry);
        long size = main.length() + (companion.exists() ? companion.length() : 0);
        Long entries = null;
        try {
            if (entry.getType() == SpellCheckDictionaryType.HUNSPELL) {
                try (InputStream dic = new FileInputStream(main)) {
                    entries = DictionaryResources.hunspellEntryCount(dic);
                }
            } else if (companion.exists()) {
                // Opened here so that a failing second open cannot leak the first.
                try (InputStream dict = new FileInputStream(main);
                        InputStream info = new FileInputStream(companion)) {
                    entries = DictionaryResources.morfologikEntryCount(dict, info);
                }
            }
        } catch (IOException ex) {
            Log.log(ex);
        }
        // No licence travels with a hand-installed file; the link opens its folder.
        return new DictionaryInfo(OStrings.getString("GUI_SPELLCHECKER_LICENSE_LOCAL"), dir.toURI().toString(),
                entries, size);
    }

    /**
     * The dictionary file to show the user: the installed file itself, or
     * a copy of the bundled one in a fresh temporary directory.
     */
    public @Nullable Path locate(SpellingDictionaryEntry entry) throws IOException {
        if (entry.isLocalFile()) {
            File main = mainFile(entry);
            return main.exists() ? main.toPath() : null;
        }
        return SpellCheckerManager.extractDictionary(entry.getLanguageCode(), entry.getType(), extractDir());
    }

    private static @Nullable Path extractDir;

    /** One directory per session for bundled copies, deleted on exit. */
    private static synchronized Path extractDir() throws IOException {
        if (extractDir == null) {
            Path dir = Files.createTempDirectory("omegat-dictionary");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(dir.toFile())));
            extractDir = dir;
        }
        return extractDir;
    }

    /** The word list: .dic for Hunspell, .dict for Morfologik. */
    private File mainFile(SpellingDictionaryEntry entry) {
        String ext = entry.getType() == SpellCheckDictionaryType.HUNSPELL ? OConsts.SC_DICTIONARY_EXTENSION
                : OConsts.SC_MORFOLOGIK_EXTENSION;
        return new File(dir, entry.getLanguageCode() + ext);
    }

    /** The companion: .aff for Hunspell, .info for Morfologik. */
    private File companionFile(SpellingDictionaryEntry entry) {
        String ext = entry.getType() == SpellCheckDictionaryType.HUNSPELL ? OConsts.SC_AFFIX_EXTENSION
                : ".info";
        return new File(dir, entry.getLanguageCode() + ext);
    }

    /**
     * Uninstall (delete) a given dictionary from the dictionary directory
     *
     * @param lang
     *            : the language code (xx_YY) of the dictionary to be deleted
     * @return true upon success, otherwise false
     */
    @Deprecated
    public boolean uninstallDictionary(String lang) {
        if (lang == null || lang.isEmpty()) {
            return false;
        }
        Optional<SpellingDictionaryEntry> target = getLocalDictionaryEntries().stream().filter(
                it -> it.getLanguageCode().equals(lang)).findFirst();
        if (target.isPresent()) {
            String base = getDirectory() + File.separator + lang;
            if (target.get().getType().equals(SpellCheckDictionaryType.HUNSPELL)) {
                File affFile = new File(base + OConsts.SC_AFFIX_EXTENSION);
                if (!affFile.delete()) {
                    return false;
                }
                File dicFile = new File(base + OConsts.SC_DICTIONARY_EXTENSION);
                return dicFile.delete();
            } else {
                File dictFile = new File(base + OConsts.SC_MORFOLOGIK_EXTENSION);
                return dictFile.delete();
            }
        }
        return false;
    }

    /**
     * return a list of names of installable dictionaries (e.g. en_US - english
     * (USA))
     */
    @Deprecated
    public List<String> getInstallableDictionaryNameList() throws IOException {
        return getDictionaryNameList(getInstallableDictionaryCodeList());
    }

    /**
     * returns a list of codes (xx_YY) of installable dictionaries
     */
    public List<String> getInstallableDictionaryCodeList() throws IOException {
        List<String> localDicList = getLocalDictionaryCodeList();

        List<String> remoteDicList = getRemoteDictionaryCodeList();

        List<String> result = new ArrayList<>();

        // compare the two lists
        for (String dicCode : remoteDicList) {
            if (!localDicList.contains(dicCode)) {
                result.add(dicCode);
            }
        }

        return result;
    }

    /**
     * downloads the list of available dictionaries from the net
     */
    private List<String> getRemoteDictionaryCodeList() throws IOException {
        List<String> result = new ArrayList<>();

        // download the file
        String dictionary = Preferences.getPreference(Preferences.SPELLCHECKER_DICTIONARY_URL);
        if (dictionary == null || dictionary.isEmpty()) {
            return result;
        }
        String htmlfile = HttpConnectionUtils.getURL(URI.create(dictionary).toURL());

        // build a list of available language codes
        Matcher matcher = PatternConsts.DICTIONARY_ZIP.matcher(htmlfile);

        while (matcher.find()) {
            // strip the quotes from the ends
            String match = matcher.group();
            int dotPosition = match.indexOf(".");
            // delete the '.zip"'
            result.add(match.substring(1, dotPosition));
        }

        return result;
    }

    /**
     * installs a remote dictionary by downloading the corresponding zip file
     * from the net and by installing the aff and dic file to the dictionary
     * directory.
     *
     * @param langCode
     *            : the language code (xx_YY)
     */
    @Deprecated
    public void installRemoteDictionary(String langCode) throws IOException {
        String dictionary = Preferences.getPreference(Preferences.SPELLCHECKER_DICTIONARY_URL);
        String from = dictionary + "/" + langCode + ".zip";

        // Dirty hack for the French dictionary. Since it is named
        // fr_FR_1-3-2.zip, we remove the "_1-3-2" portion
        // [ 2138846 ] French dictionary cannot be downloaded and installed
        int pos = langCode.indexOf("_1-3-2");
        if (pos != -1) {
            langCode = langCode.substring(0, pos);
        }
        List<String> expectedFiles = List.of(langCode + OConsts.SC_AFFIX_EXTENSION,
                langCode + OConsts.SC_DICTIONARY_EXTENSION);
        HttpConnectionUtils.downloadZipFileAndExtract(URI.create(from).toURL(), dir, expectedFiles);
    }

    public static class SpellingDictionaryEntry {
        private final String languageCode;
        private final SpellCheckDictionaryType type;
        private final boolean localFile;

        public SpellingDictionaryEntry(String languageCode, SpellCheckDictionaryType type,
                boolean localFile) {
            this.languageCode = languageCode;
            this.type = type;
            this.localFile = localFile;
        }

        /** Dictionary code in the xx_YY form. */
        public String getLanguageCode() {
            return languageCode;
        }

        public SpellCheckDictionaryType getType() {
            return type;
        }

        /**
         * Whether the dictionary is a file in the dictionary folder
         * (installed or hand-copied), as opposed to a bundled dictionary a
         * language module registered.
         */
        public boolean isLocalFile() {
            return localFile;
        }
    }

}
