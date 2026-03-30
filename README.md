# 📱 Ethernet Config

Android APP for configuring static IP on ethernet interface via USB ethernet adapter.

**No root required** — uses [Shizuku](https://github.com/rikka-apps/Shizuku) for ADB-level network configuration.

## Use Case

- Network engineers connecting phones to switches/routers via ethernet cable
- Configure static IP on phone → ping gateway → open switch management web UI
- Perfect for environments without DHCP

## Features

- 🔌 Auto-detect ethernet connection status
- 🌐 Configure static IP / subnet mask / gateway / DNS via Shizuku
- 📡 Ping gateway connectivity test
- 🔍 Auto-scan common switch management IPs (192.168.1.1, 192.168.0.1, etc.)
- 🌍 Built-in WebView for switch management pages
- 📋 IP configuration profiles (save/load common configs)

## Requirements

- Android 12+ (API 31+)
- USB OTG ethernet adapter
- [Shizuku](https://shizuku.rikka.app/) installed & running (one-time ADB setup)

## Quick Start

1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) on your phone
2. Start Shizuku via ADB (one-time):
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. Install Ethernet Config APK
4. Connect ethernet cable to phone via USB adapter
5. Open app → configure IP → connect to switch

## Tech Stack

- Kotlin + Jetpack Compose
- Shizuku API for network configuration
- EthernetManager for connection monitoring
- WebView for management UI

## License

MIT
