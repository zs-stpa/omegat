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

import javax.xml.stream.XMLInputFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import org.jspecify.annotations.NullMarked;

/**
 * One place for the Jackson XML mapper that reads and writes OmegaT's own
 * configuration files (file filters, segmentation rules).
 *
 * @author Stephan Pakebusch (stephan.pakebusch at zollsoft.de)
 */
@NullMarked
public final class XmlMapperFactory {

    private XmlMapperFactory() {
    }

    /**
     * Creates a mapper for a JAXB-annotated configuration model.
     * <p>
     * The mapper neither resolves DTDs nor external entities and does not
     * expand entity references. Elements and attributes the model does not
     * know are skipped, so a file written by a newer OmegaT stays readable;
     * the unknown parts are not kept on save. Repeated elements must stay
     * contiguous: an unknown element between two entries of the same list
     * ends the first group, and only the second one survives.
     */
    public static XmlMapper createConfigMapper() {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        return XmlMapper.builder(new XmlFactory(xmlInputFactory)).defaultUseWrapper(false)
                .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY,
                        JsonInclude.Include.NON_EMPTY))
                .addModule(new JakartaXmlBindAnnotationModule()).build();
    }
}
