# FB2 import follow-up — 2026-10-18

Owner requested an import audit after seeing a missing cover and weaker/less text
than in Moon+ Reader. The concrete book was requested by local path; it was not
available during the initial audit. Do not claim its content was compared directly.

## Findings and changes

The metadata importer returned only title/author and exited at description. It
never resolved coverpage/image into the matching embedded binary, so FB2 cover
bytes were always null. New streaming metadata extraction follows the local image
reference, tolerates namespace prefixes, skips unrelated binaries, bounds Base64
buffers and checks raster image bounds without decoding the whole bitmap.

Book title, all authors (including middle names/nickname-only authors) and plain
annotation are read from description/title-info. Scanner/document authors and
original-language metadata stay separate. Annotation is stored as the imported
book description. Existing custom title/author/description are not overwritten.

Opening an older FB2 with an empty cover field tries local extraction once per
source fingerprint per repository instance. The database updates only a still-empty
cover field, preserving concurrent edits, IDs, annotations, completion, progress,
reading/listening anchors and offsets. A custom existing cover is kept. Optional
cover failures never prevent reading. No Room schema or backup format is changed.

The structured reader previously threw on malformed/oversized optional image data,
which could reject an otherwise readable book. Invalid images are now skipped;
subsequent text and valid images remain available. The 20MiB per-image limit stays.
Epigraphs, quotation authors and table headers retain supported quote/italic/bold
styles. Existing readable-text characters/order/source offsets are unchanged, so
content revision remains 2. Disposable cache version advances to 3 to rebuild
resources/style data; original files and user data are retained. The legacy revision-1
parser remains untouched for anchor migration compatibility.

## Verification

- Four new Android FB2 tests passed, 13.572s: namespace-prefixed title-info with
  a cover binary at file end; Windows-1251 book with all 12,000 ordered text markers
  retained across multiple/nested bodies and disk cache restart; invalid image data
  with subsequent text/valid image preserved; import plus old-record cover repair,
  exact book-field comparison, retained bookmark ID and custom-cover protection.
- All 75 JVM tests pass after the import changes.
- Packaged APK permissions contain no INTERNET or ACCESS_NETWORK_STATE; no new
  runtime dependency or permission added.
- Final combined build: BUILD SUCCESSFUL, 6m24s; lint 0 errors / 83 warnings.
  Warnings are retained visibly; no new suppression/baseline was introduced.
- Actual MainActivity smoke test: library → drawer → Settings → Appearance;
  exactly one theme choice plus motion, choose Night, Back to library, force-stop
  and relaunch. Night persisted. Settings home and relaunched library screenshots
  visually inspected (`comfort-settings.png`, `comfort-relaunch.png`).
- The emulator's old quick-boot state produced Launcher/System UI ANRs; cold boot
  without a snapshot restored usable system UI. Initial startup under build load
  was slow; no release/performance improvement is claimed.
- Final combined instrumentation run: **38 tests passed**, zero failures/skips,
  82.929s, including all new FB2 cases, library/settings, geometry/restoration,
  migrations/backups/PDF/cover lifecycle and both bundled local voices.
  Log: `C:/Users/TopMob/.codex/tools/bookmt-audit/comfort-combined-android.txt`.
- `python docs/ux/verify_spec.py`: 232 contrast checks and runtime token parity;
  `git diff --check` passed. Debug APK built at
  `app/build/outputs/apk/debug/app-debug.apk`.

Logs: `C:/Users/TopMob/.codex/tools/bookmt-audit/comfort-fb2-build.log` and
`comfort-fb2-tests.txt`. Android API 35 emulator only; no physical phone used.

## Scope and limits

This increment covers raw XML FB2. Compressed .fb2.zip dispatch, full table layout,
arbitrary embedded CSS, SVG covers and speculative formatting options are not added.
Body text, multiple bodies, nested sections, verse, footnotes, supported inline
styles and local images use the existing structured reader. The new long-book test
checks marker sequence against source through disk cache restart, rather than
comparing page count (which depends on font, margins and layout). It does not replace
a direct comparison of the owner's particular file with Moon+ Reader.

Format reference: [FictionBook schema maintained by its author](https://github.com/gribuser/fb2/blob/master/FictionBook.xsd).
Only the schema's structural rules were consulted; no schema code or book content
was copied into the application. Runtime parsing remains local with no downloads,
HTTP client or Internet permission.
