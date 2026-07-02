# NATIVE-P5-006 Evidence

Task: Implement image source import and complex parser status.

Completed scope:
- Image sources remain visible in Sources as queued parser material.
- Chat image drafts are structured as URI/mime/name/sourceId attachments.
- Message attachments persist through `message_attachments` and are returned with message records.
- Message deletion removes quotes and attachments.
- LLM planning gates image attachments on vision capability.
- Default capability inference avoids a Room schema migration for this slice.
- PDF, DOCX, PPTX, EPUB, HTML, TXT, Markdown, and Image status coverage is tested.

Verification:
- Focused unit tests: `:core:data`, `:core:llm`, `:feature:chat`, `:feature:sources`, `:app`.
- Full verification: `.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace`.
- Device smoke: `Phase5SourcesDeviceTest` on HMA-AL00 / Android 10.
- APK badging: `com.reversetutor.preview`.

Residual risks:
- Real provider multimodal payload execution remains disabled.
- Anthropic image payload uses stored URI metadata as preview evidence; production provider execution still needs byte/base64 loading, size limits, and compression policy.
- Chat image picker device coverage is not yet a dedicated instrumentation test.
- PDF/DOCX/PPTX/EPUB local parser libraries remain a later parser-selection task.
