# Device Validation: Native Preview Launch

Date: 2026-06-30

Task: NATIVE-P1-001 / Phase 1 native scaffold device smoke

## Device

- Manufacturer: HUAWEI
- Model: HMA-AL00
- Android version: 10
- SDK: 29
- ADB serial: HJS0218B27008886

## APK

- Path: `mobile-native/app/build/outputs/apk/debug/app-debug.apk`
- Package: `com.reversetutor.preview`
- Version code: 1
- Version name: `0.1.0-native-preview`
- Size: 8428591 bytes

## Steps

1. Confirmed device was visible through ADB.
2. Confirmed existing old package `com.reversetutor.app` was installed before preview install.
3. Installed native preview with:

```powershell
adb install -r mobile-native/app/build/outputs/apk/debug/app-debug.apk
```

4. Started native preview activity:

```powershell
adb shell am start -n com.reversetutor.preview/.MainActivity
```

5. Confirmed foreground resumed activity:

```text
com.reversetutor.preview/.MainActivity
```

6. Captured screenshot and logcat evidence.
7. Confirmed old and new packages coexist:

```text
package:com.reversetutor.app
package:com.reversetutor.preview
```

## Result

Passed.

The native preview installed beside the existing PWA/APK package and launched to the Compose preview screen.

## Evidence Files

- Screenshot: `mobile-native/qa/device-validation/2026-06-30-native-preview-hma-al00.png`
- Logcat: `mobile-native/qa/device-validation/2026-06-30-native-preview-hma-al00-logcat.txt`

## Notes

- No `FATAL EXCEPTION`, `AndroidRuntime`, `Crash`, or `ANR` match was found in the captured logcat sample.
- This validates scaffold launch only. Navigation, data, import/export, graph, and chat flows are later phase work.
