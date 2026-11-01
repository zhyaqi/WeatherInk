# 汉王 N10 Touch 2024 ADB 调试指南

本文记录本项目在汉王 N10 Touch 2024 上的连接、安装、截图和排错流程。

## 设备信息

| 项目 | 值 |
| --- | --- |
| 设备 | 汉王 N10 Touch 2024 |
| 系统 | Android 11 |
| 型号 | `N10Touch` |
| Product / Device | `rk3566_eink` |
| 屏幕 | 1404 × 1872 |
| 屏幕密度 | 330 |
| 局域网地址 | `192.168.1.78` |
| 应用包名 | `com.zyq.weatherink` |

局域网地址由路由器分配，可能在设备重启后变化。应以设备当前的 Wi-Fi
设置或路由列表为准。

## 找到 ADB

优先使用 `PATH` 中的 `adb`：

```powershell
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
```

当前电脑上的便携版 ADB：

```text
D:\zyq\.tools\android-platform-tools\platform-tools\adb.exe
```

如果没有加入 `PATH`：

```powershell
$adb = 'D:\zyq\.tools\android-platform-tools\platform-tools\adb.exe'
```

Android SDK 位于：

```text
D:\zyq\.tools\android-sdk
```

## 无线 ADB 连接

确保电脑与电纸书连接同一个局域网，然后执行：

```powershell
& $adb start-server
& $adb connect 192.168.1.78:5555
& $adb devices -l
```

成功时会出现：

```text
192.168.1.78:5555  device  product:rk3566_eink model:N10Touch device:rk3566_eink
```

如果状态为 `unauthorized`，解锁设备并在弹窗中允许 USB 调试。

## 首次或重启后开启网络 ADB

如果出现以下任一情况，说明设备的网络 ADB 尚未监听：

- `cannot connect ... 10061`
- TCP `5555` 被拒绝
- `adb devices` 中没有任何设备

此时需要连接一次支持数据传输的 USB-C 线：

1. 在电纸书上启用“开发者选项 → USB 调试”。
2. 使用数据线连接电脑。
3. 在电纸书弹窗中允许该电脑进行 USB 调试。
4. 检查设备：

```powershell
& $adb devices -l
```

得到 USB 序列号后，将其切换到 TCP 模式：

```powershell
& $adb -s <USB序列号> tcpip 5555
Start-Sleep -Seconds 2
& $adb connect 192.168.1.78:5555
& $adb devices -l
```

网络连接成功后可以拔掉 USB。确认无线链路：

```powershell
& $adb -s 192.168.1.78:5555 shell echo NETWORK_ADB_OK
```

设备重启后，`5555` 通常需要重新通过 USB 开启。

## 基础设备检查

```powershell
$serial = '192.168.1.78:5555'

& $adb -s $serial shell getprop ro.product.model
& $adb -s $serial shell getprop ro.build.version.release
& $adb -s $serial shell wm size
& $adb -s $serial shell wm density
& $adb -s $serial shell ip route get 192.168.1.55
```

预期型号为 `N10Touch`，系统版本为 `11`。

## 构建和安装

```powershell
$env:ANDROID_HOME = 'D:\zyq\.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat assembleDebug lintDebug --no-daemon

& $adb -s 192.168.1.78:5555 install -r `
    app\build\outputs\apk\debug\app-debug.apk
```

覆盖安装会保留应用数据。除非明确需要清空 API 配置、城市和天气缓存，
不要执行 `adb uninstall` 或 `adb shell pm clear`。

启动应用：

```powershell
& $adb -s 192.168.1.78:5555 shell am force-stop com.zyq.weatherink
& $adb -s 192.168.1.78:5555 shell am start -W `
    -n com.zyq.weatherink/.MainActivity
```

## 自动化界面检查

获取当前 Activity：

```powershell
& $adb -s 192.168.1.78:5555 shell dumpsys activity activities |
    Select-String 'mResumedActivity'
```

抓取截图：

```powershell
& $adb -s 192.168.1.78:5555 shell screencap -p /sdcard/weatherink.png
& $adb -s 192.168.1.78:5555 pull `
    /sdcard/weatherink.png `
    D:\zyq\WeatherInk\screenshots\weatherink.png
```

获取控件树：

```powershell
& $adb -s 192.168.1.78:5555 shell uiautomator dump /sdcard/window.xml
& $adb -s 192.168.1.78:5555 shell cat /sdcard/window.xml
```

截图和控件树可用于确认自动刷新后的温度、天气、观测时间以及按钮位置。

## 自动刷新验证

```powershell
& $adb -s 192.168.1.78:5555 shell am force-stop com.zyq.weatherink
& $adb -s 192.168.1.78:5555 shell am start -W `
    -n com.zyq.weatherink/.MainActivity
Start-Sleep -Seconds 5
& $adb -s 192.168.1.78:5555 shell uiautomator dump `
    /sdcard/weatherink-auto.xml
```

重点检查 `currentTemperature`、`currentCondition`、`updatedTime` 和
`statusText`，确认打开应用后数据已更新且没有崩溃。

## 日志

```powershell
& $adb -s 192.168.1.78:5555 logcat -c
& $adb -s 192.168.1.78:5555 logcat
```

只看崩溃：

```powershell
& $adb -s 192.168.1.78:5555 logcat -d -t 300 `
    AndroidRuntime:E '*:S'
```

## 常见问题

### `cannot connect ... 10061`

设备在线但没有监听 `5555`。先检查 IP，再使用 USB 执行：

```powershell
& $adb -s <USB序列号> tcpip 5555
```

### `adb devices` 为空

检查以下项目：

- USB 线是否支持数据传输
- 设备是否已解锁
- 是否启用 USB 调试
- Windows 是否出现未识别的 Android 设备
- `adb kill-server` 后是否能够重新发现设备

### Windows 识别了 USB，但 ADB 没有设备

打开设备上的 USB 用途选择，必要时选择“传输文件/MTP”，并重新开关
USB 调试。如果连接时没有“允许 USB 调试”弹窗，可在开发者选项中先
撤销 USB 调试授权后重新插入。

### `unauthorized`

解锁设备，点击“允许 USB 调试”。需要长期连接时可勾选“始终允许”。

### 中文输入法导致 `adb shell input text` 内容错误

汉王默认中文输入法会联想和转换拉丁字符，尤其会改变点号。不要依赖
`input text` 批量填写 API Host 或 API Key；这些内容应在设备上使用
真实键盘或粘贴操作完成。

## 安全约定

- 不提交 QWeather API Key 或 API Host
- 不提交注册页、账户页或包含手机号/邮箱的截图
- 不提交设备序列号和 `shared_prefs` 内容
- 不把 `WeatherInk-v*.apk`、`app/build` 或 `local.properties` 提交到 Git
- 当前仓库是公开仓库，提交和发布前检查敏感信息
