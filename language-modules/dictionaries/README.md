# Dictionary sources and artifacts for language packs

Inventory, importers and the artifact build for Hunspell spelling
dictionaries that OmegaT ships as language modules without LanguageTool.
The manifest records where each dictionary lives upstream so the content
stays maintained there; nothing is copied into the repository.

How a module gets its dictionary:

1. `dictionary-hunspell-<xx>/artifact.json` pins the upstream files (URL at a commit,
   SHA-256, licence, locales). `tools/generate_modules.py` writes it from
   `sources.json`.
2. This directory is an included build (`includeBuild` in the root
   settings.gradle) with one subproject per artifact. Its `download` task
   fetches and verifies the files, `normalize` runs
   `org.omegat.dictionaries.HunspellNormalizer` (UTF-8, repaired affix
   headers), `verifyDictionaries` loads the result with the Lucene parser
   OmegaT uses, and `jar` packs `org/omegat/dictionaries/hunspell/<stem>.aff`
   and `.dic` plus the licence files as `org.omegat.dictionaries:dictionary-hunspell-<xx>`.
3. `language-modules/<xx>` depends on that coordinate through the version
   catalog alias `libs.hunspell.<xx>`; Gradle substitutes the included
   build's jar until the artifacts are published. The module's plugin class
   registers its language codes with `BundledHunspellDictionary` in the core,
   which serves the pair; no dictionary class per module.
4. In the source distribution the artifact jar is taken from
   `lib/provided/module` like the LanguageTool jars.

Downloads happen once per pin: a file already present with the pinned
SHA-256 is not fetched again. `./gradlew -p language-modules/dictionaries
checkUpstream` compares every pinned file with the upstream branch by ETag
(two HEAD requests, no content) and lists what changed; updating a pin means
re-running the scanner on fresh clones and the generator.

Regenerate modules and artifacts after changing `sources.json`:

```
python3 -I tools/generate_modules.py --normalized <normalized dir> \
    --lucene-classpath "<OmegaT lib>/*:build/classes/java/main"
```

Files:

- `sources.json`: machine-readable manifest (one entry per language, all
  known sources, SPDX licence, grade, upstream URLs, word count, encoding,
  pinned collection commits).
- `SOURCES.md`: the same as a table plus per-source notes; generated.
- `exclusions.json`: languages left out on purpose, with the reason; the
  scanner marks them in both outputs.
- `tools/scan_sources.py`: builds both files from local checkouts of
  `LibreOffice/dictionaries` and `wooorm/dictionaries`; with
  `--normalize-into` it also writes UTF-8 pairs (the manifest records each
  pair's path relative to that directory, which `generate_modules.py` takes
  as `--normalized`) and with `--check-with-lucene` loads each one through
  `src/main/java/org/omegat/dictionaries/LuceneHunspellCheck.java`, the parser
  OmegaT uses (the hunspell CLI is more tolerant and is not the reference).
- `tools/import_oxt.py`: unpacks a LibreOffice extension (.oxt or directory)
  and writes UTF-8 `xx_YY.aff`/`xx_YY.dic` pairs plus licence files.
- `tools/wordlist_to_hunspell.py`: wraps a plain word list (Aspell dump,
  Apertium expansion) into a minimal Hunspell pair.
- `tools/hunspell_common.py`: shared encoding normalisation.

Regenerate:

```
git clone --depth 1 https://github.com/LibreOffice/dictionaries lo
git clone --depth 1 https://github.com/wooorm/dictionaries wooorm
python3 -I tools/scan_sources.py --libreoffice lo --wooorm wooorm \
    --out-json sources.json --out-md SOURCES.md --check-with-hunspell hunspell \
    --normalize-into normalized --check-with-lucene ../../build/install/OmegaT/lib \
    --skip he
```

Grades: green = GPLv3-compatible free licence; yellow = free but
GPL-incompatible or unversioned, needs a reading of the licence file before
bundling; red = no licence statement found in the dictionary's own files.

## Network, offline builds and publication

The included build fetches the pinned upstream files on first use, so a fresh
checkout needs network access for `raw.githubusercontent.com`; afterwards the
checksummed copies under `build/download` are reused and `--offline` works.
An offline build with a missing file fails with a message instead of a vague
resolution error.

The included build is the stopgap until the artifacts are published under
`org.omegat.dictionaries`. From then on, `-Pomegat.dictionaries=repository`
(root `settings.gradle`) leaves the included build out and the modules resolve
`dictionary-hunspell-<xx>` like any other dependency. Publishing needs the
project's repository credentials (`publishToMavenLocal` per artifact exists for
local trials).

The artifact names start with `dictionary-` on purpose: `providedModuleLib`
resolves names as the glob `<name>-*.jar`, so `hunspell-<xx>` would have been
swept into the Hunspell spell checker module of the source distribution.
`LanguageModuleContentTest` guards against such prefixes.
