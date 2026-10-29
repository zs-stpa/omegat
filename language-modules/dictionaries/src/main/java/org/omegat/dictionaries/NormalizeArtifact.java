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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Build entry point: normalises every pair of one artifact.
 * Arguments: {@code <downloadDir> <normalizedDir> <stem,stem,...>}; the
 * download directory holds one folder per stem with {@code <stem>.aff} and
 * {@code <stem>.dic}, the output is flat.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class NormalizeArtifact {

    private NormalizeArtifact() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: NormalizeArtifact downloadDir normalizedDir stem[,stem...]");
            System.exit(2);
        }
        Path download = Path.of(args[0]);
        Path normalized = Path.of(args[1]);
        for (String stem : args[2].split(",")) {
            Path in = download.resolve(stem);
            HunspellNormalizer.Result result = HunspellNormalizer.convert(in.resolve(stem + ".aff"),
                    in.resolve(stem + ".dic"), normalized.resolve(stem + ".aff"), normalized.resolve(stem + ".dic"));
            System.out.println(stem + ": " + result);
        }
    }
}
