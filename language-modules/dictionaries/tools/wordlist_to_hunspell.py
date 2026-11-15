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
"""Turn a plain word list into a minimal UTF-8 Hunspell pair.

Sources without affix rules (Aspell dumps via "aspell dump master",
Apertium monodix expansions, Wiktionary head-word lists) become a .dic
with one surface form per line and an .aff that only declares the
encoding and the word characters. Input lines are trimmed, deduplicated
and sorted; empty lines and lines starting with # are skipped.

Usage: python3 -I wordlist_to_hunspell.py WORDS.txt OUTDIR/xx_YY [--encoding ENC]
       [--try-chars CHARS] [--keep-case]

@author stephan.pakebusch at zollsoft.de
"""
import argparse
import sys
import unicodedata
from pathlib import Path


def read_words(path, encoding, keep_case):
    words = set()
    for line in Path(path).read_text(encoding=encoding).splitlines():
        word = line.strip().lstrip("﻿")
        if not word or word.startswith("#"):
            continue
        word = unicodedata.normalize("NFC", word.split("/")[0].split("\t")[0])
        if not keep_case and word.islower() is False and word.lower() in words:
            continue
        words.add(word)
    return sorted(words)


def word_chars(words):
    """Non-letter characters that occur inside words, for the WORDCHARS line."""
    chars = set()
    for w in words:
        for ch in w:
            if not ch.isalpha() and not ch.isspace():
                chars.add(ch)
    return "".join(sorted(chars))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("wordlist")
    ap.add_argument("target", help="output path without extension, e.g. out/fi_FI")
    ap.add_argument("--encoding", default="utf-8", help="encoding of the word list (default utf-8)")
    ap.add_argument("--try-chars", default=None, help="TRY line for suggestions; default: letters by frequency")
    ap.add_argument("--keep-case", action="store_true", help="keep capitalised duplicates of lower-case words")
    args = ap.parse_args()
    words = read_words(args.wordlist, args.encoding, args.keep_case)
    if not words:
        sys.exit("error: no words read")
    target = Path(args.target)
    target.parent.mkdir(parents=True, exist_ok=True)
    try_chars = args.try_chars
    if try_chars is None:
        freq = {}
        for w in words:
            for ch in w:
                if ch.isalpha():
                    freq[ch] = freq.get(ch, 0) + 1
        try_chars = "".join(ch for ch, _ in sorted(freq.items(), key=lambda kv: -kv[1])[:60])
    aff = ["SET UTF-8", f"TRY {try_chars}"]
    extra = word_chars(words)
    if extra:
        aff.append(f"WORDCHARS {extra}")
    target.with_suffix(".aff").write_text("\n".join(aff) + "\n", encoding="utf-8")
    target.with_suffix(".dic").write_text(f"{len(words)}\n" + "\n".join(words) + "\n", encoding="utf-8")
    print(f"{target}.dic: {len(words)} words")


if __name__ == "__main__":
    main()
