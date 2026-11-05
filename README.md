# BookMT

Офлайн-читалка электронных книг для Android с локальным синтезом речи.

## Возможности

- EPUB, FB2, TXT, Markdown и PDF;
- пагинация и непрерывная прокрутка;
- поиск по книге и закладки;
- темы Day, Sepia, Night и AMOLED;
- локальный TTS на базе Sherpa-ONNX;
- импорт книг и резервное копирование без обязательного сетевого доступа.

Приложение ориентировано на локальную обработку данных: библиотека, настройки и синтез речи работают на устройстве.

## Сборка

Требуются Android SDK и JDK, совместимые с Gradle-проектом.

```bash
git clone https://github.com/TopMob/BookMT.git
cd BookMT
python tools/prepare_tts.py
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Для Windows используйте `gradlew.bat`.

## Архитектура

Проект разделён на domain, data и presentation-слои. Подробности:

- [архитектура](ARCHITECTURE.md);
- [движок чтения](docs/specs/READER_ENGINE.md);
- [правила разработки](AGENTS.md).

## Стек

Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Kotlin Coroutines, Sherpa-ONNX.

## Статус

Проект находится в разработке. API и формат локального хранения могут изменяться.

## Лицензия

Apache License 2.0.
