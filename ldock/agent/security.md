# Security rules: BookMT

BookMT is an offline Android e-reader. Security focuses on safe file parsing, Storage Access Framework (SAF), data integrity, and strict isolation from the network. These rules apply to all tasks.

---

## 1. Offline boundary and permissions

| ID | Rule |
|---|---|
| SEC-01 | **MUST NOT** add `android.permission.INTERNET` or network capabilities. BookMT is an offline application; all processing, parsing, and TTS must run locally. |
| SEC-02 | **MUST NOT** add third-party analytics, tracking SDKs, ad networks, or crash reporters that transmit data outside the device. |
| SEC-03 | Request only minimal Android permissions (`POST_NOTIFICATIONS` for TTS media service, `WAKE_LOCK`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`). Use SAF for file imports rather than broad storage access. |

## 2. File parsing and input validation

| ID | Rule |
|---|---|
| SEC-10 | **Zip Slip Prevention**: When unzipping EPUB or backup archives, validate every `ZipEntry` canonical path against the destination directory before writing: `entryFile.canonicalPath.startsWith(destDir.canonicalPath)`. Reject entries containing `..` or leading slashes. |
| SEC-11 | **XXE Prevention**: When parsing XML (FB2, EPUB container.xml, content.opf, toc.ncx), configure XML parsers (`XmlPullParser`, `DocumentBuilderFactory`, SAX) to disable external entities, external DTDs, and entity expansion: prevent XXE and Billion Laughs DoS attacks. |
| SEC-12 | **Path Traversal**: Validate and sanitize file names, book titles, and directory paths when saving extracted book covers or exported backups. Never allow path traversal via unsanitized book metadata. |
| SEC-13 | **Resource Exhaustion**: Cap book parsing buffer sizes and recursion depth. Guard against decompression bombs (huge uncompressed streams in zip files). |
| SEC-14 | **Graceful Degradation**: Corrupted or malformed book files must produce clean, user-friendly errors; parsers must never crash the app or throw unhandled exceptions. |

## 3. Storage and data integrity

| ID | Rule |
|---|---|
| SEC-20 | **Room SQL Injection**: Always use Room compile-time validated queries (`@Query`) with bound parameters. Never construct raw SQL strings with concatenated user search terms or filter strings. |
| SEC-21 | **Backup Validation**: When restoring Room DB or settings from a backup file, validate file format, version headers, and SQLite schema integrity before replacing active databases. |
| SEC-22 | **Content Streams**: Always use `.use { ... }` blocks when opening `ContentResolver` input/output streams to prevent file descriptor leaks and lockups. |

## 4. Privacy and logging

| ID | Rule |
|---|---|
| SEC-31 | **No Book Content in Logs**: Never log full text paragraphs, book contents, or personal user notes to Logcat, even in debug builds. |
| SEC-32 | **No Hardcoded Keys**: No hardcoded secrets, signing keys, or tokens in source code or `BuildConfig`. |
| SEC-33 | **Never commit sensitive files**: Never commit `local.properties`, release keystores (`*.jks`, `*.keystore`), or credentials. |

## 5. Dependencies and supply chain

| ID | Rule |
|---|---|
| SEC-40 | Pin all dependency versions in `gradle/libs.versions.toml`. Check official releases and Maven Central coordinates. |
| SEC-41 | Verify that any new library or ONNX model introduced is legitimate, open-source, and does not bundle native binaries with unauthorized network calls. |
