# BookMT — Architecture

> Обязательное ограничение: полностью офлайн-приложение. Сеть, облако, телеметрия и удалённый анализ не входят в архитектуру. TTS — встроенные локальные модели и локальный синтез ([OFFLINE_TTS.md](docs/specs/OFFLINE_TTS.md)). См. `AGENTS.md` и [FEATURE_BASELINE.md](docs/specs/FEATURE_BASELINE.md).

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
│   ├── tts/               OnnxTtsEngine (sherpa-onnx), TtsPlaybackService, HeuristicTextAnalyzer, DefaultVoices
│   ├── mapper/            Entity <-> domain
│   └── repository/        Repository implementations
└── presentation/         Jetpack Compose (Material 3)
    ├── navigation/        NavHost + typed routes
    ├── bookshelf/         Library browser, details editor, global bookmarks, components
    ├── reader/            ViewModel + UiState + Screen + paginator + components + pdf/
    ├── settings/          App settings, appearance, reading, speech preferences
    ├── components/        Shared UI components (ComfortStates)
    ├── theme/             ComfortPalette, unified themes, typography, motion
    └── util/              Formatters, local document SAF contracts
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
  `ParserModule`; `BookParserFactory` dispatches automatically. PDF uses a separate PdfRenderer screen.
- **Offline neural TTS:** bundled Supertonic 3 INT8 via sherpa-onnx, Russian F1/M1.
  `ReadAloudUseCase` lazily feeds bounded passages with absolute offsets. One passage
  is synthesized ahead while `AudioTrack` plays; pause retains the session, completion
  releases it. `TtsPlaybackService` owns the foreground notification, media session
  and wake lock. The reader ViewModel owns the reading job; leaving the reader stops it. Events carry a book ID and session ID; reading and listening have separate anchors.
  No Android system TTS, runtime downloads or Internet permission. The role-analysis
  interface remains extensible, but the selected voice currently reads every role.
  See [OFFLINE_TTS.md](docs/specs/OFFLINE_TTS.md) for model preparation, licensing and validation.

## Tech stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Hilt · Room (KSP) · DataStore · Coroutines/Flow ·
Navigation-Compose · Coil · minSdk 24 / targetSdk 36.

See [TECHNICAL_AUDIT.md](docs/audits/TECHNICAL_AUDIT.md) for fixes, validation and remaining limitations.

> The `ksp` version in `gradle/libs.versions.toml` must track the Kotlin version exactly; bump it if
> a Gradle sync reports an unresolved KSP artifact.

## Reader engine 1.1

The main path is `BookParser.parseTo` → `DiskBookCache.Writer` → `BookContent` with
`ChapterCollection` backed by local blocks. `parse` retains the old canonical text for
legacy anchor migration; it is not the normal reading path. `TextAnchors` captures
source-relative locations plus context; `PositionMigration` updates old annotations
and book positions in a Room transaction. Schema 3 preserves versions 1/2 through migrations.

`ReaderPaginator` stores page boundaries instead of book-text copies. `ReaderContent`
measures the requested block first, then fills the global index. `ReaderRichText`
uses the same styles and placeholders for measuring and displaying. Page indices are
cached on disk; source blocks are CRC-checked. Background pagination yields to speech.
Source files and user annotations are never stored in or removed with this cache.

See [READER_ENGINE.md](docs/specs/READER_ENGINE.md) for compatibility, limits and verification.
