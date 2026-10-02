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

package org.omegat.externalfinder.item;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;

import org.omegat.core.Core;
import org.omegat.core.data.IProject;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.data.TMXEntry;
import org.omegat.util.OStrings;

/**
 * URL or command template of an external search, with its placeholders.
 * <p>
 * A placeholder is written as <code>{name}</code>, <code>{name:argument}</code>,
 * <code>{name:{regex}}</code> or <code>{name:argument:{regex}}</code>. The
 * names are listed in {@link #NAMES}: {@value #TARGET} is the selected editor
 * text, {@value #PROP} takes a property key as argument, all others describe
 * the current segment and its translation.
 * <p>
 * A regex narrows the value: the first match is used, group 1 if the regex
 * has groups, otherwise the whole match; no match yields an empty value.
 * Braces inside the regex must be balanced or escaped with a backslash.
 * <p>
 * Templates are parsed when validated and when run, so a stored template
 * that stopped parsing fails with the same message the editor dialog shows.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class PlaceholderTemplate {

    public static final String TARGET = "target";
    public static final String SOURCE = "source";
    public static final String TRANSLATION = "translation";
    public static final String COMMENT = "comment";
    public static final String NOTE = "note";
    public static final String PROP = "prop";
    public static final String FILE = "file";
    public static final String PATH = "path";
    public static final String ID = "id";
    public static final String NUMBER = "number";
    public static final String STATUS = "status";
    public static final String LINK = "link";
    public static final String ORIGIN = "origin";
    public static final String AUTHOR = "author";
    public static final String DATE = "date";
    public static final String CREATOR = "creator";
    public static final String CREATED = "created";
    public static final String DUPLICATE = "duplicate";
    public static final String DUPLICATES = "duplicates";
    public static final String ALTERNATIVE = "alternative";
    public static final String PARAGRAPH = "paragraph";

    /** Placeholder names in the order the editor dialog offers them. */
    public static final List<String> NAMES = List.of(TARGET, SOURCE, TRANSLATION, COMMENT, NOTE, PROP, FILE,
            PATH, ID, NUMBER, STATUS, LINK, ORIGIN, AUTHOR, DATE, CREATOR, CREATED, DUPLICATE, DUPLICATES,
            ALTERNATIVE, PARAGRAPH);

    /** Values the placeholders of one run draw from; missing values resolve to empty text. */
    public static final class Context {
        private final @Nullable String selection;
        private final Map<String, @Nullable String> values;
        private final String @Nullable [] properties;

        private Context(@Nullable String selection, Map<String, @Nullable String> values,
                String @Nullable [] properties) {
            this.selection = selection;
            this.values = values;
            this.properties = properties;
        }

        /**
         * Context of a run: the editor selection, the current segment and its
         * translation record from the project; each may be absent.
         */
        public static Context of(@Nullable String selection, @Nullable SourceTextEntry entry,
                @Nullable TMXEntry translation) {
            Map<String, @Nullable String> values = new LinkedHashMap<>();
            if (entry != null) {
                values.put(SOURCE, entry.getSrcText());
                values.put(COMMENT, entry.getComment());
                values.put(FILE, entry.getKey().file);
                values.put(PATH, entry.getKey().path);
                values.put(ID, entry.getKey().id);
                values.put(NUMBER, Integer.toString(entry.entryNum()));
                values.put(DUPLICATE, entry.getDuplicate().name().toLowerCase(Locale.ENGLISH));
                values.put(DUPLICATES, Integer.toString(entry.getNumberOfDuplicates()));
                values.put(PARAGRAPH, Boolean.toString(entry.isParagraphStart()));
            }
            if (translation != null) {
                values.put(TRANSLATION, translation.isTranslated() ? translation.translation : "");
                values.put(NOTE, translation.note);
                values.put(STATUS, translation.isTranslated() ? "translated" : "untranslated");
                values.put(LINK, translation.linked == null ? ""
                        : translation.linked.name().substring(1).toLowerCase(Locale.ENGLISH));
                values.put(ORIGIN, translation.origin);
                values.put(AUTHOR, translation.changer);
                values.put(DATE, isoDate(translation.changeDate));
                values.put(CREATOR, translation.creator);
                values.put(CREATED, isoDate(translation.creationDate));
                values.put(ALTERNATIVE, Boolean.toString(!translation.defaultTranslation));
            }
            return new Context(selection, values, entry == null ? null : entry.getRawProperties());
        }

        /** Context of the editor's current state: selection, current segment and its translation. */
        public static Context ofEditor() {
            String selection = Core.getEditor().getSelectedText();
            SourceTextEntry entry = Core.getEditor().getCurrentEntry();
            IProject project = Core.getProject();
            TMXEntry translation = entry != null && project.isProjectLoaded() ? project.getTranslationInfo(entry)
                    : null;
            return of(selection, entry, translation);
        }

        /** Context with a selection only, for callers that have no segment. */
        public static Context ofSelection(String selection) {
            return new Context(selection, Collections.emptyMap(), null);
        }

        /** Illustrative values for the sample output of the editor dialog. */
        public static Context sample(boolean nonAscii) {
            Map<String, @Nullable String> values = new LinkedHashMap<>();
            values.put(SOURCE, "Sample source");
            values.put(TRANSLATION, "Sample translation");
            values.put(COMMENT, "Sample comment");
            values.put(NOTE, "Sample note");
            // The file carries a directory so the sample shows how slashes encode.
            values.put(FILE, "dir/sample.txt");
            values.put(PATH, "/sample/path");
            values.put(ID, "sample-id");
            values.put(NUMBER, "42");
            values.put(STATUS, "translated");
            values.put(LINK, "ice");
            values.put(ORIGIN, "sample.tmx");
            values.put(AUTHOR, "translator");
            values.put(DATE, "2026-01-02T03:04:05Z");
            values.put(CREATOR, "creator");
            values.put(CREATED, "2026-01-01T00:00:00Z");
            values.put(DUPLICATE, "none");
            values.put(DUPLICATES, "0");
            values.put(ALTERNATIVE, "false");
            values.put(PARAGRAPH, "true");
            String selection = nonAscii ? "føø bår" : "foo bar";
            return new Context(selection, values, new String[] { "note", "Sample comment" });
        }

        private static String isoDate(long epochMillis) {
            return epochMillis == 0 ? "" : Instant.ofEpochMilli(epochMillis).toString();
        }

        public @Nullable String getSelection() {
            return selection;
        }

        @Nullable
        String valueOf(Placeholder placeholder) {
            if (TARGET.equals(placeholder.name)) {
                return selection;
            }
            if (PROP.equals(placeholder.name)) {
                return property(placeholder.argument);
            }
            return values.get(placeholder.name);
        }

        private @Nullable String property(@Nullable String key) {
            if (properties == null || key == null) {
                return null;
            }
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i + 1 < properties.length; i += 2) {
                if (key.equals(properties[i])) {
                    if (joined.length() > 0) {
                        joined.append('\n');
                    }
                    joined.append(properties[i + 1]);
                }
            }
            return joined.length() == 0 ? null : joined.toString();
        }
    }

    /** One parsed placeholder. */
    static final class Placeholder {
        final String raw;
        final String name;
        final @Nullable String argument;
        final @Nullable Pattern pattern;

        Placeholder(String raw, String name, @Nullable String argument, @Nullable Pattern pattern) {
            this.raw = raw;
            this.name = name;
            this.argument = argument;
            this.pattern = pattern;
        }

        String apply(@Nullable String value) {
            if (value == null) {
                return "";
            }
            if (pattern == null) {
                return value;
            }
            Matcher matcher = pattern.matcher(value);
            if (!matcher.find()) {
                return "";
            }
            if (matcher.groupCount() > 0) {
                String group = matcher.group(1);
                return group == null ? "" : group;
            }
            return matcher.group();
        }
    }

    private static final Pattern NAME_CHARS = Pattern.compile("[A-Za-z0-9_]+");

    /** Literal strings and placeholders, in template order. */
    private final List<Object> parts;

    private PlaceholderTemplate(List<Object> parts) {
        this.parts = parts;
    }

    /**
     * Parse a template.
     *
     * @throws ExternalFinderValidationException
     *             on an unknown placeholder name, a missing or surplus argument,
     *             an invalid regex or unbalanced braces
     */
    public static PlaceholderTemplate parse(String template) throws ExternalFinderValidationException {
        List<Object> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '{') {
                literal.append(c);
                i++;
                continue;
            }
            Matcher name = NAME_CHARS.matcher(template);
            if (!name.find(i + 1) || name.start() != i + 1 || name.end() >= template.length()
                    || (template.charAt(name.end()) != '}' && template.charAt(name.end()) != ':')) {
                // A brace that does not open a placeholder is literal text.
                literal.append(c);
                i++;
                continue;
            }
            int end = parsePlaceholder(template, i, name.group(), name.end(), parts, literal);
            i = end;
        }
        if (literal.length() > 0) {
            parts.add(literal.toString());
        }
        return new PlaceholderTemplate(Collections.unmodifiableList(parts));
    }

    /** Parse one placeholder starting at {@code start}; returns the index after its closing brace. */
    private static int parsePlaceholder(String template, int start, String name, int afterName,
            List<Object> parts, StringBuilder literal) {
        String argument = null;
        Pattern pattern = null;
        int i = afterName;
        if (template.charAt(i) == ':') {
            i++;
            if (i < template.length() && template.charAt(i) == '{') {
                int close = balancedClose(template, i, name);
                pattern = compile(template.substring(i + 1, close), name);
                i = close + 1;
            } else {
                int argEnd = i;
                while (argEnd < template.length() && template.charAt(argEnd) != ':'
                        && template.charAt(argEnd) != '}' && template.charAt(argEnd) != '{') {
                    argEnd++;
                }
                argument = template.substring(i, argEnd);
                i = argEnd;
                if (i < template.length() && template.charAt(i) == ':') {
                    i++;
                    if (i >= template.length() || template.charAt(i) != '{') {
                        throw new ExternalFinderValidationException(
                                OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_AFTERKEY", name));
                    }
                    int close = balancedClose(template, i, name);
                    pattern = compile(template.substring(i + 1, close), name);
                    i = close + 1;
                }
            }
        }
        if (i >= template.length() || template.charAt(i) != '}') {
            throw new ExternalFinderValidationException(
                    OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_BRACES", name));
        }
        i++;
        String raw = template.substring(start, i);
        if (!NAMES.contains(name)) {
            throw new ExternalFinderValidationException(
                    OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_UNKNOWN", raw));
        }
        if (PROP.equals(name) && (argument == null || argument.isEmpty())) {
            throw new ExternalFinderValidationException(
                    OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_KEY", raw, "{" + PROP + ":key}"));
        }
        if (!PROP.equals(name) && argument != null) {
            throw new ExternalFinderValidationException(
                    OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_ARGUMENT", raw));
        }
        if (literal.length() > 0) {
            parts.add(literal.toString());
            literal.setLength(0);
        }
        parts.add(new Placeholder(raw, name, argument, pattern));
        return i;
    }

    /** Index of the brace closing the one at {@code open}; backslash escapes a brace. */
    private static int balancedClose(String template, int open, String name) {
        int depth = 0;
        for (int i = open; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new ExternalFinderValidationException(
                OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_BRACES", name));
    }

    private static Pattern compile(String regex, String name) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new ExternalFinderValidationException(
                    OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_REGEX", name, e.getDescription()));
        }
    }

    public boolean hasPlaceholders() {
        return parts.stream().anyMatch(Placeholder.class::isInstance);
    }

    /** Whether the template reads the editor selection. */
    public boolean usesSelection() {
        return parts.stream().anyMatch(p -> p instanceof Placeholder placeholder && TARGET.equals(placeholder.name));
    }

    /**
     * Whether a template reads the editor selection. A template that does not
     * parse is reported as using it, so such an item stays hidden without a
     * selection instead of failing on every segment.
     */
    public static boolean usesSelection(String template) {
        try {
            return parse(template).usesSelection();
        } catch (ExternalFinderValidationException e) {
            return true;
        }
    }

    /** Fill in the placeholders; {@code encoder} is applied to every placeholder value. */
    public String resolve(Context context, UnaryOperator<String> encoder) {
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            result.append(part instanceof Placeholder placeholder ? resolve(placeholder, context, encoder) : part);
        }
        return result.toString();
    }

    /**
     * Fill in the placeholders and split the literal text at
     * {@code delimiter}, so a delimiter inside a placeholder value or regex
     * never splits an argument. Trailing empty arguments are dropped.
     */
    public List<String> resolveSplit(Context context, UnaryOperator<String> encoder, String delimiter) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Placeholder placeholder) {
                current.append(resolve(placeholder, context, encoder));
                continue;
            }
            String[] pieces = ((String) part).split(Pattern.quote(delimiter), -1);
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0) {
                    arguments.add(current.toString());
                    current.setLength(0);
                }
                current.append(pieces[i]);
            }
        }
        arguments.add(current.toString());
        while (!arguments.isEmpty() && arguments.get(arguments.size() - 1).isEmpty()) {
            arguments.remove(arguments.size() - 1);
        }
        return arguments;
    }

    private static String resolve(Placeholder placeholder, Context context, UnaryOperator<String> encoder) {
        return encoder.apply(placeholder.apply(context.valueOf(placeholder)));
    }
}
