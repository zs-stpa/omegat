#!/usr/bin/env python3
# OmegaT - Computer Assisted Translation (CAT) tool
#          with fuzzy matching, translation memory, keyword search,
#          glossaries, and translation leveraging into updated projects.
#
# Copyright (C) 2026 Stephan Pakebusch
#               Home page: https://www.omegat.org/
#               Support center: https://omegat.org/support
#
# This file is part of OmegaT.
#
# OmegaT is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# OmegaT is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
"""Import spelling dictionaries from a LibreOffice extension (.oxt) or an
unpacked extension directory into UTF-8 Hunspell pairs.

The extension's dictionaries.xcu names the spelling pairs (DICT_SPELL) and
their locales; hyphenation and thesaurus entries are skipped. Every pair is
written as <locale>.aff/<locale>.dic with the locale spelled xx_YY, next to
the licence and README files of the extension, plus an import.json with the
facts needed for the module manifest.

Usage: python3 -I import_oxt.py SOURCE OUTDIR [--keep-all-locales]

@author stephan.pakebusch at zollsoft.de
"""
import argparse
import json
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

# Run with -I, the script directory is not on the path by default.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hunspell_common import ImportError_, convert_pair  # noqa: E402

XCU_NS = {"oor": "http://openoffice.org/2001/registry"}
DOC_FILE = re.compile(r"(?i)^(readme|licen|copying|gpl|lgpl|mpl|llicen|authors|changelog)")


def locale_file_stem(locale):
    parts = locale.replace("_", "-").split("-")
    stem = parts[0].lower()
    for p in parts[1:]:
        stem += "_" + (p.title() if len(p) == 4 else p.upper())
    return stem


def locate(ext_dir, relative):
    """Resolve a %origin% path; the LibreOffice git tree keeps some pairs in a
    subfolder that the packaging step flattens, so fall back to the basename."""
    direct = ext_dir / relative
    if direct.is_file():
        return direct
    hits = [p for p in ext_dir.rglob(Path(relative).name) if p.is_file()]
    return hits[0] if len(hits) == 1 else direct


def spelling_pairs(ext_dir):
    """Yield (locales, aff path, dic path) for every DICT_SPELL node."""
    xcu = ext_dir / "dictionaries.xcu"
    if not xcu.is_file():
        raise ImportError_(f"{ext_dir}: no dictionaries.xcu")
    root = ET.parse(xcu).getroot()
    for node in root.iterfind(".//node"):
        fmt = node.find("prop[@oor:name='Format']/value", XCU_NS)
        if fmt is None or (fmt.text or "").strip() != "DICT_SPELL":
            continue
        locs = node.find("prop[@oor:name='Locations']/value", XCU_NS)
        locale_values = [v.text or "" for v in node.iterfind("prop[@oor:name='Locales']/value", XCU_NS)]
        files = [locate(ext_dir, f.replace("%origin%/", "")) for f in (locs.text or "").split()]
        aff = next((f for f in files if f.suffix == ".aff"), None)
        dic = next((f for f in files if f.suffix == ".dic"), None)
        if aff is None or dic is None or not aff.is_file() or not dic.is_file():
            print(f"warn: incomplete pair in {xcu}: {[str(f) for f in files]}", file=sys.stderr)
            continue
        yield " ".join(locale_values).split() or [aff.stem.replace("-", "_")], aff, dic


def import_extension(source, outdir, keep_all_locales):
    source, outdir = Path(source), Path(outdir)
    tmp = None
    try:
        if source.is_file():
            tmp = Path(tempfile.mkdtemp(prefix="oxt-"))
            with zipfile.ZipFile(source) as zf:
                for member in zf.namelist():
                    target = (tmp / member).resolve()
                    if not str(target).startswith(str(tmp.resolve())):
                        raise ImportError_(f"{source}: refusing path {member}")
                zf.extractall(tmp)
            ext_dir = tmp
        else:
            ext_dir = source
        outdir.mkdir(parents=True, exist_ok=True)
        report = {"source": str(source), "dictionaries": [], "documents": []}
        for locales, aff, dic in spelling_pairs(ext_dir):
            targets = locales if keep_all_locales else locales[:1]
            for locale in targets:
                stem = locale_file_stem(locale)
                facts = convert_pair(aff, dic, outdir / f"{stem}.aff", outdir / f"{stem}.dic")
                facts.update({"locale": locale, "file": stem, "all_locales": locales,
                              "source_aff": aff.name, "source_dic": dic.name})
                report["dictionaries"].append(facts)
        for doc in sorted(p for p in ext_dir.iterdir() if p.is_file() and DOC_FILE.match(p.name)):
            shutil.copyfile(doc, outdir / doc.name)
            report["documents"].append(doc.name)
        (outdir / "import.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                                            encoding="utf-8")
        return report
    finally:
        if tmp is not None:
            shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("source", help=".oxt file or unpacked extension directory")
    ap.add_argument("outdir")
    ap.add_argument("--keep-all-locales", action="store_true",
                    help="write one copy per locale the extension lists, not only the first")
    args = ap.parse_args()
    try:
        report = import_extension(args.source, args.outdir, args.keep_all_locales)
    except (ImportError_, ET.ParseError, zipfile.BadZipFile) as ex:
        sys.exit(f"error: {ex}")
    for d in report["dictionaries"]:
        print(f"{d['file']}: {d['entries']} entries from {d['source_encoding']}"
              + (f" (declared {d['declared_count']})" if d["declared_count"] not in (None, d["entries"]) else ""))


if __name__ == "__main__":
    main()
