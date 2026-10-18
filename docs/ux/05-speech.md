# Speech: comfort, control and pronunciation

## Ground truth and product boundary

Current engine is local Supertonic 3 INT8 via sherpa-onnx, two bundled voices,
fixed synthesis steps in current code, sentence/paragraph pause settings. Current
engine passes segment text directly to generation. A local pronunciation dictionary
does not yet exist. No evaluated evidence in this roadmap proves that this model
supports a particular stress/phoneme notation. Do not build a misleading accent
editor before testing the actual bundled model. Existing PCM tests establish audio
generation, not correctness of stress or listener preference.

Speech stops when user leaves the reader for the library/another book. Home, screen
lock and temporary system dialogs do not by themselves leave the reader session;
existing background playback can continue. No player in library, no autoplay on
opening a book/app, no automatic start after restore. Temporary audio-focus behavior
must preserve existing explicit pause/noisy-route rules and tests.

## A01 — Start and compact controls

Listen consistently opens a speech sheet with primary “Читать отсюда” and remembered
settings. Two taps for a new session are deliberate: location is visible before
sound begins; pause/resume during a session remain one tap. Start location is explicit:
“С текущего места” and, if different, “Продолжить прослушивание” with chapter/snippet.
Default is current visible anchor unless user deliberately chooses saved listening
position; do not silently substitute another page. A selected-range start uses that
original offset. No selection-dependent hidden start semantics after selection ended.

Quiet reader compact control layout: [Pause/Resume] [Stop] [Speech settings].
56dp primary target, others >=48; label status/voice in remaining width only when
it fits, otherwise put it in sheet. Safe area and anchor behavior are R10. Stop is
ordinary neutral icon, not error-red destructive action; stopping is safe/reversible.
Show local voice names, not implementation IDs. No animated waveform or fake volume
meter. M26 for icon, M21 for spoken highlight; no status-driven width jumps.

| State | UI | Accepted actions |
|---|---|---|
| Idle | “Читать отсюда”; saved-position alternative | Start, voice/settings |
| Preparing | “Готовим озвучку…” after T04; pause/stop stay visible | Pause, Stop; no duplicate starts |
| Playing | Pause, Stop, actual current phrase highlight | Pause, Stop, settings, optional phrase navigation once implemented |
| Paused | Resume, Stop; retain phrase highlight | Resume, Stop, settings |
| Failed | “Не удалось продолжить озвучку”; reason + Retry | Retry saved segment or Stop; no autoplay loop |
| Finished | “Озвучка завершена” inside sheet/status, Idle controls | Explicit restart only |

Pause during preparing must not be labeled as completed playback. Status updates
follow engine events, never optimistic “playing” before audio starts. Stop immediately
changes intent, cancels queued work, preserves last actual playback anchor. Late
callbacks from stopped book/session cannot resurrect controls or move the page.
A failure to save position is shown separately from successful audio stop.

## A02 — Speech settings sheet

Order: location summary; Play/Pause/Stop; Voice; Speed; Pauses; Sleep timer;
Pronunciation dictionary; Advanced/legal. Do not expose unavailable timer/dictionary
until corresponding phase ships. Default compact sheet shows first four; rest
scrolls, no expanding multi-level settings maze.

Voice: two radio rows with names and “Послушать образец” 48dp action. Example uses
one fixed local short sentence, not user's book text. Preview is a real engine
session, cancellable with Stop; never overlaps book audio. When active speech would
be interrupted, explicit preview action pauses it and states “Книга на паузе”;
return leaves book paused until Resume. Preview does not change book positions or
mark paragraphs listened. No hidden repeat loop.

Speed: 0.75–1.50×, 0.05 steps, visible current value and minus/plus. Pauses retain
existing ranges: sentence 0–1000ms step 50, paragraph 0–1500ms step 50. Show friendly
units (“0,55 с”) and small “Послушать пример” preview. Existing defaults 220/550ms
remain valid even off step grid; no forced rounding on upgrade. Sentence and
paragraph boundaries explain which pause wins (paragraph replaces sentence pause,
not both added accidentally). Trim/pad policy needs audio QA; no arbitrary blanket
silence insertion advertised as pronunciation improvement.

Initially preserve current engine capability: changes apply on next explicit
restart; show “Новые настройки применятся при следующем запуске” and button
“Применить и продолжить” that restarts from current phrase with preserved source
anchor. Do not claim seamless live change with an already-prefetched queue. Later
engine work may support boundary-safe reconfiguration; then spec must define queue
invalidation and audible discontinuity tests before changing this wording.

## A03 — Following speech and manual navigation

Highlight actual playing phrase, never the prefetched/generated next one. Keep
original phrase range even if synthesis text is different. Follow current phrase
only when it leaves visible reading viewport; one anchored page change/short scroll,
not per character or per callback. In scroll mode place phrase around upper third
with sufficient context, unless already fully visible. M18 for long jump, M19 for
short scroll; cancel immediately if user touches.

Manual scrolling/turning page suspends automatic following while speech continues;
show “К озвучиваемому месту” inside controls. It returns once and resumes following.
Opening search/notes/footnote/settings does not unexpectedly drag underlying page;
freeze follow until surface closes. Explicit navigation via search/chapter/bookmark
pauses speech and leaves listening anchor intact; user may choose “Читать отсюда”
or “Продолжить прослушивание”. Keep reading and listening anchors separate.
Leaving reader stops, without “keep playing?” prompt. Changing book stops previous
session before preparing new one. Explicit Stop also clears follow state.

Previous/next phrase are optional P5 functionality: real source segment boundaries,
not audio seeking by a made-up fixed number of seconds. Accessible labels specify
“Предыдущая фраза”, “Следующая фраза”. At boundary disable appropriately. Advancing
while paused selects new phrase and stays paused; while playing continues there.

