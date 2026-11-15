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
"""Shared helpers for the Hunspell importers: encoding detection and
normalisation of .aff/.dic pairs to UTF-8 as OmegaT bundles them.

@author stephan.pakebusch at zollsoft.de
"""
import re
from pathlib import Path

SET_LINE = re.compile(rb"^\s*SET\s+(\S+)", re.M)
# UTF-8 bytes read as Latin-1 leave these pairs behind; single letters such
# as Spanish Ñ or Welsh â are not counted.
MOJIBAKE = re.compile("[\u00c3\u00c2\u00c5\u00c4][\u0080-\u00bf]|\u00e2\u20ac")

# Hunspell SET names that Python does not know under that spelling.
ENCODING_ALIASES = {
    "ISO8859-1": "iso8859-1", "ISO8859-2": "iso8859-2", "ISO8859-3": "iso8859-3",
    "ISO8859-4": "iso8859-4", "ISO8859-5": "iso8859-5", "ISO8859-6": "iso8859-6",
    "ISO8859-7": "iso8859-7", "ISO8859-8": "iso8859-8", "ISO8859-9": "iso8859-9",
    "ISO8859-10": "iso8859-10", "ISO8859-13": "iso8859-13", "ISO8859-14": "iso8859-14",
    "ISO8859-15": "iso8859-15", "KOI8-R": "koi8_r", "KOI8-U": "koi8_u",
    "MICROSOFT-CP1251": "cp1251", "CP1251": "cp1251", "CP1250": "cp1250", "CP1252": "cp1252",
    "ISCII-DEVANAGARI": None, "TIS620-2533": "tis-620", "TIS-620": "tis-620", "UTF-8": "utf-8", "UTF8": "utf-8",
}


class ImportError_(Exception):
    """Raised when a dictionary cannot be converted."""


def declared_encoding(aff_bytes):
    """Return the SET value of an affix file, defaulting to ISO8859-1 like Hunspell."""
    if aff_bytes.startswith(b"\xef\xbb\xbf"):
        # A byte order mark hides the SET line from the line-start anchor and
        # is itself a statement for UTF-8 (LibreOffice pt_BR).
        aff_bytes = aff_bytes[3:]
        if not SET_LINE.search(aff_bytes):
            return "UTF-8"
    m = SET_LINE.search(aff_bytes)
    return m.group(1).decode("ascii", "replace").upper().replace("ISO-8859", "ISO8859") if m else "ISO8859-1"


def looks_like_utf8(data):
    """True when data is valid UTF-8 and contains multi-byte sequences."""
    if not any(b > 127 for b in data[:2_000_000]):
        return False
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        return False
    return True


def python_codec(hunspell_name):
    codec = ENCODING_ALIASES.get(hunspell_name, hunspell_name.lower())
    if codec is None:
        raise ImportError_(f"encoding {hunspell_name} has no converter; add one")
    return codec


def normalise_lines(text):
    """Strip BOM, CR and trailing blanks; make sure the file ends with a newline."""
    text = text.lstrip("﻿").replace("\r\n", "\n").replace("\r", "\n")
    lines = [line.rstrip(" \t") for line in text.split("\n")]  # ASCII blanks only
    while lines and lines[-1] == "":
        lines.pop()
    return "\n".join(lines) + "\n"


# A header may carry trailing junk after the count (bn_BD: "SFX X Y 2$").
# Only ASCII blanks separate fields: flags may be any character, including
# U+00A0, which str.split() and \s would swallow.
AFFIX_HEADER = re.compile(r"^(PFX|SFX)[ \t]+([^ \t]+)[ \t]+([YN])[ \t]+(\d+)[^ \t]*[ \t]*(?:#.*)?$")


def fields(line):
    """Split an affix line on ASCII blanks only."""
    return [f for f in re.split(r"[ \t]+", line.strip(" \t\r")) if f]


def is_typo_of(token, kind):
    """True for a three-letter token one letter away from PFX/SFX (SFK, SFT)."""
    return len(token) == 3 and token != kind and sum(a != b for a, b in zip(token, kind)) == 1


def repair_affix_counts(aff_lines):
    """Fix PFX/SFX headers whose rule count does not match the rules below.

    Hunspell's C library tolerates a wrong count, a mistyped rule keyword
    (SFK, SFT) and junk after the count; Lucene's parser rejects all three,
    so several upstream files load in LibreOffice but not in OmegaT. Returns
    the corrected lines and the number of lines changed.
    """
    fixed = 0
    i = 0
    while i < len(aff_lines):
        m = AFFIX_HEADER.match(aff_lines[i].rstrip(" \t\r"))
        if not m:
            i += 1
            continue
        kind, flag, declared = m.group(1), m.group(2), int(m.group(4))
        clean_header = f"{kind} {flag} {m.group(3)} {declared}"
        j = i + 1
        while j < len(aff_lines):
            parts = fields(aff_lines[j])
            if len(parts) >= 4 and parts[1] == flag and (parts[0] == kind or is_typo_of(parts[0], kind)) \
                    and not AFFIX_HEADER.match(aff_lines[j].rstrip(" \t\r")):
                if parts[0] != kind:
                    aff_lines[j] = kind + aff_lines[j][3:]
                    fixed += 1
                j += 1
            else:
                break
        actual = j - i - 1
        if actual > 0:
            if actual != declared:
                clean_header = f"{kind} {flag} {m.group(3)} {actual}"
            if aff_lines[i].rstrip(" \t\r") != clean_header:
                aff_lines[i] = clean_header
                fixed += 1
        i = j if actual > 0 else i + 1
    return aff_lines, fixed


