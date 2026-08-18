#!/usr/bin/env python3
# *************************************************************************
#  OmegaT - Computer Assisted Translation (CAT) tool
#           with fuzzy matching, translation memory, keyword search,
#           glossaries, and translation leveraging into updated projects.
#
#  Copyright (C) 2026 Stephan Pakebusch
#                Home page: https://www.omegat.org/
#                Support center: https://omegat.org/support
#
#  This file is part of OmegaT.
#
#  OmegaT is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  OmegaT is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program.  If not, see <https://www.gnu.org/licenses/>.
# *************************************************************************
"""Generate the dev-doc slide on paint-time editor colours.

Run from this directory to regenerate the SVG in place:

    python3 make_paint_time_slide.py

The OmegaT logo paths are read from the project's images/OmegaT.svg, so the
script must see the repository root above it (or OMEGAT_LOGO pointing at that
file).

@author Stephan Pakebusch
"""
import html
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def find_logo():
    """The project's images/OmegaT.svg, looked up above this script."""
    env = os.environ.get("OMEGAT_LOGO")
    if env:
        return env
    directory = HERE
    for _ in range(8):
        candidate = os.path.join(directory, "images", "OmegaT.svg")
        if os.path.isfile(candidate):
            return candidate
        parent = os.path.dirname(directory)
        if parent == directory:
            break
        directory = parent
    sys.exit("images/OmegaT.svg not found; set OMEGAT_LOGO to its path")


def load_logo(x, y, size):
    """The project logo file embedded verbatim as a nested svg element.

    Nothing inside images/OmegaT.svg is rewritten - its gradients, filters and
    viewBox travel along - only the XML declaration is dropped (a nested
    element must not carry one) and the root tag gets the position and display
    size it is placed at.
    """
    svg = open(find_logo()).read()
    svg = re.sub(r"^\s*<\?xml[^>]*\?>\s*", "", svg)
    root = re.match(r"<svg\b[^>]*>", svg, re.S)
    if not root:
        sys.exit("images/OmegaT.svg does not start with an svg element")
    tag = root.group()
    tag = re.sub(r'\swidth="[^"]*"', "", tag, count=1)
    tag = re.sub(r'\sheight="[^"]*"', "", tag, count=1)
    tag = tag[: -1].rstrip() + f'\n   x="{x}" y="{y}" width="{size}" height="{size}">'
    return tag + svg[root.end():].rstrip()


LOGO = load_logo(26, 12, 48)

INK = "#1e282c"
GREY = "#5f6b71"
PANEL = "#f4f6f7"
BORDER = "#c8d0d4"
RED = "#ef3b39"
GREEN = "#2e6b4f"
GREEN_BG = "#e9f5ee"
RED_BG = "#fdecec"
BLUE = "#1f5673"
BLUE_BG = "#eef4f8"

HEADER = """<!--
  {desc}

  **************************************************************************
  OmegaT - Computer Assisted Translation (CAT) tool
           with fuzzy matching, translation memory, keyword search,
           glossaries, and translation leveraging into updated projects.

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
  **************************************************************************
-->"""


def esc(s):
    return html.escape(s, quote=False)


def text(x, y, s, size=12.5, fill=INK, weight="normal", anchor="start", family=None):
    fam = ' font-family="Menlo, Consolas, monospace"' if family == "mono" else ""
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" '
            f'font-weight="{weight}" text-anchor="{anchor}"{fam}>{esc(s)}</text>')


def step(x, y, w, n, title, detail, accent=INK, bg=PANEL, h=56):
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{bg}" '
           f'stroke="{BORDER}" stroke-width="1.2"/>',
           f'<circle cx="{x + 20}" cy="{y + h / 2}" r="12" fill="{accent}"/>',
           text(x + 20, y + h / 2 + 4.5, str(n), 12.5, "#ffffff", "bold", "middle"),
           text(x + 42, y + 23, title, 13, INK, "bold")]
    if detail:
        out.append(text(x + 42, y + 42, detail, 11.5, GREY, family="mono"))
    return "\n".join(out)


def arrow(x1, y1, x2, y2, color=INK, width=1.4, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<path d="M{x1} {y1} L{x2} {y2}" stroke="{color}" stroke-width="{width}" '
            f'fill="none"{d} marker-end="url(#arrow)"/>')


def callout(x, y, w, label, lines, accent):
    h = 28 + 17 * len(lines)
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" '
           f'fill="{RED_BG if accent == RED else GREEN_BG}" stroke="{accent}" '
           f'stroke-width="1.2"/>',
           text(x + 12, y + 19, label, 11.5, accent, "bold")]
    ly = y + 37
    for ln in lines:
        out.append(text(x + 12, ly, ln, 11.3, INK))
        ly += 17
    return "\n".join(out), h


