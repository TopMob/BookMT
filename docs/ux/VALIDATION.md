# Design validation and delivery state

Date: 2026-10-18. This record covers **documentation**, not implemented UI.
Implementation evidence is tracked in [the first increment report](IMPLEMENTATION_2026-10-18.md),
[library/settings report](IMPLEMENTATION_P2_2026-10-18.md) and
[FB2 import follow-up](IMPLEMENTATION_FB2_2026-10-18.md).
Older design choices are superseded by the latest owner revision in README.

## Checked

- Source inventory: existing Compose screens, dialogs/sheets, reader controls,
  PDF, library workflows, settings, themes, local document contracts and speech
  engine inspected at baseline `1922391`.
- Specification coverage: L01–L08 library, S01–S05 settings/files, R01–R12 reader,
  A01–A08 speech; existing surface/source map in README.
- Four complete color palettes, Material role mapping and component states.
- 27 motion contracts and 16 timing contracts, including no-motion behavior,
  system Back, selection gestures and deliberate system surfaces.
- `python docs/ux/verify_spec.py`: **232 contrast checks passed**, including
  secondary/pressed text, primary button, state containers, inverse colors,
  book text on highlight fills and interactive borders. Text pairs >=4.5:1;
  borders >=3:1. Disabled/decorative roles are exempt and cannot convey essential
  content alone. First draft failures were corrected before delivery.
- JSON shape/hex colors, local Markdown links and fenced blocks checked by the
  same script. `git diff --check` checks repository whitespace before commit.
- Existing development plan linked to this specification and its superseded
  library-playback proposal removed. No application source modified.

Run the check again whenever tokens or document links change:

```powershell
python docs/ux/verify_spec.py
git diff --check
```

The script uses only Python standard library; it is documentation tooling, not an
Android dependency. It cannot prove visual quality, layout/accessibility behavior
or pronunciation. No APK build, screenshot validation, new device test or human
speech evaluation was performed for this documentation-only task. Those checks
belong to implementation phases and are specified in document 06.

## Delivery status

| Work | State |
|---|---|
| Design specification and token validation | Complete |
| P0 device/UI baseline and settings migration | Migration implemented; reliable performance baseline pending |
| P1 visual/interaction primitives | Shared foundation implemented; full screen/accessibility coverage pending |
| P2 library/settings/reader chrome | Not started |
| P3 file/task workflows | Not started |
| P4 selection/marks/reading tools | Not started |
| P5 speech UX/timer | Not started |
| P6 pronunciation experiment/dictionary | Not started; human quality gate required |
| P7 custom palettes/final consistency | Not started |

Update this table only with implementation evidence. Preserve separation between
design checks and shipped functionality. Start from P0/P1, not all phases at once.
