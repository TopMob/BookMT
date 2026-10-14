# BookMT development constraints

- BookMT is a fully offline Android reader. All reading, search, annotations, analysis and future TTS must work locally.
- Do not add runtime Internet permissions, HTTP clients, telemetry, ads, accounts, cloud backup/synchronization, remote catalogues, online AI calls, automatic model downloads or network-required voices.
- Future voice/model packages must support local file import and inference without a server. Android system TTS is not an unconditional offline guarantee: another process may choose a network voice.
- Prefer owned local copies of imported books, fonts and covers. Keep existing book IDs, bookmarks and reading positions when changing file locations.
- Manual local ZIP backup and Android file import/export are supported. Request local-only providers in document pickers; external provider implementations remain outside this app.
- Dependency resolution during development and Git operations are not runtime app functionality and may use the network.
- Preserve compatibility with existing Room databases and backups; do not use destructive migrations.
