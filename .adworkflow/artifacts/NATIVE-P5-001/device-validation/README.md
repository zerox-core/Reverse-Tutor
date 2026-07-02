# NATIVE-P5-001 Device Evidence

Task: `NATIVE-P5-001` Context hub shell

Device:

- Model: HUAWEI HMA-AL00
- Android: 10 / API 29
- Package: `com.reversetutor.preview`
- Build type: internal debug preview

Validated:

- Context Hub is reachable from an active Chat session through the Chat header.
- Context Hub shows the active session title.
- Required surfaces are visible: Overview, Graph, Anchors, Notes, Errors, Session settings.
- Empty/deferred copy explicitly states graph/source parity is not complete.
- Graph section exposes a deferred state instead of pretending native graph rendering is complete.
- System back behavior was covered by `Phase5ContextHubDeviceTest`: Context Hub returns to Chat.

Evidence files:

- `TEST-HMA-AL00-10-p5-001.xml`: instrumentation result for `Phase5ContextHubDeviceTest`.
- `sessions.xml`: app launched to Sessions before manual evidence capture.
- `chat.xml`: active Chat page with `Open context hub` entry.
- `context-hub.png` / `context-hub.xml`: Context Hub overview and required surface chips.
- `context-hub-graph.png` / `context-hub-graph.xml`: Graph deferred state.

Not covered:

- Real graph renderer, pan/zoom/select, and graph detail sheets.
- Anchors, notes, errors, or session settings persistence/editing.
- Full dark mode, dynamic type, TalkBack, IME, and small-screen matrix.
- Signed or release APK build.
