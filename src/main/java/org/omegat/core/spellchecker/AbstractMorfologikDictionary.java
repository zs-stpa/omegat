/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2024 Hiroshi Miura
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
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import morfologik.stemming.Dictionary;

import org.omegat.util.Log;

public abstract class AbstractMorfologikDictionary implements ISpellCheckerDictionary {
    private static final String DICT_EXT = ".dict";
    private static final String META_EXT = ".info";

    private InputStream infoInputStream;
    private InputStream dictInputStream;

    protected abstract String[] getDictionaries();

    protected String getDictionary(String language) {
        return Arrays.stream(getDictionaries()).filter(lang -> lang.startsWith(language)).findFirst().orElse(null);
    }
    protected abstract InputStream getResourceAsStream(String resource);

    /**
     * Location of a resource when the module can name one, so sizes come
     * from the file or jar entry instead of draining the stream.
     */
    protected @Nullable URL getResource(String resource) {
        return null;
    }

    @Override
    public morfologik.stemming.Dictionary getMorfologikDictionary(String language) {
        String target = getDictionary(language);
        if (target != null) {
            infoInputStream = getResourceAsStream(target + META_EXT);
            dictInputStream = getResourceAsStream(target + DICT_EXT);
            if (infoInputStream == null || dictInputStream == null) {
                close();
                return null;
            }
            try {
                return Dictionary.read(dictInputStream, infoInputStream);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    @Override
    public DictionaryInfo getDictionaryInfo(String language) {
        String target = getDictionary(language);
        if (target == null) {
            return DictionaryInfo.UNKNOWN;
        }
        try {
            Long size = DictionaryResources.sum(
                    DictionaryResources.size(getResource(target + DICT_EXT),
                            () -> getResourceAsStream(target + DICT_EXT)),
                    DictionaryResources.size(getResource(target + META_EXT),
                            () -> getResourceAsStream(target + META_EXT)));
            Long entries = DictionaryResources.morfologikEntryCount(getResourceAsStream(target + DICT_EXT),
                    getResourceAsStream(target + META_EXT));
            return new DictionaryInfo(null, null, entries, size);
        } catch (IOException ex) {
            Log.log(ex);
            return DictionaryInfo.UNKNOWN;
        }
    }

    @Override
    public @Nullable Path extractDictionary(Path targetDir, String language) {
        String target = getDictionary(language);
        if (target == null) {
            return null;
        }
        try {
            Path dict = targetDir.resolve(target + DICT_EXT);
            copyResource(target + DICT_EXT, dict);
            copyResource(target + META_EXT, targetDir.resolve(target + META_EXT));
            return dict;
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public @Nullable String getDictionaryStem(String language) {
        return getDictionary(language);
    }

    private void copyResource(String resource, Path to) throws IOException {
        try (InputStream in = getResourceAsStream(resource)) {
            if (in == null) {
                throw new FileNotFoundException(resource);
            }
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public SpellCheckDictionaryType getDictionaryType() {
        return SpellCheckDictionaryType.MORFOLOGIK;
    }

    @Override
    public void close() {
        // Nothing was opened when no dictionary matched.
        closeQuietly(infoInputStream);
        closeQuietly(dictInputStream);
    }

    private static void closeQuietly(final @Nullable InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
