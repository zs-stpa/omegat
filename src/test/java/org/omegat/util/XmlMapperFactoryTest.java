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

package org.omegat.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.Test;

import gen.core.filters.Filters;

/**
 * Contract of the mapper used for OmegaT's own configuration files.
 *
 * @author Stephan Pakebusch (stephan.pakebusch at zollsoft.de)
 */
public class XmlMapperFactoryTest {

    private final XmlMapper mapper = XmlMapperFactory.createConfigMapper();

    @Test
    public void skipsUnknownElementsAndAttributes() throws Exception {
        String xml = "<filters removeTags='false' future='x'>" //
                + "<futureBlock attr='1'><nested><deeper/></nested></futureBlock>" //
                + "<filter className='a.B' enabled='true'><futureChild/>" //
                + "<files sourceFilenameMask='*.a' future='y'/></filter>" //
                + "</filters>";

        Filters filters = mapper.readValue(xml, Filters.class);

        assertFalse(filters.isRemoveTags());
        assertEquals(1, filters.getFilters().size());
        assertEquals("*.a", filters.getFilters().get(0).getFiles().get(0).getSourceFilenameMask());
    }

    @Test
    public void doesNotExpandDeclaredEntities() {
        String xml = "<!DOCTYPE filters [<!ENTITY x 'expanded'>]>" //
                + "<filters><filter className='&x;' enabled='true'/></filters>";

        try {
            Filters filters = mapper.readValue(xml, Filters.class);
            assertNotEquals("expanded", filters.getFilters().get(0).getClassName());
        } catch (Exception rejected) {
            // refusing the document is fine as well
        }
    }

    @Test
    public void writesDeclarationAndIndents() throws Exception {
        Filters filters = new Filters();
        filters.setRemoveTags(false);

        String xml = mapper.writeValueAsString(filters);

        assertTrue(xml, xml.startsWith("<?xml version='1.0' encoding='UTF-8'?>"));
        assertTrue(xml, xml.contains("\n"));
    }
}
