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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import morfologik.stemming.Dictionary;

/**
 * Reads the facts of {@link DictionaryInfo} from dictionary files and
 * resources, shared by the installed-file and the bundled code paths.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
final class DictionaryResources {

    /** The first line of a Hunspell word list carries the entry count. */
    private static final Pattern HUNSPELL_COUNT = Pattern.compile("^\\uFEFF?\\s*(\\d+)");

    private DictionaryResources() {
    }

    /**
     * Entry count declared in the first line of a Hunspell {@code .dic}
     * stream, or null when the line is not a number. Closes the stream.
     */
    static @Nullable Long hunspellEntryCount(@Nullable InputStream dic) throws IOException {
        if (dic == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(dic, StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null) {
                return null;
            }
            Matcher m = HUNSPELL_COUNT.matcher(first);
            return m.find() ? Long.valueOf(m.group(1)) : null;
        }
    }

    /**
     * Number of sequences in a Morfologik automaton, or null when a stream
     * is missing or unreadable. Closes the streams.
     */
    static @Nullable Long morfologikEntryCount(@Nullable InputStream dict, @Nullable InputStream info) {
        if (dict == null || info == null) {
            closeQuietly(dict);
            closeQuietly(info);
            return null;
        }
        try (InputStream d = dict; InputStream i = info) {
            Dictionary dictionary = Dictionary.read(d, i);
            long count = 0;
            for (ByteBuffer ignored : dictionary.fsa) {
                count++;
                if ((count & 0xFFFF) == 0 && Thread.currentThread().isInterrupted()) {
                    // A cancelled worker must not finish millions of sequences.
                    return null;
                }
            }
            return count;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Size of a resource: from its location when known (file length or jar
     * entry size), otherwise by draining the stream. Null when there is
     * neither.
     */
    static @Nullable Long size(@Nullable URL location, Supplier<@Nullable InputStream> stream)
            throws IOException {
        if (location != null) {
            URLConnection connection = location.openConnection();
            long length = connection.getContentLengthLong();
            if (length >= 0) {
                return length;
            }
            try (InputStream in = connection.getInputStream()) {
                return in.transferTo(OutputStream.nullOutputStream());
            }
        }
        InputStream opened = stream.get();
        if (opened == null) {
            return null;
        }
        try (InputStream in = opened) {
            return in.transferTo(OutputStream.nullOutputStream());
        }
    }

    /** Sum of the known sizes; null when none is known. */
    static @Nullable Long sum(@Nullable Long first, @Nullable Long second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + second;
    }

    /** The jar a class was loaded from; null for a directory or unknown. */
    static @Nullable Path jarOf(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI());
            return Files.isRegularFile(path) ? path : null;
        } catch (URISyntaxException | IllegalArgumentException | SecurityException ex) {
            return null;
        }
    }

    /**
     * Fills licence and upstream a provider left open from the
     * {@code <stem>.properties} a module ships next to or for its
     * dictionary (keys {@code license} and {@code upstream}, the latter the
     * place where that licence statement can be read). Modules without such
     * a file stay open: no guess from the module licence.
     */
    static DictionaryInfo completeOrigin(DictionaryInfo info, @Nullable Path jar, @Nullable String stem,
            SpellCheckDictionaryType type) {
        if (jar == null || stem == null || (info.license() != null && info.upstream() != null)) {
            return info;
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = propertiesEntry(file, stem, type);
            if (entry == null) {
                return info;
            }
            try (InputStream in = file.getInputStream(entry)) {
                return originFrom(info, in);
            }
        } catch (IOException ex) {
            return info;
        }
    }

    /** Licence and upstream from a properties stream, keeping known values. */
    static DictionaryInfo originFrom(DictionaryInfo info, InputStream properties) throws IOException {
        Properties declared = new Properties();
        declared.load(properties);
        String license = info.license() != null ? info.license() : blankToNull(declared.getProperty("license"));
        String upstream = info.upstream() != null ? info.upstream()
                : blankToNull(declared.getProperty("upstream"));
        return info.withOrigin(license, upstream);
    }

    /**
     * {@code <stem>.<engine>.properties} when a module serves one stem with
     * both engines from different sources (de: Hunspell pair and Morfologik
     * automaton), else {@code <stem>.properties}.
     */
    private static @Nullable JarEntry propertiesEntry(JarFile file, String stem, SpellCheckDictionaryType type) {
        String engineSuffix = "/" + stem + "." + type.name().toLowerCase(Locale.ENGLISH) + ".properties";
        String suffix = "/" + stem + ".properties";
        JarEntry plain = null;
        Enumeration<JarEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(engineSuffix)) {
                return entry;
            }
            if (plain == null && entry.getName().endsWith(suffix)) {
                plain = entry;
            }
        }
        return plain;
    }

    static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static void closeQuietly(@Nullable InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
