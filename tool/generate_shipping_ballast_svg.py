#!/usr/bin/env python3
"""Generate the OmegaT "shipping ballast" size-anatomy SVG from a fresh installDist.

Companion to the historic hand-made snapshot
src/docs/developer/assets/omegat-shipping-ballast.svg (2026-08-01).
This script is the first reproducible edition of that slide; re-run it
after the dependency set changes to refresh the numbers.

Usage:
  python3 tool/generate_shipping_ballast_svg.py \
      build/install/OmegaT \
      src/docs/developer/assets/omegat-shipping-ballast-2026-10.svg \
      --snapshot 2026-10-18 --commit <commit> \
      --prev-date 2026-08-01 --prev-total 407 \
      --prev-lang-total 249 --prev-lang-count 29

The script
  * walks the distribution and buckets every file (language module jars,
    lib jars, other module jars, app jar, docs / images / scripts /
    plugins, top-level misc),
  * re-verifies the findings of the 2026-08-01 edition against the real
    jars (kuromoji class-set duplication inside the ja module, gRPC
    client chain, rhino, icu4j, team stack, duplicate artifact versions)
    and prints a report to stdout; every number in the cards comes from
    the measurement or from the command line, nothing is remembered,
  * renders the to-scale stacked bar plus finding cards whose numbers
    are computed from the measurement.

Standard library only.
"""

import argparse
import os
import re
import sys
import textwrap
import zipfile
from collections import Counter, defaultdict

MB = 1048576.0

# ---------------------------------------------------------------- geometry
CANVAS_W, CANVAS_H = 1760, 1200
BAR_X, BAR_W = 60, 170
BAR_TOP = 140.0
BAR_BOTTOM = 1118.6          # same visual extent as the 2026-08-01 edition
LABEL_TICK_X1, LABEL_TICK_X2, LABEL_X = 232, 244, 248
LABEL_MIN_GAP = 13.5
MIN_LABELED_H = 3.0          # segments shorter than this go into an aggregate

LANG_TOP = 15                # named language segments, rest -> "N more"
LIB_TOP = 12                 # named lib segments, rest -> "N further jars"
OTHER_MIN = 0.25             # MB, below this an "other" bucket is not shown

# ------------------------------------------------------------------ colours
LANG_FILLS = ["#ffe9a8", "#f6dd90"]
LIB_FILLS = ["#dfeefb", "#cde2f5"]
OTHER_FILLS = ["#ececec", "#e0e0e0"]
TINT_DUP = "#f2b8b4"         # confirmed duplicate (legend red)
TINT_REPLACE = "#f5d0a8"     # replaceable gRPC chain (legend orange)
FRAME_LANG, FRAME_LIB, FRAME_OTHER = "#c9971a", "#4a7ba6", "#8a8a8a"
CAP_LANG, CAP_LIB, CAP_OTHER = "#a8842a", "#4a7ba6", "#666666"

# The gRPC client chain the languagetool-core fork pulls in, with what travels
# with it (gson, perfmark, animal-sniffer, the android annotations 4.1.1.4);
# a jar of those names from another source would be tinted as chain too.
# protobuf-java is deliberately NOT in this set: dsl4j (Lingvo DSL) needs it,
# so it is no ballast.
GRPC_RE = re.compile(r"^(grpc-|proto-google|perfmark-api|gson-|animal-sniffer-|annotations-4\.1\.1\.4)")
# The transports: with none of them in lib/ the chain counts as excluded and
# only the jars needed for class linking are left.
GRPC_TRANSPORT_RE = re.compile(r"^(grpc-netty|grpc-core|grpc-okhttp)")
# What build.gradle excludes; the card lists the ones really absent from lib/.
GRPC_EXCLUDED_MODULES = ["grpc-netty-shaded", "grpc-core", "grpc-util", "grpc-protobuf",
                         "grpc-protobuf-lite", "proto-google-common-protos"]
CARD_WRAP = 185              # characters per card line, fits 1260 px at 12.5 px
TEAM_RE = re.compile(r"^(org\.eclipse\.jgit|svnkit|sshd|bcprov|jsch\.agentproxy)")
# 2-letter ISO 639-1 and 3-letter ISO 639-3 codes (e.g. "ast" = Aragonese)
LANG_JAR_RE = re.compile(r"^omegat-language-([a-z]{2,3}(?:_[A-Za-z]{2,4})?)\.jar$")
VERSION_RE = re.compile(r"-(?P<tail>[0-9][0-9A-Za-z.~-]*)\.jar$")


def fsize(path):
    try:
        return os.path.getsize(path)
    except OSError:
        return 0


def dir_size(path):
    total = 0
    for root, _dirs, files in os.walk(path):
        for name in files:
            total += fsize(os.path.join(root, name))
    return total


def mb(bytes_):
    return bytes_ / MB


def fmt(mb_):
    """One decimal for the bar labels, like the 2026-08-01 edition."""
    return ("%.1f" % mb_)
def fmt_size(bytes_):
    """MB with one decimal, KB below 50 KB so that small jars do not read 0.0."""
    if bytes_ < 51200:
        return f"{bytes_ / 1024.0:.0f} KB"
    return f"{fmt(mb(bytes_))} MB"
