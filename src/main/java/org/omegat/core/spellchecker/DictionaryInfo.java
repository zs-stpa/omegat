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

import org.jspecify.annotations.Nullable;

/**
 * Facts about one spelling dictionary for display: licence and upstream
 * of a bundled pair, number of entries and size of its files. Every part
 * may be unknown.
 *
 * @param license
 *            licence identifier, e.g. an SPDX short name
 * @param upstream
 *            URL of the source the dictionary was taken from
 * @param entries
 *            number of entries in the word list
 * @param sizeBytes
 *            total size of the dictionary files
 * @author stephan.pakebusch at zollsoft.de
 */
public record DictionaryInfo(@Nullable String license, @Nullable String upstream, @Nullable Long entries,
        @Nullable Long sizeBytes) {

    /** Nothing is known. */
    public static final DictionaryInfo UNKNOWN = new DictionaryInfo(null, null, null, null);

    /** The same facts with licence and upstream replaced. */
    public DictionaryInfo withOrigin(@Nullable String newLicense, @Nullable String newUpstream) {
        return new DictionaryInfo(newLicense, newUpstream, entries, sizeBytes);
    }
}
