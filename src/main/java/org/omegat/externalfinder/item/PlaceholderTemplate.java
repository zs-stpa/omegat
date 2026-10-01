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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;

import org.omegat.core.data.SourceTextEntry;
import org.omegat.util.OStrings;

/**
 * URL or command template of an external search, with its placeholders.
 * <p>
 * A placeholder is written as <code>{name}</code>, <code>{name:argument}</code>,
 * <code>{name:{regex}}</code> or <code>{name:argument:{regex}}</code>. The
 * names are {@value #TARGET} (selected editor text), {@value #COMMENT}
 * (comment of the current segment), {@value #PROP} (one property of the
 * current segment, the argument is its key), {@value #FILE} (source file of
 * the current segment) and {@value #ID} (identifier of the current segment).
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
    public static final String COMMENT = "comment";
    public static final String PROP = "prop";
    public static final String FILE = "file";
    public static final String ID = "id";

    /** Placeholder names in the order the editor dialog offers them. */
    public static final List<String> NAMES = List.of(TARGET, COMMENT, PROP, FILE, ID);

    /** Values the placeholders of one run draw from. */
    public static final class Context {
        private final @Nullable String selection;
        private final @Nullable String comment;
        private final String @Nullable [] properties;
        private final @Nullable String file;
        private final @Nullable String id;

        private Context(@Nullable String selection, @Nullable String comment, String @Nullable [] properties,
                @Nullable String file, @Nullable String id) {
            this.selection = selection;
            this.comment = comment;
            this.properties = properties;
            this.file = file;
            this.id = id;
        }

        /** Context of a run: the editor selection and the current segment, either may be absent. */
        public static Context of(@Nullable String selection, @Nullable SourceTextEntry entry) {
            if (entry == null) {
                return new Context(selection, null, null, null, null);
            }
            return new Context(selection, entry.getComment(), entry.getRawProperties(), entry.getKey().file,
                    entry.getKey().id);
        }

        /** Context with a selection only, for callers that have no segment. */
        public static Context ofSelection(String selection) {
            return new Context(selection, null, null, null, null);
        }

        /** Illustrative values for the sample output of the editor dialog. */
        public static Context sample(boolean nonAscii) {
            String selection = nonAscii ? "føø bår" : "foo bar";
            // The file carries a directory so the sample shows how slashes encode.
            return new Context(selection, "Sample comment", new String[] { "note", "Sample comment" },
                    "dir/sample.txt", "sample-id");
        }

        public @Nullable String getSelection() {
            return selection;
        }

        @Nullable
        String valueOf(Placeholder placeholder) {
            return switch (placeholder.name) {
            case TARGET -> selection;
            case COMMENT -> comment;
            case PROP -> property(placeholder.argument);
            case FILE -> file;
            case ID -> id;
            default -> null;
            };
        }

        private @Nullable String property(@Nullable String key) {
            if (properties == null || key == null) {
                return null;
            }
            StringBuilder values = new StringBuilder();
            for (int i = 0; i + 1 < properties.length; i += 2) {
                if (key.equals(properties[i])) {
                    if (values.length() > 0) {
                        values.append('\n');
                    }
                    values.append(properties[i + 1]);
                }
            }
            return values.length() == 0 ? null : values.toString();
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
