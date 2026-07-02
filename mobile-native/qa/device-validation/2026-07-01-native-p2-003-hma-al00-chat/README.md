# NATIVE-P2-003 HMA-AL00 Chat Smoke

Device: HMA-AL00, Android 10 / API 29.
Package: `com.reversetutor.preview`.
APK: `mobile-native/app/build/outputs/apk/debug/app-debug.apk`.

Verified flow:

- Installed debug APK with `adb install -r`.
- Opened Sessions and selected `Reverse Tutor preview`.
- Confirmed Chat title, empty timeline, composer, image action, and disabled blank Send.
- Typed `P2_003 device smoke`; confirmed IME-safe composer area and enabled Send.
- Sent the message and confirmed timeline renders `You`, the message text, and Quote/Note/Regenerate/Delete actions.
- Selected Quote, sent `quoted reply`, and confirmed timeline renders `Replying to: P2_003 device smoke`.
- Opened Note action and confirmed explicit deferred dialog.
- Opened local image draft and confirmed `Local image draft` plus `Cancel image`.
- Restarted the app, reopened the session, and confirmed both messages and quote context reloaded from Room.
- Captured app-pid scoped logcat; no crash/error keywords were found.

Evidence files are numbered in execution order in this directory.
