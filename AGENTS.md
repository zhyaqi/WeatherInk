# WeatherInk Agent Notes

## Target Device

- Device: Hanwang N10 Touch 2024
- Android: 11
- Model: `N10Touch`
- Product/device: `rk3566_eink`
- Display: 1404 x 1872, density 330
- Current LAN address: `192.168.1.78`
- The LAN address is DHCP-assigned and may change.

## ADB Setup on This Windows Host

Prefer `adb` from `PATH` when available. On the current host, the known
standalone path is:

```text
D:\zyq\.tools\android-platform-tools\platform-tools\adb.exe
```

The Android SDK is:

```text
D:\zyq\.tools\android-sdk
```

Connect over Wi-Fi:

```powershell
& $adb connect 192.168.1.78:5555
& $adb devices -l
```

If `5555` is refused or no device appears:

1. Connect a data-capable USB cable.
2. Confirm USB debugging is enabled and authorize the computer on the device.
3. Run `adb devices -l` and use the USB serial.
4. Run `adb -s <usb-serial> tcpip 5555`.
5. Reconnect with `adb connect 192.168.1.78:5555`.
6. After the network connection works, USB can be disconnected.

TCP mode normally resets after a device reboot.

## Build and Install

Use Java 17 and Android SDK Platform/Build Tools 34:

```powershell
$env:ANDROID_HOME = 'D:\zyq\.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat assembleDebug lintDebug --no-daemon
& $adb -s 192.168.1.78:5555 install -r app\build\outputs\apk\debug\app-debug.apk
```

Launch the app:

```powershell
& $adb -s 192.168.1.78:5555 shell am force-stop com.zyq.weatherink
& $adb -s 192.168.1.78:5555 shell am start -W -n com.zyq.weatherink/.MainActivity
```

## Validation

- Check `adb devices -l` before every device operation.
- Use `uiautomator dump` and `screencap` for real-device UI verification.
- Confirm automatic refresh by force-stopping and restarting the app, then
  checking that the observation time and weather data update.
- Run `lintDebug` after source or resource changes.
- The Hanvon Chinese IME can alter Latin/dotted strings entered through
  `adb shell input text`; avoid assuming that text injection is reliable for
  the API Host and API Key fields.

## Security

- Never commit the QWeather API Host, API Key, account screenshots, device
  serial number, or app-private SharedPreferences.
- The repository is public. Scan history before publishing anything derived
  from the device.
- Keep the APK, build output, local screenshot captures, and
  `local.properties` ignored.

See `docs/HANWANG_N10_TOUCH_ADB.md` for the complete operational guide.
