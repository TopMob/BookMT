# BookMT — Architecture

> Обязательное ограничение: полностью офлайн-приложение. Сеть, облако, телеметрия и удалённый анализ не входят в архитектуру. Будущая TTS — локальные модели и локальный синтез. См. `AGENTS.md` и `FEATURE_BASELINE.md`.

An Android e-book reader. **Clean Architecture + MVVM**, single-module, organized by
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
    ├── bookshelf/         Library, details editor, global bookmarks, grid/list, sort/filter, progress
    ├── reader/            ViewModel + UiState + Screen + paginator + components (paged/scroll, controls, drawers, settings)
    └── theme/             ReaderColors (Day/Sepia/Night/AMOLED/custom)
```

## Key design decisions

- **Dependency rule:** `presentation → domain ← data`. The domain layer has zero Android imports
  and is fully unit-testable (see `app/src/test`).
- **Reactive state:** Room/DataStore expose `Flow`s; ViewModels expose a single immutable
  `StateFlow<UiState>` collected with `collectAsStateWithLifecycle`. All filtering/sorting/pagination
  runs off the main thread via an injected `DispatcherProvider`.
- **Reading surface:** `ReaderPaginator` slices chapter text into bounded-size pages at word
  boundaries, using Compose text measurement for the actual viewport and font. `HorizontalPager`
  and `LazyColumn` compose visible pages. Parsing still loads the book into memory; very large
  books need a disk-backed parser/cache. A single **absolute character offset**
  addresses any position (progress, bookmarks, TTS cursor).
- **Extensible parsing:** add a format by implementing `BookParser` and binding it `@IntoSet` in
  `ParserModule`; `BookParserFactory` dispatches automatically. PDF is a structural placeholder.
- **Unfinished multi-voice TTS:** `TextAnalyzer` tags prose with `SpeakerRole`
  (NARRATOR / MALE / FEMALE / UNKNOWN); `TtsEngine` consumes the tagged stream and switches
  `VoiceProfile`s. Native synthesis and playback are not implemented. `OnnxTtsEngine.prepare`
  explicitly reports this limitation rather than simulating silent playback. ONNX is not shipped
  until a working engine and voice assets are integrated.

## Tech stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Hilt · Room (KSP) · DataStore · Coroutines/Flow ·
Navigation-Compose · Coil · minSdk 24 / targetSdk 36.

See [TECHNICAL_AUDIT.md](TECHNICAL_AUDIT.md) for fixes, validation and remaining limitations.

> The `ksp` version in `gradle/libs.versions.toml` must track the Kotlin version exactly; bump it if
> a Gradle sync reports an unresolved KSP artifact.
