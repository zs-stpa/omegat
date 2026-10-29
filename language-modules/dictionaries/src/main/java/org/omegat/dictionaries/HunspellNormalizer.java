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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an upstream Hunspell pair into the UTF-8 pair OmegaT bundles.
 *
 * Upstream files carry defects the Hunspell C library tolerates and the
 * Lucene parser used by OmegaT rejects: affix headers with a wrong rule
 * count or trailing junk, mistyped rule keywords (SFK, SFT), a byte order
 * mark in front of the SET line, a SET line that does not match the bytes,
 * and a word count sharing its line with a comment. This class is the Java
 * twin of tools/hunspell_common.py, which the inventory scripts use.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class HunspellNormalizer {

    private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    private static final Pattern SET_LINE = Pattern.compile("^[ \\t]*SET[ \\t]+([^ \\t\\r\\n]+)", Pattern.MULTILINE);
    private static final Pattern AFFIX_HEADER = Pattern
            .compile("^(PFX|SFX)[ \\t]+([^ \\t]+)[ \\t]+([YN])[ \\t]+(\\d+)[^ \\t]*[ \\t]*(?:#.*)?$");
    private static final Pattern COUNT_LINE = Pattern.compile("^[ \\t]*(\\d+)(?:[ \\t].*)?$");
    private static final Pattern BLANKS = Pattern.compile("[ \\t]+");

    /** Facts about one conversion, for the build log and the manifest. */
    public static final class Result {
        public String declaredEncoding = "";
        public String usedEncoding = "";
        public boolean verbatim;
        public int affixLinesFixed;
        public int commentLinesReplaced;
        public Integer declaredCount;
        public int entries;

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH,
                    "declared %s, read as %s, %d entries (declared %s), %d affix lines fixed, "
                            + "%d comment lines with replaced bytes%s",
                    declaredEncoding, usedEncoding, entries, declaredCount, affixLinesFixed,
                    commentLinesReplaced, verbatim ? ", copied verbatim" : "");
        }
    }

    private HunspellNormalizer() {
    }

    /**
     * Command line: {@code <in.aff> <in.dic> <out.aff> <out.dic>}.
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("usage: HunspellNormalizer in.aff in.dic out.aff out.dic");
            System.exit(2);
        }
        Result result = convert(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        System.out.println(args[2] + ": " + result);
    }

    /**
     * Converts a pair; returns what was changed.
     */
    public static Result convert(final Path affIn, final Path dicIn, final Path affOut, final Path dicOut)
            throws IOException {
        byte[] aff = Files.readAllBytes(affIn);
        byte[] dic = Files.readAllBytes(dicIn);
        Result result = new Result();
        result.declaredEncoding = declaredEncoding(aff);
        Charset charset = charsetFor(result.declaredEncoding);
        if (!charset.equals(StandardCharsets.UTF_8) && looksLikeUtf8(aff, dic)) {
            // A single-byte SET over valid multi-byte UTF-8 is a wrong
            // declaration; converting as Latin-x would double-encode words.
            charset = StandardCharsets.UTF_8;
        }
        result.usedEncoding = charset.name();

        List<String> affLines = decodeAffix(stripBom(aff), charset, result);
        if (affLines == null) {
            // Flags in another encoding than declared: any re-encoding would
            // change them, so ship the pair byte for byte.
            result.verbatim = true;
            Files.createDirectories(affOut.getParent());
            Files.write(affOut, aff);
            Files.write(dicOut, dic);
            result.declaredCount = leadingCount(firstLine(dic, charset));
            result.entries = result.declaredCount == null ? 0 : result.declaredCount;
            return result;
        }
        String dicText;
        try {
            dicText = strictDecode(stripBom(dic), charset);
        } catch (CharacterCodingException ex) {
            throw new IOException(dicIn + ": not decodable as " + charset.name(), ex);
        }

        affLines = normaliseLines(affLines);
        boolean hadSet = false;
        for (int i = 0; i < affLines.size(); i++) {
            if (affLines.get(i).matches("[ \\t]*SET[ \\t]+\\S+.*")) {
                affLines.set(i, "SET UTF-8");
                hadSet = true;
                break;
            }
        }
        if (!hadSet) {
            affLines.add(0, "SET UTF-8");
        }
        result.affixLinesFixed = repairAffixCounts(affLines);

        List<String> dicLines = normaliseLines(new ArrayList<>(Arrays.asList(dicText.split("\n", -1))));
        if (!dicLines.isEmpty()) {
            Matcher count = COUNT_LINE.matcher(dicLines.get(0));
            if (count.matches()) {
                result.declaredCount = Integer.valueOf(count.group(1));
                dicLines.remove(0);
            }
        }
        List<String> entries = new ArrayList<>(dicLines.size());
        for (String line : dicLines) {
            if (!line.isBlank()) {
                entries.add(line);
            }
        }
        result.entries = entries.size();

        Files.createDirectories(affOut.getParent());
        Files.writeString(affOut, String.join("\n", affLines) + "\n", StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(dicText.length() + 16);
        sb.append(entries.size()).append('\n');
        for (String entry : entries) {
            sb.append(entry).append('\n');
        }
        Files.writeString(dicOut, sb.toString(), StandardCharsets.UTF_8);
        return result;
    }

    /** SET value of the affix file; Hunspell's default is ISO8859-1. */
    static String declaredEncoding(final byte[] aff) {
        byte[] body = stripBom(aff);
        Matcher m = SET_LINE.matcher(new String(body, StandardCharsets.ISO_8859_1));
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ENGLISH).replace("ISO-8859", "ISO8859");
        }
        return body.length < aff.length ? "UTF-8" : "ISO8859-1";
    }

    static Charset charsetFor(final String hunspellName) {
        String name = hunspellName;
        if (name.startsWith("ISO8859-")) {
            name = "ISO-8859-" + name.substring("ISO8859-".length());
        } else if (name.startsWith("MICROSOFT-")) {
            name = name.substring("MICROSOFT-".length());
        } else if (name.equals("UTF8")) {
            name = "UTF-8";
        } else if (name.equals("TIS620-2533")) {
            name = "TIS-620";
        }
        return Charset.forName(name);
    }

    static byte[] stripBom(final byte[] data) {
        if (data.length >= 3 && data[0] == BOM[0] && data[1] == BOM[1] && data[2] == BOM[2]) {
            return Arrays.copyOfRange(data, 3, data.length);
        }
        return data;
    }

    /** True when all parts are valid UTF-8 and at least one multi-byte sequence occurs. */
    static boolean looksLikeUtf8(final byte[]... parts) {
        boolean multiByte = false;
        for (byte[] data : parts) {
            for (byte b : data) {
                if (b < 0) {
                    multiByte = true;
                    break;
                }
            }
            try {
                strictDecode(data, StandardCharsets.UTF_8);
            } catch (CharacterCodingException ex) {
                return false;
            }
        }
        return multiByte;
    }

    private static String strictDecode(final byte[] data, final Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
    }

    /**
     * Decodes the affix file line by line. Comment lines may lose characters
     * (hu_HU keeps a Latin-2 header over UTF-8 rules); a rule line that does
     * not decode returns null, the signal to copy the pair verbatim.
     */
    private static List<String> decodeAffix(final byte[] aff, final Charset charset, final Result result) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= aff.length; i++) {
            if (i == aff.length || aff[i] == '\n') {
                byte[] raw = Arrays.copyOfRange(aff, start, i);
                start = i + 1;
                try {
                    lines.add(strictDecode(raw, charset));
                } catch (CharacterCodingException ex) {
                    String lenient = new String(raw, charset);
                    if (!lenient.stripLeading().startsWith("#")) {
                        return null;
                    }
                    lines.add(lenient);
                    result.commentLinesReplaced++;
                }
            }
        }
        return lines;
    }

    private static String firstLine(final byte[] data, final Charset charset) {
        byte[] body = stripBom(data);
        int end = 0;
        while (end < body.length && body[end] != '\n') {
            end++;
        }
        return new String(body, 0, end, charset);
    }

    private static Integer leadingCount(final String line) {
        Matcher m = COUNT_LINE.matcher(line.strip());
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /** Strips CR and trailing blanks, drops trailing empty lines. */
    private static List<String> normaliseLines(final List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String s = line;
            if (!s.isEmpty() && s.charAt(0) == '\ufeff') {
                s = s.substring(1);
            }
            int end = s.length();
            while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t' || s.charAt(end - 1) == '\r')) {
                end--;
            }
            out.add(s.substring(0, end));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static List<String> fields(final String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(BLANKS.split(trimmed));
    }

    private static boolean isTypoOf(final String token, final String kind) {
        if (token.length() != 3 || token.equals(kind)) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < 3; i++) {
            if (token.charAt(i) != kind.charAt(i)) {
                diff++;
            }
        }
        return diff == 1;
    }

    /**
     * Rewrites PFX/SFX headers whose count does not match the rules below,
     * mends mistyped rule keywords and strips junk after the count. Returns
     * the number of lines changed.
     */
    static int repairAffixCounts(final List<String> lines) {
        int fixed = 0;
        int i = 0;
        while (i < lines.size()) {
            Matcher header = AFFIX_HEADER.matcher(lines.get(i));
            if (!header.matches()) {
                i++;
                continue;
            }
            String kind = header.group(1);
            String flag = header.group(2);
            int j = i + 1;
            while (j < lines.size()) {
                List<String> parts = fields(lines.get(j));
                boolean rule = parts.size() >= 4 && parts.get(1).equals(flag)
                        && (parts.get(0).equals(kind) || isTypoOf(parts.get(0), kind))
                        && !AFFIX_HEADER.matcher(lines.get(j)).matches();
                if (!rule) {
                    break;
                }
                if (!parts.get(0).equals(kind)) {
                    lines.set(j, kind + lines.get(j).substring(3));
                    fixed++;
                }
                j++;
            }
            int actual = j - i - 1;
            if (actual > 0) {
                String clean = kind + " " + flag + " " + header.group(3) + " " + actual;
                if (!lines.get(i).equals(clean)) {
                    lines.set(i, clean);
                    fixed++;
                }
                i = j;
            } else {
                i++;
            }
        }
        return fixed;
    }
}