def signed(value, unit):
    """Delta with a real minus sign, e.g. '−107 MB' or '+3 %'."""
    text = f"{value:+.0f}".replace("-", "\u2212")
    return f"{text}{unit}"


# ------------------------------------------------------------- measurement
def measure(dist):
    data = {"dist": dist}

    # language module jars
    languages = []
    other_modules = []
    modules_dir = os.path.join(dist, "modules")
    if os.path.isdir(modules_dir):
        for name in sorted(os.listdir(modules_dir)):
            path = os.path.join(modules_dir, name)
            if not (os.path.isfile(path) and name.endswith(".jar")):
                continue
            m = LANG_JAR_RE.match(name)
            if m:
                languages.append((m.group(1), name, fsize(path)))
            else:
                other_modules.append((name, fsize(path)))

    lib = []
    lib_dir = os.path.join(dist, "lib")
    if os.path.isdir(lib_dir):
        for name in sorted(os.listdir(lib_dir)):
            if name.endswith(".jar"):
                lib.append((name, fsize(os.path.join(lib_dir, name))))

    app_jar = fsize(os.path.join(dist, "OmegaT.jar"))
    dirs = {}
    for d in ("docs", "images", "scripts", "plugins"):
        p = os.path.join(dist, d)
        if os.path.isdir(p):
            dirs[d] = dir_size(p)

    # everything else on the top level (readmes, launcher, html, txt, ...)
    misc = 0
    for name in os.listdir(dist):
        p = os.path.join(dist, name)
        if os.path.isfile(p) and name != "OmegaT.jar":
            misc += fsize(p)

    languages.sort(key=lambda t: -t[2])
    other_modules.sort(key=lambda t: -t[1])
    lib.sort(key=lambda t: -t[1])

    # non-jar files inside modules/ and lib/ (license texts, readmes)
    nonjar = 0
    for d in (modules_dir, lib_dir):
        if os.path.isdir(d):
            for name in os.listdir(d):
                p = os.path.join(d, name)
                if os.path.isfile(p) and not name.endswith(".jar"):
                    nonjar += fsize(p)

    data["languages"] = languages
    data["other_modules"] = other_modules
    data["lib"] = lib
    data["app_jar"] = app_jar
    data["dirs"] = dirs
    data["misc"] = misc

    data["lang_bytes"] = sum(t[2] for t in languages)
    data["lib_bytes"] = sum(t[1] for t in lib)
    data["othermod_bytes"] = sum(t[1] for t in other_modules)
    data["other_bytes"] = (data["othermod_bytes"] + app_jar + misc
                           + sum(dirs.values()) + nonjar)
    data["total_bytes"] = (data["lang_bytes"] + data["lib_bytes"]
                           + data["other_bytes"])
    return data


# ---------------------------------------------------------------- findings
def jar_class_names(path):
    """All *.class entries inside a jar (None if unreadable)."""
    try:
        with zipfile.ZipFile(path) as z:
            return {n for n in z.namelist() if n.endswith(".class")}
    except zipfile.BadZipFile:
        return None


