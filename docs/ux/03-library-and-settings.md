# Library, settings and data workflows

All surfaces use document 01 and M/T IDs from document 02. Every screen supports
loading, empty, content, error and restored state where applicable. A spinner never
substitutes indefinitely for an error. Error actions preserve existing content.

## L01 — Library home

Current issue: top bar has menu/search/tools/sort/view; tools mixes filtering,
maintenance and statistics; Continue floats over content and uses a play icon.
Choice: keep library as the only home, with a stable layout and explicit discovery.

```text
[Menu] Библиотека                          [Search] [More]
[Продолжить чтение                                  >]
[small cover] Title (2 lines) / Author / Chapter · 34%
[Все книги v]                   [Сортировка: Недавние]
[active filter chips, only when needed]
book grid/list …
                                  [+ Добавить книгу]
```

Continue card exists only for a real eligible last book; never for an empty library.
It is an item at top of the scrolling list (not sticky, not floating), all of it
opens that book. Book icon instead of play. No duplicate card for each recent book.
Respect an explicitly finished book; do not reopen it just because its last-read
timestamp is newest. Preserve filters when opening Continue even if it is outside
the filtered subset. Returning restores exact anchor book + pixel offset.

Toolbar max 3 icon targets including Menu; More contains “Вид библиотеки”, “Выбрать
книги”, “Добавить книги…” as accessible alternative to the import button. Sort is
one text button in the filter row, not another icon. Import button bottom-end 56
high; content bottom inset equals its measured height + 16 + navigation inset, not
hardcoded 180dp. Button never covers the final item. At small width use compact
plus icon with accessible “Добавить книгу”; expanded label where it fits.

Grid: min cell 144dp; cover 2:3 fit, title 2 lines, author 1, progress label and
favorite marker. List: cover 56×84, title up to 3 lines, author 2, progress/status.
Use `isFinished` for read status, not progress==100%. Metadata allows wrapping at
large font; thumbnail does not inflate with font scale. No percent animation.
Book actions available from visible 48dp overflow target and long press; long press
opens same context sheet (not a different selection mode). Tap opens book, once.
M01 feedback; opening M04; cover appearance M25 only on first actual decode.

First launch empty: book icon, “Ваша библиотека”, “Добавьте книгу с устройства,
чтобы начать читать”, primary “Добавить книгу”. No tutorial modal or permissions.
Filtered empty: “По этим условиям книг нет”, active conditions and “Сбросить фильтры”.
Search empty uses query-specific wording. Loading uses same layout placeholders
without shimmer. Failed library read gives Retry and preserves available records.

Acceptance: 1/20/1000-book libraries; no overlapping actions at 320dp; long titles;
scroll restoration after opening and after orientation; repeated tap opens once.

## L02 — Library drawer

Entries in order: Library, Marks, Settings. Width min(320dp, windowWidth−56dp),
opaque themed surface, selected row + icon. Favorites and Collections are library
filters, not duplicate navigation destinations. No profile header, account, online
store, badges or dashboard. Title BookMT 22sp; optional version only in About.
M07; outside tap/Back closes. Select destination on release, close/navigate as one
transition, avoiding drawer-then-screen double travel. Drawer swipe only from
allowed app area; never override OS Back region. Keyboard/TalkBack focus trapped
while modal and returned to Menu on close.

## L03 — Library search

Search action changes top bar to Back + focused search field + Clear. Placeholder
“Название или автор”; include tags only if actually supported and label accordingly.
Search is within current filters; visible chips remain, with “Искать во всей
библиотеке” action when results are empty. T03, M10 for mode change. Query survives
opening book and returning; Back first hides keyboard, next leaves search and
restores previous unsearched list anchor. Clear empties query, stays in search.
IME Search hides keyboard and displays results without an extra screen. Cancel
stale work; never flash “nothing found” before newest query completes. Failed
search retains query and retry; an old result list must be labeled as old or hidden.

## L04 — Filters, sorting, view and collections

“Все книги” opens a BookMT bottom sheet: status (All/Unread/Reading/Finished),
Favorites toggle, Format, Collections. Multi-collection selection is intersection;
say “Книги со всеми выбранными коллекциями”. Use wrapping chips or a searchable
list, not dozens of horizontally hidden tags. Changes are draft until “Показать
N книг”; Reset resets draft; Back/outside drops uncommitted changes. Count updates
as local query completes, do not show an invented number. M06.

Sort opens small sheet: Recently read (descending), Added (descending), Title
(ascending), Author (ascending), Progress (ascending), matching actual supported
sort enum; unavailable choices are omitted until query support exists. Direction
is in the same sheet with clear labels, not hidden under statistics. Choosing
applies immediately and closes. Stable ID tie-break prevents random rearrangement.
Sort resets scroll to top because order changes; choosing same sort changes nothing.

