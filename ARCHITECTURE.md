# BookMT — Architecture

A modern, AI-augmented e-book reader. **Clean Architecture + MVVM**, single-module, organized by
layer then feature.

```
com.TopMob.bookmt
├── core/                  Cross-cutting: DI (Hilt modules), common (Resource, DispatcherProvider, Constants)
├── domain/                Pure Kotlin — no Android deps
│   ├── model/             Book, Bookmark, BookContent, ReaderSettings, TextSegment/SpeakerRole, VoiceProfile…
│   ├── parser/            BookParser interface (+ metadata, exceptions)
│   ├── tts/               TtsEngine, TextAnalyzer interfaces
│   ├── repository/        Repository interfaces
│   └── usecase/           GetBooks, ImportBook, OpenBook, UpdateReadingProgress, ManageBookmarks, ReadAloud
├── data/                  Implements domain contracts
│   ├── local/             Room (entities, DAOs, DB, converters)
│   ├── settings/          DataStore-backed ReaderSettings
│   ├── parser/            TXT, Markdown, FB2 parsers + PDF placeholder + factory
│   ├── tts/               OnnxTtsEngine (stub seams), HeuristicTextAnalyzer, DefaultVoices
│   ├── mapper/            Entity <-> domain
│   └── repository/        Repository implementations
└── presentation/         Jetpack Compose (Material 3)
    ├── navigation/        NavHost + typed routes
    ├── bookshelf/         ViewModel + UiState + Screen + components (grid/list, sort/filter, progress)
    ├── reader/            ViewModel + UiState + Screen + paginator + components (paged/scroll, controls, drawers, settings)
    └── theme/             ReaderColors (Day/Sepia/Night/AMOLED/custom)
```

## Key design decisions

- **Dependency rule:** `presentation → domain ← data`. The domain layer has zero Android imports
  and is fully unit-testable (see `app/src/test`).
- **Reactive state:** Room/DataStore expose `Flow`s; ViewModels expose a single immutable
  `StateFlow<UiState>` collected with `collectAsStateWithLifecycle`. All filtering/sorting/pagination
  runs off the main thread via an injected `DispatcherProvider`.
- **Performance:** `ReaderPaginator` slices the flattened book stream into bounded-size pages at word
  boundaries (O(n), off-thread), so the `HorizontalPager`/`LazyColumn` only ever composes the visible
  page(s) regardless of book size — no UI jank on large books. A single **absolute character offset**
  addresses any position (progress, bookmarks, TTS cursor).
- **Extensible parsing:** add a format by implementing `BookParser` and binding it `@IntoSet` in
  `ParserModule`; `BookParserFactory` dispatches automatically. PDF is a structural placeholder.
- **AI multi-voice TTS:** `TextAnalyzer` tags prose with `SpeakerRole`
  (NARRATOR / MALE / FEMALE / UNKNOWN); `TtsEngine` consumes the tagged stream and switches
  `VoiceProfile`s on the fly. `OnnxTtsEngine` wires the entire control flow with native
  inference/playback stubbed at `SEAM:` markers, ready for sherpa-onnx + Piper `.onnx` models in
  `assets/voices/`.

## Tech stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Hilt · Room (KSP) · DataStore · Coroutines/Flow ·
Navigation-Compose · Coil · ONNX Runtime · minSdk 24 / targetSdk 36.

> The `ksp` version in `gradle/libs.versions.toml` must track the Kotlin version exactly; bump it if
> a Gradle sync reports an unresolved KSP artifact.