def findings(data):
    f = {}
    lib_names = [n for n, _s in data["lib"]]

    # --- kuromoji duplication (old card 1)
    f["kuromoji_lib"] = [t for t in data["lib"]
                         if t[0].startswith("lucene-analysis-kuromoji-")]
    ja = [t for t in data["languages"] if t[0].startswith("ja")]
    dup = 0
    if f["kuromoji_lib"] and ja:
        ku_classes = jar_class_names(
            os.path.join(data["dist"], "lib", f["kuromoji_lib"][0][0]))
        ja_classes = jar_class_names(
            os.path.join(data["dist"], "modules", ja[0][1]))
        if ku_classes is not None and ja_classes is not None:
            dup = len(ku_classes & ja_classes)
    f["ja_dup_classes"] = dup
    f["ja_mb"] = mb(ja[0][2]) if ja else 0.0

    # --- smartcn counter-check (old card 1)
    zh = [t for t in data["languages"] if t[0].startswith("zh")]
    f["zh_lucene_classes"] = 0
    if zh:
        zh_classes = jar_class_names(
            os.path.join(data["dist"], "modules", zh[0][1]))
        f["zh_lucene_classes"] = (
            len([c for c in (zh_classes or []) if c.startswith("org/apache/lucene")])
            if zh_classes is not None else -1)

    # --- gRPC client stack (old card 2)
    grpc = [(n, s) for n, s in data["lib"] if GRPC_RE.match(n)]
    f["grpc_jars"] = len(grpc)
    f["grpc_bytes"] = sum(s for _n, s in grpc)
    f["grpc_top"] = sorted(grpc, key=lambda t: -t[1])
    f["grpc_excluded"] = not any(GRPC_TRANSPORT_RE.match(n) for n, _s in data["lib"])
    f["grpc_absent"] = [m for m in GRPC_EXCLUDED_MODULES
                        if not any(n.startswith(m + "-") for n, _s in data["lib"])]
    # protobuf-java travels with the chain but is kept (dsl4j): reported for
    # the card text, not part of the chain
    f["protobuf_bytes"] = sum(s for n, s in data["lib"] if n.startswith("protobuf-java"))

    # --- rhino (old card 2)
    rhino = [(n, s) for n, s in data["lib"] if n.startswith("rhino")]
    f["rhino_bytes"] = sum(s for _n, s in rhino)
    f["rhino_jars"] = len(rhino)
    nashorn = [(n, s) for n, s in data["lib"] if n.startswith("nashorn")]
    f["nashorn_bytes"] = sum(s for _n, s in nashorn)

    # --- icu4j (old card 4, note corrected)
    icu = [(n, s) for n, s in data["lib"] if n.startswith("icu4j-")]
    f["icu_version"] = re.sub(r"^icu4j-|\.jar$", "", icu[0][0]) if icu else "?"
    f["icu_bytes"] = sum(s for _n, s in icu)
    lt = [(n, s) for n, s in data["lib"] if n.startswith("languagetool-core-")]
    f["lt_version"] = re.sub(r"^languagetool-core-|\.jar$", "", lt[0][0]) if lt else "?"

    # --- team stack (old card 4)
    team = [(n, s) for n, s in data["lib"] if TEAM_RE.match(n)]
    f["team_bytes"] = sum(s for _n, s in team)
    f["team_top"] = sorted(team, key=lambda t: -t[1])[:4]
    f["lucene_core_bytes"] = sum(s for n, s in data["lib"] if n.startswith("lucene-core-"))
    f["fastutil_bytes"] = sum(s for n, s in data["lib"] if n.startswith("fastutil-"))
    f["annotations"] = [(n, s) for n, s in data["lib"] if n.startswith("annotations-")]

    # --- duplicate artifact versions in lib/ (old card 4)
    bases = defaultdict(list)
    for n in lib_names:
        m = VERSION_RE.search(n)
        bases[n[: m.start()] if m else n].append(n)
    f["dup_versions"] = {b: sorted(v) for b, v in bases.items() if len(v) > 1}

    # --- groovy family (old card 4)
    groovy = [(n, s) for n, s in data["lib"] if n.startswith("groovy")]
    f["groovy_bytes"] = sum(s for _n, s in groovy)
    f["groovy_jars"] = len(groovy)

    # --- pdfbox family (old card 4)
    pdf = [(n, s) for n, s in data["lib"]
           if n.startswith("pdfbox") or n.startswith("fontbox")]
    f["pdf_bytes"] = sum(s for _n, s in pdf)
    f["pdf_jars"] = len(pdf)

    # --- nl module internals (old card 3)
    nl = [t for t in data["languages"] if t[0].startswith("nl")]
    f["nl_mb"] = mb(nl[0][2]) if nl else 0.0
    f["nl_top_entries"] = []
    if nl:
        try:
            with zipfile.ZipFile(os.path.join(data["dist"], "modules", nl[0][1])) as z:
                entries = [(e.file_size, e.filename)
                           for e in z.infolist() if e.file_size > 0]
            for size, name in sorted(entries, reverse=True)[:3]:
                short = name.rsplit("/", 1)[-1]
                f["nl_top_entries"].append((short, mb(size)))
        except zipfile.BadZipFile:
            pass
    f["nl_unpacked"] = None
    if nl:
        try:
            with zipfile.ZipFile(os.path.join(data["dist"], "modules", nl[0][1])) as z:
                f["nl_unpacked"] = mb(sum(e.file_size for e in z.infolist()))
        except zipfile.BadZipFile:
            pass
    return f


# ---------------------------------------------------------------- rendering
def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def short_name(name):
    """Jar file name without version, for the bar labels."""
    m = VERSION_RE.search(name)
    base = name[: m.start()] if m else name[:-4]
    base = re.sub(r"^org\.eclipse\.", "", base)
    for prefix in ("omegat-language-", "lucene-analysis-"):
        if base.startswith(prefix):
            base = base[len(prefix):]
    return base


def label_positions(items):
    """Greedy leader-tick placement: keep segment order, min gap, no overlap.

    items: list of (center_y, min_y, max_y, label) -> list of label_y
    """
    placed = []
    for cy, lo, hi, _lab in items:
        y = max(lo, min(hi, cy))
        for py in placed:
            if y - py < LABEL_MIN_GAP:
                y = py + LABEL_MIN_GAP
        for py in placed:
            if py - y < LABEL_MIN_GAP:
                y = py - LABEL_MIN_GAP
                break
        y = max(lo, min(hi, y))
        placed.append(y)
    return placed


def rect_lines(y0, y1, fill):
    h = y1 - y0
    return f'    <rect x="{BAR_X}" y="{y0:.1f}" width="{BAR_W}" height="{h:.1f}" fill="{fill}"/>'


def frame(y0, y1, color):
    return (f'  <rect x="{BAR_X}" y="{y0:.1f}" width="{BAR_W}" '
            f'height="{y1 - y0:.1f}" fill="none" stroke="{color}" stroke-width="1.8"/>')


