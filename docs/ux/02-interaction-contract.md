# Interaction, motion and timing contract

## Motion policy

Animate only a user-caused state change or real task progress. Static reading must
schedule no animation frames. No shimmer, pulsing CTA, looping cover, parallax,
spring overshoot, blur, paper curl, confetti, spinning settings icons or moving
gradient. Do not animate font metrics, reader padding, large list item size or
re-pagination. Animations never delay starting the requested work or saving data.

Three in-app options: “Как в системе” (default), “Минимум движения”, “Без анимаций”.
System animation scale zero overrides every option to zero duration. For nonzero
system scale, framework-managed animations inherit it exactly once; do not multiply
duration again. Minimal mode removes all translation/scale and uses at most 80ms
opacity/state-color feedback. Off mode atomically changes state; progress remains
readable as text/count and a static bar. No custom timer bypasses disabled animations.
Honor system haptics separately. Never change device animation settings.

Easing definitions (pseudocode, use project-supported equivalents):

```text
standard = cubicBezier(0.2, 0.0, 0.0, 1.0)
enter    = cubicBezier(0.0, 0.0, 0.2, 1.0)
exit     = cubicBezier(0.4, 0.0, 1.0, 1.0)
linear   = cubicBezier(0.0, 0.0, 1.0, 1.0)
```

## Exhaustive motion vocabulary

All new animated behavior must use an ID below, or add a reviewed spec entry first.
Numbers are milliseconds at system scale 1. Start delay is **0** unless specified.
Do not apply both a parent transition and the same child transition.

| ID | Trigger and property | Enter / exit | Easing and constraints |
|---|---|---|---|
| M01 | Button/row pressed state opacity | 60 / 100 | Linear; press feedback immediate, no action delay; one indication only |
| M02 | Checkbox, favorite, bookmark icon state | 100 / 100 | Color + icon swap; no bounce/rotation; failed write restores old state |
| M03 | Nonreader screen navigation | 160 / 120 | Incoming alpha 0→1 + 12dp horizontal travel; outgoing alpha only; simultaneous, not sequential |
| M04 | Enter/leave reader | 120 / 100 | Chrome opacity only; book text appears once ready at full alpha; no cover morph |
| M05 | Reader top/bottom bars | 160 / 120 | Alpha + 8dp travel from own edge, enter/exit; overlay must not resize page |
| M06 | Bottom sheet | 200 / 160 | Translate its height from bottom; scrim 140/120 concurrent; no bounce |
| M07 | Side pane / library drawer | 200 / 160 | Horizontal from logical start; scrim concurrent; no book reflow each frame |
| M08 | Small anchored menu | 100 / 80 | Alpha only; anchor stays fixed; no opening scale |
| M09 | Confirmation/dialog | 120 / 100 | Alpha + 4dp vertical enter; no scale; scrim concurrent |
| M10 | Tab/chip selection indicator | 120 / 120 | Color; content swaps without sliding entire rows |
| M11 | List/grid view change | 100 / 80 | One visible viewport fade; retain anchor book; no all-items morph |
| M12 | Single row addition/removal | 120 / 100 | Alpha only; final layout snaps once; bulk/filter updates have no stagger |
| M13 | Inline section expand/collapse | 160 / 120 | Clip/reveal bounded small section; no full-screen animateContentSize |
| M14 | Snackbar/status banner | 140 / 100 | Alpha + 8dp up; inset-aware; one at a time |
| M15 | Theme changes | 0 / 0 | Atomic colors in one frame; optional feedback only on selected swatch M02 |
| M16 | Optional paged-reader slide | 160 / 160 | Standard settle, horizontal; default page mode is instantaneous (0ms) |
| M17 | Manual page swipe | Finger tracks 1:1; settle 160 max | No pre-delay; disabled motion still tracks finger, final settle is immediate |
| M18 | Search jump / bookmark / restore | 0 / 0 | Position snap anchored to text, no long scroll through intermediate content |
| M19 | Short programmatic scroll | 160 / 160 | At most one viewport; cancel instantly on touch; longer jumps use M18 |
| M20 | Image/PDF reset-to-fit or double-tap zoom | 160 / 160 | Standard; pinch/pan follow fingers without easing; fixed focal anchor |
| M21 | Speech highlight change | 0 / 0 | Background switches on actual playback event; no per-word animation |
| M22 | Determinate task progress | 100 per update | Linear, only forward within same phase; no smoothing hides completion |
| M23 | Real indeterminate task | Platform bounded indicator | Only visible task; off-screen stopped; minimal/off replaces motion with text |
| M24 | Slider value/preview | 0 / 0 | Thumb/value track input directly; work scheduling uses timing rules |
| M25 | Small content loaded (cover/preview) | 100 / 0 | Alpha once; same-size placeholder; no replay while scrolling |
| M26 | Speech play/pause state | 80 / 80 | Icon swap opacity; status semantics immediate; hit target constant |
| M27 | Predictive Back | OS gesture progress | No extra M03/M04/M09 on top; canceled gesture restores exact previous state |

