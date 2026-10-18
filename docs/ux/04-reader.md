# Reader screens and overlays

## R01 — Opening and the quiet reading canvas

The first visible book frame must already be the correct restored passage. Never
display chapter one while seeking to a saved anchor. Show the resolved theme's
background immediately; after T04 show “Открываем книгу…” and real phase only when
known. Cancel/Back remains available. Large-book pagination may finish later with
“Рассчитываем страницы…” in the progress area; text does not wait for the total.

Reader body: user's font and margins; clean flat background; no texture, gradient,
advertisement, onboarding overlay or engagement message. Preserve existing 18sp,
1.4 line spacing, 24dp horizontal / 16dp vertical defaults unless user changes them.
Set text metrics once per layout request; rendering highlights never repaginates.
Restore uses block/text anchor plus actual visible line geometry as current fixes
require. Layout loading/error placeholders have bounded height, never zero-size
items that trigger loading the whole book.

Controls visible on initial entry, then explicit center tap hides them. Remember
hidden/visible state within retained reader instance, not indefinitely across an
unrelated book. No time-based hiding. When quiet, show optional bottom progress
line (12sp, secondaryText), disabled by user if desired; never a ticking clock.
Progress displays “Глава 3 · 34%” while total unknown; then “128 / 430 · 34%”. No
fake page count or animated number rolling. Tap progress opens R05 when controls
visible; quiet progress is not an invisible destructive hit target.

Default canvas remains stable when toolbars show/hide: bars are overlays, not
padding animations. Opening tools is a temporary tool mode; toolbar-covered lines
become fully visible on dismissal without any position change. Do not advertise
that all text remains exposed while tools are open. Selection toolbar anchors away
from selected text; active speech uses the separate safe-area contract in R10.
Insets for physical cutouts/system bars stay correct even in fullscreen; changing
fullscreen anchors text once at final dimensions, without intermediate reflow.

Acceptance: reopening at start/middle/end, fast Back/reopen, rotation, first opening
of large book, background completion of pagination, huge fonts; same passage remains.

## R02 — Reader controls and navigation hierarchy

```text
Top:    [Back] Chapter or book title       [Bookmark] [More]
Bottom: [Contents] [Search] [Aa / Appearance] [Listen]
        compact progress text above action row when controls visible
```

Four bottom actions equally sized with icon + label; minimum 64dp row at normal
font, grow for font scale; top 56dp minimum. Keep bar background opaque surface.
Place top and bottom in inset-safe positions. M05 only, no layout size animation.
Bookmarks top action adds a bookmark at the actually visible reading anchor and
changes to selected state M02. If already bookmarked, tap opens bookmark details
with explicit Delete; do not silently remove a mark with a casual second tap.

More order: Marks, Go to, Return to previous place (when available), Auto-page,
Reading controls. Export lives in Marks, not a generic reader menu. Quick theme
toggle lives in Appearance's first row with remembered light/dark names; never
cycles unknown palettes. Avoid duplicate Settings entry showing entire application
settings while reader is open. Keep frequently used actions within two taps.

Top title ellipsizes; Contents sheet shows full current chapter; full book title
in header there. Long-press title does not unexpectedly invoke Android text actions.
Disabled actions explain why (e.g. Contents absent); unavailable PDF actions omitted.

Back with no child surface exits directly, stops audio, saves reading/listening
positions, restores library. No extra tap required to hide toolbars. During active
search/selection/editor use the explicit Back stack in document 02.

## R03 — Page and scroll behavior

Default page animation None; optional Slide M16 in Reading → Pages. Manual swipe
M17 follows pointer using at most current + adjacent prepared pages, no bitmap
capture of whole chapter. If adjacent text is unavailable, keep current readable
page and a small loading indicator; don't animate into empty white space. Touch
interrupts any settle immediately. One gesture commits one page. Repeated taps
coalesce a bounded pending target, not an unbounded animation queue.

At first/last page no bounce, error sound or redundant popup. Disabled explicit
previous/next actions expose state. Reaching last page may retain existing finished
book bookkeeping; do not force return to library or launch another title. User may
change read status in Details. Scroll uses native inertial behavior and no custom
overscroll stretch of the book text. Page-step scroll retains one line overlap.
Manual touch interrupts programmatic scroll; long jumps use M18.

Hardware keyboard: PageUp/PageDown page step; arrows line step in scroll; Escape
closes top overlay; Enter activates focused controls. Editing fields own keys.
Volume behavior follows document 02. A click on a link/image/annotation consumes
the tap; it cannot also turn page. Long press release never fires ordinary tap.

## R04 — Contents and book marks

Compact: full-height modal sheet with header, close button and scrollable content;
use M06. Wide: side pane under document 01, M07; text viewport changes once at final
width and restores anchor, not on every animation frame. Tabs Contents / Marks keep
independent scroll states; user can access Marks from More without a second overlay.

