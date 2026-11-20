# Android module: BookMT

Applies to all Android work in BookMT. The core rules in `AGENTS.md` and `security.md` apply.

| Area | Rules |
|---|---|
| Architecture & Layering | Strict Clean Architecture: `presentation → domain ← data`. **MUST NOT** import `android.*` or `androidx.*` into `domain/`. Domain contains pure Kotlin models, repository interfaces, use cases, and parser interfaces. |
| UI Toolkit | Jetpack Compose + Material 3 exclusively. **MUST NOT** introduce XML Views for screens (XML is reserved for app theme, launcher icons, splash screen, and backup rules). |
| State Management | ViewModels expose a single immutable `StateFlow<UiState>`. Compose screens collect state using `collectAsStateWithLifecycle()`. Handle user actions via sealed UI events or direct ViewModel methods. |
| Asynchrony & Threading | Use Kotlin Coroutines and Flow. Inject `DispatcherProvider` (`io`, `default`, `main`) into ViewModels, Repositories, and UseCases. **MUST NOT** perform file I/O, book parsing, DB queries, or pagination calculations on `Dispatchers.Main`. |
| Dependency Injection | Use Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Module`, `@InstallIn`). Use multi-binding (`@IntoSet`) for registering `BookParser` implementations in `ParserModule`. |
| Persistence (Room) | Use Room via KSP. Room database queries return `Flow<List<T>>` for reactive observing or suspend functions for mutations. Keep Room schema export enabled (`schemas` folder). Handle schema migrations cleanly. |
| Settings (DataStore) | Use Jetpack DataStore Preferences for user reading preferences (`ReaderSettings`). Expose settings as `Flow<ReaderSettings>`. |
| Book Parsing | Implement `BookParser` interface. Handle corrupted or partial files gracefully without crashing; return `Resource.Error` with clear messaging. Never hold entire multi-hundred-megabyte books in unchunked memory. |
| Pagination & Cursor | All book positions must be expressed in absolute character offsets. The paginator must respect word boundaries, font metrics, and line heights without clipping lines or losing words. |
| Media & Background TTS | TTS audio playback runs in a foreground service with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Implement proper lifecycle cleanup: release audio resources, cancel scopes, and stop foreground notifications when playback stops. |
| Offline Invariant | BookMT is strictly an offline e-reader. **MUST NOT** declare `android.permission.INTERNET` or add HTTP networking libraries. |
| Permissions & SAF | Use Storage Access Framework (SAF) (`ActivityResultContracts.OpenDocument`, `CreateDocument`) for reading external files and exporting backups. Only declare runtime permissions strictly needed (`POST_NOTIFICATIONS` on API 33+, `READ_EXTERNAL_STORAGE` up to API 32). |
| Localization | All UI text must reside in `res/values/strings.xml` (default) and `res/values-ru/strings.xml` (Russian). Never use hardcoded strings in `@Composable` functions. |
| Lifecycle & Memory | Avoid leaking `Context`, `CoroutineScope`, or heavy bitmaps. Book covers must be loaded asynchronously via Coil with appropriate downsampling. Handle process death and screen rotation without losing reading position. |
| Verification | Run `.\gradlew.bat testDebugUnitTest` for all changes. Verify edge cases: empty books, missing covers, zero-length chapters, cold app restarts. |