def copy_verbatim(aff_src, dic_src, aff_dst, dic_dst, enc_name, reason):
    """Copy a pair unchanged when normalisation would alter its meaning."""
    Path(aff_dst).parent.mkdir(parents=True, exist_ok=True)
    Path(aff_dst).write_bytes(Path(aff_src).read_bytes())
    Path(dic_dst).write_bytes(Path(dic_src).read_bytes())
    with open(dic_src, "rb") as fh:
        first = fh.readline().strip().lstrip(b"\xef\xbb\xbf")
    count_match = re.match(rb"\s*(\d+)", first)
    declared = int(count_match.group(1)) if count_match else None
    return {"source_encoding": enc_name, "declared_count": declared, "entries": declared,
            "decode_errors": 0, "affix_counts_fixed": 0, "verbatim_copy": reason}


def convert_pair(aff_src, dic_src, aff_dst, dic_dst):
    """Write a UTF-8 .aff/.dic pair; returns facts about the input.

    Both files are decoded with the encoding the affix file declares. The
    SET line is rewritten to UTF-8 and the word count on the first .dic line
    is recomputed from the actual entries, since several upstream files ship
    a stale count.
    """
    aff_bytes = Path(aff_src).read_bytes()
    dic_bytes = Path(dic_src).read_bytes()
    enc_name = declared_encoding(aff_bytes)
    codec = python_codec(enc_name)
    encoding_note = None
    if codec != "utf-8" and looks_like_utf8(aff_bytes + dic_bytes):
        # A single-byte SET over files that are valid multi-byte UTF-8 is a
        # wrong declaration; converting them as Latin-x would double-encode
        # every accented word.
        encoding_note = f"declared {enc_name}, content is valid UTF-8; read as UTF-8"
        codec = "utf-8"
    decode_errors = 0
    try:
        aff_text = aff_bytes.decode(codec)
    except UnicodeDecodeError:
        # Some affix files keep their comments in another encoding than the
        # rules (hu_HU: Latin-2 header, UTF-8 rules). Comments may lose
        # characters, a rule line that does not decode is a real error.
        decoded = []
        for raw in aff_bytes.split(b"\n"):
            try:
                decoded.append(raw.decode(codec))
            except UnicodeDecodeError:
                if not raw.lstrip().startswith(b"#"):
                    # Flags in another encoding than declared (LibreOffice
                    # hu_HU): any re-encoding would change them, so ship
                    # the pair byte for byte and let the load check decide.
                    return copy_verbatim(aff_src, dic_src, aff_dst, dic_dst, enc_name,
                                         "rule lines not decodable as declared encoding")
                decoded.append(raw.decode(codec, errors="replace"))
                decode_errors += 1
        aff_text = "\n".join(decoded)
    try:
        dic_text = dic_bytes.decode(codec)
    except UnicodeDecodeError as ex:
        raise ImportError_(f"{dic_src}: declared {enc_name} but {ex}") from ex
    aff_text = normalise_lines(aff_text)
    aff_lines = aff_text.split("\n")
    had_set = False
    for i, line in enumerate(aff_lines):
        if re.match(r"\s*SET\s+\S+", line):
            aff_lines[i] = "SET UTF-8"
            had_set = True
            break
    if not had_set:
        aff_lines.insert(0, "SET UTF-8")
    aff_lines, affix_counts_fixed = repair_affix_counts(aff_lines)
    dic_text = normalise_lines(dic_text)
    dic_lines = dic_text.split("\n")
    if dic_lines and dic_lines[-1] == "":
        dic_lines.pop()
    declared_count = None
    # The count may be followed by a comment on the same line (fy); only
    # the number is kept, the words below are what matters.
    count_match = re.match(r"\s*(\d+)(?:\s|$)", dic_lines[0]) if dic_lines else None
    if count_match:
        declared_count = int(count_match.group(1))
        dic_lines = dic_lines[1:]
    entries = [line for line in dic_lines if line.strip()]
    Path(aff_dst).parent.mkdir(parents=True, exist_ok=True)
    Path(aff_dst).write_text("\n".join(aff_lines), encoding="utf-8")
    Path(dic_dst).write_text(f"{len(entries)}\n" + "\n".join(entries) + "\n", encoding="utf-8")
    mojibake = len(MOJIBAKE.findall(dic_text))
    return {"source_encoding": enc_name, "declared_count": declared_count, "entries": len(entries),
            "decode_errors": decode_errors, "affix_counts_fixed": affix_counts_fixed,
            "encoding_note": encoding_note,
            # Share of typical double-encoding marker characters in the word list.
            "mojibake_markers": mojibake}
