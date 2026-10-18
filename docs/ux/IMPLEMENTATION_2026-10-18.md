# First implementation increment — 2026-10-18

Scope: durable preference migration and the shared visual/motion foundation of P0/P1.
This is not completion of the whole roadmap. Screen restructuring, advanced text
selection, sleep timer and pronunciation dictionary remain later phases.

## Delivered behavior

- All four exact palettes are compiled into Kotlin and mapped to Material roles,
  including fixed roles; wallpaper colors and template purple defaults removed.
- App theme and reader inheritance/override are explicit choices. Preset choice
  uses an in-app themed radio dialog. Reader menus and dialogs inherit its palette.
- Reader's day/night shortcut remembers its light/dark choices, including Sepia
  and Black, instead of cycling unpredictably through themes.
- UI typography/spacing/shapes are shared. Compact speech buttons have 48dp targets.
- Shared choice rows, empty/error states and delayed loading indicators added.
  Empty library now explains how to start; filtered-empty state is distinct.
- Reader bar motion is 160/120ms with only 8dp travel. Side-panel enter/exit now
  actually animates and uses palette-specific scrim. Navigation has short transitions;
  reader text does not fade through another page during navigation.
- App-owned scroll animations honor reduced motion. Window recomposer observes
  Android animator scale and applies it exactly once. No device setting is changed
  by the app. Reduced-motion loading uses static text instead of an infinite spinner.
- Known legacy highlight colors map to readable theme fills for display. Arbitrary
  old colors are adapted for contrast; original stored ARGB and text ranges stay intact.
- Reader waits for loaded settings before rendering its initial content/theme.

## Persistence and upgrade decisions

Appearance keys live in the original reader Preferences DataStore, which is already
part of ZIP backups. Room version and book/mark/position formats are unchanged.
`comfort_ui_version=1` is installed through a one-time, non-destructive DataStore
migration. Existing settings and unknown keys are retained. Old installs with an
empty preference file are recognized by package install/update timestamps and keep
old reader defaults; a fresh install follows Android day/night and leaves volume
buttons to the system by default. Initialization is written once, so a subsequent
update cannot mistake a new installation for an old one.

The small launch cache is derived from successfully loaded settings, not exported
as user data. ZIP restore invalidates it before opening restored preferences.
A bootstrap timeout or read failure does not persist a guessed theme as a choice.
The activity background uses the launch palette before composing the navigation
graph. Android's own starting splash remains OS-owned; on Android 12+ its first
frame follows manifest day/night resources, so an explicitly different app palette
cannot be guaranteed before the activity gets control.

## Conservative compatibility choice

The pinned Material3 1.4.0 exposes its motion-scheme implementation only internally
to Kotlin callers. No internal API bypass, dependency upgrade or animation library
was introduced. Both reduced modes snap legacy Material effects at the window
recomposer level; the app motion policy still distinguishes zero motion from the
maximum-80ms effects contract for future controlled components. Currently Minimal
and Off therefore look alike for many components. Native system-window animations
and system file pickers remain Android-owned. Exact per-component reduced effects
and the remaining motion vocabulary must be completed during later screen work.

## Verification

- Unit tests: 71 passed, zero failures/errors/skips, including legacy/new settings,
  durable migration, unknown keys, appearance round trips and annotation contrast.
- Token verification: 232 contrast checks; runtime Kotlin palettes also checked
  against the JSON source of truth.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline
  --continue --console=plain --max-workers=2`: **BUILD SUCCESSFUL**.
- Lint: **0 errors, 68 warnings** (the previous project report had 64 warnings;
  warnings were not suppressed or baseline-hidden).
- Android instrumentation on the API 35 emulator in airplane mode: **29 tests passed**,
  93.035 seconds, no failures/skips. Includes the existing geometry/restoration,
  Room/backup/PDF/cover lifecycle suites, both real local voices and new appearance tests.
- New UI tests produced 12 screenshots: four palettes at 1.0/1.5/2.0 font scale.
  All four 2.0-scale captures were visually inspected; new controls wrap and remain
  reachable. This is a component matrix, not completion of every legacy screen.
- Manual smoke test of the actual MainActivity after installing over the old APK:
  library, drawer, Settings, in-app App theme dialog, selection of Night, force-stop
  and relaunch with the selected dark palette retained. Screenshots inspected.
- Packaged APK permissions and merged debug manifest contain neither INTERNET nor
  ACCESS_NETWORK_STATE. No new runtime permission or dependency added.
- `git diff --check`: passed.

Build log: `C:/Users/TopMob/.codex/tools/bookmt-audit/ux-verified-build.log`.
Instrumentation log: `C:/Users/TopMob/.codex/tools/bookmt-audit/ux-android-tests.txt`.
Component captures: `C:/Users/TopMob/.codex/tools/bookmt-audit/ux-foundation/`.
Actual app captures: `ux-after-library.png`, `ux-after-settings-night.png` and
`ux-after-night-relaunch.png` under the same audit tools directory.
APK: `app/build/outputs/apk/debug/app-debug.apk` (debug signing, existing version 1.1).

Baseline captures and frame traces are kept outside Git in
`C:/Users/TopMob/.codex/tools/bookmt-audit/ux-before-*`. Emulator: API 35,
`sdk_gphone64_x86_64`, 1080×2400, density 420. Startup produced an Android System UI
ANR; frame samples under host build load were too sparse/noisy for a performance
comparison. No speed or jank improvement is claimed. A stable performance baseline
and the full screen/accessibility matrix remain required for P2, not waived.

## Remaining roadmap work

P0 persistence work and source inventory are implemented; baseline capture exists,
but reliable performance measurements are pending. P1's shared foundation is
implemented; full keyboard/TalkBack/focus-ring checks across legacy screens, the
complete screenshot matrix and every component-specific motion contract are pending.
P2 will restructure the library/settings/reader chrome and apply those primitives
consistently. P3–P7 remain unimplemented. In particular, pronunciation quality has
not changed and no stress-correction claim is made.

Physical phone, Bluetooth/calls, release performance and subjective reading/speech
comfort were not tested in this increment. The owner's untracked note file is untouched.
