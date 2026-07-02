# NATIVE-P5-005 Device Evidence

Task: `NATIVE-P5-005` Source library and simple local parsers

Device:

- Model: HUAWEI HMA-AL00
- Android: 10 / API 29
- Package under test: `com.reversetutor.preview`
- Build type: internal debug preview

Validated:

- Sources route is reachable from the native app shell.
- Source library displays parser status legend.
- Seeded Markdown source is visible with `supported_local` status and parsed snippets.
- Reprocess entry works for the Markdown source and reports `Last import: supported_local`.
- Unsupported seeded source remains visible with `unsupported` status.

Evidence files:

- `TEST-HMA-AL00-10-p5-005.xml`: instrumentation result for `Phase5SourcesDeviceTest`.
- `test-result.textproto`: Android test platform result, status `PASSED`.
- `test-results.log`: instrumentation runner output, `OK (1 test)`.
- `logcat-Phase5SourcesDeviceTest.txt`: device logcat for the test run.

Not covered:

- Manual Android file picker interaction with the system document UI was not captured as a screenshot because the native preview package was not left installed after the Gradle instrumentation run, and this task did not perform a separate manual `installDebug`.
- Full binary parser support for PDF, DOCX, PPTX, EPUB, or images.
- URI permission persistence and re-reading original files during Reprocess.
- Source-to-chat attachment, anchors, graph links, and full context integration.
- Signed or release APK build.
