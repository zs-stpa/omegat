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

package org.omegat.core.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.omegat.util.Log;
import org.omegat.util.OStrings;

/**
 * HTML report of the line differences between the local and the team
 * version of a file-backed {@link TeamSetting}. Written to the log
 * directory so it survives the dialog that offers it and can be inspected
 * or attached later like any other log artifact.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public final class TeamSettingDiffReport {

    /**
     * Upper bound of the quadratic LCS table; beyond it the diff degrades
     * to whole-block removed/added instead of freezing the caller.
     */
    private static final long LCS_CELL_LIMIT = 4_000_000L;

    /**
     * Unchanged lines shown around each changed block; longer identical
     * stretches are folded into a counted gap row, so the report stays
     * readable for large configuration files.
     */
    private static final int CONTEXT_LINES = 10;

    private TeamSettingDiffReport() {
    }

    /**
     * Writes the report for the given setting to the log directory and
     * returns the written file.
     *
     * @param setting
     *            the diverged setting
     * @param localValue
     *            canonical content of the local version, null when the
     *            local project carries no file
     * @param teamValue
     *            canonical content of the team version, null when the team
     *            project carries no file
     */
    public static File write(TeamSetting setting, @Nullable String localValue, @Nullable String teamValue)
            throws IOException {
        File dir = new File(Log.getLogLocation());
        return write(setting, localValue, teamValue, dir);
    }

    static File write(TeamSetting setting, @Nullable String localValue, @Nullable String teamValue,
            File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create report directory " + dir);
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File file = new File(dir, "setting-diff_" + sanitize(setting.getKey()) + "_" + stamp + ".html");
        Files.writeString(file.toPath(), render(setting, localValue, teamValue), StandardCharsets.UTF_8);
        return file;
    }

    private static String sanitize(String key) {
        return key.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String render(TeamSetting setting, @Nullable String localValue,
            @Nullable String teamValue) {
        List<String> local = toLines(localValue);
        List<String> team = toLines(teamValue);
        String title = OStrings.getString("TEAM_SETTING_DIFF_TITLE", setting.getDisplayName());
        String localLabel = OStrings.getString("TEAM_SETTING_DIFF_LOCAL");
        String teamLabel = OStrings.getString("TEAM_SETTING_DIFF_TEAM");

        List<DiffLine> lines = diff(local, team);
        boolean[] keep = contextWindow(lines);
        StringBuilder body = new StringBuilder();
        int skipped = 0;
        for (int idx = 0; idx < lines.size(); idx++) {
            if (!keep[idx]) {
                skipped++;
                continue;
            }
            if (skipped > 0) {
                appendGapRow(body, skipped);
                skipped = 0;
            }
            DiffLine line = lines.get(idx);
            String cls;
            String sign;
            switch (line.side) {
            case LOCAL:
                cls = "local";
                sign = "&minus;";
                break;
            case TEAM:
                cls = "team";
                sign = "+";
                break;
            default:
                cls = "both";
                sign = "&nbsp;";
                break;
            }
            body.append("<tr class=\"").append(cls).append("\"><td class=\"sign\">").append(sign)
                    .append("</td><td>").append(escape(line.text)).append("</td></tr>\n");
        }
        if (skipped > 0) {
            appendGapRow(body, skipped);
        }

        return "<!DOCTYPE html>\n"
                + "<!--\n"
                + " OmegaT - Computer Assisted Translation (CAT) tool\n"
                + "          with fuzzy matching, translation memory, keyword search,\n"
                + "          glossaries, and translation leveraging into updated projects.\n"
                + "\n"
                + " Copyright (C) 2026 Stephan Pakebusch\n"
                + "               Home page: https://www.omegat.org/\n"
                + "               Support center: https://omegat.org/support\n"
                + "\n"
                + " This file was generated by OmegaT, which is free software: you can\n"
                + " redistribute it and/or modify it under the terms of the GNU General\n"
                + " Public License as published by the Free Software Foundation, either\n"
                + " version 3 of the License, or (at your option) any later version.\n"
                + "-->\n"
                + "<html><head><meta charset=\"UTF-8\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + "<style>\n"
                + "body { font-family: sans-serif; margin: 2em; background: #fff; color: #222; }\n"
                + "table { border-collapse: collapse; width: 100%; font-family: monospace; }\n"
                + "td { padding: 0 0.5em; white-space: pre-wrap; }\n"
                + "td.sign { width: 1em; text-align: center; -webkit-user-select: none; user-select: none; }\n"
                + ".local { background: #ffe5e5; }\n"
                + ".team { background: #e2f5e2; }\n"
                + "tr.gap td { color: #888; font-style: italic; padding: 0.4em 0.5em; }\n"
                + ".legend span { padding: 0.1em 0.5em; margin-right: 1em; }\n"
                + "</style></head><body>\n"
                + "<h1>" + escape(title) + "</h1>\n"
                + "<p class=\"legend\"><span class=\"local\">&minus; " + escape(localLabel)
                + "</span><span class=\"team\">+ " + escape(teamLabel) + "</span></p>\n"
                + "<table>\n" + body + "</table>\n</body></html>\n";
    }

    private static void appendGapRow(StringBuilder body, int skipped) {
        // Plain digits on purpose: no locale grouping in a line counter.
        body.append("<tr class=\"gap\"><td class=\"sign\">&vellip;</td><td>")
                .append(escape(OStrings.getString("TEAM_SETTING_DIFF_SKIPPED",
                        Integer.toString(skipped))))
                .append("</td></tr>\n");
    }

    /**
     * Marks the lines worth showing: every changed line plus up to
     * {@link #CONTEXT_LINES} unchanged lines on each side of it.
     */
    private static boolean[] contextWindow(List<DiffLine> lines) {
        boolean[] keep = new boolean[lines.size()];
        for (int idx = 0; idx < lines.size(); idx++) {
            if (lines.get(idx).side == Side.BOTH) {
                continue;
            }
            int from = Math.max(0, idx - CONTEXT_LINES);
            int to = Math.min(lines.size() - 1, idx + CONTEXT_LINES);
            for (int k = from; k <= to; k++) {
                keep[k] = true;
            }
        }
        // A fold hiding a single line costs the same row it saves; show
        // the line instead.
        for (int idx = 0; idx < keep.length; idx++) {
            if (!keep[idx] && (idx == 0 || keep[idx - 1])
                    && (idx == keep.length - 1 || keep[idx + 1])) {
                keep[idx] = true;
            }
        }
        return keep;
    }

    private static List<String> toLines(@Nullable String value) {
        // The canonical content is produced with the platform line
        // separator, so on Windows the lines carry \r\n.
        return value == null ? List.of() : List.of(value.split("\r?\n", -1));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private enum Side { LOCAL, TEAM, BOTH }

    private static final class DiffLine {
        private final Side side;
        private final String text;

        private DiffLine(Side side, String text) {
            this.side = side;
            this.text = text;
        }
    }

    /**
     * Longest-common-subsequence line diff. Configuration files are small,
     * so the quadratic table stays cheap; identical leading and trailing
     * lines are peeled off first to keep it that way for near-identical
     * files.
     */
    private static List<DiffLine> diff(List<String> local, List<String> team) {
        int prefix = 0;
        while (prefix < local.size() && prefix < team.size()
                && local.get(prefix).equals(team.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < local.size() - prefix && suffix < team.size() - prefix
                && local.get(local.size() - 1 - suffix).equals(team.get(team.size() - 1 - suffix))) {
            suffix++;
        }
        List<String> localMid = local.subList(prefix, local.size() - suffix);
        List<String> teamMid = team.subList(prefix, team.size() - suffix);

        int n = localMid.size();
        int m = teamMid.size();
        if ((long) (n + 1) * (m + 1) > LCS_CELL_LIMIT) {
            // Two heavily rewritten large files would make the quadratic
            // table freeze the caller or exhaust memory; fall back to the
            // coarse but correct "everything changed" rendering of the
            // non-shared middle.
            List<DiffLine> coarse = new ArrayList<>();
            for (int k = 0; k < prefix; k++) {
                coarse.add(new DiffLine(Side.BOTH, local.get(k)));
            }
            localMid.forEach(line -> coarse.add(new DiffLine(Side.LOCAL, line)));
            teamMid.forEach(line -> coarse.add(new DiffLine(Side.TEAM, line)));
            for (int k = local.size() - suffix; k < local.size(); k++) {
                coarse.add(new DiffLine(Side.BOTH, local.get(k)));
            }
            return coarse;
        }
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = localMid.get(i).equals(teamMid.get(j)) ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<DiffLine> result = new ArrayList<>();
        for (int k = 0; k < prefix; k++) {
            result.add(new DiffLine(Side.BOTH, local.get(k)));
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (localMid.get(i).equals(teamMid.get(j))) {
                result.add(new DiffLine(Side.BOTH, localMid.get(i)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add(new DiffLine(Side.LOCAL, localMid.get(i)));
                i++;
            } else {
                result.add(new DiffLine(Side.TEAM, teamMid.get(j)));
                j++;
            }
        }
        while (i < n) {
            result.add(new DiffLine(Side.LOCAL, localMid.get(i)));
            i++;
        }
        while (j < m) {
            result.add(new DiffLine(Side.TEAM, teamMid.get(j)));
            j++;
        }
        for (int k = local.size() - suffix; k < local.size(); k++) {
            result.add(new DiffLine(Side.BOTH, local.get(k)));
        }
        return result;
    }
}