Sheet drag follows finger. Dismiss when platform sheet threshold is reached;
use supported gesture behavior, not invented velocity heuristics. Scrollable sheet
content consumes vertical scroll first; drag-to-dismiss from header only if list
is not at top or an editor is dirty. Dirty editors require their close protocol.
Full-height search results do not swipe away horizontally while selecting text.

## Timing and latency

| ID | Event | Required behavior |
|---|---|---|
| T01 | Tap | Visual response next frame; action starts on recognized release; no artificial debounce for navigation |
| T02 | Long press | Android configured long-press timeout; cancel at platform touch slop; never hardcode 500ms |
| T03 | Search input | Show typed text immediately; debounce query 180ms, IME Search bypasses delay; cancel stale searches |
| T04 | Loading indicator | Show only if task still pending after 150ms; no minimum visible time; dismiss as soon as ready |
| T05 | Slow task | At 2s show real phase and available cancel/back; at 10s add recovery guidance, no fabricated percentage/ETA |
| T06 | Reader typography slider | Update small preview immediately; commit expensive full layout on release; keyboard/stepper coalesce 180ms, flush on close |
| T07 | Color/brightness | Visual update immediately; persist coalesced latest value at 150ms + flush on exit; no full pagination |
| T08 | Toast-like success | Inline state preferred; snackbar 4s nominal; platform accessibility recommended timeout wins |
| T09 | Undo | At least 8s nominal, accessibility timeout wins; never only a fleeting inaccessible action |
| T10 | Error | Persistent inline state until resolved/dismissed; no compulsory timeout |
| T11 | Rapid repeated tap | In-flight navigation/import/save has a stable operation ID; suppress duplicates only for same operation, not arbitrary 500ms |
| T12 | Reader controls | No inactivity timeout; hide only by user action; no timers to interrupt reading |
| T13 | Deferred focus/IME | Request after surface placement, not fixed sleep; no keyboard on read-only sheets |
| T14 | Return-to-origin affordance | Persists through reading session until used/replaced; never disappears after a few seconds |
| T15 | Active selection handle at page edge | 500ms dwell; then at most one page per 700ms while held; no ordinary-touch edge turning |
| T16 | Brightness drag value indicator | Visible during drag; dismiss 600ms after release; no announcement every frame |

Back/Cancel takes effect immediately even if an enter transition is running.
Animation cancellation is visual only; do not cancel a successfully requested
save. Lock repeated destructive actions to operation state. Keep cancelable reads
cancelable; finish atomic writes/recovery through existing data-safety machinery.

## Input arbitration and gestures

Highest priority: OS reserved edge gestures → modal/IME → text selection handles →
image zoom/pan → explicit links → scrolling/swipe → tap zones. A consumed gesture
must not also turn a page or toggle bars. Always use actual system insets; do not
intercept Android Back edges with a full-height invisible touch strip.

Default paged reader: left 25% previous, middle 50% controls, right 25% next,
excluding insets and visible controls. Preserve old 33/34/33 behavior for upgraded
users unless they select the new preset; do not silently alter learned gestures.
Optional left-hand preset swaps left/right actions; center unchanged. Add a diagram
in Controls settings, with a practice region and Reset. Arbitrary zone editor is
not required in the first release. New installs use volume-page navigation OFF;
existing settings retained. Volume controls audio whenever TTS is active, including
paused/preparing, and outside the reader. No volume hijacking in editors/overlays.