Contents: full book title header, current chapter emphasized with accentContainer
and “Текущая глава” semantics. Initial scroll to current chapter once, not after
every content update. Nested entries indent 12dp/level capped at 36dp; hierarchy
also expressed in accessibility level. Expand/collapse chevron separate 48dp target;
label navigates. Expand small groups M13; huge groups swap without animating height.
Long titles wrap up to 3 lines; detail on focus/accessible full name. Empty:
“В этой книге нет оглавления”, with Go to percentage.

Choosing chapter/mark records origin once, resolves target, then closes sheet.
If target fails to resolve, keep sheet and explain; do not close into wrong passage.
Marks reuse L08 item/editor semantics, default order by position, filters types.
Add current bookmark from header too, with explicit named action. Neither delete
nor edit bubbles into navigation. Export is a labeled overflow action.

## R05 — Go to and return

Progress tap / More → Go to opens sheet with two methods: Percentage and Page.
Percentage available when content offsets are known; 0–100, stepper/numeric value,
slider previews candidate only. Page number enabled only when final total known.
Original position stays until “Перейти”. Validate integer/range, inline error,
no clamping silently to another value. Numeric keyboard requested deliberately.
IME Go equals button; duplicate action guarded. Cancel discards candidate.

Actual navigation uses M18 and records origin once. After search/chapter/mark/seek/
explicit Start again, show a small non-expiring “Вернуться” control in chrome
and More, not a timed snackbar. It restores saved anchor, not old page number.
Keep session history up to existing 50 entries; ordinary page turns don't fill it.
Do not persist an infinite cross-session history. Back remains screen/layer navigation,
not history navigation. Return unavailable after its origin book was removed.

## R06 — Search within book

Full-height search sheet: Back/Close, focused field “Найти в книге”, Clear, results
count/status, scrollable excerpts with chapter and emphasized query. T03; results
have stable offset keys. First query only focuses on user request, not on restoring
a reader after process death. Explain cap “Первые 1000 совпадений; уточните запрос”.
No zero-results flash during work; errors preserve query and expose Retry.

Tap result resolves passage, closes results and shows lightweight search navigation
strip in tool mode: previous/next result, “3 из 12”, Results, Close. Strip is above
bottom tools and does not change page metrics. Quiet reading hides strip with bars;
query remains recoverable. Current match searchCurrent + outline, others search;
clear on exiting search mode. “Вернуться” restores pre-search place; previous/next
results do not create dozens of history entries. Reopening results preserves list
anchor/query. New query resets result selection only after latest results arrive.

## R07 — Selection, highlight, quote and note

Current dialog duplicates page text and asks user to select there. Target: select
in the original page with visible handles and a BookMT action toolbar. This is an
engine-dependent P4 change, not a quick toolbar reskin. Until stable, restyle the
existing dialog and explicitly say “Уточните выделение”; do not pretend in-place
selection already exists. Never ship broken range mapping to meet visual spec.

Long press selects word; one optional haptic. Handles track finger, magnifier only
if supported accessibly and without another external toolbar. Toolbar placed above
range when room, below otherwise, clamped inside insets and outside handles. Actions:
Copy, Highlight, Note, Listen; More: Share, Pronunciation (single word/short phrase,
after P6). At narrow width text/icon overflow remains reachable. No Translate,
Search web or Android process-text app list. Read-only book text cannot be edited.

Highlight opens four semantic swatches with names/checkmark and “Выделить”. Do not
label colors merely 1/2/3/4. Save on explicit selection action, show persisted state;
if failure retain selection and message. Add note opens editor with original quote,
Save/Cancel and durable draft. Copy writes original text and clears selection;
Share only after explicit action hands off to chooser, preserving range on return.
Listen records original source start/end, then clears visual selection after start
is accepted. Pronunciation replacements must never rewrite source anchors.

Tap existing annotation opens its details/actions; overlapping annotations offer
choice by quote/note, no arbitrary deletion. In paged mode initial implementation
selects within page. Cross-page drag extension is separately gated: edge dwell
500ms, then at most one page per 700ms while holding; stop when pointer leaves edge,
cancel/Back restores original selection. No edge-turn without an active handle.
Scroll selection auto-scrolls only under active handle near inner 24dp edge, capped
at 240dp/s; respect OS regions. Exported range includes exactly selected text,
Unicode boundaries intact; retained data anchors survive formatting changes.

## R08 — Appearance while reading

Aa opens compact bottom sheet with tabs Text / Theme / Screen; retain last tab for
this reader session. Header Close, not generic application Settings. At compact
height use full screen with book preview. A small sample preview is always stable
and uses actual selected font. No expensive live re-layout of entire book per tick.

Text: size stepper 10–40sp in 1sp steps + slider + value; font picker; line spacing
1.0–2.5 in 0.1 steps; horizontal/vertical margins 0–64dp in 2dp steps; alignment
Start/Justify/Center; publisher formatting toggle with “Стили книги”. Preserve
stored odd margin values until user adjusts. Justify allowed but not newly forced;
no promise of hyphenation until language-aware implementation exists. Advanced
typography such as paragraph spacing/indent is later engine work, not inert knobs.

