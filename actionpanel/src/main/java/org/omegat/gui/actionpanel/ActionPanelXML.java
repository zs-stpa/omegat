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

package org.omegat.gui.actionpanel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jspecify.annotations.Nullable;

import org.omegat.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads and writes the panel configuration as a small XML file. Follows the
 * ExternalFinder persistence conventions: hardened parser, atomic write via a
 * temporary file. The parser keeps unknown action types and ignores unknown
 * elements and attributes, so files written by newer versions survive a
 * load/save round trip here.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class ActionPanelXML {

    static final String ROOT_ELEMENT = "actionpanel";
    static final String ROW_ELEMENT = "row";
    static final String ACTION_ELEMENT = "action";
    static final String VERSION_ATTRIBUTE = "version";
    static final String ID_ATTRIBUTE = "id";
    static final String NAME_ATTRIBUTE = "name";
    static final String ICON_ATTRIBUTE = "icon";
    static final String TYPE_ATTRIBUTE = "type";
    static final String TEXT_COLOR_ATTRIBUTE = "textColor";
    static final String BACKGROUND_COLOR_ATTRIBUTE = "backgroundColor";
    static final String BORDER_COLOR_ATTRIBUTE = "borderColor";
    static final int FORMAT_VERSION = 1;

    private ActionPanelXML() {
    }

    /** Rows of a file plus whether any of them lacked an id there. */
    public record ReadResult(List<ActionRow> rows, boolean idsAdded) {
    }

    public static List<ActionRow> read(File file) throws IOException {
        return readWithReport(file).rows();
    }

    /**
     * Read the rows; a row without an id (a file from before ids existed, or
     * a hand-written one) gets a fresh id, and the result says so, so the
     * caller can persist the ids once instead of minting new ones per start.
     */
    public static ReadResult readWithReport(File file) throws IOException {
        try {
            DocumentBuilder builder = createDocumentBuilder();
            Document doc = builder.parse(file);
            Element root = doc.getDocumentElement();
            if (!ROOT_ELEMENT.equals(root.getTagName())) {
                throw new IOException("Unexpected root element: " + root.getTagName());
            }
            try {
                int version = Integer.parseInt(root.getAttribute(VERSION_ATTRIBUTE));
                if (version > FORMAT_VERSION) {
                    Log.log("Action panel configuration " + file + " has format version " + version
                            + "; this version understands " + FORMAT_VERSION
                            + ". Unknown content is preserved.");
                }
            } catch (NumberFormatException ignored) {
                // Missing or malformed version: treat as current.
            }
            List<ActionRow> rows = new ArrayList<>();
            boolean idsAdded = false;
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && ROW_ELEMENT.equals(node.getNodeName())) {
                    Element rowElement = (Element) node;
                    ActionRow row = readRow(rowElement);
                    idsAdded |= !row.id().equals(attr(rowElement, ID_ATTRIBUTE));
                    rows.add(row);
                }
            }
            return new ReadResult(rows, idsAdded);
        } catch (ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new IOException(e);
        }
    }

    private static ActionRow readRow(Element rowElement) {
        String id = attr(rowElement, ID_ATTRIBUTE);
        if (id == null || id.isBlank()) {
            id = ActionRow.newId();
        }
        String name = rowElement.getAttribute(NAME_ATTRIBUTE);
        String icon = rowElement.hasAttribute(ICON_ATTRIBUTE) ? rowElement.getAttribute(ICON_ATTRIBUTE)
                : null;
        String textColor = attr(rowElement, TEXT_COLOR_ATTRIBUTE);
        String backgroundColor = attr(rowElement, BACKGROUND_COLOR_ATTRIBUTE);
        String borderColor = attr(rowElement, BORDER_COLOR_ATTRIBUTE);
        ActionSpec spec = null;
        NodeList children = rowElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && ACTION_ELEMENT.equals(node.getNodeName())) {
                Element actionElement = (Element) node;
                String type = actionElement.getAttribute(TYPE_ATTRIBUTE);
                Map<String, String> attrs = new LinkedHashMap<>();
                NamedNodeMap attributes = actionElement.getAttributes();
                for (int a = 0; a < attributes.getLength(); a++) {
                    Node attr = attributes.item(a);
                    if (!TYPE_ATTRIBUTE.equals(attr.getNodeName())) {
                        attrs.put(attr.getNodeName(), attr.getNodeValue());
                    }
                }
                spec = ActionSpec.of(type, attrs);
                break;
            }
        }
        return new ActionRow(id, name, icon, spec, textColor, backgroundColor, borderColor);
    }

    private static @Nullable String attr(Element element, String attribute) {
        return element.hasAttribute(attribute) ? element.getAttribute(attribute) : null;
    }

    /**
     * Write the rows atomically: to a temporary file first, then move over
     * the target so a crash never leaves a half-written configuration.
     */
    public static void write(List<ActionRow> rows, File file) throws IOException {
        try {
            DocumentBuilder builder = createDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement(ROOT_ELEMENT);
            root.setAttribute(VERSION_ATTRIBUTE, Integer.toString(FORMAT_VERSION));
            doc.appendChild(root);
            for (ActionRow row : rows) {
                Element rowElement = doc.createElement(ROW_ELEMENT);
                rowElement.setAttribute(ID_ATTRIBUTE, row.id());
                rowElement.setAttribute(NAME_ATTRIBUTE, row.name());
                if (row.iconRef() != null) {
                    rowElement.setAttribute(ICON_ATTRIBUTE, row.iconRef());
                }
                if (row.textColor() != null) {
                    rowElement.setAttribute(TEXT_COLOR_ATTRIBUTE, row.textColor());
                }
                if (row.backgroundColor() != null) {
                    rowElement.setAttribute(BACKGROUND_COLOR_ATTRIBUTE, row.backgroundColor());
                }
                if (row.borderColor() != null) {
                    rowElement.setAttribute(BORDER_COLOR_ATTRIBUTE, row.borderColor());
                }
                ActionSpec spec = row.action();
                if (spec != null) {
                    Element actionElement = doc.createElement(ACTION_ELEMENT);
                    actionElement.setAttribute(TYPE_ATTRIBUTE, spec.type());
                    for (Map.Entry<String, String> en : spec.attributes().entrySet()) {
                        actionElement.setAttribute(en.getKey(), en.getValue());
                    }
                    rowElement.appendChild(actionElement);
                }
                root.appendChild(rowElement);
            }

            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Cannot create directory " + dir);
            }
            File temp = File.createTempFile(file.getName(), ".tmp", dir);
            try {
                TransformerFactory factory = TransformerFactory.newInstance();
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                Transformer transformer = factory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(new DOMSource(doc), new StreamResult(temp));
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        } catch (ParserConfigurationException | javax.xml.transform.TransformerException e) {
            throw new IOException(e);
        }
    }

    private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder();
    }
}
