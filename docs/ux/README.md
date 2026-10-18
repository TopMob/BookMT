# BookMT — comfort-first interface roadmap

Status: **target specification; implementation in progress**. Date: 2026-10-18.
First increment: [P0/P1 implementation report](IMPLEMENTATION_2026-10-18.md).
Current increment: [library/settings and page navigation](IMPLEMENTATION_P2_2026-10-18.md).

Latest owner revision (takes precedence over older reports): one shared app/reader
theme, neutral graphite accents in Day/Night/Black, and forward screen turns starting
at the first not-fully-visible line. Separate reader theme controls are removed.
Inspected baseline: `192239150dc1ea89014b97fe35d0b2d8e6a8cf88`.
Audience: the implementation agent and the owner. Product decisions below come from
the owner's instructions in this conversation. They supersede conflicting UX proposals
in `DEVELOPMENT_PLAN.md`; offline/data-safety rules in `AGENTS.md` still apply.

## Objective

Make opening, reading, listening, finding and annotating a book comfortable. The
success criterion is reduced effort and preserved trust, not session duration,
engagement, streaks, notifications or number of settings. Quiet, warm, legible and
predictable is the visual direction. No subscriptions, advertising, accounts,
recommendation feeds, engagement reminders or celebratory interruptions.

This is a native Android Compose application, not a website. Do not introduce a
WebView or HTML controls to implement this design. In-app palettes and menus must
be owned by BookMT. Deliberate Android interactions are specified separately.

## Read order and authority

1. [Visual system](01-visual-system.md) and [exact color tokens](theme-tokens.json).
2. [Motion, input, timing and platform boundaries](02-interaction-contract.md).
3. [Library and settings screens](03-library-and-settings.md).
4. [Reading screens and overlays](04-reader.md).
5. [Speech and pronunciation](05-speech.md).
6. [Implementation sequence and acceptance](06-delivery-and-qa.md).

Design checks and handoff: [validation record](VALIDATION.md).

Token JSON is authoritative for colors. The motion table is authoritative for
durations. Screen specifications determine actions and state. If implementation
exposes a real conflict, record the decision here and update both conflicting
sections before coding; do not silently substitute framework defaults.

Numbers are design targets, not measurements of current performance. The original
design work inspected source and test reports. Implementation evidence and remaining
checks are tracked separately in the linked implementation report.
Existing features are mapped below. Proposed capabilities are explicitly gated
in the delivery roadmap: a UI must not pretend an unavailable capability works.

## Primary user journeys

| Journey | Desired outcome | Common discomfort to prevent |
|---|---|---|
| First launch → import → read | One clear import action, then useful text | Onboarding carousel, permission barrage, empty unexplained screen |
| Return to a book | Same visible passage, familiar formatting | Wrong page flashed first, jump after pagination, lost library scroll |
| Read at night | Consistent dark page, panels and loading surfaces | White dialog, brightness reset, theme cycling through bright colors |
| Adjust text | See preview and keep original passage | Re-pagination on every slider tick, changed position |
| Search → inspect → return | Find passage and return with one action | Lost query, lost origin, mistaken page-turn during selection |
| Listen | Clear start/pause/stop, correct position and honest status | Unexpected sound, tiny controls, preparing shown as playing |
| Leave book | Save positions; stop speech; return to library | Confirmation on every exit, audio continuing in library |
| Restore library | Understand replacement, preserve recovery | Immediate destructive import, unclear result, silent missing books |

## Decisions after considering alternatives

