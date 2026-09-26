#!/usr/bin/env python3
"""Print "<fork clone URL> <source branch>" for a SourceForge merge request.

SourceForge (Allura) offers no REST endpoint for merge requests, so the
source branch is read from the ref label on the MR page. Fails loudly if
the page layout changes.

Usage: python3 ci/sf-mr-source.py <MR number>
"""

import re
import sys
import urllib.parse
import urllib.request

MR_URL = "https://sourceforge.net/p/omegat/code/merge-requests/{}/"


def main() -> None:
    if len(sys.argv) != 2 or not sys.argv[1].isdigit():
        sys.exit("usage: sf-mr-source.py <MR number (digits only)>")
    url = MR_URL.format(sys.argv[1])
    with urllib.request.urlopen(url, timeout=30) as resp:
        html = resp.read().decode("utf-8", "replace")
    m = re.search(r'class="ref" href="/u/([^/"]+)/([^/"]+)/ci/(.+?)/~/"', html)
    if not m:
        sys.exit(f"Could not find the source branch on {url}")
    user, repo, branch = (urllib.parse.unquote(g) for g in m.groups())
    print(f"https://git.code.sf.net/u/{user}/{repo} {branch}")


if __name__ == "__main__":
    main()