# Implementation roadmap and verification

## Instructions for the next implementation agent

Read `AGENTS.md`, this folder's README, interaction contract and the specific phase
before modifying source. This specification is a target design, not evidence that
features already exist. Inspect current code again if HEAD differs from baseline.
Use current Android/Compose APIs available in the repository; snippets here are
behavioral pseudocode. Do not add a UI framework, animation library, network client,
cloud backend, system TTS fallback or remote fonts to achieve this design.

Complete one coherent phase, validate it, update status and commit/push. Preserve
unrelated user files (currently `что бы не забыть.txt` is untracked). Avoid a broad
rewrite of ReaderViewModel/pagination while changing chrome. Any migration remains
non-destructive. Create no fake/inert settings while waiting for backend work.

## Phase dependency map

```text
P0 inventory/baseline -> P1 tokens & interaction primitives
P1 -> P2 library/settings + reader chrome
P2 -> P3 data/task workflows
P2 -> P4 reading tools/selection
P2 -> P5 speech UX/timer (P4 needed for start-from-in-place-selection)
P5 -> P6 pronunciation experiment -> dictionary only after evidence gate
P1/P2 -> P7 custom themes and final polish
```

Do not treat phases as a mandate to finish every future feature in a single change.
Priority within each phase is correctness/legibility, then convenience, then motion.
P0–P2 form the first useful interface release. P3–P7 are subsequent scoped deliveries.

## P0 — Baseline and durable preference model

Deliverables: inventory checked against README; screenshots of existing app in
Day/Night; timings/frame traces on emulator with hardware noted; backup fixture;
settings migration proposal. No subjective “faster/smoother” claims without comparison.
Record existing text-position regression tests and keep them green throughout.

Model new settings explicitly: appThemeMode (shared app/reader; owner revision 2026-10-18), lastLightTheme,
lastDarkTheme, motionMode, hapticsPreference, pageAnimation, tapPreset,
brightnessGestureEnabled, followSpeech preference if needed. Existing font/margins/
voice/pause/brightness/volume/fullscreen/orientation values stay unchanged.
Do not treat an absent preference as a deliberately chosen default. Version the
initialization/migration path and run once; backup optional keys must restore safely.
New-install defaults differ from retained old user preferences as specified.

Gate: upgrades and restore preserve old books, settings, marks and both positions;
old backups without new fields remain readable. No new Internet permissions in
merged debug/release manifests. Select actual SDK/test tools from local configuration.

## P1 — Visual and interaction foundation

Implement token models, Material mapping, reader-scope theme, all built-in palettes,
typography/spacing, 48dp hit targets, shared row/sheet/dialog/error/empty components.
Central motion vocabulary, animation scale integration, focus semantics, Back stack
rules. Persist/resolved launch theme to avoid a white startup flash. Consolidate
UI copy into Russian/English resources.

Source focus: `ui/theme/*`, `presentation/theme/ReaderColors.kt`, reusable presentation
components and settings DataStore/repositories. Do not mutate annotation stored
colors to make screenshots prettier. Add contrast checking using provided verifier.

Gate: all theme/component states at 1×/1.5×/2× font scale; animation disabled system
and app; keyboard/TalkBack; no default purple or light dialog in dark reader.
Motion must not trigger pagination, data writes or recomposition of all book text.

## P2 — Navigation, library, settings and reader chrome

Implement L01–L05 structure, L07 details/edit separation, S01–S03 grouping, R01–R06
chrome and tool navigation where existing engine supports them. Move Continue inline;
split filters/statistics/maintenance; relocate view/sort; preserve list/query state.
Appearance shortcuts share the same settings state. Use T06 to stop repagination
per slider tick. Existing font and source import remain functional.

Reader controls and page effects are independently reviewable commits. Keep current
selection dialog until P4. R11 PDF chrome/explicit Fit/page-number action can ship
here using existing PDF engine. No nonexistent PDF search or TTS button.

Gate: library -> reader -> Back restores list, reader stops TTS on exit; bars toggle
without page reflow; first visible text correct; Apply/newest layout wins. Old
position/cache/PDF lifecycle regression suites pass. Visible menus owned by BookMT
except intentional platform handoffs. No import/delete functionality lost in redesign.

## P3 — Data operations and file comfort

Implement L06, S04/S05 and metadata draft safety: operation IDs/states, cancel at safe
boundaries, per-file results, source repair, restore preview and final confirmation,
safe relaunch/recovery screen, cache accounting/cleanup. This phase changes workflow
logic, not just styling. Verify current journal semantics before adapting UI.

Gate: failure/cancel/process interruption per workflow, low-space condition, legacy
ZIP and settings-only ZIP, owned/external source handling, duplicate import, staged
cover/font lifecycle. Restore must never show success on rollback. Manual backup
must include newly added preferences. Test on disposable app data, not owner's library.

## P4 — Reading tools and annotations

Implement L08, R07–R09: consistent marks/editor, local export, handles in original
text, accessible custom action toolbar, note drafts, jump origin, footnote/image
viewer constraints. Single-page selection first; cross-page only after anchor and
gesture tests. Actual Undo requires reversible data operation and retained ID; keep
confirmation until ready. No new parser/table feature hidden inside this UI phase.

Gate: correct Unicode range, copying source text, selection across text styles,
large font, rotated page, saved mark after reopening, no duplicate platform toolbar,
no page turn when link/handle consumed input. Fullscreen image pan cannot lose image.

## P5 — Speech usability and timer

Implement A01–A05: truthful state machine, adequate hit targets, safe compact control
area, explicit start locations, follow/manual browsing, sample preview, pending
settings and Apply & continue. Add timer and phrase skip only with complete engine
support; preserve queued-session identity and stop semantics. No library mini-player.

