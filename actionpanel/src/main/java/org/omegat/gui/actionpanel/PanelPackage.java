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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.Nullable;

import org.omegat.externalfinder.ExternalFinder;
import org.omegat.externalfinder.item.ExternalFinderConfiguration;
import org.omegat.externalfinder.item.ExternalFinderItem;
import org.omegat.externalfinder.item.ExternalFinderXMLLoader;
import org.omegat.externalfinder.item.ExternalFinderXMLWriter;
import org.omegat.gui.actionpanel.ActionSpec.AutotextRefActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ColorSchemeActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ExternalSearchActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ScriptActionSpec;
import org.omegat.gui.actionpanel.ActionSpec.ShortcutSetActionSpec;
import org.omegat.util.StaticUtils;

/**
 * A panel configuration together with the things its rows refer to: external
 * search sets, scripts, colour schemes, shortcut sets and icons. Exported as
 * one package file, so an import on another machine finds what the rows
 * point at. Autotext references are reported but never packed: autotext is
 * the user's own dictionary.
 * <p>
 * The package is a zip archive with {@code actionpanel.xml} at the root and
 * one folder per resource kind; a plain {@code .xml} export is still read.
 * Packed rows refer to resources by the packed name, so a script that lived
 * at an absolute path on the exporting machine is found in the scripts
 * folder of the importing one.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class PanelPackage {

    public static final String EXTENSION = "omtpanel";
    static final String ROWS_ENTRY = "actionpanel.xml";
    static final String SEARCH_SETS_ENTRY = "finder.xml";

    /** What a row can refer to outside the configuration file. */
    public enum Kind {
        EXTERNAL_SEARCH("RESOURCE_EXTERNAL_SEARCH", null),
        SCRIPT("RESOURCE_SCRIPT", "scripts/"),
        COLOR_SCHEME("RESOURCE_COLOR_SCHEME", "colorschemes/"),
        SHORTCUT_SET("RESOURCE_SHORTCUT_SET", "shortcutsets/"),
        ICON("RESOURCE_ICON", "icons/"),
        AUTOTEXT("RESOURCE_AUTOTEXT", null);

        private final String labelKey;
        private final @Nullable String folder;

        Kind(String labelKey, @Nullable String folder) {
            this.labelKey = labelKey;
            this.folder = folder;
        }

        public String label() {
            return ActionPanelModule.getString(labelKey);
        }

        /** Zip folder of this kind, or null for kinds stored otherwise. */
        @Nullable
        String folder() {
            return folder;
        }
    }

    /**
     * One thing the rows refer to. {@code file} is where it lives locally
     * (null for search sets and autotext); {@code name} is the name it is
     * packed under, unique per kind; {@code shipped} marks a script of the
     * OmegaT installation, which an export leaves out by default.
     */
    public record Resource(Kind kind, String name, @Nullable File file, boolean shipped) {
        public boolean packable() {
            return kind != Kind.AUTOTEXT && (kind == Kind.EXTERNAL_SEARCH || file != null);
        }

        /** Whether the thing exists locally, for the export and import tables. */
        public boolean present() {
            if (kind == Kind.EXTERNAL_SEARCH) {
                return ActionInvoker.findExternalSearch(name) != null;
            }
            if (kind == Kind.AUTOTEXT) {
                return ActionInvoker.findAutotextItem(name) != null;
            }
            return file != null && file.isFile();
        }
    }

    /** A resource carried by a package, with its bytes. */
    public record Entry(Kind kind, String name, byte[] data) {
    }

    /** What a package holds. */
    public record Contents(List<ActionRow> rows, List<Entry> entries, List<Resource> unresolved) {
    }

    /** The user's choice per imported entry. */
    public enum Decision {
        IMPORT, KEEP_EXISTING, SKIP
    }

    private PanelPackage() {
    }

    /** The resources the rows refer to, each file once, in row order. */
    public static List<Resource> referencedResources(List<ActionRow> rows) {
        return referencedResources(rows, new File(StaticUtils.installDir(), "scripts"));
    }

    static List<Resource> referencedResources(List<ActionRow> rows, File shippedScripts) {
        return new ArrayList<>(new Resources(shippedScripts).collect(rows).values());
    }

    /**
     * Collects resources keyed by what they resolve to, so two rows pointing
     * at the same file share one entry while two different files of the same
     * name get distinct packed names.
     */
    private static final class Resources {
        private final File shippedScripts;
        private final Map<String, Resource> byTarget = new LinkedHashMap<>();
        private final Set<String> packedNames = new HashSet<>();

        Resources(File shippedScripts) {
            this.shippedScripts = shippedScripts;
        }

        Map<String, Resource> collect(List<ActionRow> rows) {
            for (ActionRow row : rows) {
                ActionSpec spec = row.action();
                if (spec instanceof ExternalSearchActionSpec search) {
                    add(Kind.EXTERNAL_SEARCH, search.name(), null, false);
                } else if (spec instanceof ScriptActionSpec script) {
                    File file = scriptFile(script.fileName());
                    boolean inScriptsFolder = ActionPanelFolders.getScriptsFolder().equals(file.getParentFile());
                    add(Kind.SCRIPT, file.getName(), file,
                            inScriptsFolder && new File(shippedScripts, file.getName()).isFile());
                } else if (spec instanceof ColorSchemeActionSpec scheme) {
                    File file = ActionPanelFolders.resolveColorScheme(scheme.ref());
                    add(Kind.COLOR_SCHEME, file.getName(), file, false);
                } else if (spec instanceof ShortcutSetActionSpec set) {
                    File file = ActionPanelFolders.resolveShortcutSet(set.ref());
                    add(Kind.SHORTCUT_SET, file.getName(), file, false);
                } else if (spec instanceof AutotextRefActionSpec ref) {
                    add(Kind.AUTOTEXT, ref.source(), null, false);
                }
                if (row.iconRef() != null) {
                    File file = ActionPanelConfig.resolveIcon(row.iconRef());
                    add(Kind.ICON, file.getName(), file, false);
                }
            }
            return byTarget;
        }

        private void add(Kind kind, String name, @Nullable File file, boolean shipped) {
            String key = targetKey(kind, name, file);
            if (byTarget.containsKey(key)) {
                return;
            }
            String packedName = name;
            for (int n = 2; !packedNames.add(kind + "\n" + packedName); n++) {
                int dot = name.lastIndexOf('.');
                packedName = dot > 0 ? name.substring(0, dot) + "-" + n + name.substring(dot) : name + "-" + n;
            }
            byTarget.put(key, new Resource(kind, packedName, file, shipped));
        }
    }

    private static String targetKey(Kind kind, String name, @Nullable File file) {
        return kind + "\n" + (file == null ? name : file.getAbsolutePath());
    }

    private static File scriptFile(String fileName) {
        File file = new File(fileName);
        return file.isAbsolute() ? file : new File(ActionPanelFolders.getScriptsFolder(), fileName);
    }

    /**
     * Write the package: the rows, their references rewritten to the packed
     * names of the chosen resources, plus those resources. The file is
     * replaced only once the package is complete.
     */
    public static void write(File target, List<ActionRow> rows, List<Resource> included) throws IOException {
        Map<String, String> packedNames = new LinkedHashMap<>();
        for (Resource resource : included) {
            packedNames.put(targetKey(resource.kind(), resource.name(), resource.file()), resource.name());
        }
        List<ActionRow> exported = new ArrayList<>();
        for (ActionRow row : rows) {
            exported.add(rewrite(row, packedNames));
        }
        File rowsFile = File.createTempFile("actionpanel", ".xml");
        File temp = File.createTempFile(target.getName(), ".tmp", target.getAbsoluteFile().getParentFile());
        try {
            ActionPanelXML.write(exported, rowsFile);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp.toPath()))) {
                putEntry(zip, ROWS_ENTRY, Files.readAllBytes(rowsFile.toPath()));
                List<ExternalFinderItem> sets = new ArrayList<>();
                for (Resource resource : included) {
                    if (resource.kind() == Kind.EXTERNAL_SEARCH) {
                        ExternalFinderItem set = ActionInvoker.findExternalSearch(resource.name());
                        if (set != null) {
                            sets.add(set);
                        }
                    } else if (resource.packable() && resource.file() != null && resource.file().isFile()) {
                        putEntry(zip, resource.kind().folder() + resource.name(),
                                Files.readAllBytes(resource.file().toPath()));
                    }
                }
                if (!sets.isEmpty()) {
                    putEntry(zip, SEARCH_SETS_ENTRY, searchSetsXml(sets));
                }
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(rowsFile.toPath());
            Files.deleteIfExists(temp.toPath());
        }
    }

    /** A row whose packed references point at the packed names. */
    private static ActionRow rewrite(ActionRow row, Map<String, String> packedNames) {
        ActionRow result = row;
        ActionSpec spec = row.action();
        if (spec instanceof ScriptActionSpec script) {
            String packed = packedNames.get(targetKey(Kind.SCRIPT, "", scriptFile(script.fileName())));
            if (packed != null) {
                result = result.withAction(new ScriptActionSpec(packed));
            }
        } else if (spec instanceof ColorSchemeActionSpec scheme) {
            String packed = packedNames
                    .get(targetKey(Kind.COLOR_SCHEME, "", ActionPanelFolders.resolveColorScheme(scheme.ref())));
            if (packed != null) {
                result = result.withAction(new ColorSchemeActionSpec(packed));
            }
        } else if (spec instanceof ShortcutSetActionSpec set) {
            String packed = packedNames
                    .get(targetKey(Kind.SHORTCUT_SET, "", ActionPanelFolders.resolveShortcutSet(set.ref())));
            if (packed != null) {
                result = result.withAction(new ShortcutSetActionSpec(packed));
            }
        }
        String iconRef = row.iconRef();
        if (iconRef != null) {
            String packed = packedNames.get(targetKey(Kind.ICON, "", ActionPanelConfig.resolveIcon(iconRef)));
            if (packed != null) {
                result = result.withIconRef(ActionPanelConfig.ICON_FOLDER + "/" + packed);
            }
        }
        return result;
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] searchSetsXml(List<ExternalFinderItem> sets) throws IOException {
        File temp = File.createTempFile("finder", ".xml");
        try {
            new ExternalFinderXMLWriter(temp).write(new ExternalFinderConfiguration(
                    ExternalFinder.getGlobalConfig().getPriority(), sets));
            return Files.readAllBytes(temp.toPath());
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    /** Read a package, or a plain configuration file from an earlier export. */
    public static Contents read(File file) throws IOException {
        if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".xml")) {
            List<ActionRow> rows = ActionPanelXML.read(file);
            return new Contents(rows, List.of(), unresolvedReferences(rows, List.of()));
        }
        List<ActionRow> rows = null;
        List<Entry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file.toPath()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !isSafeName(name)) {
                    continue;
                }
                byte[] data = zip.readAllBytes();
                if (ROWS_ENTRY.equals(name)) {
                    rows = readRows(data);
                } else if (SEARCH_SETS_ENTRY.equals(name)) {
                    for (ExternalFinderItem set : readSearchSets(data)) {
                        entries.add(new Entry(Kind.EXTERNAL_SEARCH, set.getName(), searchSetsXml(List.of(set))));
                    }
                } else {
                    Kind kind = kindOf(name);
                    if (kind != null) {
                        entries.add(new Entry(kind, name.substring(kind.folder().length()), data));
                    }
                }
            }
        }
        if (rows == null) {
            throw new IOException(ActionPanelModule.getString("IMPORT_NO_ROWS"));
        }
        return new Contents(rows, entries, unresolvedReferences(rows, entries));
    }

    /** Only plain names directly inside a kind's folder count; nested paths are ignored. */
    private static @Nullable Kind kindOf(String entryName) {
        for (Kind kind : Kind.values()) {
            String folder = kind.folder();
            if (folder != null && entryName.startsWith(folder) && entryName.length() > folder.length()
                    && entryName.indexOf('/', folder.length()) < 0) {
                return kind;
            }
        }
        return null;
    }

    /** No absolute paths and no parent-directory segments leave the package folder. */
    static boolean isSafeName(String entryName) {
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            return false;
        }
        for (String segment : entryName.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static List<ActionRow> readRows(byte[] data) throws IOException {
        File temp = File.createTempFile("actionpanel", ".xml");
        try {
            Files.write(temp.toPath(), data);
            return ActionPanelXML.read(temp);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private static List<ExternalFinderItem> readSearchSets(byte[] data) throws IOException {
        File temp = File.createTempFile("finder", ".xml");
        try {
            Files.write(temp.toPath(), data);
            return new ExternalFinderXMLLoader(temp, ExternalFinderItem.SCOPE.GLOBAL).load().getItems();
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    /** References the package does not carry and that are absent here, for the import overview. */
    static List<Resource> unresolvedReferences(List<ActionRow> rows, List<Entry> entries) {
        List<Resource> unresolved = new ArrayList<>();
        for (Resource resource : referencedResources(rows)) {
            boolean carried = entries.stream()
                    .anyMatch(e -> e.kind() == resource.kind() && e.name().equals(resource.name()));
            if (!carried && !resource.present()) {
                unresolved.add(resource);
            }
        }
        return unresolved;
    }

    /** Whether an entry already exists locally, which the import table shows. */
    public static boolean exists(Entry entry) {
        File destination = destination(entry);
        return destination != null ? destination.isFile() : ActionInvoker.findExternalSearch(entry.name()) != null;
    }

    private static @Nullable File destination(Entry entry) {
        return switch (entry.kind()) {
        case SCRIPT -> new File(ActionPanelFolders.getScriptsFolder(), entry.name());
        case COLOR_SCHEME -> new File(ActionPanelFolders.getColorSchemeFolder(), entry.name());
        case SHORTCUT_SET -> new File(ActionPanelFolders.getShortcutSetFolder(), entry.name());
        case ICON -> new File(ActionPanelConfig.getIconFolder(), entry.name());
        default -> null;
        };
    }

    /** Put the chosen entries in place; search sets go into the global configuration. */
    public static void install(Map<Entry, Decision> decisions) throws IOException {
        List<ExternalFinderItem> newSets = new ArrayList<>();
        for (Map.Entry<Entry, Decision> decision : decisions.entrySet()) {
            if (decision.getValue() != Decision.IMPORT) {
                continue;
            }
            Entry entry = decision.getKey();
            File destination = destination(entry);
            if (destination != null) {
                Files.createDirectories(destination.toPath().getParent());
                try (InputStream in = new ByteArrayInputStream(entry.data())) {
                    Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } else if (entry.kind() == Kind.EXTERNAL_SEARCH) {
                newSets.addAll(readSearchSets(entry.data()));
            }
        }
        if (!newSets.isEmpty()) {
            ExternalFinderConfiguration global = ExternalFinder.getGlobalConfig();
            Map<String, ExternalFinderItem> byName = new LinkedHashMap<>();
            for (ExternalFinderItem set : global.getItems()) {
                byName.put(set.getName(), set);
            }
            for (ExternalFinderItem set : newSets) {
                byName.put(set.getName(), set);
            }
            ExternalFinder.setGlobalConfig(
                    new ExternalFinderConfiguration(global.getPriority(), new ArrayList<>(byName.values())));
        }
    }
}