Scroll reader: vertical drag scrolls; tap noninteractive text toggles controls;
no previous/next side tap zones. Explicit page-step buttons/keyboard can move one
viewport minus one line for continuity. Double-tap text has no special action,
so ordinary tap must not wait for a double-tap timeout. Long press selects word.
Double-tap is used only in image/PDF zoom areas (reset/2× toggle).

New-user brightness gesture OFF, independent of manual brightness setting. Opt-in
vertical drag uses a 24dp strip starting after max(system left gesture inset, 24dp),
never while selection/modal/pinch active; the strip is shown in practice preview.
Require vertical intent beyond system slop before consumption. In scroll mode it
remains off unless separately explicitly enabled. Provide a slider alternative.
Show a small themed percentage indicator during drag, remove 600ms after release
with M14. Clamp 1–100%; changing brightness affects this window only.

Haptics: optional app “Тактильный отклик”, default follows system availability;
one system long-press feedback on selection, optional confirmation for bookmark.
No vibration per page, slider tick, TTS phrase, error or every button. Never play UI
sounds. TalkBack gestures override ordinary reader touch gestures; expose explicit
Next/Previous page, Show controls, Add bookmark, Start speech actions.

## Back, focus and state

Back resolves one layer only: IME → active child editor/dialog → selection → sheet/
drawer/search → screen. Showing reader chrome alone does not consume Back: leave
the book. Top toolbar arrow exits its screen but first resolves a dirty editor;
it is not a synonym for “hide controls”. Plain reader Back stops TTS immediately
on committed navigation and saves positions. Predictive Back cancellation does
neither. Home/lock preserves an already-started speech session as currently intended.
Library root Back lets Android leave; no exit dialog, no “press twice”.

Keep library query/filter/sort, scroll anchor and reader origin in retained state.
Selection is transient; note/metadata drafts survive rotation and process recreation
locally without being committed as final data. On app relaunch do not auto-resume
audio. Restore navigation safely: default to library with Continue card; explicit
external Open operation may open its imported book. No surprise modal restoration
for completed operations. Modal close returns accessibility focus to its trigger.

Dirty editor close: one dialog “Сохранить изменения?” with Save, Discard, Continue
editing. Back/outside tap is Continue editing. Empty untouched form closes directly.
Saving failure leaves draft and editor intact. Retain durable draft until successful
save/discard; label any restored draft. Appearance preview Apply/Cancel follows its
specific contract; not every settings row needs a save dialog.

## In-app vs system surfaces

| Action | Owner | Rule |
|---|---|---|
| Theme/custom color, sort, filter, book actions, contents, note actions | BookMT | Compose-themed surfaces; no external Activity or implicit intent |
| Selected book-text toolbar | BookMT | Copy, highlight, note, listen, More; suppress duplicate generic toolbar and process-text/web actions |
| Editing note/metadata text | Android IME + text editing | Keep normal cursor, paste, undo, accessibility; not a web fallback |
| Import book/font/cover/model/dictionary or restore ZIP | Android document picker | Only explicit import action; request local-only; preserve caller/draft on cancel |
| Export ZIP/TXT/dictionary | Android document creator | Label “Сохранить файл…”; show suggested name/type; cancellation is normal |
| Share quote/notes | Android chooser | Only explicit “Поделиться…”; no auto-open after Copy |
| Notification permission | Android | Explain at relevant user action; refusal does not block local reading/speech |
| Copy | Clipboard | Do not duplicate OS clipboard toast/preview with another success toast |
| External URL in book | BookMT preview | Show address, Copy, Close; no automatic browser/WebView/network fetch |
| System bars, predictive Back, volume, TalkBack | Android | Respect platform behavior/insets; theme icons where supported |

BookMT cannot style or enforce offline behavior of a third-party provider, keyboard
or share target. Describe intentional handoff in the action label, not a warning
before every picker. Do not replace useful platform access with broad storage
permissions. No system color picker is needed anywhere. Keep the native editable
text toolbar where appropriate; the prohibition is on unintended duplicate/external
actions, not accessible keyboard support.
