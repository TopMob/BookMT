# Visual system

## Theme ownership

Four built-in palettes: Day / День, Sepia / Сепия, Night / Ночь, Black / Чёрная.
`theme-tokens.json` specifies opaque sRGB colors as `#RRGGBB`. Alpha is explicit
only in the state-layer rules below; do not reinterpret hex as ARGB.

New installations: application appearance follows Android light/dark selection
(Day/Night). Reader inherits application appearance until explicitly changed.
Existing installations: preserve reader theme and all typography preferences;
initialize application appearance from the saved reader theme once when migrating.
Do not reset existing DAY to NIGHT merely because defaults changed. Persist the
resolved appearance for the launch window so startup does not briefly show white.

Reader override is optional and clearly labeled “Оформление этой читалки” (global
reader preference, not per-book). All overlays opened inside the reader inherit
its resolved palette, including text fields, selection UI, loading and errors.
Outside the reader use app appearance. Reader → library changes palette atomically
when the destination becomes visible; do not fade through a white surface.
Changing Android day/night mode must not change an explicitly selected reader theme.

Remember a preferred light palette (Day initially) and dark palette (Night initially).
Quick toggle switches that pair; choosing Sepia or Black updates the respective
preference. It never walks through unrelated palettes. Dynamic wallpaper colors
are disabled for this design; do not accidentally retain current dynamicColor=true.

System status/navigation bars use the currently visible surface's background and
dark icons in Day/Sepia, light icons in Night/Black. Where Android draws its own
contrast protection, respect it and inspect the result; do not promise pixel control
over OS-owned surfaces. App splash uses background and a static accent book mark,
no branded delay or logo animation. Follow-system launches use current Android
day/night resources; explicit palette launches use the persisted resolved palette.
While reader settings resolve, keep the correct launch surface, not a white default.

## Token assignment

| Token | Usage |
|---|---|
| background / readerBackground | Window/library background / actual reading canvas |
| surface | Cards, toolbar, settings sections, selection toolbar |
| surfaceRaised | Sheet/dialog and menus; opaque, no blur |
| surfaceInset | Search/input fill, progress track, preview surround |
| text / readerText | Primary UI text / book body |
| secondaryText | Author, chapter metadata, helper text; never opacity-reduced again |
| accent / onAccent | Link, active control, primary button fill / button text |
| accentContainer / onAccentContainer | Selected chip, current chapter, active navigation |
| border | Interactive boundary; use where shape/fill alone is insufficient |
| divider | Nonessential separator; must not be sole control boundary |
| error, warning, success + containers | Icon and label plus supporting surface; never color alone |
| selection | Temporary selected-text background; body text remains readerText |
| tts | Spoken-phrase fill; thin accent edge distinguishes it from annotations |
| search / searchCurrent | Other search hits / current hit; current hit also outlined |
| markYellow/Green/Blue/Pink | Saved annotation display palette, semantic names and checkmarks |
| disabledText / disabledSurface | Disabled controls; provide explanation for unavailable actions |
| scrim | Black source for overlay dimming; opacity per theme in JSON |

Material roles must be fully assigned, not left to purple defaults:

```text
primary=secondary=tertiary=accent
onPrimary=onSecondary=onTertiary=onAccent
primaryContainer=secondaryContainer=tertiaryContainer=accentContainer
onPrimaryContainer=onSecondaryContainer=onTertiaryContainer=onAccentContainer
primaryFixed=secondaryFixed=tertiaryFixed=accentContainer
primaryFixedDim=secondaryFixedDim=tertiaryFixedDim=accentContainer
onPrimaryFixed=onSecondaryFixed=onTertiaryFixed=onAccentContainer
onPrimaryFixedVariant=onSecondaryFixedVariant=onTertiaryFixedVariant=onAccentContainer
background=background; onBackground=text
surface=surface; onSurface=text
surfaceVariant=surfaceInset; onSurfaceVariant=secondaryText
surfaceDim=background; surfaceBright=surfaceRaised
surfaceContainerLowest=background; surfaceContainerLow=surface
surfaceContainer=surface; surfaceContainerHigh=surfaceRaised
surfaceContainerHighest=surfaceInset
outline=border; outlineVariant=divider
error=error; onError=onError; errorContainer=errorContainer; onErrorContainer=error
inverseSurface=inverseSurface; inverseOnSurface=inverseText; inversePrimary=inverseAccent
surfaceTint=accent; scrim=scrim
```

Use only roles available in the project's Compose version; explicit component
tokens fill any gaps. `tonalElevation=0` for authored surfaces so exact colors do
not change through implicit tinting. Border and surface separation carry hierarchy.
Sheets/menus may use a fixed 4dp shadow; no animated shadow or multiple soft layers.

## Component geometry and typography

All layout values dp, all text values sp. Spacing scale: 4, 8, 12, 16, 24, 32.
Standard horizontal screen padding 16; reading margins remain user-controlled.
Text fields have 12 radius, buttons 12, cards 12, sheets top corners 24, dialogs 20.
Book cover radius 6; preserve original cover aspect ratio within a 2:3 slot using
fit (no crop of title). Fill unused space with surfaceInset. Missing cover: flat
accentContainer, title and author; no random color per recomposition or fake texture.

| Element | Size / line height | Weight | Behavior |
|---|---|---|---|
| Screen title | 22 / 28 | Medium | Single line with ellipsis; full title in details |
| Section title | 18 / 24 | Medium | Wrap |
| Body / settings row | 16 / 24 | Normal | Wrap; no fixed-height clipping |
| Secondary information | 14 / 20 | Normal | Wrap where needed |
| Button / chip | 14 / 20 | Medium | Text grows; minimum 48 high hit area |
| Small metadata | 12 / 16 | Normal | Only nonessential metadata |
| Reader default | 18; line multiplier 1.4 | Normal serif | Preserve existing values; range 10–40 |

