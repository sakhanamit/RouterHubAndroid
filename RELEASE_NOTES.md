# 🚀 RouterHub Release Notes & Changelog

## 🆕 What's New in v1.2.0 (Build 3)

### ⚡ 1. Accurate Multi-Stream Internet Speedometer Test
* **Full-Duration TCP Saturation:** Solved the issue where speed tests terminated prematurely in ~5 seconds with inaccurate readings. Implemented true 10.0s multi-stream download, 8.0s upload, and 1.5–2.0s multi-host latency/jitter measurement (total ~20s test, conforming to FCC/Ookla broadband measurement standards).
* **Cloudflare Anycast CDN Integration:** Powered by Cloudflare's global Anycast edge network (`https://speed.cloudflare.com/__down` & `__up`), routing automatically to the closest geographic point of presence.
* **Continuous Multi-Stream Workers:** Parallel coroutine download workers continuously stream chunks. If a chunk finishes, the next chunk is immediately requested without stalling or hitting premature EOF.
* **FCC/Ookla Warm-Up Filtering:** Discards the initial 1.5s TCP slow-start ramp-up from sustained average calculation, eliminating skewed low throughput readings.
* **Uncapped High-Speed Support:** Full support for gigabit broadband and 5G connections up to 1,000+ Mbps without arbitrary clamping.

### 🎨 2. Redesigned Professional Speedometer & Real-Time Analytics
* **Adaptive Dynamic Speedometer Gauge:** Gauge scale dynamically scales (0–100, 250, 500, or 1000 Mbps) to match your connection speed.
* **Radial Graduation Ticks & Needle Glow:** Custom-drawn radial graduation marks with multi-stop sweep gradient arc, outer glow, and animated needle.
* **Live Throughput Wave (Sparkline):** Real-time stream chart showing live speed fluctuations with gradient fill under the curve.
* **Network Performance Capabilities Card:** Automatically evaluates network readiness for:
  * 🎬 **4K / 8K Video Streaming** (Netflix, YouTube, Prime Video UHD)
  * 🎮 **Online Gaming & Low Latency** (Multiplayer games, fast reaction time)
  * 📹 **HD Video Conferencing** (Zoom, Teams, Google Meet HD calls)
  * ☁️ **Fast Cloud Backups & Large Uploads** (Drive, Photos, large media syncing)
* **Comprehensive Metrics:** Displays Peak Download/Upload, Sustained Average, Total Transferred MB, Ping Latency rating (Ultra Low, Good, Fair), and Jitter stability.

### 📱 3. Fluid App Navigation & UI Polish
* **Streamlined Top Bar (Fixed Squished Header):** Collapsed secondary actions (Privacy masking toggle, Check Updates, Welcome Guide) into a clean 3-dot overflow dropdown menu, ensuring the "Router Hub" title and gateway subtitle never get truncated or squished.
* **Gateway Status Card Alignment (Fixed "ONLI / NE" Line-Break):** Enforced single-line rendering and flexible text allocation on the Connected Gateway card, eliminating badge line-breaks and IP address clipping.
* **Accurate Gateway Identification (Fixed "Smartphone / PC" Mislabel):** Prioritized gateway classification so primary routers with locally administered/randomized MAC addresses are accurately labelled as "Primary Gateway Router" instead of "Smartphone / PC (Private MAC)".
* **Eliminated FAB Layout Overlap:** Removed redundant Floating Action Buttons from the Speed Test and Security tabs (where full-width on-screen action buttons exist), and added 120dp bottom clearance on scrollable lists so the "Web Admin" button on device cards is never obscured.
* **Subnet Card & Button Scaling:** Compacted subnet header metadata (`Subnet: .../24 • GW • IP`) and action chip to prevent text clipping on smaller displays.
* **Horizontal Scroll for WebView Credential Bar:** Added smooth horizontal scrolling to the router login helper bar so "Copy User", "Copy Pass", and "Fill Credentials" buttons never clip against the screen edge.
* **Smooth Screen Transitions:** Integrated Jetpack Compose `Crossfade` across all tabs (Routers, Devices, Speed Test, Security) for silky-smooth switching.

### 🛡️ 4. Security Audit DNS Resolver Accuracy
* **Fixed False-Positive "Unrecognized External DNS Resolver":** Integrated Android Private DNS (DNS-over-TLS) detection via `LinkProperties.privateDnsServerName`.
* **Standard ISP & Gateway DNS Handling:** Standard ISP recursive resolvers and local gateway forwarders are now properly recognized as passed (`isPassed = true`) with helpful security guidance instead of triggering false high-severity red alarms.
* **Expanded Verified Secure Resolvers:** Added full IPv4/IPv6 support for Cloudflare (1.1.1.1, 1.0.0.1, 2606:4700::), Google (8.8.8.8, 8.8.4.4, 2001:4860::), Quad9 (9.9.9.9, 149.112.112.112), Cisco OpenDNS, AdGuard, CleanBrowsing, and ControlD.

---

## 📦 Download v1.2.0

* **Release APK:** [`RouterHub-v1.2-release.apk`](https://github.com/sakhanamit/RouterHubAndroid/raw/master/RouterHub-v1.2-release.apk)
* **Debug APK:** [`RouterHub-v1.2-debug.apk`](https://github.com/sakhanamit/RouterHubAndroid/raw/master/RouterHub-v1.2-debug.apk)
* **Live Webpage:** [sakhanamit.github.io/RouterHubAndroid](https://sakhanamit.github.io/RouterHubAndroid/)

---

## 🚀 RouterHub v1.1.0 Release Notes

**RouterHub v1.1.0** introduced categorized device scanning, protocol badges, color-coded latency, an interactive speed test gauge, dynamic network resolution fixes, in-app update checks, and data masking privacy controls.

### 📱 Features in v1.1.0
* **Device Categorization:** Automatically groups discovered devices into structured categories (Gateways & Routers, Smartphones, Computers, Smart TVs, IoT, Printers/Cameras, NAS).
* **Protocol Icons & Badges:** Displays badges with protocol icons for open ports (`HTTP:80`, `HTTPS:443`, `SSH:22`, `TELNET:23`, `SMB:445`, `RTSP:554`, `WEB:8080`).
* **Color-Coded Response Times:** Fast (< 25 ms), Normal (25–100 ms), Slow (> 100 ms).
* **Dedicated Security Rescan:** Prominent re-run button in the Security tab hero card.
* **Dynamic Gateway Auto-Detection Fix:** Added reactive polling using `WifiManager.dhcpInfo` to instantly re-detect router IP and subnet range.
* **Privacy Data Masking Toggle:** One-tap toggle in top app bar to mask IP addresses and MAC addresses.