## A04 — Sleep timer (optional feature, P5)

Presets Off / 15 / 30 / 45 / 60 minutes; custom 1–180 via stepper/numeric entry.
Option “Закончить текущую фразу” default on. Start only by Apply, not dragging.
Based on elapsed time, not wall clock or per-composition countdown. Pausing speech
does not pause timer; text says “Остановить через …, включая паузы”. App process
death stops audio and discards timer; no wakeup or restart of speech on relaunch.

At deadline: if paused/preparing stop immediately; if playing and finish-phrase is
on, finish currently audible phrase only, then stop. Hard ceiling 30s beyond
deadline; no finishing an entire chapter or queued fragments. Display “После этой
фразы” at deadline. Sleep stop must release resources and keep listening position.
No sound/vibration on timer completion. Remaining rounded minutes in sheet; compact
chip only when tools visible; no per-second recomposition of reading canvas.
Changing timer replaces it; Stop/exit cancels it; notification has Stop as fallback.

## A05 — License, permission and notification

License is bundled local content, shown before first actual synthesis if acceptance
required by existing product/legal flow. Explain briefly, Read terms, Accept & start,
Cancel. Acceptance persisted, not asked each session. Never turn legal content into
a promotional onboarding carousel. Theme/scroll/large text follow normal full-height
sheet rules; buttons remain reachable.

Avoid chaining license, permission and sample automatically on first tap. Notification
permission is requested after explicit “Управление на экране блокировки” action or
contextual explanation in speech sheet, with “Разрешить” and “Позже”. Refusal must
not prevent local playback. Android foreground-service requirements still apply;
do not condition starting its required service on notification grant. Do not nag
again on each book; permission status and explicit retry live in settings.

Notification remains an intentional Android media surface: book title, voice/status,
Pause/Resume, Stop; tapping returns to same reader session. Respect notification
privacy; no paragraph or annotation text on lock screen by default. No persistent
notification after stopped session, no library navigation shortcut that keeps audio
alive. Honor hardware audio route changes; don't resume after explicit user pause.

## A06 — Pronunciation improvement: evidence gate first

P6a experiment must use installed engine/model, fixed voice/speed and local WAV
outputs. Compare untouched text, combining acute stress, alternative documented
notations if supported, `е/ё` variants and explicit spoken-form replacements.
Unsupported notation must not be assumed to work because WAV is nonempty.
Keep both F1 and M1 samples, source string, synthesis string and outcome.

Corpus at least 40 phrases: ambiguous stress pairs (“за́мок/замо́к”, “му́ка/мука́”),
names/surnames, е/ё ambiguity, dates, ordinal/cardinal numbers, abbreviations and
sentences crossing punctuation/paragraph boundaries. Accented examples here are
test inputs, not claimed supported syntax. Human listening labels right/wrong/
unclear/artifact; owner evaluates later. Automated checks cover Unicode safety,
finite PCM and anchor preservation, not linguistic correctness. No device/network
voice or online AI fallback. Do not change default synthesis until sample review.

Decision gate: if stress markers demonstrably work, expose vowel-selection editor
with Preview. If not, expose only tested “Произносить как…” replacements; explain
that spelling changes can affect pronunciation and must be previewed. Don't ship a
button promising correct stress if the backend ignores it. A replacement engine/model
is a separate local benchmark project including size, license, memory and latency,
not a mandatory dependency of the interface refresh.

## A07 — Local pronunciation dictionary

List screen: search word/phrase, scope All/This book/Global, Add. Row: original →
spoken form, scope, enabled toggle, overflow Edit/Delete. No external dictionary
lookup or synchronization. Empty state explains purpose with Add, not an onboarding
tour. Access through speech settings and selected word's More menu.

Editor: original exact word/phrase, spoken form or verified stress editor, scope
Occurrence / This book / All books, case behavior, Preview, Save. From selection
default Occurrence (store anchor+context); manually created rule defaults This book
when a book is available, otherwise explicitly Global. Preview failure preserves
draft. Never auto-enable a newly edited rule while another phrase is speaking;
snapshot rules for synthesis session or invalidate queued segments at a defined
safe boundary, keeping already-playing audio intact.

Match precedence: occurrence > book > global; longest complete match first; no
recursive replacement. Unicode-aware word boundaries, NFC matching with mapping
back to source; preserve case unless explicitly overridden. Treat е and ё as
different by default. Do not substitute a substring in another word or infer all
inflections. Duplicate rule/scope prompts Replace or Cancel, not silent precedence.
Limit input e.g. 200 source / 400 spoken characters, validate nonempty speech text.
Apply transformation after source segmenting, before synthesis; retain original
start/end for UI and listening position. Rules spanning segment boundaries require
explicit support or an editor explanation, never partial matching across fragments.

Manual export/import uses versioned UTF-8 JSON through local picker, with preview
and conflict count. Keep original rules on failed import; no executable patterns,
regex code or remote resource URLs. Dictionaries and stable book-scope IDs included
in manual ZIP with backward-compatible optional entries. Restore to another device
maps book scopes using preserved IDs; orphan rules reported, not applied globally.

## A08 — Text normalization beyond dictionary

Separate P6b after evidence: local handling of selected abbreviations/numbers/dates,
with conservative exact rules and opt-out, previewable examples. Never blindly
expand every “г.” or replace every “е” with “ё”. Reader/source text stays intact;
transformed speech carries source mapping. Uncertain phrase falls back unchanged.
Record quality regressions by sample, not assumption that extra normalization helps.
Performance budget measured on local hardware, no mandatory model download.