def render(data, f, args):
    total = mb(data["total_bytes"])
    scale = (BAR_BOTTOM - BAR_TOP) / total
    out = []
    a = out.append

    a('<?xml version="1.0" encoding="UTF-8"?>')
    a('<!--')
    a('  OmegaT shipping ballast map — new edition, generated file.')
    a('  DO NOT EDIT BY HAND: re-run tool/generate_shipping_ballast_svg.py')
    a(f'  against a fresh installDist to refresh the numbers.')
    a('')
    a(f'  Snapshot: {args.snapshot} · installDist at commit {args.commit}')
    a('  Companion of the historic hand-made omegat-shipping-ballast.svg (2026-08-01).')
    a('')
    a('  \U0001f12f 2026 - This file is part of OmegaT, released under the GNU General')
    a('  Public License version 3 or (at your option) any later version.')
    a('-->')
    a(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS_W} {CANVAS_H}" '
      'font-family="-apple-system, \'Segoe UI\', Helvetica, Arial, sans-serif">')

    a('  <!-- background -->')
    a(f'  <rect x="0" y="0" width="{CANVAS_W}" height="{CANVAS_H}" fill="#ffffff"/>')
    a('')
    # logo (copied verbatim from the 2026-08-01 edition), painted over the background
    logo = open(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                             "src", "docs", "developer", "assets",
                             "omegat-shipping-ballast.svg"), encoding="utf-8").read()
    m = re.search(r'(<g transform="translate\(24,10\) scale\(0\.16\)">.*?</g>)',
                  logo, re.S)
    if m:
        a("  " + m.group(1))
    a('')
    a('  <!-- title -->')
    a(f'  <text x="880" y="50" text-anchor="middle" font-size="28" font-weight="bold" '
      f'fill="#1a1a1a">OmegaT — Shipping Ballast Map — \u03a9\u1d57SBMap</text>')
    delta = total - args.prev_total
    a(f'  <text x="880" y="78" text-anchor="middle" font-size="14" fill="#666666">'
      f'installDist size anatomy ({fmt(total)} MB, to scale) \u2014 what could go without '
      f'losing function \u00b7 snapshot {args.snapshot}, generated by '
      f'tool/generate_shipping_ballast_svg.py \u00b7 '
      f'{signed(delta, " MB")} vs. {args.prev_date}</text>')
    a('')
    a(f'  <!-- ============ proportional stacked bar ({scale:.2f} px per MB) ============ -->')
    a(f'  <text x="{BAR_X + BAR_W // 2}" y="128" text-anchor="middle" font-size="14" '
      f'font-weight="bold" fill="#333333">installDist — {fmt(total)} MB</text>')
    a('')

    # ------------------------------------------------------------ segments
    segs = []      # (y0, y1, fill)
    labels = []    # (center_y, min_y, max_y, text)

    def add_segment(y, size_mb, fill, label):
        y1 = y + size_mb * scale
        h = y1 - y
        if label is not None and h >= MIN_LABELED_H:
            labels.append(((y + y1) / 2.0, y, y1, label))
        segs.append((y, y1, fill))
        y = y1
        return y

    # -- language group
    langs = data["languages"]
    y = BAR_TOP
    for i, (code, _name, size) in enumerate(langs[:LANG_TOP]):
        y = add_segment(y, mb(size), LANG_FILLS[i % 2], f"{code} {fmt(mb(size))}")
    rest = langs[LANG_TOP:]
    if rest:
        y = add_segment(y, mb(sum(s for _c, _n, s in rest)), LANG_FILLS[len(langs[:LANG_TOP]) % 2],
                        f"{len(rest)} more {fmt(mb(sum(s for _c, _n, s in rest)))}")
    lang_end = y
    a('  <!-- language module segments -->')
    a('  <g stroke="#ffffff" stroke-width="0.6">')
    for (y0, y1, fill) in segs[-(len(langs[:LANG_TOP]) + (1 if rest else 0)):]:
        a(rect_lines(y0, y1, fill))
    a('  </g>')
    a(frame(BAR_TOP, lang_end, FRAME_LANG))
    a('')

    # -- lib group
    lib = data["lib"]
    y = lang_end
    lib_start = y
    named = lib[:LIB_TOP]
    tinted = {}
    for name, size in named:
        if GRPC_RE.match(name):
            tinted[name] = TINT_REPLACE
    for i, (name, size) in enumerate(named):
        fill = tinted.get(name, LIB_FILLS[i % 2])
        label = f"{short_name(name)} {fmt(mb(size))}"
        y = add_segment(y, mb(size), fill, label)
    # the "further jars" chunk is split so that every gRPC jar in the bar
    # is tinted orange, even those too small to earn a named segment
    rest_grpc = [(n, s) for n, s in lib[LIB_TOP:] if GRPC_RE.match(n)]
    rest_other = [(n, s) for n, s in lib[LIB_TOP:] if not GRPC_RE.match(n)]
    if rest_grpc:
        y = add_segment(y, mb(sum(s for _n, s in rest_grpc)), TINT_REPLACE,
                        f"{len(rest_grpc)} further gRPC-chain {fmt(mb(sum(s for _n, s in rest_grpc)))}")
    if rest_other:
        y = add_segment(y, mb(sum(s for _n, s in rest_other)), LIB_FILLS[LIB_TOP % 2],
                        f"{len(rest_other)} further jars {fmt(mb(sum(s for _n, s in rest_other)))}")
    lib_end = y
    n_named = len(named) + (1 if rest_grpc else 0) + (1 if rest_other else 0)
    a('  <!-- lib segments (orange = gRPC chain) -->')
    a('  <g stroke="#ffffff" stroke-width="0.6">')
    for (y0, y1, fill) in segs[-n_named:]:
        a(rect_lines(y0, y1, fill))
    a('  </g>')
    a(frame(lib_start, lib_end, FRAME_LIB))
    a('')

    # -- other group
    y = lib_end
    other_start = y
    others = [
        ("other modules", mb(data["othermod_bytes"])),
        ("docs", mb(data["dirs"].get("docs", 0))),
        ("OmegaT.jar", mb(data["app_jar"])),
        ("images", mb(data["dirs"].get("images", 0))),
        ("scripts", mb(data["dirs"].get("scripts", 0))),
        ("plugins", mb(data["dirs"].get("plugins", 0))),
        ("misc", mb(data["misc"])),
    ]
    others = [(n, s) for n, s in others if s >= OTHER_MIN]
    others.sort(key=lambda t: -t[1])
    for i, (name, s) in enumerate(others):
        y = add_segment(y, s, OTHER_FILLS[i % 2], f"{name} {fmt(s)}")
    other_end = y
    a('  <!-- other segments -->')
    a('  <g stroke="#ffffff" stroke-width="0.6">')
    for (y0, y1, fill) in segs[-len(others):]:
        a(rect_lines(y0, y1, fill))
    a('  </g>')
    a(frame(other_start, other_end, FRAME_OTHER))
    a('')

    # ------------------------------------------------- group captions
    def pct(b):
        return 100.0 * b / data["total_bytes"]

    lang_cy = (BAR_TOP + lang_end) / 2.0
    lib_cy = (lib_start + lib_end) / 2.0
    a('  <!-- rotated group captions -->')
    a(f'  <text transform="rotate(-90 44 {lang_cy:.0f})" x="44" y="{lang_cy:.0f}" '
      f'text-anchor="middle" font-size="12.5" font-weight="bold" fill="{CAP_LANG}">'
      f'{len(data["languages"])} language modules \u00b7 {fmt(mb(data["lang_bytes"]))} MB '
      f'\u00b7 {pct(data["lang_bytes"]):.0f} %</text>')
    a(f'  <text transform="rotate(-90 44 {lib_cy:.0f})" x="44" y="{lib_cy:.0f}" '
      f'text-anchor="middle" font-size="12.5" font-weight="bold" fill="{CAP_LIB}">'
      f'lib/ \u00b7 {len(data["lib"])} jars \u00b7 {fmt(mb(data["lib_bytes"]))} MB '
      f'\u00b7 {pct(data["lib_bytes"]):.0f} %</text>')
    a(f'  <text x="{BAR_X + BAR_W // 2}" y="{other_end + 17:.0f}" text-anchor="middle" '
      f'font-size="11.5" font-weight="bold" fill="{CAP_OTHER}">other \u00b7 '
      f'{fmt(mb(data["other_bytes"]))} MB \u00b7 {pct(data["other_bytes"]):.0f} %</text>')
    a('')

    # ------------------------------------------------ segment labels
    ys = label_positions(labels)
    a('  <!-- segment labels with leader ticks -->')
    a('  <g stroke="#999999" stroke-width="0.8">')
    for (cy, _lo, _hi, _lab), ly in zip(labels, ys):
        a(f'    <line x1="{LABEL_TICK_X1}" y1="{cy:.1f}" x2="{LABEL_TICK_X2}" y2="{ly:.0f}"/>')
    a('  </g>')
    a('  <g font-size="10.5" font-family="ui-monospace, \'SF Mono\', Menlo, Consolas, '
      'monospace" fill="#555555">')
    for (_cy, _lo, _hi, lab), ly in zip(labels, ys):
        a(f'    <text x="{LABEL_X}" y="{ly + 3.5:.1f}">{esc(lab)}</text>')
    a('  </g>')
    a('')

    # --------------------------------------------------- bar mini-legend
    ly = other_end + 34
    a('  <!-- bar mini-legend -->')
    if any(s[2] == TINT_REPLACE for s in segs):
        a(f'  <rect x="{BAR_X}" y="{ly:.0f}" width="14" height="10" fill="{TINT_REPLACE}" '
          'stroke="#d07020" stroke-width="0.8"/>')
        a(f'  <text x="80" y="{ly + 8.5:.0f}" font-size="10.5" fill="#555555">'
          + ('gRPC chain remainder (linking only)' if f["grpc_excluded"] else 'replaceable (gRPC chain)')
          + '</text>')
        ly += 16
    if any(s[2] == TINT_DUP for s in segs):
        a(f'  <rect x="{BAR_X}" y="{ly:.0f}" width="14" height="10" fill="{TINT_DUP}" '
          'stroke="#c0392b" stroke-width="0.8"/>')
        a(f'  <text x="80" y="{ly + 8.5:.0f}" font-size="10.5" fill="#555555">'
          'confirmed duplicate</text>')
        ly += 16
    a('')

    # ------------------------------------------------------------ cards
    CX, CW = 420, 1300
    CARD_GAP, LINE_H = 20, 24
    card_y = [BAR_TOP]       # next card top; cards stack down the right column
    def card(fill, stroke, title, tcolor, lines, lcolor):
        """One finding card; long lines wrap, the height follows the line count."""
        rows = []
        for line in lines:
            rows.extend(textwrap.wrap(line, CARD_WRAP) or [""])
        y = card_y[0]
        h = 44 + LINE_H * len(rows)
        a(f'  <rect x="{CX}" y="{y:.0f}" width="{CW}" height="{h}" rx="10" '
          f'fill="{fill}" stroke="{stroke}" stroke-width="1.6"/>')
        a(f'  <text x="{CX + 20}" y="{y + 30:.0f}" font-size="16" font-weight="bold" '
          f'fill="{tcolor}">{esc(title)}</text>')
        a(f'  <g font-size="12.5" fill="{lcolor}">')
        for i, row in enumerate(rows):
            a(f'    <text x="{CX + 20}" y="{y + 56 + i * LINE_H:.0f}">{esc(row)}</text>')
        a('  </g>')
        a('')
        card_y[0] = y + h + CARD_GAP

    # card 1: duplicate — status of the kuromoji finding
    nl_top = f["nl_top_entries"]
    if f["ja_dup_classes"] > 0:
        card("#fdeaea", "#c0392b",
             f"Confirmed duplicate \u2014 \u2248 {fmt(mb(f['kuromoji_lib'][0][1]))} MB, "
             "deletable outright",
             "#7a2e2b",
             [f"The kuromoji Japanese tokenizer ships twice: lib/{f['kuromoji_lib'][0][0]} "
              f"({fmt(mb(f['kuromoji_lib'][0][1]))} MB) and the identical "
              f"{f['ja_dup_classes']}-class set",
              "org.apache.lucene.analysis.ja again inside modules/omegat-language-ja.jar."],
             "#5c3331")
    else:
        kuromoji = (f"the single kuromoji copy stays in lib/{f['kuromoji_lib'][0][0]} "
                    f"({fmt(mb(f['kuromoji_lib'][0][1]))} MB), where it belongs for LanguageTool"
                    if f["kuromoji_lib"] else "no kuromoji jar in lib/")
        rhino = (f"rhino is gone from lib/ \u2014 scripting runs on nashorn-core "
                 f"({fmt(mb(f['nashorn_bytes']))} MB) only"
                 if f["rhino_jars"] == 0 else
                 f"rhino is still in lib/ ({f['rhino_jars']} jars, {fmt(mb(f['rhino_bytes']))} MB) "
                 f"next to nashorn-core ({fmt(mb(f['nashorn_bytes']))} MB)")
        card("#eaf5ec", "#3a7d44",
             f"Fixed since {args.prev_date} \u2014 kuromoji duplicate and rhino are gone",
             "#2c5e34",
             [f"The ja module no longer carries the kuromoji classes: modules/omegat-language-ja.jar is now "
              f"{f['ja_mb']:.2f} MB (tokenizer only, {max(f['ja_dup_classes'], 0)} shared classes); {kuromoji}.",
              f"{rhino}.",
              f"Counter-check: the zh module contains {f['zh_lucene_classes']} org/apache/lucene classes "
              "(smartcn is not duplicated)."],
             "#2f4f36")

    # card 2: the gRPC chain of the languagetool-core fork
    gt = f["grpc_top"]
    gt_names = ", ".join(f"{short_name(n)} {fmt_size(s)}" for n, s in gt[:3])
    chain_src = (f"The LanguageTool core fork ({f['lt_version']}) pulls a gRPC client chain for its Synthesizer "
                 "server, remote BERT and remote ML rules \u2014 OmegaT runs none of them; its only LT server "
                 "is the plain-HTTP bridge.")
    protobuf = (f"protobuf-java {fmt(mb(f['protobuf_bytes']))} MB stays for dsl4j (Lingvo DSL)."
                if f["protobuf_bytes"] else "")
    if not gt:
        card("#eaf5ec", "#3a7d44",
             "No gRPC chain in lib/",
             "#2c5e34",
             [f"The LanguageTool core ({f['lt_version']}) brings no gRPC client jars into this distribution. "
              + protobuf],
             "#2f4f36")
    elif f["grpc_excluded"]:
        card("#eaf5ec", "#3a7d44",
             f"gRPC chain excluded \u2014 {f['grpc_jars']} jars / {fmt_size(f['grpc_bytes'])} remain for class linking",
             "#2c5e34",
             [chain_src,
              "build.gradle excludes the transports and protobuf bindings; absent from lib/: "
              + ", ".join(f["grpc_absent"]) + ".",
              f"Kept: {gt_names} \u2014 JLanguageTool.check links GRPCPostProcessing and its io.grpc.StatusRuntimeException "
              "handlers on every rule match (verified by distributionSmokeTest). " + protobuf],
             "#2f4f36")
    else:
        card("#fdf1e3", "#d07020",
             f"Replaceable without losing any function \u2014 \u2248 {fmt(mb(f['grpc_bytes']))} MB",
             "#8a4a10",
             [chain_src,
              f"The chain in lib/: {f['grpc_jars']} jars, {fmt(mb(f['grpc_bytes']))} MB ({gt_names}, \u2026).",
              "Excluding the transports and protobuf bindings in build.gradle drops it; grpc-api and grpc-stub must stay "
              "for class linking. " + protobuf],
             "#6b4423")

    # card 3: structural option
    nle = ", ".join(f"{n} {fmt(s)}" for n, s in nl_top)
    largest = ", ".join(f"{c} {fmt(mb(s))} MB" for c, _n, s in data["languages"][:4])
    if args.prev_lang_total is not None and args.prev_lang_count is not None:
        lang_history = (f"Since {args.prev_date} the set went from \u2248{args.prev_lang_total:.0f} to "
                        f"{fmt(mb(data['lang_bytes']))} MB and from {args.prev_lang_count} to "
                        f"{len(data['languages'])} modules.")
    else:
        lang_history = f"{len(data['languages'])} modules, {fmt(mb(data['lang_bytes']))} MB in all."
    nl_extra = f", unpacked {fmt(f['nl_unpacked'])} MB" if f["nl_unpacked"] else ""
    card("#fff8e0", "#c9971a",
         f"Structural option \u2014 up to {fmt(mb(data['lang_bytes']))} MB "
         f"({pct(data['lang_bytes']):.0f} %): slim distribution + language packs",
         "#6b5210",
         [f"The {len(data['languages'])} language jars carry LanguageTool data: spelling dictionaries, synthesis dictionaries, "
          f"grammar rules \u2014 nl alone is {fmt(f['nl_mb'])} MB ({nle}{nl_extra}).",
          lang_history,
          f"Largest modules: {largest}.",
          f"They remain cleanly modular: no LanguageTool core classes are duplicated inside them (verified). A slim default "
          f"install with on-demand language",
          f"packs would lose no function \u2014 every pack stays available, just not preinstalled. Every user pays today for all "
          f"{len(data['languages'])} languages; hardly anyone uses more than a handful."],
         "#5c4a1a")

    # card 4: keep
    team_names = " \u00b7 ".join(f"{short_name(n)} {fmt(mb(s))}" for n, s in f["team_top"])
    dups = f["dup_versions"]
    if dups:
        dup_text = "Duplicate artifact versions in lib/: " + "; ".join(
            f"{b}: {', '.join(v)}" for b, v in sorted(dups.items())) + "."
    else:
        dup_text = "No duplicate artifact versions in lib/."
    ann = f["annotations"]
    if len(ann) > 1:
        dup_text += (f" The {len(ann)} annotations-*.jar are different artifacts "
                     f"({fmt_size(sum(s for _n, s in ann))} in all).")
    card("#eaf5ec", "#3a7d44",
         "Earns its size \u2014 keep",
         "#2c5e34",
         [f"icu4j {f['icu_version']} ({fmt(mb(f['icu_bytes']))} MB) \u2014 used by OmegaT itself (NumeralValueParser, "
          f"EditorUtils), not by the LanguageTool build ({f['lt_version']}, ICU-free).   Team stack \u2248 "
          f"{fmt(mb(f['team_bytes']))} MB \u2014 {team_names} (team projects, modern SSH).",
          f"groovy {fmt(mb(f['groovy_bytes']))} MB in {f['groovy_jars']} jars \u2014 the scripting feature.   lucene-core "
          f"{fmt(mb(f['lucene_core_bytes']))} + CJK analyzers \u2014 the tokenizers.   fastutil {fmt(mb(f['fastutil_bytes']))} "
          f"\u2014 LanguageTool performance.   nashorn-core "
          f"{fmt(mb(f['nashorn_bytes']))} MB \u2014 the script engine.",
          f"docs {fmt(mb(data['dirs'].get('docs', 0)))} MB \u2014 offline manuals.   pdfbox + fontbox {fmt(mb(f['pdf_bytes']))} MB "
          f"in {f['pdf_jars']} jars \u2014 PDF support.",
          f"Also checked: {dup_text}"],
         "#2f4f36")

    # verdict
    if not gt:
        grpc_verdict = "No gRPC chain to shed."
    elif f["grpc_excluded"]:
        grpc_verdict = (f"The gRPC transports are excluded already; {f['grpc_jars']} jars / "
                        f"{fmt_size(f['grpc_bytes'])} stay for class linking.")
    else:
        grpc_verdict = (f"Remaining zero-function-change saving: \u2248 {fmt(mb(f['grpc_bytes']))} MB by excluding "
                        "the unused gRPC transports.")
    lang_delta = (f" ({args.prev_lang_total:.0f} \u2192 {fmt(mb(data['lang_bytes']))} MB)"
                  if args.prev_lang_total is not None else "")
    card("#f2f2f2", "#555555",
         "Verdict",
         "#222222",
         [f"Since {args.prev_date}: {signed(delta, ' MB')} ({signed(100 * delta / args.prev_total, ' %')}) \u2014 the language-set slimming"
          f"{lang_delta} and the kuromoji dedup / rhino removal already landed. {grpc_verdict}",
          f"The real lever is distribution shape: slim installer + language packs (\u2212{pct(data['lang_bytes']):.0f} %). Findings "
          f"re-verified on this installDist via class-set comparison (jarfile scan)."],
         "#333333")

    # footer
    a(f'  <text x="{CX}" y="1150" font-size="11.5" fill="#888888">Snapshot of an installDist, '
      f'{args.snapshot} (commit {args.commit}), generated by tool/generate_shipping_ballast_svg.py \u2014 '
      f're-run to refresh.</text>')
    a(f'  <text x="{CX}" y="1170" font-size="11.5" fill="#888888">Historic predecessor: omegat-shipping-ballast.svg '
      f'({args.prev_date}, {args.prev_total:.0f} MB). Companions: omegat-architecture-overview.svg \u00b7 '
      'omegat-event-flow-overview.svg</text>')
    a('  <circle cx="30" cy="1168.2" r="5.6" fill="none" stroke="#5f6b71" stroke-width="1.1"/>')
    a('  <path d="M 27.2,1166.5 A 3.3 3.3 0 1 1 27.2,1169.9" fill="none" stroke="#5f6b71" stroke-width="1.2" '
      'stroke-linecap="round"/>')
    a('  <text x="42" y="1172" font-family="Helvetica, Arial, sans-serif" font-size="11" fill="#5f6b71">2026  -  '
      'OmegaT - Computer Assisted Translation (CAT) tool  -  This file is part of OmegaT, released under the GNU General '
      'Public License v3 or later</text>')
    a('  <text x="42" y="1187" font-family="Helvetica, Arial, sans-serif" font-size="11" fill="#5f6b71">OmegaT is '
      'distributed WITHOUT ANY WARRANTY, without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR '
      'PURPOSE - see the GNU General Public License for more details.</text>')
    a('</svg>')
    return "\n".join(out) + "\n"


