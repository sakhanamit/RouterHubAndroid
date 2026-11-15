# 🤖 RouterHub Android — Agent Guidelines & Architecture Playbook

## 📌 Project Overview
**RouterHub Android** is a native Android application built with Kotlin and Jetpack Compose (Material 3) for network administration, router management, local device discovery, high-speed multi-stream bandwidth testing, and Wi-Fi security auditing.

- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target / Compile SDK:** 34 (Android 14)
- **Primary Language:** Kotlin (with Kotlin Coroutines & Jetpack Compose)
- **Build System:** Gradle (Kotlin DSL `build.gradle.kts`) with Android Gradle Plugin (AGP)

---

## 🛠️ Build & Development Environment
When executing Gradle tasks, tests, or builds on this machine, ensure the environment variables point to the installed Android Studio JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test
.\gradlew.bat assembleRelease
```

- **Local SDK Path:** Configured in `local.properties` (`C:\Users\Amit Chaudhary\AppData\Local\Android\Sdk`).
- **Release Signing:** Keystore at `app/routerhub-release.jks` with alias `routerhub`.

---

## 🧱 Architectural Principles & Coding Standards

### 1. Jetpack Compose UI
- Use **Material 3** components and token roles (`MaterialTheme.colorScheme.*`).
- Hoist state to ViewModels or caller composables; keep UI nodes stateless.
- Use `derivedStateOf` or stable data models to avoid unnecessary recompositions during high-frequency emissions (e.g. speed tests, scan progress).
- Use `Crossfade` or `AnimatedContent` for clean screen and tab transitions.

### 2. Networking & Subnet Discovery
- Never perform blocking network calls on the main thread; always route through `Dispatchers.IO`.
- Use **fail-fast reachability** (short timeouts <250ms) before scanning ports to avoid multi-second freezes on offline IP addresses.
- Always close sockets and HTTP connections inside a `finally` block or Kotlin `use { ... }` block.
- Correctly differentiate between randomized client MACs and primary gateway routers using gateway IP matching.

### 3. WebView & Security
- Keep WebView file access disabled (`allowFileAccess = false`, `allowContentAccess = false`).
- Always sanitize credentials using `escapeJsString` before passing to `evaluateJavascript` to prevent script breakout and syntax errors.
- Store sensitive router credentials using `EncryptedSharedPreferences` backed by Android KeyStore.

### 4. Unit Testing
- Unit tests run under JVM using Gradle `test`.
- Maintain `unitTests.isReturnDefaultValues = true` in `app/build.gradle.kts`.
- Prefer pure Kotlin utility functions over mockable Android SDK stubs in business logic to ensure 100% deterministic test execution.

---

## 🧠 Workspace Skill Agents

The following specialized skills are installed in `.agent/skills/` to assist with development workflows:

| Skill Agent | Location | Specialty |
| :--- | :--- | :--- |
| **`android-kotlin-compose-expert`** | `.agent/skills/android-kotlin-compose-expert` | Jetpack Compose, Material 3, StateFlow, Coroutines, Gradle Kotlin DSL, R8/ProGuard. |
| **`android-network-wifi-specialist`** | `.agent/skills/android-network-wifi-specialist` | `WifiManager`, subnet scanning, socket timeouts, Anycast speed test engine, DoT DNS audit. |
| **`android-webview-security-expert`** | `.agent/skills/android-webview-security-expert` | WebView hardening, safe JS credential injection, EncryptedSharedPreferences, SSL handling. |
| **`anti-reversing-techniques`** | `.agent/skills/anti-reversing-techniques` | Reverse engineering protection, code obfuscation, tampering detection, and R8 protection. |
| **`security-scanning-security-hardening`**| `.agent/skills/security-scanning-security-hardening` | Android manifest hardening, component export checks, network security config. |
| **`security-scanning-security-sast`** | `.agent/skills/security-scanning-security-sast` | Static application security testing (SAST) for Android Kotlin & Java source code. |
| **`secrets-management`** | `.agent/skills/secrets-management` | Android Keystore, MasterKeys, AES-256 GCM credential storage, key lifecycle. |
| **`frontend-mobile-security-xss-scan`** | `.agent/skills/frontend-mobile-security-xss-scan` | Detection and mitigation of XSS and script injection vulnerabilities in mobile WebViews. |
| **`accessibility-compliance-accessibility-audit`** | `.agent/skills/accessibility-compliance-accessibility-audit` | WCAG & Android accessibility standards, touch target sizes, TalkBack support. |
| **`debugging-strategies`** | `.agent/skills/debugging-strategies` | Hypothesis-driven debugging, coroutine tracing, and network socket fault isolation. |
| **`performance-engineer`** | `.agent/skills/performance-engineer` | Mobile throughput tuning, GC allocation reduction, and Compose render performance. |
| **`mobile-security-coder`** | `.agent/skills/mobile-security-coder` | OWASP Mobile Top 10, cryptographic standards, input validation, permission controls. |
| **`network-engineer`** | `.agent/skills/network-engineer` | Network protocols, socket architectures, SSL/TLS, DNS routing, and edge connectivity. |
| **`protocol-reverse-engineering`** | `.agent/skills/protocol-reverse-engineering` | Network protocol analysis, packet dissection, and custom router protocol handling. |
| **`unit-testing-test-generate`** | `.agent/skills/unit-testing-test-generate` | JUnit test generation, edge case validation, test coverage improvement. |
| **`ui-ux-designer`** | `.agent/skills/ui-ux-designer` | Material Design tokens, visual hierarchy, mobile accessibility, responsive layout polish. |
| **`ui-visual-validator`** | `.agent/skills/ui-visual-validator` | Visual regression testing, design system fidelity, and layout integrity checks. |
| **`cicd-automation-workflow-automate`** | `.agent/skills/cicd-automation-workflow-automate` | GitHub Actions CI/CD, automated build pipelines, release automation. |
