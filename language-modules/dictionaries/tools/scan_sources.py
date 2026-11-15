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
"""Scan Hunspell dictionary collections and write the source manifest.

Reads local checkouts of LibreOffice/dictionaries and wooorm/dictionaries,
pairs every spelling dictionary with its licence, upstream reference and
file facts, and writes sources.json (machine) plus SOURCES.md (human).

Run with ``python3 -I`` and pass the checkouts as arguments; the checkouts
are untrusted data and nothing in them is executed.

@author stephan.pakebusch at zollsoft.de
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hunspell_common import ImportError_, convert_pair  # noqa: E402

# Languages that already ship as LanguageTool based modules in language-modules/.
OMEGAT_LT_MODULES = set("ar ast be br ca da de el en eo es fa fr ga gl it ja km nl pl pt ro ru sk sl sv ta tl uk zh".split())

# Languages with a dedicated tokenizer class in org.omegat.tokenizer.
OMEGAT_TOKENIZERS = set("ar hy eu bg ca zh ja ko cs da nl en fi fr gl de el hi hu id ga it lv nb nn no fa pl pt ro ru es sv th tr".split())
# Lucene analysis-common analyzers without an OmegaT tokenizer class yet.
LUCENE_ANALYZERS_UNUSED = set("bn ckb et lt ne sr ta te".split())
# Scripts that ICU4J segments with its bundled break dictionaries.
ICU_DICTIONARY_BREAK = set("th lo km my".split())

GREEN = {
    "GPL-2.0-or-later", "GPL-2.0+", "GPL-3.0", "GPL-3.0-only", "GPL-3.0-or-later", "GPL-3.0+",
    "LGPL-2.0", "LGPL-2.0-or-later", "LGPL-2.1", "LGPL-2.1-only", "LGPL-2.1-or-later", "LGPL-2.1+",
    "LGPL-3.0", "LGPL-3.0-only", "LGPL-3.0-or-later", "LGPL-3.0+",
    "MPL-2.0", "BSD", "BSD-2-Clause", "BSD-3-Clause", "MIT", "Apache-2.0", "ISC",
    "CC0-1.0", "Unlicense", "CC-BY-3.0", "CC-BY-4.0", "CC-BY-SA-4.0", "AGPL-3.0", "AGPL-3.0-or-later",
    "AGPL-3.0-only", "EUPL-1.2", "Public-Domain", "LGPL",
}
# Free licences that OmegaT can redistribute as separate data with the licence
# attached, but which the FSF lists as GPL-incompatible or which stay ambiguous.
YELLOW = {"GPL-2.0", "GPL-2.0-only", "MPL-1.1", "CC-BY-SA-3.0", "LPPL-1.3c", "EUPL-1.1", "SISSL",
          "GFDL-1.1", "GFDL-1.2", "GFDL-1.3", "GPL-1.0"}

LICENSE_PATTERNS = [
    ("AGPL-3.0", re.compile(r"GNU Affero General Public License", re.I)),
    ("LGPL-3.0", re.compile(r"(LGPL[- ]?v?3|Lesser General Public License,? (?:version|v\.?) ?3)", re.I)),
    ("LGPL-2.1", re.compile(r"(LGPL[- ]?v?2\.1|Lesser General Public License,? (?:version|v\.?) ?2\.1)", re.I)),
    ("LGPL-2.0", re.compile(r"(LGPL[- ]?v?2(?![.\d])|Library General Public License)", re.I)),
    ("GPL-3.0", re.compile(r"(GPL[- ]?v?3|General Public License,? (?:version|v\.?) ?3|GPL version 3)", re.I)),
    ("GPL-2.0", re.compile(r"(GPL[- ]?v?2|General Public License,? (?:version|v\.?) ?2|GPL version 2)", re.I)),
    ("MPL-2.0", re.compile(r"(MPL[- ]?v?2|Mozilla Public License,?\s+(?:version|v\.?)\s*2)", re.I)),
    ("MPL-1.1", re.compile(r"(MPL[- ]?v?1\.1|Mozilla Public License,?\s+(?:version|v\.?)\s*1\.1)", re.I)),
    ("CC0-1.0", re.compile(r"(CC0|Creative Commons Zero)", re.I)),
    ("LGPL", re.compile(r"(\bLGPL\b|Lesser GNU Public License|GNU Lesser General Public License)", re.I)),
    ("Apache-2.0", re.compile(r"Apache License,? (?:version|v\.?) ?2", re.I)),
    ("BSD-3-Clause", re.compile(r"(BSD|Redistributions in binary form must reproduce)", re.I)),
    ("MIT", re.compile(r"(MIT License|Permission is hereby granted, free of charge)", re.I)),
    ("CC-BY-SA-3.0", re.compile(r"(CC[- ]BY[- ]SA[- ]?3|Attribution-ShareAlike 3)", re.I)),
    ("CC-BY-SA-4.0", re.compile(r"(CC[- ]BY[- ]SA[- ]?4|Attribution-ShareAlike 4)", re.I)),
    ("CC-BY-4.0", re.compile(r"(CC[- ]BY[- ]?4\.0|Attribution 4\.0)", re.I)),
    ("CC-BY-3.0", re.compile(r"(CC[- ]BY[- ]?3\.0|Attribution 3\.0)", re.I)),
    ("GFDL-1.1", re.compile(r"Free Documentation License", re.I)),
    ("LPPL-1.3c", re.compile(r"LaTeX Project Public License", re.I)),
    ("EUPL-1.1", re.compile(r"European Union Public Licen[cs]e,? (?:version|v\.?) ?1\.1", re.I)),
    ("EUPL-1.2", re.compile(r"European Union Public Licen[cs]e,? (?:version|v\.?) ?1\.2", re.I)),
    ("SISSL", re.compile(r"Sun Industry Standards Source License", re.I)),
    ("Public-Domain", re.compile(r"public domain", re.I)),
    ("GPL", re.compile(r"(GNU General Public License|\bGPL\b)", re.I)),
]
OR_LATER = re.compile(r"(any later version|or later|or \(at your option\)|or any later|oder (?:jede )?sp[aä]tere)", re.I)
# Full GPL texts speak of "noncommercial distribution", so only explicit
# usage restrictions count here.
NON_FREE = re.compile(r"(non[- ]?commercial (?:use|purposes) only|may not be (?:sold|redistributed)|"
                      r"CC[- ]BY[- ]NC|(?-i:NonCommercial|NoDerivs))", re.I)
URL = re.compile(r"https?://[^\s<>\"')\]]+")
URL_NOISE = re.compile(r"(gnu\.org|mozilla\.org/MPL|w3\.org|openoffice\.org/2001|openoffice\.org/extensions|"
                       r"apache\.org/licenses|creativecommons\.org|opensource\.org/licenses|sourceforge\.net/projects/hunspell|"
                       r"hunspell\.github\.io|libreoffice\.org/download|documentfoundation)", re.I)
XCU_NS = {"oor": "http://openoffice.org/2001/registry"}
DESC_NS = {"d": "http://openoffice.org/extensions/description/2006", "xlink": "http://www.w3.org/1999/xlink"}


def read_text(path, limit=400_000):
    data = Path(path).read_bytes()[:limit]
    for enc in ("utf-8", "cp1252", "latin-1"):
        try:
            return data.decode(enc)
        except UnicodeDecodeError:
            continue
    return data.decode("latin-1", errors="replace")


def aff_facts(aff):
    facts = {"encoding": "UTF-8", "flag": "short", "compound": False, "iconv": False, "complexprefixes": False}
    text = read_text(aff, 2_000_000)
    for line in text.splitlines():
        parts = line.split()
        if not parts:
            continue
        key = parts[0].upper()
        if key == "SET" and len(parts) > 1:
            facts["encoding"] = parts[1].upper().replace("ISO-8859", "ISO8859")
        elif key == "FLAG" and len(parts) > 1:
            facts["flag"] = parts[1]
        elif key.startswith("COMPOUND") or key in ("ONLYINCOMPOUND", "CHECKCOMPOUNDPATTERN"):
            facts["compound"] = True
        elif key == "ICONV":
            facts["iconv"] = True
        elif key == "COMPLEXPREFIXES":
            facts["complexprefixes"] = True
    return facts


def dic_words(dic):
    """Leading number of the first .dic line; a comment may follow it (fy)."""
    with open(dic, "rb") as fh:
        first = fh.readline().strip().lstrip(b"\xef\xbb\xbf")
    m = re.match(rb"\s*(\d+)", first)
    return int(m.group(1)) if m else None


def detect_licenses(texts):
    """Return (spdx ids, or_later, non_free_hint) from licence/readme texts."""
    found = []
    or_later = False
    non_free = False
    for text in texts:
        head = text[:60_000]
        for spdx, pattern in LICENSE_PATTERNS:
            if pattern.search(head) and spdx not in found:
                found.append(spdx)
        if OR_LATER.search(head):
            or_later = True
        if NON_FREE.search(head):
            non_free = True
    if "GPL" in found and any(f.startswith("GPL-") for f in found):
        found.remove("GPL")
    if "LGPL" in found and any(f.startswith("LGPL-") for f in found):
        found.remove("LGPL")
    if "BSD-3-Clause" in found and ("GPL-2.0" in found or "GPL-3.0" in found) and len(found) > 2:
        # Full GPL texts mention BSD-style wording; keep it only when nothing else explains it.
        pass
    return found, or_later, non_free


def classify(spdx_expr, or_later, non_free, has_license_file, declared=True):
    """Map an SPDX expression (or detected list) to green / yellow / red with a reason.

    A declared expression (package.json) is a real choice between its
    alternatives. A detected list comes from reading README and licence
    files, where several licences usually mean several components, so a
    non-green member there caps the grade at yellow until someone reads
    which licence covers the spelling dictionary.
    """
    if not spdx_expr:
        return "red", "no licence statement found"
    expr = spdx_expr.strip().strip("()")
    alternatives = [a.strip().strip("()") for a in re.split(r"\s+OR\s+", expr)]
    reasons = []
    best = "red"
    for alt in alternatives:
        parts = [p.strip() for p in re.split(r"\s+AND\s+", alt)]
        grades = []
        for p in parts:
            pid = p
            if pid in ("GPL-2.0", "GPL-2.0-only") and or_later:
                pid = "GPL-2.0-or-later"
            if pid in GREEN:
                grades.append("green")
            elif pid in YELLOW:
                grades.append("yellow")
            elif pid == "GPL":
                grades.append("yellow")
            else:
                grades.append("red")
        grade = "green" if all(g == "green" for g in grades) else ("red" if "red" in grades else "yellow")
        if grade == "green" and best != "green":
            best = "green"
        elif grade == "yellow" and best == "red":
            best = "yellow"
        reasons.append(f"{alt}: {grade}")
    if not declared and best == "green" and len(alternatives) > 1 and any(": yellow" in r or ": red" in r for r in reasons):
        best = "yellow"
        reasons.append("several licences detected in the files; read which one covers the dictionary")
    if non_free and best == "green":
        best = "yellow"
        reasons.append("text hints at usage restrictions; read the licence file")
    if not has_license_file and best == "green":
        best = "yellow"
        reasons.append("no licence or README file to redistribute with the dictionary")
    return best, "; ".join(reasons)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def raw_url(collection, snapshot, rel_path):
    """Pinned download URL of a file in one of the two collections."""
    remote = (snapshot.get("remote") or "").removesuffix(".git").replace("git@github.com:", "https://github.com/")
    return f"{remote.replace('https://github.com/', 'https://raw.githubusercontent.com/')}/{snapshot['commit']}/{rel_path}"


def add_download_facts(entries, snapshots, roots):
    """Add sha256 and pinned raw URLs for the pair and its licence/README files."""
    for e in entries:
        root = roots[e["collection"]]
        snap = snapshots[e["collection"]]
        e["sha256_aff"] = sha256(root / e["aff"])
        e["sha256_dic"] = sha256(root / e["dic"])
        e["url_aff"] = raw_url(e["collection"], snap, e["aff"])
        e["url_dic"] = raw_url(e["collection"], snap, e["dic"])
        docs = []
        doc_dir = Path(e["aff"]).parent if e["collection"] == "wooorm" else Path(e["path"]).parts[0]
        for name in e["license_files"] + e["readme_files"]:
            rel = str(Path(doc_dir) / name)
            if (root / rel).is_file():
                docs.append({"name": name, "path": rel, "sha256": sha256(root / rel),
                             "url": raw_url(e["collection"], snap, rel)})
        e["documents"] = docs


def select_sources(languages):
    """Mark the sources a module bundles: the collection holding the green,
    Lucene-loadable pair with most words, and every such pair of that
    collection for the language (Serbian ships Cyrillic and Latin)."""
    for lang in languages:
        for s in lang["sources"]:
            s["selected"] = False
        if not lang.get("bundle"):
            continue
        usable = [s for s in lang["sources"] if s["grade"] == "green" and (s.get("import") or {}).get("lucene") is not False]
        if not usable:
            lang["bundle"] = False
            lang["bundle_note"] = "no green pair loads in Lucene"
            continue
        best = max(usable, key=lambda s: (s["words"] or 0))
        for s in usable:
            if s["collection"] == best["collection"]:
                s["selected"] = True


def norm_code(code):
    code = code.replace("_", "-")
    parts = code.split("-")
    out = [parts[0].lower()]
    for p in parts[1:]:
        if len(p) == 4:
            out.append(p.title())
        elif len(p) in (2, 3):
            out.append(p.upper())
        else:
            out.append(p)
    return "-".join(out)


def locate(ext_dir, relative):
    """Return the path relative to ext_dir; the LibreOffice tree keeps some
    pairs in a subfolder that packaging flattens, so fall back to the basename."""
    if (ext_dir / relative).is_file():
        return relative
    hits = [p for p in ext_dir.rglob(Path(relative).name) if p.is_file()]
    return str(hits[0].relative_to(ext_dir)) if len(hits) == 1 else relative


def scan_libreoffice(root):
    root = Path(root)
    entries = []
    for sub in sorted(p for p in root.iterdir() if p.is_dir() and (p / "dictionaries.xcu").is_file()):
        try:
            xcu = ET.parse(sub / "dictionaries.xcu").getroot()
        except ET.ParseError as ex:
            print(f"warn: {sub.name}: {ex}", file=sys.stderr)
            continue
        display, publisher, homepage, version = None, None, None, None
        desc = sub / "description.xml"
        if desc.is_file():
            try:
                droot = ET.parse(desc).getroot()
                node = droot.find("d:display-name/d:name[@lang='en-US']", DESC_NS)
                if node is None:
                    node = droot.find("d:display-name/d:name", DESC_NS)
                display = node.text.strip() if node is not None and node.text else None
                pub = droot.find("d:publisher/d:name[@lang='en-US']", DESC_NS)
                if pub is None:
                    pub = droot.find("d:publisher/d:name", DESC_NS)
                if pub is not None:
                    publisher = (pub.text or "").strip() or None
                    homepage = pub.get("{http://www.w3.org/1999/xlink}href")
                ver = droot.find("d:version", DESC_NS)
                version = ver.get("value") if ver is not None else None
            except ET.ParseError:
                pass
        # Hyphenation, thesaurus and Lightproof READMEs describe other data
        # with other licences; keep the documents about the spelling dictionary.
        doc_files = sorted(p for p in list(sub.iterdir()) + list((sub / "dictionaries").glob("*"))
                           if p.is_file()
                           and re.match(r"(?i)(readme|licen|copying|gpl|lgpl|mpl|llicen)", p.name)
                           and not re.search(r"(?i)(hyph|thes|README_th_(?!TH)|lightproof|synonym)", p.name))
        license_files = [p for p in doc_files if re.match(r"(?i)(licen|copying|gpl|lgpl|mpl|llicen)", p.name)]
        texts = [read_text(p) for p in doc_files]
        found, or_later, non_free = detect_licenses(texts)
        urls = []
        for t in texts:
            for u in URL.findall(t):
                u = u.rstrip(".,;")
                if not URL_NOISE.search(u) and u not in urls:
                    urls.append(u)
        for node in xcu.iterfind(".//node"):
            fmt = node.find("prop[@oor:name='Format']/value", XCU_NS)
            if fmt is None or (fmt.text or "").strip() != "DICT_SPELL":
                continue
            locs = node.find("prop[@oor:name='Locations']/value", XCU_NS)
            locale_values = [v.text or "" for v in node.iterfind("prop[@oor:name='Locales']/value", XCU_NS)]
            files = [locate(sub, f.replace("%origin%/", "")) for f in (locs.text or "").split()]
            aff = next((f for f in files if f.endswith(".aff")), None)
            dic = next((f for f in files if f.endswith(".dic")), None)
            if not aff or not dic or not (sub / aff).is_file() or not (sub / dic).is_file():
                print(f"warn: {sub.name}: incomplete pair {files}", file=sys.stderr)
                continue
            # Some xcu files nest the locales in <it> elements; fall back to the file stem.
            locale_list = " ".join(locale_values).split() or [Path(aff).stem.replace("-", "_")]
            spdx = " OR ".join(found) if found else None
            grade, reason = classify(spdx, or_later, non_free, bool(doc_files), declared=False)
            entries.append({
                "collection": "libreoffice",
                "path": f"{sub.name}/{aff[:-4]}",
                "locales": [norm_code(l) for l in locale_list],
                "aff": str((sub / aff).relative_to(root)),
                "dic": str((sub / dic).relative_to(root)),
                "aff_bytes": (sub / aff).stat().st_size,
                "dic_bytes": (sub / dic).stat().st_size,
                "words": dic_words(sub / dic),
                "aff_facts": aff_facts(sub / aff),
                "display_name": display,
                "publisher": publisher,
                "version": version,
                "license_spdx": spdx,
                "license_detected": True,
                "license_or_later": or_later,
                "license_files": [p.name for p in license_files],
                "readme_files": [p.name for p in doc_files if p.name.lower().startswith("readme")],
                "grade": grade,
                "grade_reason": reason,
                "upstream_urls": ([homepage] if homepage and not URL_NOISE.search(homepage) else []) + urls[:4],
                "hunspell_cli": hunspell_loads(sub / aff, sub / dic),
                "import": normalize_and_check("libreoffice", f"{sub.name}/{Path(aff).stem}", sub / aff, sub / dic),
            })
    return entries


def parse_crawl(crawl_sh):
    """Map wooorm dictionary code -> (source page, archive url) from crawl.sh."""
    sources, codes = {}, {}
    text = read_text(crawl_sh)
    for m in re.finditer(r'^crawl\s+"([^"]+)"\s+"([^"]*)"\s+"([^"]*)"', text, re.M):
        sources[m.group(1)] = {"page": m.group(2), "archive": m.group(3)}
    for m in re.finditer(r'^generate\s+"([^"]+)"\s+"([^"]+)"\s*\\\s*\n\s*"([^"]*)"\s+"([^"]*)"', text, re.M):
        codes[m.group(1)] = {"source": m.group(2), "dic_in_archive": m.group(3), "dic_encoding": m.group(4)}
    return sources, codes


def scan_wooorm(root):
    root = Path(root)
    sources, codes = parse_crawl(root / "script" / "crawl.sh")
    entries = []
    for sub in sorted(p for p in (root / "dictionaries").iterdir() if p.is_dir()):
        pkg = sub / "package.json"
        aff, dic = sub / "index.aff", sub / "index.dic"
        if not (pkg.is_file() and aff.is_file() and dic.is_file()):
            continue
        meta = json.loads(read_text(pkg))
        spdx = meta.get("license")
        readme = read_text(sub / "readme.md") if (sub / "readme.md").is_file() else ""
        src = re.search(r"^\[source\]:\s*(\S+)", readme, re.M)
        lic_text = read_text(sub / "license") if (sub / "license").is_file() else ""
        _, or_later, non_free = detect_licenses([lic_text])
        grade, reason = classify(spdx, or_later, non_free, bool(lic_text))
        code = sub.name
        crawl = codes.get(code, {})
        source = sources.get(crawl.get("source", ""), {})
        urls = []
        for u in (src.group(1) if src else None, source.get("page"), source.get("archive")):
            if u and u not in urls:
                urls.append(u)
        entries.append({
            "collection": "wooorm",
            "path": f"dictionaries/{code}",
            "locales": [norm_code(code)],
            "aff": str(aff.relative_to(root)),
            "dic": str(dic.relative_to(root)),
            "aff_bytes": aff.stat().st_size,
            "dic_bytes": dic.stat().st_size,
            "words": dic_words(dic),
            "aff_facts": aff_facts(aff),
            "display_name": meta.get("description"),
            "publisher": None,
            "version": meta.get("version"),
            "license_spdx": spdx,
            "license_detected": False,
            "license_or_later": or_later,
            "license_files": ["license"] if lic_text else [],
            "readme_files": ["readme.md"] if readme else [],
            "grade": grade,
            "grade_reason": reason,
            "upstream_urls": urls,
            "source_encoding": crawl.get("dic_encoding"),
            "hunspell_cli": hunspell_loads(aff, dic),
            "import": normalize_and_check("wooorm", code.replace("-", "_"), aff, dic),
        })
    return entries


HUNSPELL_CLI = None
NORMALIZE_DIR = None
LUCENE_LIB_DIR = None


def normalize_and_check(collection, rel_stem, aff, dic):
    """Convert the pair into the normalised tree and load it with Lucene.

    Returns a dict with the importer facts and the Lucene verdict, or None
    when no normalisation directory was given.
    """
    if NORMALIZE_DIR is None:
        return None
    out = Path(NORMALIZE_DIR) / collection / rel_stem
    try:
        facts = convert_pair(aff, dic, f"{out}.aff", f"{out}.dic")
    except ImportError_ as ex:
        return {"import_error": str(ex), "lucene": None}
    facts["normalized"] = (Path(collection) / rel_stem).as_posix()  # relative to the normalisation directory
    if LUCENE_LIB_DIR is None:
        facts["lucene"] = None
        return facts
    checker = (Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "org" / "omegat"
               / "dictionaries" / "LuceneHunspellCheck.java")
    try:
        proc = subprocess.run(["java", "-Xmx2g", "-cp", f"{LUCENE_LIB_DIR}/*", str(checker), str(out)],
                              capture_output=True, text=True, timeout=600)
        line = next((l for l in proc.stdout.splitlines() if l.startswith(("OK", "FAIL"))), proc.stderr.strip()[-200:])
    except (subprocess.TimeoutExpired, OSError) as ex:
        line = f"FAIL {ex}"
    facts["lucene"] = line.startswith("OK")
    facts["lucene_detail"] = line
    return facts


def hunspell_loads(aff, dic):
    """Return whether the hunspell CLI accepts the pair, or None when unchecked."""
    if HUNSPELL_CLI is None:
        return None
    stem = str(aff)[:-4]
    if str(dic)[:-4] != stem:
        return None
    try:
        proc = subprocess.run([HUNSPELL_CLI, "-d", stem], input="test\n", capture_output=True, text=True,
                              timeout=120)
    except (subprocess.TimeoutExpired, OSError):
        return False
    return proc.returncode == 0 and "Failure" not in proc.stderr and "Can't open" not in proc.stderr


def git_head(path):
    try:
        out = subprocess.run(["git", "-C", str(path), "log", "-1", "--format=%H %cI"],
                             capture_output=True, text=True, check=True).stdout.strip()
        sha, date = out.split()
        remote = subprocess.run(["git", "-C", str(path), "remote", "get-url", "origin"],
                                capture_output=True, text=True, check=True).stdout.strip()
        branch = subprocess.run(["git", "-C", str(path), "rev-parse", "--abbrev-ref", "HEAD"],
                                capture_output=True, text=True, check=True).stdout.strip()
        return {"commit": sha, "date": date, "remote": remote, "branch": branch}
    except (subprocess.CalledProcessError, ValueError, FileNotFoundError):
        return {"commit": None, "date": None, "remote": None, "branch": None}


def tokenizer_status(lang):
    if lang in OMEGAT_TOKENIZERS:
        return "omegat"
    if lang in LUCENE_ANALYZERS_UNUSED:
        return "lucene-unused"
    if lang in ICU_DICTIONARY_BREAK:
        return "icu-dictionary"
    return "default"


def load_exclusions(path):
    """Languages left out on purpose: {code: reason}; absent file means none."""
    if path is None or not Path(path).is_file():
        return {}
    return json.loads(Path(path).read_text(encoding="utf-8"))


def group_by_language(entries, exclusions):
    langs = {}
    for e in entries:
        for loc in e["locales"] or ["und"]:
            lang = loc.split("-")[0]
            langs.setdefault(lang, {"language": lang, "locales": set(), "sources": []})
            langs[lang]["locales"].add(loc)
        langs[lang]["sources"].append(e)
    out = []
    for lang, data in sorted(langs.items()):
        grades = [s["grade"] for s in data["sources"]]
        best = "green" if "green" in grades else ("yellow" if "yellow" in grades else "red")
        out.append({
            "language": lang,
            "locales": sorted(data["locales"]),
            "excluded": exclusions.get(lang),
            # Decision 2026-10-19: bundle green only, yellow and red are dropped.
            "bundle": best == "green" and lang not in exclusions and lang not in OMEGAT_LT_MODULES,
            "in_omegat_lt_module": lang in OMEGAT_LT_MODULES,
            "tokenizer": tokenizer_status(lang),
            "best_grade": best,
            "sources": data["sources"],
        })
    return out


def write_markdown(path, manifest):
    lines = ["# Hunspell dictionary sources", "",
             "Generated by tools/scan_sources.py; do not edit by hand.", "",
             "Snapshots:", ""]
    for name, snap in manifest["snapshots"].items():
        lines.append(f"- {name}: {snap['remote']} @ {snap['commit']} ({snap['date']})")
    lines += ["", "Grades: green = GPLv3-compatible free licence; yellow = free but GPL-incompatible or ambiguous,",
              "redistributable as separate data with its licence attached after review; red = no usable licence found.",
              "Grade \"excluded\" marks a language left out on purpose; exclusions.json has the reason.",
              "Bundle = yes: green, not excluded, no LanguageTool module yet (decision 2026-10-19: green only).", "",
              "| Lang | Locales | OmegaT module | Bundle | Tokenizer | Grade | Best source | Licence | Words | Enc | Upstream |",
              "|---|---|---|---|---|---|---|---|---|---|---|"]
    for lang in manifest["languages"]:
        srcs = sorted(lang["sources"], key=lambda s: ({"green": 0, "yellow": 1, "red": 2}[s["grade"]], -(s["words"] or 0)))
        best = srcs[0]
        upstream = best["upstream_urls"][0] if best["upstream_urls"] else ""
        lines.append("| {} | {} | {} | {} | {} | {} | {} | {} | {} | {} | {} |".format(
            lang["language"], " ".join(lang["locales"]), "yes" if lang["in_omegat_lt_module"] else "",
            "yes" if lang.get("bundle") else "", lang["tokenizer"], "excluded" if lang["excluded"] else lang["best_grade"], f"{best['collection']}:{best['path']}",
            best["license_spdx"] or "?", best["words"] or "?", best["aff_facts"]["encoding"], upstream))
    lines += ["", "## All sources", ""]
    for lang in manifest["languages"]:
        lines.append(f"### {lang['language']}")
        if lang["excluded"]:
            lines.append(f"- EXCLUDED: {lang['excluded']}")
        for s in lang["sources"]:
            cli = {True: "", False: "; hunspell CLI rejects", None: ""}[s.get("hunspell_cli")]
            imp = s.get("import") or {}
            if imp.get("lucene") is False:
                cli += f"; LUCENE REJECTS ({imp.get('lucene_detail', '')[:80]})"
            elif imp.get("import_error"):
                cli += f"; IMPORT FAILS ({imp['import_error'][:80]})"
            else:
                notes = []
                if imp.get("affix_counts_fixed"):
                    notes.append(f"{imp['affix_counts_fixed']} affix count(s) repaired")
                if imp.get("decode_errors"):
                    notes.append(f"{imp['decode_errors']} undecodable bytes replaced")
                if imp.get("declared_count") not in (None, imp.get("entries")) and imp.get("entries"):
                    notes.append("word count corrected")
                if imp.get("encoding_note"):
                    notes.append(imp["encoding_note"])
                if imp.get("mojibake_markers", 0) > 50:
                    notes.append(f"SUSPECT ENCODING: {imp['mojibake_markers']} double-encoding markers in words")
                if imp.get("verbatim_copy"):
                    notes.append(f"copied verbatim ({imp['verbatim_copy']})")
                if notes:
                    cli += "; importer: " + ", ".join(notes)
            lines.append(f"- {s['collection']}:{s['path']} [{s['grade']}] {s['license_spdx'] or '?'} "
                         f"({s['grade_reason']}); words {s['words']}, {s['aff_facts']['encoding']}, flag {s['aff_facts']['flag']}"
                         f"{', compounds' if s['aff_facts']['compound'] else ''}; licence files: {', '.join(s['license_files']) or 'none'}{cli}")
            for u in s["upstream_urls"]:
                lines.append(f"  - {u}")
        lines.append("")
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--libreoffice", required=True, help="checkout of LibreOffice/dictionaries")
    ap.add_argument("--wooorm", required=True, help="checkout of wooorm/dictionaries")
    ap.add_argument("--out-json", required=True)
    ap.add_argument("--out-md", required=True)
    ap.add_argument("--check-with-hunspell", metavar="HUNSPELL", help="hunspell executable to load-test every pair")
    ap.add_argument("--normalize-into", metavar="DIR", help="write UTF-8 normalised pairs below DIR")
    ap.add_argument("--check-with-lucene", metavar="LIBDIR",
                    help="OmegaT lib directory; load every normalised pair with Lucene (needs --normalize-into)")
    ap.add_argument("--exclusions", default=str(Path(__file__).resolve().parent.parent / "exclusions.json"),
                    help="JSON {language: reason} of languages left out on purpose")
    ap.add_argument("--skip", default="", metavar="CODES",
                    help="comma-separated language codes to leave out of the manifest entirely")
    args = ap.parse_args()
    global HUNSPELL_CLI, NORMALIZE_DIR, LUCENE_LIB_DIR
    HUNSPELL_CLI = args.check_with_hunspell
    NORMALIZE_DIR = args.normalize_into
    LUCENE_LIB_DIR = args.check_with_lucene
    skip = {c.strip() for c in args.skip.split(",") if c.strip()}
    entries = [e for e in scan_libreoffice(args.libreoffice) + scan_wooorm(args.wooorm)
               if not any(loc.split("-")[0] in skip for loc in e["locales"])]
    snapshots = {"libreoffice": git_head(args.libreoffice), "wooorm": git_head(args.wooorm)}
    add_download_facts(entries, snapshots, {"libreoffice": Path(args.libreoffice), "wooorm": Path(args.wooorm)})
    manifest = {
        "snapshots": snapshots,
        "exclusions": load_exclusions(args.exclusions),
        "languages": group_by_language(entries, load_exclusions(args.exclusions)),
    }
    select_sources(manifest["languages"])
    Path(args.out_json).write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_markdown(args.out_md, manifest)
    counts = {}
    for lang in manifest["languages"]:
        counts[lang["best_grade"]] = counts.get(lang["best_grade"], 0) + 1
    print(f"{len(entries)} dictionaries, {len(manifest['languages'])} languages, grades {counts}")


if __name__ == "__main__":
    main()
