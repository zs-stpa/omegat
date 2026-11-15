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

package org.omegat.dictionaries;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Loads a Hunspell pair with the Lucene parser OmegaT uses and spells the
 * remaining arguments; the inventory scripts and the verifyDictionaries task
 * run it. Prints one line per pair: {@code OK <ms> <word=ok|bad ...>} or
 * {@code FAIL <reason>}. With several stems (no words) each is checked in
 * turn and the exit code is the number of failures.
 * The hunspell command line tolerates defects (wrong affix counts) that
 * this parser rejects, so this is the check that counts for bundling.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class LuceneHunspellCheck {

    private LuceneHunspellCheck() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length > 1 && Files.exists(Path.of(args[0] + ".aff"))
                && Files.exists(Path.of(args[1] + ".aff"))) {
            int failures = 0;
            for (String stem : args) {
                if (!check(stem, new String[0])) {
                    failures++;
                }
            }
            System.exit(failures);
        }
        if (!check(args[0], Arrays.copyOfRange(args, 1, args.length))) {
            System.exit(1);
        }
    }

    private static boolean check(final String stem, final String[] words) throws Exception {
        Path tmp = Files.createTempDirectory("lucene-hunspell");
        try {
            return check(stem, words, tmp);
        } finally {
            try (Stream<Path> tree = Files.walk(tmp)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean check(final String stem, final String[] words, final Path tmp) throws Exception {
        long start = System.nanoTime();
        Dictionary dictionary;
        try (InputStream aff = Files.newInputStream(Path.of(stem + ".aff"));
                InputStream dic = Files.newInputStream(Path.of(stem + ".dic"))) {
            dictionary = new Dictionary(new NIOFSDirectory(tmp), "chk", aff, List.of(dic), true);
        } catch (Exception ex) {
            System.out.println(stem + ": FAIL " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        Hunspell speller = new Hunspell(dictionary);
        StringBuilder out = new StringBuilder(stem).append(": OK ").append(millis);
        for (String word : words) {
            out.append(' ').append(word).append('=').append(speller.spell(word) ? "ok" : "bad");
        }
        System.out.println(out);
        return true;
    }
}
