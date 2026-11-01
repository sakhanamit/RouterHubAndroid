# 🚀 RouterHub v1.1.0 Release Notes & Changelog

**RouterHub v1.1.0** introduces a major feature update including categorized device scanning, protocol badges, color-coded latency, an interactive speed test gauge, dynamic network resolution fixes, in-app update checks, and data masking privacy controls.

---

## 🆕 What's New in v1.1.0

### ⚡ 1. Interactive Internet Speedometer Test
* **Speed Gauge UI:** Semi-circular speedometer gauge with animated needle, real-time speed counter, download peak, and upload peak metrics.
* **Network Metrics:** Measures Ping Latency (ms) and Jitter Variance (ms).
* **Zero-Allocation Engine:** Optimized memory management using static reusable buffers, completely eliminating OutOfMemory and Garbage Collection pressure.

---

### 📱 2. Categorized Subnet Device Scanner
* **Device Categorization:** Automatically groups discovered devices into structured categories:
  * 📡 *Gateways & Routers*
  * 📱 *Smartphones & Mobile Devices*
  * 💻 *Computers & Laptops*
  * 📺 *Smart TVs & Media Players*
  * 🔌 *Smart Home & IoT Devices*
  * 🖨️ *Printers & Cameras*
  * 💾 *Network Storage (NAS)*
* **Protocol Icons & Badges:** Displays badges with protocol icons for open ports (`HTTP:80`, `HTTPS:443`, `SSH:22`, `TELNET:23`, `SMB:445`, `RTSP:554`, `WEB:8080`).
* **Color-Coded Response Times:**
  * 🟢 **Fast (< 25 ms):** `⚡ 12ms` (Emerald Container)
  * 🟠 **Normal (25–100 ms):** `📶 48ms` (Amber Container)
  * 🔴 **Slow (> 100 ms):** `🐢 165ms` (Rose Container)

---

### 🛡️ 3. Security Tab Rescan & Dynamic Gateway Fix
* **Dedicated Rescan Button:** Added a prominent `RE-RUN SECURITY AUDIT` button in the Security tab hero card.
* **Dynamic Gateway Auto-Detection Fix:** Added reactive polling using `WifiManager.dhcpInfo` to instantly re-detect router IP and subnet range (`192.168.1.0/24`) whenever Wi-Fi or router IP changes.
* **Router Online Fix:** Connected gateway shows `ONLINE` even when WAN internet access is offline.

---

### 🔒 4. Privacy Data Masking & In-App Updates
* **Privacy Masking Toggle:** One-tap toggle in top app bar to mask IP addresses (`192.168.1.***`) and MAC addresses (`50:C7:BF:**:**:**`) for privacy and screenshots.
* **In-App Update Check:** Direct link to [`sakhanamit.github.io/RouterHubAndroid/`](https://sakhanamit.github.io/RouterHubAndroid/) for checking latest releases and changelogs.

---

## 📦 Download & Installation

* **Release APK:** [`RouterHub-v1.1-release.apk`](https://github.com/sakhanamit/RouterHubAndroid/raw/master/RouterHub-v1.1-release.apk)
* **Debug APK:** [`RouterHub-v1.1-debug.apk`](https://github.com/sakhanamit/RouterHubAndroid/raw/master/RouterHub-v1.1-debug.apk)
* **Webpage:** [sakhanamit.github.io/RouterHubAndroid](https://sakhanamit.github.io/RouterHubAndroid/)
