# Library, settings and reading comfort — 2026-10-18

Scope: the library/settings portion of P2 and the owner's page-button correction.
The previous implementation report is historical; the owner's latest instructions
replace independent app/reader themes and the original green accents.

## Behavior delivered

- One theme setting for the whole app and reading surface: System, Day, Sepia,
  Night, Black. Neutral graphite accents in Day/Night/Black; warm Sepia retained.
  Version-2 preference migration favors a previous explicit reader palette, otherwise
  retains the app choice. Unknown fields and all typography/voice/position data stay.
  Launch-cache versioning prevents reusing the old independent app palette.
- Settings home has six icon rows: Appearance, Reading, Speech, Library/files,
  Language, About. Reader settings reuse the first three categories and controls.
  Back first returns to the category home. Categories retain their scroll positions.
  Theme is exposed once in Appearance, speech once in Speech.
- Typography and margin sliders keep a local value while dragging and commit on
  release (or leaving the control), reducing repeated expensive reader layout work.
- Forward screen turns in scroll mode use actual rendered line geometry: the first
  line not completely visible above the lower reading margin starts the next screen.
  Backward turns align to a complete line. Tap/buttons and opted-in volume paging
  share the same action. Rapid presses use a bounded channel; no percentage step.
  Normal book-end clamping and oversized-inline-content progress are preserved.
- Library continue-reading is a normal scrollable card with title/progress/book icon.
  Search is inline with 180ms debounce and immediate IME action. Search Back clears
  the query and restores the previous library location. Search, filters and view
  state survive supported screen restoration; sort ties use stable book IDs.
- Filters have local drafts, Cancel/Apply, matching-book count, status/format/tags
  and searchable collections. Sort and list/grid choice are separate dialogs.
  Drawer has Library, Marks, Settings. Maintenance/statistics move under settings.
- List/grid covers fit without cropping. Each book has a visible action menu;
  selection shows checkboxes and Select all. Font-scale-aware grid cell minimums
  keep large text usable. Import button has measured bottom clearance.
- Marking read/unread changes only the completion flag, retaining both reading and
  listening positions. A 100% book can have its read flag removed without rewinding.
  Existing DB migration already initializes that flag for legacy finished books.
- ZIP restore asks for explicit replacement confirmation after selecting the local
  archive. Existing staged restore backend is retained. This is not the later
  archive-preview/recovery workflow.

## Verification

- 75 JVM tests passed, zero failures/errors/skips.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline
  --continue --console=plain --max-workers=2`: BUILD SUCCESSFUL; lint 0 errors,
  82 warnings (68 in the previous increment; warnings are not suppressed).
- All 34 Android tests passed on the API 35 emulator in airplane mode, 89.952s.
  The ten reader navigation tests also passed separately, 68.968s.
- A new geometry test exposed a trailing empty layout line at page boundaries;
  forward navigation now ignores lines with no source character. Both normal and
  large text then passed four successive screen turns plus backward alignment.
- New UI checks cover 1000 books, inline continue-card scrolling, filter draft
  Cancel/Apply and all six settings categories at 200% text. Eight screenshots
  cover library/settings in four palettes; Day settings and Night/Sepia library
  captures visually inspected. The prior 12 palette/component captures also pass.
- Initial concurrent-build run had a PixelCopy timeout; the complete rerun after
  build finished passed. No performance claim follows from these emulator results.
- 232 contrast checks and Kotlin/JSON token parity passed.
- Actual activity smoke testing is recorded with the final FB2 increment below.

Logs/captures are outside Git under `C:/Users/TopMob/.codex/tools/bookmt-audit/`:
`comfort-p2-build-final.log`, `comfort-p2-android-final.txt`,
`comfort-p2-navigation.txt`, `ux-library-settings/`.

Follow-up during this increment: [FB2 import audit](IMPLEMENTATION_FB2_2026-10-18.md).
Final combined validation is recorded there.

## Remaining work

This does not complete P2 or the full roadmap. Reader chrome, compact appearance
preview, details/edit separation, full accessibility/focus checks and stable release
performance measurements still need their own increments. Current settings category
summaries describe their contents rather than live values. New category navigation
is immediate; no speculative decorative transitions were introduced. Existing shared
motion policy still applies to Material components, drawer and dialogs.

P3 operation cancellation/partial-result workflows, archive preview, storage accounting,
P4 selection/annotation tools, P5 sleep timer/speech UI, P6 pronunciation experiment and
P7 custom themes remain pending. Bulk action errors still use the existing snackbar
path; there is no new Undo or transactional batch guarantee. Pronunciation has not
changed. No physical phone, Bluetooth/call or subjective speech-quality test is claimed.
No runtime network permission, dependency, account or telemetry was added. The owner's
untracked note is untouched.