Size/spacing/margins update preview as dragged, apply on release/stepper coalescing
T06 and preserve first visible anchor. If multiple requests race, only newest layout
may publish; cancel stale jobs. Keep old laid-out text until new ready, then atomic
swap, never interpolate glyph sizes. Sheet still responds during calculation.
Close flushes pending final change before returning; errors retain previous usable
layout and show which setting failed. No per-change Apply button for normal text
controls. “Сбросить текст” confirms affected settings only, retains theme and data.

Font picker: built-ins with sample “Съешь ещё этих мягких французских булок”, imported
fonts list, “Добавить TTF/OTF…”. Validate locally, keep previous on failure; font
filename/name and remove choice clear. Removing active imported font selects a
built-in before deleting owned file, preserves text anchor. Custom fonts never
replace essential UI icons/fonts. No automatic download.

Theme: four preset tiles, pair shortcut labeled by destination (“Ночной режим” /
“Дневной режим”), “Как в приложении”, custom later. Atomic M15. Screen: manual
brightness slider with value and System reset, fullscreen, link to orientation/
control settings. Do not toggle hidden edge gesture merely by enabling brightness.

## R09 — Footnotes, links, images and unsupported layout

Footnote tap opens themed sheet with body, source label, Close and optional “Перейти
к примечанию”. Reading position unchanged for preview. Bounded lazy loading with
real “Показать полностью” if content truncated; never silently show ellipsis as if
full footnote. Error Retry/Close; source tap not also page turn. Nested footnote
replaces sheet content with local Back breadcrumb; bounded depth 5 then offer jump.
No stack of scrims. Jump records origin, closes sheet only when target resolves.

External URL: own preview with selectable address, Copy, Close. No browser launch,
network request, web image download, or link prefetch. Internal broken link shows
“Не удалось найти место в книге”; retain current passage. Images: full-screen
themed viewer, Close top-start, Fit control, optional caption. Pinch 1–6×, constrained
pan so image cannot be lost off screen, double-tap 2×/fit M20. Back closes viewer,
does not exit book. Do not animate from tiny source thumbnail or load original
unbounded bitmap; decode according to visible size/memory budget. Display alt text
and retry when unavailable. Swipe-down dismissal off to avoid pan conflict.

Tables/unsupported content: show readable current fallback, optional “Открыть
таблицу” only once structured table viewer exists. That future viewer uses horizontal
scroll contained within table with fixed text size; no page-turn while dragging it.
Do not claim complete CSS/SVG support. A parser limitation requires parser work,
not a decorative loading indicator.

## R10 — Speech and automatic paging overlays

Speech details in document 05. While playing/preparing/paused, quiet reader retains
compact controls inside a reserved bottom safe area, never over spoken text. Reserve
64dp + navigation inset once at session start (larger measured height at large font),
re-anchor/re-layout once if required; keep same safe area through pause/resume and
toolbars, release only on stop with anchor preservation. Never resize for each
status change. Full tools can replace compact controls in the same area. If too
short for usable text, use dedicated speech panel while retaining text anchor.

Automatic paging: explicit start panel with interval, Start/Cancel. During run small
Stop action in reserved status/footer space; no ticking countdown over paragraphs.
Opening controls/selection pauses its clock; resume after full interval, per S03.
TTS and auto-page mutually exclusive: starting TTS stops auto-page; starting auto-page
while speech active offers Stop speech & Start / Cancel, never both page controllers.

## R11 — PDF

Same theme, Back, bookmark action and basic chrome as text reader; PDF-specific
bottom row Previous / page “12 из 98” / Next / Fit. Page number opens Go to, no
hidden reset-zoom behavior. Fit is explicit. Page slider in Go to sheet previews
number and commits once; do not render every dragged intermediate page.

Original page colors by default, background around page from theme. Loading uses
themed surface; previous page remains while next is preparing, with requested page
label distinct from displayed page; commit number and image atomically. Error never
labels old bitmap as new page. Old visible bitmap lifetime preserved per existing fix.

Pinch 1–5× and bounded pan, double tap 2×/fit. Single tap shows controls (may wait
platform double-tap discrimination in this zoom region only). At >1× drag pans;
do not turn page when reaching pan edge. At fit use explicit page buttons initially;
horizontal swipe page navigation later only after gesture conflict tests. One page
at a time; no paper curl or animated bitmap rescale on navigation. Bookmark list
uses same item style, labels physical page number, confirms delete or real Undo.

No TTS, text search/selection, table of contents or password input affordance until
the corresponding PDF backend supports it. For unsupported encrypted document,
show truthful explanation, Close and Choose another file; no fake password form.
Night inversion/crop/OCR are separate functional projects. Preserve physical PDF
page positions independently of text offsets. Rotation preserves page and sensible
fit/zoom focal point, not a new page.

## R12 — Reading failures

Opening failure: title, short localized reason, Retry, Back; repair source when
appropriate. In-session cache corruption: keep last readable content, explain
“Не удалось прочитать часть книги”, Rebuild cache and Close; rebuilding cannot
delete annotations or original file. Interrupted operation is not an error popup.
Unknown exceptions show safe localized summary plus Details copy for local report,
not raw stack trace. Do not retry expensive work endlessly without user action.
All error states use current reader palette; no white platform dialog at night.
