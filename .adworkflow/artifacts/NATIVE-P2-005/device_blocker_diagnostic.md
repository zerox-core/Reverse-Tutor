# NATIVE-P2-005 Device Blocker Diagnostic

Date: 2026-07-01

## Result

The Windows host can see a Huawei USB device, but ADB cannot see an Android debug interface.

## Commands

```powershell
adb kill-server; adb start-server; adb devices -l
adb devices -l
Get-PnpDevice -PresentOnly | Where-Object { $_.FriendlyName -match 'Android|ADB|Huawei|HUAWEI|HMA|MTP|Phone|Composite|USB' -or $_.InstanceId -match 'VID_12D1|ANDROID|ADB' }
```

## Evidence

- `adb devices -l` returns only `List of devices attached` with no device rows.
- Windows PnP lists Huawei VID `12D1` / PID `107E` entries as WPD, USB Mass Storage, and USB Composite Device.
- No present device is listed as `Android`, `ADB`, or an ADB interface.

## Interpretation

The USB cable and Windows device enumeration are working at least partially. The phone is not exposing an ADB debugging interface to the host. This usually means USB debugging/HDB authorization is off, the RSA authorization prompt was not accepted, the USB mode is not suitable, or the Huawei/Android ADB driver interface is missing.

## User-Side Recovery Checklist

1. On the phone, enable Developer options and turn on USB debugging.
2. On Huawei/Honor devices, also enable HDB/HiSuite debugging if present.
3. Set USB mode to File transfer/MTP instead of charge-only.
4. Unplug and reconnect the cable, then accept the RSA debugging authorization prompt.
5. If no authorization prompt appears, revoke USB debugging authorizations, toggle USB debugging off/on, and reconnect.
6. If Windows still shows only WPD/Mass Storage, install or repair the Huawei/Android ADB driver.

## Next Command After Recovery

```powershell
adb devices -l
```

Expected result: one device row appears with state `device`. Then run the P2-005 install/smoke flow.