Gate: both voices produce real sound offline; start/pause during preparation/stop,
late callback after leaving, rapid book switch, denied notification, timer with
pause/lock/deadline/preparing, voice preview while book paused. Emulator audio tests
are not a human pronunciation judgment or a physical Bluetooth validation.

## P6 — Pronunciation experiment and optional dictionary

P6a produces local WAV corpus + result table and unsupported/supported notation
decision; no default speech changes. Human sample review is an external acceptance
dependency: if unavailable, mark quality assessment pending and ship no claimed
stress fix. Continue independent interface tasks instead of inventing results.
P6b implements A07 only with a verified supported representation, rule precedence,
source mapping, snapshot/queue semantics, import/export/backup and scope UI.
Conservative normalization A08 is separately evaluated, not bundled blindly.

Gate: same paragraph highlight/positions before and after replacements; ambiguous
words don't get a global rule without explicit scope; dictionary survives backup;
no matching inside longer words, no recursive replacement, both voices evaluated.

## P7 — Custom palettes and final consistency

Implement custom editor only with all dependent colors validated, preview/apply/
cancel and backup support. Finish large-screen adaptations, accessibility semantics,
no-motion mode and copy consistency. Tune motion only using recorded frame traces.
Do not expand into PDF OCR, series parsing, online catalogs or character voices.

## Performance acceptance methodology

These are targets for comparison, not existing measured facts:

- Static reader after settle: no app animation loop or repeated pagination; optional
  progress changes are driven by reading/playback, not a 60Hz timer.
- Tap indication visible next frame where possible; p95 input-to-feedback target
  <=100ms on nominated test device. Loading data may take longer and is separate.
- At 60Hz target <=16.7ms per frame, at 120Hz <=8.3ms; report missed-frame distribution,
  p50/p95 and worst stalls. Aim <5% missed frames on repeated common interactions;
  do not claim success from average FPS alone or emulator alone.
- Warm book-opening p95 should not regress by >10% versus same-build baseline setup
  and dataset, without a documented correctness reason. Record cold separately.
- Retain at most necessary viewport/adjacent animation content; no whole-book image
  cache. Compare steady and peak memory after 20 repeated open/close cycles, not a
  single noisy PSS sample; investigate sustained growth, set budget after baseline.
- Animation must not starve synthesis. Compare audible gaps/underruns and generation
  time with UI idle vs opening controls; no quality claim based solely on CPU percent.
- Run realistic large FB2/EPUB, image-heavy EPUB, TXT, PDF and malformed file fixtures.
  For repeated measurement use same content/hash, font, width, cache state and thermal
  conditions; make benchmark fixtures anonymous/local and legally usable.

Use release/profileable behavior for performance conclusions where possible; debug
instrumentation for correctness. Never increase synthesis threads or disable safety
checks just to improve animation numbers. Tune expensive layout first, duration last.

## Acceptance matrix

| Area | Required scenarios | Proof |
|---|---|---|
| Themes | 4 palettes × library/reader/sheet/dialog/error/IME inset; night startup | Screenshots + token contrast output |
| Motion | system zero, app minimal/off, rapid reverse, predictive Back cancel | Screen recording + assertions of final state |
| Accessibility | TalkBack, keyboard, switch-access-compatible actions; 200% text | Manual emulator pass + semantics checks |
| Layout | 320/360/412/600/840dp, landscape short height, split window, cutouts | Representative screenshot matrix, no clipping |
| Library | 0/1/20/1000 books; no results; long titles; restored query/scroll | UI tests for high-risk navigation plus inspection |
| Reader | same passage after rotation/font/fullscreen/exit; cache pending/corrupt | Existing and targeted geometry regression tests |
| Actions | repeated taps, cancel opening animation, keyboard Back, dirty draft | State-focused tests; no duplicate mutations |
| Files | picker canceled/denied/source missing/disk full/invalid data | Disposable fixtures, ID and content hashes |
| Backup | old/new ZIP, partial failure, interrupted install, retry rollback | Existing integration suite + new preview workflow checks |
| Marks | original-text copy, note draft, overlapping marks, export scope | Anchor/range verification and restore |
| TTS | both voices, states, exit/lock/Home, preview/timer/follow | Engine + instrumentation; listening separately labeled |
| Offline | airplane mode with local content, final manifests | Actual packaged build inspection, no network permissions |
| Physical device | Bluetooth/headset/calls/long playback/battery/comfort | Pending owner-authorized test, never silently assumed |

Prioritize functional tests of behavior that can lose data or navigate wrong, not
tests asserting that a color constant equals itself. UI snapshots complement but
do not replace functionality/accessibility checks. Token verifier is a docs-only
design check, not a new runtime dependency.

## Per-phase completion record

Create/update one short implementation report with: phase/surface IDs implemented,
before/after screenshots or local paths, tested build/commands, counts/failures,
known limitations, performance evidence where relevant, migration/backup behavior,
commit and push result. If something is deferred, list it explicitly; do not mark
entire phase complete because happy-path screenshots look correct.

Suggested implementation verification (adapt to local SDK/emulator availability):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --continue --console=plain --max-workers=2
# Run connectedDebugAndroidTest against explicitly selected disposable emulator.
# Inspect merged manifests for INTERNET / ACCESS_NETWORK_STATE and final APK permissions.
git diff --check
git status --short
```

No screenshots/performance traces containing owner's book text or private annotations
in Git without explicit authorization. Documentation creation itself needs no APK
build: verify links, JSON, contrast and repository diff instead.
