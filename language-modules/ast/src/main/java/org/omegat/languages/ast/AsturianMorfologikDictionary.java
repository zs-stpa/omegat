/*
 *  OmegaT - Computer Assisted Translation (CAT) tool
 *           with fuzzy matching, translation memory, keyword search,
 *           glossaries, and translation leveraging into updated projects.
 *
 *  Copyright (C) 2023-2024 Hiroshi Miura
 *                Home page: https://www.omegat.org/
 *                Support center: https://omegat.org/support
 *
 *  This file is part of OmegaT.
 *
 *  OmegaT is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  OmegaT is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.omegat.languages.ast;

import java.io.InputStream;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.languagetool.JLanguageTool;

import org.omegat.core.spellchecker.AbstractMorfologikDictionary;

/**
 * Morfologik spelling dictionary of the module, served through the shared
 * base class so sizes, counts and extraction work like for every module.
 */
@NullMarked
public class AsturianMorfologikDictionary extends AbstractMorfologikDictionary {
    private static final String DICTIONARY_BASE = "/org/languagetool/resource/ast/hunspell/";
    private static final String[] DICTIONARIES = { "ast_ES" };

    @Override
    protected String[] getDictionaries() {
        return DICTIONARIES;
    }

    @Override
    protected @Nullable InputStream getResourceAsStream(String resource) {
        return JLanguageTool.getDataBroker().getAsStream(DICTIONARY_BASE + resource);
    }
}