Use system UI family, no remote fonts. Reader offers current built-ins with human
labels: “Системный”, “С засечками”, “Без засечек”, “Моноширинный”, imported font name.
Do not force letter spacing on Russian body text. Book typography changes affect
layout; color/highlight changes must not. Honor system font scale throughout UI;
reader font scaling must use one documented conversion, never apply fontScale twice.

Targets: 48×48 minimum including icon buttons, swatches, drag-handle alternatives
and speech controls; standard settings rows 56 minimum, two-line 72 minimum.
Icon drawing 24; speech primary 28 inside 56 target. Adjacent targets do not overlap.
Slider thumb can be 20 visually but 48 touch target; track 4. Swatch 28 inside 48.

Compact width <600: single-column screens; library grid min cell 144, 12 gap;
at fontScale >=1.5 or width <360 prefer list for new sessions but preserve a user's
explicit grid choice with larger cells. Do not silently rewrite saved view mode.
600–839: settings/content max width 680 centered; sheets max width 560.
>=840: library content max width 1120; reader max text column 720dp centered;
contents/search/marks can use a 320dp side pane only if at least 360dp remains for
text. Otherwise use full overlay. Entering/leaving pane preserves text anchor.
No two-page book spread in this roadmap. Landscape with <480dp usable height uses
full-height settings/search overlays rather than a cramped half sheet.
IME/insets reduce available space; scroll actions into view, never behind keyboard.

## Component states

- Default: token fill/text, 1dp border only when needed.
- Pressed: one bounded state layer using text at 8% alpha; no scale or displacement.
  On primary buttons use onAccent at 10%. Release follows M01.
- Focused: 2dp accent outline inset 2dp; keep full keyboard focus ring visible.
- Hover (mouse): text at 4%; no hover-dependent actions.
- Selected: accentContainer + onAccentContainer + checkmark/selected semantics.
- Disabled: disabledSurface/disabledText; no pressed state. Reason appears nearby,
  e.g. “Номер страницы будет доступен после расчёта”; don't hide the reason in a tooltip.
- Loading: keep label and width; small indicator beside it when T04 threshold expires.
- Error: error icon + concise text + retry/recovery action, errorContainer background.
- Focused error: error boundary plus focus outline; neither a shake nor vibration.

Selection and highlights are opaque fills from the palette, not stacked translucent
colors. Draw priority: active text selection > current search hit > speech > other
search hits > annotation. Preserve lower-priority meaning with underline/edge marker
and accessible labels; do not erase saved annotation data. Never bold a spoken word
or alter text metrics. Legacy arbitrary annotation ARGB stays in storage: map the
four known old swatches to semantic IDs for display; for other colors derive a
readable fill and retain original value. Do not overwrite old colors on read.

## Custom palette editor (later phase)

BookMT-owned screen, not a system palette. Start from a built-in preset; editable
roles: readerBackground, readerText and accent. UI surfaces derive from the selected
base theme and use its explicit tokens. Customizing readerBackground updates the
preview only until Apply; readerText updates preview body. Accent initially uses
the base preset's exact token. A custom hex accent derives accentContainer by mixing
16% new accent with 84% base surface in sRGB; onAccent is whichever of pure black
or white has greater contrast. onAccentContainer keeps base text if >=4.5:1,
otherwise uses the higher-contrast black/white. inverseAccent keeps the new accent
if >=4.5:1 on inverseSurface, otherwise retains the base inverseAccent. Remaining
UI roles stay from the base theme. Validate all normal/pressed accent text and button
pairs from verify_spec.py before Apply; invalid accent shows inline correction and
cannot be saved. Do not silently adjust the user's entered hex. Background/text accept
`#RRGGBB`, plus in-app hue and saturation/value controls with numeric alternatives.
No eyedropper requiring screen capture; no alpha editing.

For custom reader colors, derive each highlight fill by blending its base fill
toward custom readerBackground in increments of 1%, choosing the smallest blend
that reaches 4.5:1 with custom readerText. If even the background fails, show the
reader-contrast warning below; keep user's deliberate override limited to the
reader canvas. Distinguish converging highlight colors by existing edge/underline
markers and names. Reader metadata uses base secondaryText if it passes 4.5:1 on
custom background, otherwise custom readerText. Controls remain in base-theme
surfaces and must never inherit a low-contrast override.

Live preview contains a heading, paragraph, link, selected phrase and TTS phrase.
If contrast <4.5:1 display “Текст может быть трудно читать” and a “Подобрать цвет”
action; permit a deliberate override for personal reader text only, never silently
lower contrast of interface controls. Invalid hex cannot be applied. Cancel returns
to exact old palette; Apply writes once. Reset requires confirmation only if it
would discard an edited saved custom palette. Store versioned custom data and include
it in manual backup. Do not use CUSTOM->Day fallback for an existing valid dark theme.

## Contrast and dark-mode verification

Design targets: primary/secondary text 4.5:1 against all permitted underlying
surfaces; interactive outlines/icons 3:1; primary-button text 4.5:1. Book body on
each highlight 4.5:1. Disabled text and decorative dividers are not essential text.
Ratios are computed on sRGB linear luminance, including composited state layers.
Recheck actual rendering, thin fonts, enlarged text, and every transient surface.
AMOLED background is truly black, panels slightly lifted; do not make all text white.
PDF page pixels remain original by default; a dark toolbar does not promise a dark
PDF. No PDF inversion until a separate previewable rendering option is implemented.
