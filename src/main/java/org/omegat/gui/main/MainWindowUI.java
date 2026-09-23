/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2000-2006 Keith Godfrey, Maxym Mykhalchuk, Henry Pijffers,
               2000-2006 Benjamin Siband, and Kim Bruning
               2007 Zoltan Bartko
               2008 Andrzej Sawula, Alex Buloichik
               2014 Piotr Kulik
               2015 Aaron Madlon-Kay
               2023 Jean-Christophe Helary
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

package org.omegat.gui.main;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.text.JTextComponent;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.core.events.IProjectEventListener;
import org.omegat.gui.editor.EditorUtils;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.OStrings;
import org.omegat.util.StaticUtils;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.UIDesignManager;

import com.vlsolutions.swing.docking.DockingContext;

/**
 * Class for initialize, load/save, etc. for main window UI components.
 *
 * @author Keith Godfrey
 * @author Benjamin Siband
 * @author Maxym Mykhalchuk
 * @author Kim Bruning
 * @author Henry Pijffers (henry.pijffers@saxnot.com)
 * @author Zoltan Bartko - bartkozoltan@bartkozoltan.com
 * @author Andrzej Sawula
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Piotr Kulik
 * @author Aaron Madlon-Kay
 */
public final class MainWindowUI {

    private MainWindowUI() {
    }

    public static final String UI_LAYOUT_FILE = "uiLayout" + OStrings.getBrandingToken() + ".xml";

    /**
     * Installs a {@link IProjectEventListener} that handles loading, storing,
     * and restoring the main window layout when a project-specific layout is
     * present.
     */
    public static void handlePerProjectLayouts(final MainWindow mainWindow) {
        PerProjectLayoutHandler handler = new PerProjectLayoutHandler(mainWindow);
        CoreEvents.registerProjectChangeListener(handler);
        CoreEvents.registerApplicationEventListener(handler);
    }

    private static class PerProjectLayoutHandler implements IProjectEventListener, IApplicationEventListener {

        private final MainWindow mainWindow;
        private boolean didApplyPerProjectLayout = false;

        PerProjectLayoutHandler(MainWindow mainWindow) {
            this.mainWindow = mainWindow;
        }

        @Override
        public void onApplicationStartup() {
        }

        @Override
        public void onApplicationShutdown() {
            // Project is not closed before shutdown, so we need to handle this
            // separately
            // from the onProjectChanged events.
            if (Core.getProject().isProjectLoaded() && didApplyPerProjectLayout) {
                loadScreenLayoutFromPreferences(mainWindow);
                didApplyPerProjectLayout = false;
            }
        }

        @Override
        public void onProjectChanged(PROJECT_CHANGE_TYPE eventType) {
            if (eventType == PROJECT_CHANGE_TYPE.CLOSE && didApplyPerProjectLayout) {
                loadScreenLayoutFromPreferences(mainWindow);
                didApplyPerProjectLayout = false;
                return;
            }
            if (!Core.getProject().isProjectLoaded()) {
                return;
            }
            File perProjLayout = getPerProjectLayout();
            if (!perProjLayout.isFile()) {
                return;
            }
            switch (eventType) {
            case LOAD:
                saveScreenLayout(mainWindow);
                loadScreenLayout(mainWindow, perProjLayout);
                didApplyPerProjectLayout = true;
                break;
            case SAVE:
                saveScreenLayout(mainWindow, perProjLayout);
                break;
            default:
            }
        }

        private File getPerProjectLayout() {
            return new File(Core.getProject().getProjectProperties().getProjectInternal(),
                    MainWindowUI.UI_LAYOUT_FILE);
        }
    }

    /**
     * Initialize the size of OmegaT window, then load the layout prefs.
     */
    public static void initializeScreenLayout(IMainWindow mainWindow) {
        /**
         * (23dec22) Set a reasonable default window size assuming a
         * standard"pro" laptop resolution of 1920x1080. Smaller screens do not
         * need to be considered since OmegaT will just use the whole window
         * size in such cases.
         */

        // Check the real available space accounting for macOS DOCK, Windows
        // Toolbar, etc.
        Rectangle localAvailableSpace = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int screenWidth = localAvailableSpace.width;
        int screenHeight = localAvailableSpace.height;
        int omegatWidth = OConsts.OMEGAT_WINDOW_WIDTH;
        int omegatHeight = OConsts.OMEGAT_WINDOW_HEIGHT;

        if (omegatWidth > screenWidth) {
            omegatWidth = screenWidth;
        }

        if (omegatHeight > screenHeight) {
            omegatHeight = screenHeight;
        }

        // Attempt to center the OmegaT main window on the screen
        int omegatLeftPosition = (screenWidth - omegatWidth) / 2;

        Rectangle defaultWindowSize = new Rectangle(omegatLeftPosition, 0, omegatWidth, omegatHeight);

        mainWindow.getApplicationFrame().setBounds(defaultWindowSize);
        loadScreenLayoutFromPreferences(mainWindow);

        // Ensure any "closed" Dockables are visible. These can be newly added
        // panes not included in an older layout file, or e.g. panes installed
        // by plugins.
        UIDesignManager.ensureDockablesVisible(mainWindow.getDesktop());
    }