| Question | Attractive alternative | Cost / inconvenience | Chosen behavior |
|---|---|---|---|
| Library navigation | Bottom tabs for every destination | Permanent space and multiple competing entry points | Library is home; one drawer for Library, Marks, Settings; statistics inside Settings |
| Quick reading | Floating continue button | Covers books; ambiguous play icon | Inline continue card with book title and book icon |
| First reader controls | All chrome hidden | New user cannot discover controls | Controls visible on entry; hide on deliberate center tap; no timed disappearance |
| Text animation | Paper curl / zoom from cover | Visual noise, frame cost, delay | Instant page changes by default; optional short slide |
| Settings | One enormous dialog | Scrolling, hidden options, cramped preview | Categorized settings plus a compact reader appearance sheet |
| Theme shortcut | Cycle through all themes | Cannot predict next color; flashes bright at night | Toggle between remembered light/dark choices; explicit palette in Appearance |
| Deletion | Swipe-to-delete | Accidental loss, conflicts with navigation | Explicit menu and one confirmation; reversible marks use Undo only after backend supports it |
| Brightness gesture | Always active edge strip | Steals scroll/system Back | Off for new users; explicit opt-in with visible practice area and inset-safe region |
| TTS on reader exit | Continue in library | Violates owner's intended exit behavior | Stop when leaving the book; Home/lock may continue existing session |
| Stress correction | Replace every ambiguous word globally | Different meanings and model-dependent pronunciation | Local rules with occurrence/book/global scopes; audible preview before enabling |
| Color chooser | Platform/browser fallback | Unexpected style or external surface | BookMT palette/editor; no external color intent |
| File chooser | Custom filesystem explorer | Permissions, provider incompatibility, duplicated OS behavior | Explicit local Android document picker |
| Aesthetics | Wood textures, glowing highlights, moving gradients | Busy background and resource cost | Flat warm surfaces; one restrained accent; covers supply visual variety |

## Screen/source coverage

Paths below are relative to `app/src/main/java/com/TopMob/bookmt/`.

| Existing source | Surfaces covered by this specification |
|---|---|
| `presentation/navigation/BookMTNavHost.kt` | Navigation, Back, external file entry, restoration |
| `presentation/bookshelf/BookshelfScreen.kt` | Library, drawer, search, sort/view, selection, import, deletion, tags |
| `presentation/bookshelf/components/BookItems.kt`, `BookCover.kt` | Grid/list items, progress, missing cover |
| `presentation/bookshelf/components/BookContextSheet.kt` | Actions, delete confirmation, legacy information dialog |
| `presentation/bookshelf/BookDetailsScreen.kt` | Details, metadata editing, cover import, technical data |
| `presentation/bookshelf/LibraryTools.kt` | Filters, collection choice, offline preparation, statistics |
| `presentation/bookshelf/GlobalBookmarksScreen.kt` | All marks, search/filter/edit/delete/export |
| `presentation/settings/AppSettingsScreen.kt` | Settings home, language, reading preferences, ZIP backup/restore |
| `presentation/settings/ExtraReadingPreferences.kt` | Fonts, brightness, orientation, fullscreen, volume keys, auto-page |
| `presentation/settings/TtsPreferences.kt` | Voice, speed, pauses, license |
| `presentation/reader/ReaderScreen.kt`, `components/ReaderControls.kt` | Reader shell, bars, compact speech controls, errors, auto-page |
| `presentation/reader/components/ReaderContent.kt`, `ReaderPageText.kt` | Pages/scroll, links, footnotes, images, text overlays |
| `presentation/reader/components/ReaderTools.kt` | Search, go-to, appearance, selection, note editor |
| `presentation/reader/components/ReaderSideDrawer.kt` | Contents and book marks |
| `presentation/reader/PdfReaderScreen.kt` | PDF loading, zoom/pan, page navigation and marks |
| `presentation/theme/ReaderColors.kt`, `ui/theme/*` | Unified themes and typography |
| `presentation/util/LocalDocumentContracts.kt` | Deliberate system file interaction |
| `data/tts/TtsPlaybackService.kt` | System media notification; no library player |

## Scope guardrails

- Preserve book IDs, anchors, annotations, library files, existing settings and ZIP compatibility.
- Never make a visual transition the owner of persistence, speech or import work.
- No new runtime network access; local files and bundled/imported local models only.
- No phone use unless the owner authorizes it. Emulator tests first; mark remaining
  physical-device checks honestly instead of claiming completion.
- Implement in the small phases in document 06. The owner requested this roadmap,
  not application changes during its creation.
- Commit and push completed, verified implementation phases as requested by the owner.

## Suggested prompt for the next agent

```text
Read AGENTS.md and docs/ux/README.md, then the linked specifications. Implement
the next unfinished, scoped increment; preserve existing reader positions, user preferences and
offline behavior. Treat everything in docs/ux as a target specification, not
implemented functionality. Inspect current HEAD before changes. Follow the
documented colors/motion/state contracts, validate the appropriate scenarios,
record remaining limitations, commit and push. Do not use the owner's phone
without authorization. Do not start later feature phases implicitly.
```
