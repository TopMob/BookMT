# Agent rules: BookMT

Rules and guidelines for AI coding agents working on the BookMT offline e-book reader.

## Structure

```
AGENTS.md                      core rules, project passport, workflow (always read)
ldock/agent/
  android.md                   Android, Compose M3, Clean Architecture, Room, Hilt, TTS
  security.md                  security rules: offline boundary, Zip Slip, XXE, SAF
  skills.md                    policy and recommended skills for Android/Kotlin
  README.md                    this guide
```

## Quick Reference for Developers & Agents

1. **Stack**: Kotlin 2.2, Jetpack Compose (Material 3), Hilt, Room (KSP), DataStore Preferences, Navigation-Compose, Coil, ONNX Runtime.
2. **Architecture**: Single module `:app`. Layering: `presentation → domain ← data`. The `domain` layer has 0 Android dependencies.
3. **Threading**: Inject `DispatcherProvider` for background tasks (parsing, pagination, DB).
4. **Offline**: No internet access. All features (book shelf, parsing, reading, TTS voice tagging) run entirely offline on-device.
5. **Commands**:
   - Build: `.\gradlew.bat assembleDebug`
   - Lint: `.\gradlew.bat lintDebug`
   - Unit tests: `.\gradlew.bat testDebugUnitTest`
   - Run single test: `.\gradlew.bat testDebugUnitTest --tests "<ClassName>"`
