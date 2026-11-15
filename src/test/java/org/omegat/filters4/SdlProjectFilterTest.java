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

package org.omegat.filters4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;
import org.omegat.filters4.xml.xliff.SdlProject;
import org.omegat.filters4.xml.xliff.Xliff1Filter;

/**
 * @author Stephan Pakebusch
 */
public class SdlProjectFilterTest extends org.omegat.filters.TestFilterBase {

    private static final String XLF = "src/test/resources/data/filters/xliff/filters4-xliff1/en-xx.xlf";

    /**
     * Loading a package whose filter defines no entry comparator must parse
     * the internal files although there is no output archive: while loading,
     * the ZipOutputStream is null, and the entry used to be written before it
     * was parsed, so the resulting NPE was silently logged and every package
     * loaded without segments.
     */
    @Test
    public void testParsePackageWithoutEntryComparator() throws Exception {
        // Package layout as SdlProject expects it: internal files prefixed
        // with the target language of the filter context.
        File sdlppx = new File(outFile.getParentFile(), name.getMethodName() + ".sdlppx");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(sdlppx))) {
            zip.putNextEntry(new ZipEntry(context.getTargetLang().getLanguage() + "/content.sdlxliff"));
            zip.write(Files.readAllBytes(Paths.get(XLF)));
            zip.closeEntry();
        }

        List<String> expected = parse(new Xliff1Filter(), XLF);
        assertFalse(expected.isEmpty());
        List<String> entries = parse(new SdlProject(), sdlppx.getPath());
        assertEquals(expected, entries);
    }
}
