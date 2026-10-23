# BookMT 📚

<p align="center">
  <b>A modern, privacy-first, fully offline Android e-book reader with on-device neural TTS.</b>
</p>

---

## Overview

BookMT is designed from the ground up to be an **unconditionally offline** reader for Android. It requires zero network permissions, contains no analytics, telemetry, or remote services, and performs all operations — from book parsing and library indexing to neural text-to-speech — entirely on your device.

### Key Features

- **Multi-Format Support:** EPUB, FB2, TXT, Markdown, and PDF.
- **On-Device Neural TTS:** Integrated local speech synthesis using Sherpa-ONNX (`supertonic-3`), featuring Russian voice models with zero server dependency.
- **Reading Engine 1.1:**
  - High-performance paginated and continuous scroll modes.
  - Sub-word boundary measurement with Compose text layout caching.
  - Robust reading position recovery and anchor-based bookmarking.
  - In-book search and footnote preview.
- **Comfort Design System:**
  - Curated reading palettes: Day, Sepia, Night, and AMOLED Black.
  - Fluid Material 3 motion tokens with respect for system accessibility scales.
  - Volume key and physical keyboard navigation support.
- **Privacy & Data Ownership:**
  - Complete offline guarantee: no `INTERNET` permission in manifest (`tools:node="remove"`).
  - Local document import with persistent security-scoped URI storage.
  - Manual full database backup and restore via standard Android document pickers.

---

## Architecture

BookMT follows **Clean Architecture + MVVM** principles within a modular, single-module structure:

```
com.TopMob.bookmt
├── core/                  Cross-cutting: DI (Hilt), DispatcherProvider, LocaleManager, Constants
├── domain/                Pure Kotlin business logic & entities (zero Android framework dependencies)
│   ├── model/             Book, Bookmark, BookContent, ReaderSettings, TextSegment, VoiceProfile
│   ├── parser/            BookParser interfaces and contracts
│   ├── tts/               TtsEngine and TextAnalyzer specifications
│   ├── repository/        Repository interfaces
│   └── usecase/           GetBooks, ImportBook, OpenBook, ReadAloud, ManageBookmarks
├── data/                  Data layer & hardware implementations
│   ├── local/             Room Database, DAOs, TypeConverters, Schemas
│   ├── parser/            EPUB, FB2, TXT, Markdown parsers & BookParserFactory
│   ├── reader/            DiskBookCache, persistent page index, position migration
│   ├── settings/          DataStore preferences & launch caches
│   ├── tts/               OnnxTtsEngine, TtsPlaybackService, SpeechAudio processor
│   ├── backup/            Local database and cover snapshot/restore manager
│   └── repository/        BookRepositoryImpl, BookmarkRepositoryImpl
└── presentation/          Jetpack Compose UI (Material 3)
    ├── navigation/        Type-safe navigation routes & NavHost
    ├── bookshelf/         Library browser, book details, bookmarks screen
    ├── reader/            ReaderScreen, ReaderViewModel, pagination components & controls
    └── theme/             Comfort palettes, motion scales, and typography
```

For detailed architectural decisions, see [ARCHITECTURE.md](ARCHITECTURE.md) and [READER_ENGINE.md](docs/specs/READER_ENGINE.md).

---

## Tech Stack

- **Platform:** Android (minSdk 24, targetSdk 36)
- **Language:** Kotlin 2.2+
- **UI:** Jetpack Compose (Material 3)
- **DI:** Dagger Hilt
- **Persistence:** Room (KSP), Jetpack DataStore Preferences
- **TTS Engine:** Sherpa-ONNX (embedded local ONNX Runtime)
- **Image Loading:** Coil Compose
- **Async:** Kotlin Coroutines & Flow

---

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/TopMob/BookMT.git
   cd BookMT
   ```

2. Verify bundled TTS models (required for build):
   ```bash
   python tools/prepare_tts.py
   ```

3. Build and test:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

---

## Constraints & Offline Guarantee

Refer to [AGENTS.md](AGENTS.md) for strict architectural rules:
- No network libraries, analytics, or remote synchronization.
- All dependencies and speech models must remain local and offline-operable.
- Room database migrations must preserve user data without destructive schema drops.

---

## License

This project is licensed under the Apache 2.0 License.