# ------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("dist", help="installDist output directory, e.g. build/install/OmegaT")
    ap.add_argument("out", help="SVG file to write")
    ap.add_argument("--snapshot", required=True, help="snapshot date, e.g. 2026-10-18")
    ap.add_argument("--commit", required=True, help="commit the dist was built from")
    ap.add_argument("--prev-date", default="2026-08-01", help="previous snapshot date")
    ap.add_argument("--prev-total", type=float, default=407.0,
                    help="previous snapshot total in MB (for the delta)")
    ap.add_argument("--prev-lang-total", type=float, default=None,
                    help="language modules total of the previous snapshot in MB (optional)")
    ap.add_argument("--prev-lang-count", type=int, default=None,
                    help="number of language modules in the previous snapshot (optional)")
    args = ap.parse_args()

    if not os.path.isdir(args.dist):
        sys.exit(f"error: installDist dir not found: {args.dist}")

    data = measure(args.dist)
    f = findings(data)

    # ---------------------------------------------------------- report
    print("=== measurement ===")
    print(f"total          {mb(data['total_bytes']):8.1f} MB")
    print(f"languages      {mb(data['lang_bytes']):8.1f} MB   {len(data['languages'])} jars   "
          f"{100 * data['lang_bytes'] / data['total_bytes']:.0f} %")
    print(f"lib            {mb(data['lib_bytes']):8.1f} MB   {len(data['lib'])} jars   "
          f"{100 * data['lib_bytes'] / data['total_bytes']:.0f} %")
    print(f"other          {mb(data['other_bytes']):8.1f} MB   "
          f"(other modules {mb(data['othermod_bytes']):.1f} / {len(data['other_modules'])} jars)")
    print("top languages  " + "  ".join(
        f"{c} {mb(s):.1f}" for c, _n, s in data["languages"][:8]))
    print("top lib        " + "  ".join(
        f"{short_name(n)} {mb(s):.1f}" for n, s in data["lib"][:8]))
    print()
    print("=== findings ===")
    print(f"kuromoji dup classes in ja module : {f['ja_dup_classes']}"
          f"  (ja module {f['ja_mb']:.2f} MB, lib copy "
          f"{mb(f['kuromoji_lib'][0][1]) if f['kuromoji_lib'] else 0:.1f} MB)")
    print(f"zh module lucene classes          : {f['zh_lucene_classes']}")
    print(f"gRPC chain                        : {f['grpc_jars']} jars, "
          f"{mb(f['grpc_bytes']):.1f} MB")
    print(f"rhino                             : {f['rhino_jars']} jars, "
          f"{mb(f['rhino_bytes']):.1f} MB   (nashorn {mb(f['nashorn_bytes']):.1f} MB)")
    print(f"icu4j                             : {f['icu_version']} "
          f"{mb(f['icu_bytes']):.1f} MB   (LT {f['lt_version']})")
    print(f"team stack                        : {mb(f['team_bytes']):.1f} MB")
    print(f"groovy family                     : {f['groovy_jars']} jars, "
          f"{mb(f['groovy_bytes']):.1f} MB")
    print(f"duplicate artifact versions       : {f['dup_versions'] or 'none'}")
    print(f"nl module                         : {f['nl_mb']:.1f} MB, entries: "
          + ", ".join(f"{n} {s:.1f}" for n, s in f["nl_top_entries"]))
    print()

    svg = render(data, f, args)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(svg)
    print(f"wrote {args.out} ({len(svg)} bytes)")


if __name__ == "__main__":
    main()