    /**
     * Load the main window layout from the global preferences file. Will reset
     * to defaults if global preferences are not present or if an error occurs.
     */
    private static void loadScreenLayoutFromPreferences(IMainWindow mainWindow) {
        File uiLayoutFile = new File(StaticUtils.getConfigDir(), MainWindowUI.UI_LAYOUT_FILE);
        if (uiLayoutFile.exists()) {
            loadScreenLayout(mainWindow, uiLayoutFile);
        } else {
            resetDesktopLayout(mainWindow);
        }
    }

    /**
     * Load the main window layout from the specified file. Will reset to
     * defaults if an error occurs.
     */
    private static void loadScreenLayout(IMainWindow mainWindow, File uiLayoutFile) {
        try {
            byte[] layout = Files.readAllBytes(uiLayoutFile.toPath());
            DockingContext context = mainWindow.getDesktop().getContext();
            layout = dropUnknownDockables(layout, key -> context.getDockableByKey(key) != null);
            try (InputStream in = new ByteArrayInputStream(layout)) {
                mainWindow.getDesktop().readXML(in);
            }
        } catch (Exception ex) {
            Log.log(ex);
            resetDesktopLayout(mainWindow);
        }
    }

    /**
     * Removes every dockable the given filter does not know from a stored
     * layout. A layout may reference panes this installation cannot resolve
     * - written by a version with an additional module, or by a newer
     * version - and the restore aborts on the first unknown key, throwing
     * the WHOLE layout away. Dropping just the foreign panes keeps
     * everything else in place; containers are re-collapsed so the tree
     * stays readable (a split needs two children, a tab group at least
     * one).
     */
    static byte[] dropUnknownDockables(byte[] layout, Predicate<String> isKnownKey) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(layout));

        List<Element> foreign = new ArrayList<>();
        NodeList keys = doc.getElementsByTagName("Key");
        for (int i = 0; i < keys.getLength(); i++) {
            Element key = (Element) keys.item(i);
            String name = key.getAttribute("dockName");
            if (!isKnownKey.test(name)) {
                Log.logWarningRB("LOG_LAYOUT_UNKNOWN_DOCKABLE", name);
                foreign.add((Element) key.getParentNode());
            }
        }
        if (foreign.isEmpty()) {
            return layout;
        }
        for (Element dockable : foreign) {
            dockable.getParentNode().removeChild(dockable);
        }
        collapseEmptiedContainers(doc.getDocumentElement());

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    /**
     * Re-collapses containers that lost children: a Split with a single
     * child is replaced by that child, empty Splits, tab groups, borders
     * and floating entries are dropped.
     */
    private static void collapseEmptiedContainers(Element element) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child) {
                children.add(child);
            }
        }
        for (Element child : children) {
            collapseEmptiedContainers(child);
        }
        String name = element.getNodeName();
        long elementChildren = countElementChildren(element);
        if ("Split".equals(name)) {
            if (elementChildren == 1) {
                Node parent = element.getParentNode();
                for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                    if (element.getChildNodes().item(i) instanceof Element child) {
                        parent.replaceChild(child, element);
                        return;
                    }
                }
            } else if (elementChildren == 0) {
                element.getParentNode().removeChild(element);
            }
        } else if (("TabbedDockable".equals(name) || "TabGroup".equals(name) || "TabGroups".equals(name)
                || "Floating".equals(name) || "Border".equals(name)) && elementChildren == 0) {
            element.getParentNode().removeChild(element);
        }
    }

    private static long countElementChildren(Element element) {
        long count = 0;
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }

    /**
     * Stores main window docking layout to disk.
     */
    public static void saveScreenLayout(MainWindow mainWindow) {
        File uiLayoutFile = new File(StaticUtils.getConfigDir(), MainWindowUI.UI_LAYOUT_FILE);
        saveScreenLayout(mainWindow, uiLayoutFile);
    }

    /**
     * Stores main window layout to the specified output file.
     */
    private static void saveScreenLayout(MainWindow mainWindow, File uiLayoutFile) {
        try (OutputStream out = new FileOutputStream(uiLayoutFile)) {
            mainWindow.desktop.writeXML(out);
        } catch (Exception ex) {
            Log.log(ex);
        }
    }

    /**
     * Restores main window layout to the default values (distinct from global
     * preferences).
     */
    public static void resetDesktopLayout(IMainWindow mainWindow) {
        try (InputStream in = MainWindowUI.class.getResourceAsStream("DockingDefaults.xml")) {
            mainWindow.getDesktop().readXML(in);
        } catch (Exception e) {
            Log.log(e);
        }
    }

    public static String getTrimmedSelectedTextInMainWindow(MainWindow mainWindow) {
        String selection = null;
        Component component = mainWindow.getApplicationFrame().getMostRecentFocusOwner();
        if (component instanceof JTextComponent) {
            selection = ((JTextComponent) component).getSelectedText();
            if (!StringUtil.isEmpty(selection)) {
                selection = EditorUtils.removeDirectionChars(selection);
                selection = selection.trim();
            }
        }
        return selection;
    }
}