View sheet has List / Grid with small static examples, selected check; applies
immediately, preserves anchor book, M11. No layout animation of every cover.
Collections management is reached via Filters → Collections → Manage. Create,
rename and remove collection are later backend work; until then offer existing
tag add/remove with honest labels. Removing collection never deletes its books.
Map reserved Favorites label without turning it into an editable duplicate tag.

## L05 — Book actions and multiselection

Context sheet header: cover, title, author; actions in order: Read/Continue, Details,
Add/remove favorite, Mark read/unread (based on `isFinished`), Select books, Delete.
Delete separated by divider, error color. No duplicate “Info” modal: legacy
BookInfoDialog routes to Details. All rows 56 minimum, M06. Mutations show actual
result; reversible favorites/status may use optimistic UI with rollback on error.

Selection mode: top Back, “Выбрано: N”, More; checkboxes on items. Visible bottom
actions: Collection, Read status, Delete; at narrow width use labeled menu. Select
all means current result set; label “Выбрать все найденные (N)”. Freeze result set
while selection active, or reconcile removed IDs and update count atomically.
No selected IDs outside displayed snapshot silently included in deletion. Exit
clears selection; sorting/search/filter controls temporarily replaced, not stacked.

Delete book(s): one confirmation with title/count and “Книги и их отметки будут
удалены из BookMT. Исходные файлы вне приложения останутся.” Explicit Delete/Cancel;
Cancel default focus. No promised Undo until whole-book rollback exists. During
write prevent duplicate delete; failure preserves failed items and actionable result.
Do not use swipe delete. Bulk partially failed result lists affected books.

Mark read preserves reading/listening positions; no forced jump to last page.
“Начать заново” (in Details after read) is a separate explicit navigation action,
preserves annotations and gives Return to previous place; does not silently erase
listening position. No congratulations animation on finishing.

## L06 — Import and local-source repair

Explicit Add → local Android multi-document picker (T13). Cancel returns unchanged.
After selection: inline task strip “Добавлено 3 из 8” + Details + Cancel; user may
read existing books during import. Byte/count progress only if real. Cancel stops
future files and current work at a safe boundary; completed imports remain. Do not
offer Cancel unless backend can uphold it; phase P3 includes that support.

Multiple files: remain in library, do not repeatedly open books. Single explicit
external Open with BookMT: open imported/existing duplicate book after successful
ownership copy. In-app import of one book: completion strip offers “Читать”, does
not navigate unexpectedly while user works elsewhere. Duplicate: “Уже в библиотеке”
with Open, preserving ID and marks. Unsupported/corrupt/too-large files appear in
one result list with per-file explanation, Retry if meaningful, no modal per file.

Use existing enforced limits; display them only on relevant error. No “all imported”
if some failed. Completion strip dismissible; detailed result available until next
import or app restart, no permanent badge. Size/format available before open when
known. Out-of-space message offers “Управление памятью”, no automatic deletion.

Unavailable legacy source: show book title and “Выбрать файл заново”. Repair must
validate identity (hash when available, otherwise explicit metadata review) before
replacing local source; keep book ID, positions and marks. A clearly different
edition is a new book unless a separate safe remapping workflow exists.

## L07 — Book details and edit metadata

Split read-only Details from Edit. Details: 96×144 cover, full title and author,
Continue/Read primary; Favorite secondary; description; Collections; read status;
collapsed Reading information; collapsed File information. File info includes
format, size, added date, local availability and a copyable path under Details.
No long internal URI in primary content. Statistics never framed as obligation.

Edit action opens form with title (required), author, description, collections and
Change cover. Persistent Save text button; active only when dirty and valid. Fields
remain editable while not saving; no empty field reset on late database emission.
Dirty-close protocol from document 02. Imported cover staged until save; Cancel
discards new temporary copy; old cover kept until successful save. M25 preview;
failure shows inline error without losing draft. Save completes once and returns
to Details, not directly to library. Large font turns technical key/value rows into
stacked labels rather than fixed 120dp label columns.

## L08 — All marks / book marks

Title “Отметки”; type filters All / Bookmarks / Highlights / Notes; optional Book
filter in global screen. Search placeholder “Книга, цитата или заметка”. Search
and type/filter combinations retain selection and list scroll on return. Global
default newest first, book-local default position order; user may change order.
Items show book (global), chapter, snippet up to 3 lines, note up to 3, color marker
with accessible name. Tap opens anchored position; overflow offers Edit/Delete.
Avoid two permanent destructive/edit buttons competing with every snippet.

New note editor: quoted original read-only, editable note below, Save/Cancel.
Existing highlight editor can change semantic color as well as note. Preserve
type unless user explicitly converts; saving an empty note does not silently delete
the highlight. Delete confirmation includes short snippet until robust Undo exists.
P4 may replace confirmation for single mark with real 8s Undo, preserving ID and
original contents transactionally; bulk delete still confirms.

Export menu distinguishes “Сохранить TXT…” and “Поделиться…”. Snapshot current
filtered selection before launching picker; label scope “Экспортировать N отметок”.
No content when zero; show reason. Blank list vs no search matches have distinct
messages. Failed/missing anchor offers book opening and warning, not silent wrong
position. Reuse the same editor/item components inside reader and globally.

