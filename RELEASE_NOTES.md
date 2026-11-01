# 🚀 RouterHub v1.0.0 Release Notes

**RouterHub** is an all-in-one Android network companion app designed for discovering, auditing, and managing Wi-Fi routers and connected network devices.

---

## 🌟 Key Features

### 📡 Automatic Router Discovery & Management
* **Smart Gateway Detection:** Auto-detects default gateway router IP and local Wi-Fi subnet.
* **Network Router Search:** Scans subnets to identify routers via MAC vendor lookup, open ports (80, 443, 8080), and HTTP banner analysis.
* **Router Credentials Storage:** Securely save and manage credentials for quick router admin login using `EncryptedSharedPreferences`.
* **Quick Web Admin View:** Direct navigation to router setup & admin configuration pages.

---

### 🔍 Advanced Subnet Network Scanner
* **High-Speed Subnet Scan:** Multi-threaded coroutine scanning across full `/24` subnets (up to 254 IPs).
* **Device Classification:** Automatically categorizes devices as *Routers, Phones, Computers, Smart TVs, IoT, Printers, Cameras, and Network Storage*.
* **Comprehensive Device Details:**
  * IP & MAC Address resolution via ARP table inspection.
  * Vendor/Brand/Model identification.
  * Ping response latency (`pingMs`).
  * Open port identification (HTTP, HTTPS, SSH, FTP, Telnet, RTSP, SMB, etc.).
  * Web service title extraction.
* **Search & Filter:** Instant search by IP, hostname, vendor, or device type.
* **Clipboard Integration:** One-tap copying of IP and MAC addresses.

---

### 🛡️ Security Audit & Health Score
* **Automated Security Engine:** Scans router and local network for security weaknesses.
* **Security Checks Include:**
  * Default/weak router admin credentials check.
  * Unencrypted HTTP web interface detection.
  * Open sensitive/high-risk management ports (Telnet, FTP, SSH, SMB).
  * UPnP & WPS exposure risks.
* **Security Score (0–100):** Visual health score with color-coded severity levels (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`).
* **Actionable Advice:** Clear step-by-step recommendations for resolving each security warning.

---

### 🎨 Modern UI & Experience
* Built with **Jetpack Compose** & **Material 3**.
* iOS-inspired polished card interface with smooth animations and dark mode support.
* Interactive Onboarding / Welcome screen for initial app launch.

---

## 📦 Download & Installation

1. Download [`RouterHub.apk`](https://github.com/sakhanamit/RouterHubAndroid/raw/master/RouterHub.apk) or [`RouterHub-debug.apk`](https://github.com/sakhanamit/RouterHubAndroid/raw/master/RouterHub-debug.apk).
2. Enable **"Install from Unknown Sources"** in Android Settings if prompted.
3. Open and install the APK on your Android device (Android 8.0+ / API 26+).

---

## 🛠️ Technical Specifications

* **Min SDK:** API 26 (Android 8.0 Oreo)
* **Target SDK:** API 34+ / API 35
* **Language:** 100% Kotlin
* **UI Framework:** Jetpack Compose + Material 3
* **Concurrency:** Kotlin Coroutines & Flow
* **Security:** `androidx.security:security-crypto` (AES-256 GCM)
