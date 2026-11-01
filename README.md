# RouterHub 🌐

> **A modern, privacy-focused Android network companion for router administration, subnet device discovery, and Wi-Fi security auditing.**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue)](https://developer.android.com/about/versions/14)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Toolkit](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material Design](https://img.shields.io/badge/Design-Material%203-795548)](https://m3.material.io/)
[![Security](https://img.shields.io/badge/Storage-EncryptedSharedPreferences-red)](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)

---

## 📖 Overview

**RouterHub** is an all-in-one network management and security auditing tool designed for Android. It eliminates the hassle of remembering local IP addresses, navigating finicky mobile browser interfaces for router setups, and wondering what devices are connected to your Wi-Fi network.

Whether you are managing multiple routers, tracking smart home (IoT) devices, or verifying whether your home network is exposed to common security vulnerabilities, RouterHub provides a clean, responsive, and secure experience.

---

## ✨ Features

### 🎛️ 1. Multi-Router Management
- **Auto Gateway Detection**: Detects your active Wi-Fi default gateway IP instantly on connection.
- **Multi-Router Profiles**: Save and switch between multiple router configurations (home, office, lab, secondary access points).
- **Embedded Web Console**: Custom-tuned in-app WebView optimized for router administration portals with desktop user-agent switching, HTTP basic authentication handling, and SSL exception bypass options.
- **Hardware-Backed Credential Security**: Router credentials and passwords are encrypted using Android Keystore and `EncryptedSharedPreferences` (AES-256 GCM).

### 🔍 2. Network Discovery & Subnet Scanner
- **Fast Subnet Sweep**: Concurrently scans the local `/24` subnet using asynchronous Kotlin Coroutines.
- **Hardware Identification**: Extracts MAC addresses via the kernel ARP table (`/proc/net/arp`) with vendor OUI matching.
- **Device Classification**: Automatically classifies detected devices (Routers, Computers, Smartphones, Smart TVs, IoT gadgets, Printers).
- **Port Scanner & Banner Grabbing**: Identifies common open ports (HTTP, HTTPS, SSH, SMB, alternate web ports) and retrieves HTTP page titles and ping latencies.

### 🛡️ 3. Automated Wi-Fi Security Audit
- **Security Scorecard**: Computes an aggregate network security score (0–100) based on vulnerability severity.
- **Cleartext Traffic Detection**: Flags unencrypted HTTP router administration portals exposing session cookies and passwords.
- **Insecure Protocol Checks**: Scans for vulnerable legacy services such as Telnet (Port 23) and SMB file sharing (Port 445).
- **Default Credential Risk Analysis**: Identifies devices still relying on default factory administrative credentials.
- **Actionable Remediation**: Offers step-by-step guidance to patch and harden router configurations.

### 🎨 4. Modern, Responsive Experience
- Built 100% with **Jetpack Compose** and **Material 3**.
- Fluid dark and light mode adaptation with an iOS-inspired clean surface hierarchy.
- Badge-driven navigation indicating live router counts, online devices, and active security vulnerabilities.

---

## 🛠️ Tech Stack & Architecture

- **Language**: [Kotlin](https://kotlinlang.org/) (Coroutines + Flow)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/)
- **Architecture**: Modern Android Component Architecture
- **Security**: [AndroidX Security Crypto](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) (AES-256 GCM + Android Keystore)
- **Networking**: Java NIO Sockets, Android `ConnectivityManager`, ARP inspection (`/proc/net/arp`)
- **Compatibility**: Minimum SDK 26 (Android 8.0) | Target SDK 34 (Android 14)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/RouterHubAndroid.git
   cd RouterHubAndroid
   ```

2. Open the project in **Android Studio**.

3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleDebug
   ```

4. Run on a connected device or emulator with Wi-Fi network access.

---

## 🔒 Permissions & Privacy

RouterHub is built with privacy in mind. All scans, data collection, and credential storage remain strictly **on-device**—no analytics, tracking, or cloud uploads:
- `android.permission.INTERNET`: Required to communicate with your router and perform local subnet discovery.
- `android.permission.ACCESS_NETWORK_STATE`: Required to read active network properties, subnet masks, and default gateway routes.
- `android.permission.ACCESS_WIFI_STATE`: Required to detect Wi-Fi connection states.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) (or your preferred open source license).