## S01 — Settings home

Six stable rows with current-value summaries: Appearance; Reading & controls;
Speech; Library & files; Language; About. No deeply nested generic “Advanced”.
At most two navigations from settings home to any actual control; library/file
backup gets its own workflow screen. Reuse same settings state in reader shortcuts.
Searchable settings is deferred until the catalog justifies it. M03.

Language: System / Русский / English, one choice applies immediately; preserve
screen and drafts across recreation, no app restart dialog. System resolves actual
Android locale. All UI copy/resources bilingual, no hardcoded English error leaking
from exception. Book content stays in its original language. Numbers/dates respect
locale; decimal text fields accept locale input and normalize for persistence.

About: version/build, “Работает без интернета”, local licenses, optional manual
diagnostic export entry when implemented. No feedback nag, rate-us prompt or link
that silently opens browser. Diagnostics must preview what will be saved; default
excludes book text and private annotations.

## S02 — Appearance

One shared palette: Follow system, Day, Sepia, Night, Black; selection check.
The same choice applies to library, reader and all settings; no reader override. Custom palette
editor is a later feature from document 01. Motion mode and optional haptics here.
Reader font/text settings are reached by “Текст книги” and share R08's controls.
Theme changes M15; current screen changes with choice, no confirmation for presets.
Expose exactly one theme choice in Appearance. Reader shortcuts edit that same setting. Reset appearance affects this category only and shows
which values reset; it cannot erase typography or book data.

## S03 — Reading and controls

Rows/groupings: Pages/Scroll; Text layout (R08); Gestures; Screen; Auto-page.
Gestures includes tap preset/practice, volume-page toggle, brightness gesture.
Screen includes Follow system/Portrait/Landscape, fullscreen, keep awake, brightness
Auto/manual with explicit percent. Manual brightness preview affects only this
window; exit outside reading restores system setting. Minimum 1% with visible
“Системная яркость” reset. Fullscreen retains gesture/navigation access; no settings
that trap the user. Existing user values retained on migration.

Auto-page page: default 30s, range 5–180s; presets 15/30/60 plus stepper/numeric
entry; start is an explicit reader action. It does not start merely from editing
interval. Hide or clearly disable in continuous scroll until an actual continuous
auto-scroll feature exists. Pause for modal/selection, restart full interval after
closing; stop on background, exit or end. No fractional leftover timer surprise.

## S04 — Library & files / storage

Rows: Backup, Restore, Local availability, Storage, Statistics. Storage shows
books/covers/fonts/cache with measured size (Unknown while calculating); never
infer size from stale UI. “Очистить кэш” explains rebuilt content and preserves
books/positions/marks. Exclude caches currently in use or close their readers safely.
No generic Clean button that deletes original books. Bulk offline preparation shows
progress and per-source failures, preserves IDs. Cancel stops future entries safely.
Space management is a backend-dependent phase, not a cosmetic button.

Statistics: total books, read/in progress, accumulated reading time, storage;
optional per-book rows sorted by time. Plain numbers, no streaks, rankings, goals,
red “you missed a day”, daily reminders or animated counters. Time/remaining-time
estimates labeled approximate; never fabricate estimates when data insufficient.

## S05 — Backup / restore

Backup screen explains contents: owned books, covers, fonts, annotations, positions,
preferences, and any new dictionaries/profiles once included. Display last successful
manual export if persisted. “Создать ZIP…” → local destination picker → real phases
Preparing / Writing / Complete. Existing temp-first export safety retained. Error
keeps library; missing source list offers repair. Do not promise old destination
survives a provider write failure. Cancellation safe before write; provider-dependent
partial output labeled as failed, never “saved”.

Restore: Choose ZIP → validate/stage off UI thread → preview metadata/counts/date
when available → one confirmation “Заменить библиотеку этой копией?” explaining
replacement, not merge. Offer “Сначала создать копию текущей библиотеки” without
forcing it. Legacy archives label unavailable fields “Нет данных”, not zero.
No replacement before explicit confirmation. Wrong format/integrity/space failure
leaves current state untouched. Before transactional install allow Cancel; while
install/rollback runs show honest “Восстанавливаем библиотеку”, disable conflicting
mutations and explain why cancellation is unavailable. Do not interrupt atomic work
to satisfy a cosmetic Back animation. Existing recovery journal remains authoritative.

Success shows completion before required relaunch, with “Открыть библиотеку”. No
unexplained instant process exit; implement safe restart protocol explicitly. If
relaunch cannot be automated reliably, tell user to reopen, retain completion state.
After restore verify new settings/language loaded, existing local files retained
according to backup rules, no success if rollback occurred. Rollback failure has a
dedicated recovery screen with Retry and local diagnostic export; do not open mixed
database/settings state. This workflow needs backend preview/state support in P3.