def footer():
    """The two mandated grey licence lines, one text run each."""
    l1 = ("2026 - This file is part of OmegaT, released under the GNU General Public "
          "License version 3 or (at your option) any later version.")
    l2 = ("OmegaT is distributed WITHOUT ANY WARRANTY, without even the implied warranty "
          "of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE - see the GNU General "
          "Public License for more details.")
    return "\n".join([text(32, 676, l1, 11, GREY),
                      text(32, 691, l2, 11, GREY)])


def defs():
    return f'''<defs>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7"
            markerHeight="7" orient="auto-start-reverse">
      <path d="M0 0 L10 5 L0 10 z" fill="{INK}"/>
    </marker>
  </defs>'''


def slide(desc, title, subtitle, body):
    return f'''{HEADER.format(desc=desc)}
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 700" width="1200"
     height="700" font-family="Helvetica, Arial, sans-serif">
  {defs()}
  <rect width="1200" height="700" fill="#ffffff"/>
  {LOGO}
  {text(90, 40, title, 25, INK, "bold")}
  {text(90, 61, subtitle, 12.5, GREY)}
  <path d="M32 74 H1168" stroke="{BORDER}" stroke-width="1"/>
  {body}
  <path d="M32 655 H1168" stroke="{BORDER}" stroke-width="1"/>
  {footer()}
</svg>
'''


# ------------------------------------------------------------------ the slide
COL_W = 536
LEFT = 32
RIGHT = 632

b = [f'<path d="M600 86 V640" stroke="{BORDER}" stroke-width="1" '
     'stroke-dasharray="4 4"/>']

b.append(text(LEFT, 102, "BEFORE - EVERY COLOUR CHANGE REBUILDS THE DOCUMENT",
              12, RED, "bold"))
b.append(text(RIGHT, 102, "AFTER - THE PALETTE RESOLVES WHEN PAINTING",
              12, GREEN, "bold"))

# left column: the rebuild pipeline
y = 122
b.append(step(LEFT, y, COL_W, 1, "Colors preferences: Apply",
              "style.setColor(...) per palette entry", RED))
b.append(arrow(LEFT + 24, y + 56, LEFT + 24, y + 78, GREY))
y += 78
b.append(step(LEFT, y, COL_W, 2, "Full editor view refresh",
              "refreshEditorView() -> editor.refreshView(true)", RED))
b.append(arrow(LEFT + 24, y + 56, LEFT + 24, y + 78, GREY))
y += 78
b.append(step(LEFT, y, COL_W, 3, "gotoFile() builds a new Document3",
              "text re-inserted, markers re-run, layout from scratch", RED))
b.append(arrow(LEFT + 24, y + 56, LEFT + 24, y + 78, GREY))
y += 78
b.append(step(LEFT, y, COL_W, 4, "Colours frozen into the attributes",
              "StyleConstants snapshot per span (createAttributeSet)", RED))
y += 80
co, _ = callout(LEFT, y, COL_W, "COST",
                ["O(entire document) on the EDT - seconds on large files,",
                 "caret and scroll position must be saved and restored,",
                 "far too slow for flash notifications."], RED)
b.append(co)

# right column: paint-time resolution
y = 122
b.append(step(RIGHT, y, COL_W, 1, "Colors preferences: Apply",
              "style.setColor(...) + CoreEvents.fireColorsChanged()", GREEN))
b.append(arrow(RIGHT + 24, y + 56, RIGHT + 24, y + 78, GREY))
y += 78
b.append(step(RIGHT, y, COL_W, 2, "Spans stay bound to palette entries",
              "createBoundAttributeSet: attribute carries the EditorColor",
              GREEN))
b.append(arrow(RIGHT + 24, y + 56, RIGHT + 24, y + 78, GREY))
y += 78
b.append(step(RIGHT, y, COL_W, 3, "Views resolve colours while painting",
              "ViewLabel.getForeground()/getBackground() ask the palette",
              GREEN))
b.append(arrow(RIGHT + 24, y + 56, RIGHT + 24, y + 78, GREY))
y += 78
b.append(step(RIGHT, y, COL_W, 4, "repaint() - document untouched",
              "no re-attribution, no markers, no layout", GREEN))
y += 80
co, _ = callout(RIGHT, y, COL_W, "GAIN",
                ["O(visible viewport) - instantaneous at any document size,",
                 "editor settings still rebuild only when they must",
                 "(requiresEditorRefresh), groundwork for flash",
                 "notifications and earcon-style event styling."], GREEN)
b.append(co)

SLIDE = slide("Paint-time editor colours: before, a colour change rebuilt the "
              "whole editor document; after, bound spans resolve the palette "
              "when painting and a repaint suffices.",
              "Paint-time editor colours",
              "topic/stpa/editor/paint-time-colors - decouple colour switching "
              "from document rebuilds",
              "\n".join(b))

with open(os.path.join(HERE, "paint-time-colors.svg"), "w") as f:
    f.write(SLIDE)
print("wrote paint-time-colors.svg")
