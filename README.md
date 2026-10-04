# fastspeed

A small learning project for an Android WireGuard client, written in Kotlin with Jetpack Compose. The app can import a WireGuard `.conf` file or request an experimental Cloudflare WARP profile, encrypt it locally, ask Android for VPN consent, and use the official WireGuard for Android tunnel library for the WireGuard userspace tunnel and Android TUN interface.

The WARP setup uses Cloudflare's undocumented consumer registration endpoint. It generates a fresh device key for each setup and does not bundle a shared key or account token. This third-party integration is unsupported, may stop working if Cloudflare changes the endpoint, and may be subject to Cloudflare's terms. FastSpeed is not affiliated with Cloudflare. The standard import flow remains available for configurations obtained from other providers.

## Requirements

- Android Studio with JDK 17 selected as the Gradle JDK
- Android SDK Platform 36 and Build Tools 36.0.0
- Android 8.0 (API 26) or newer
- Internet access for WARP enrollment or a valid WireGuard client configuration

## Open and build

1. Open this `MyWarpVpn` directory in Android Studio.
2. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, set **Gradle JDK** to JDK 17. If Android Studio only has its bundled JDK, install a JDK 17 from the Gradle JDK selector.
3. Allow Gradle sync to download the pinned Android, Compose, WireGuard, and OkHttp dependencies.
4. Select an Android 8.0+ device or emulator and run the `app` configuration.
5. In the app, choose **Set up Cloudflare WARP** to request a per-device profile, or choose **Import configuration** and select a valid client `.conf` file.
6. Tap **Connect**, approve Android's VPN consent screen, then inspect the persistent VPN notification and the dashboard.

Command line builds use the checked-in Gradle wrapper:

```powershell
./gradlew.bat :app:assembleDebug
```

On macOS/Linux, use `sh ./gradlew :app:assembleDebug`.

## What the app does

- Imports and validates the WireGuard `[Interface]` and `[Peer]` configuration with WireGuard's parser.
- Can register a fresh consumer WARP device through an undocumented Cloudflare endpoint and construct a WireGuard profile from the response. No static WARP key is embedded in the app.
- Encrypts the configuration using AES-GCM with a key held in Android Keystore. The ciphertext lives under `noBackupFilesDir`; Android backup is disabled.
- Uses `VpnService.prepare()` for system consent and a foreground `VpnService` with an ongoing notification while connected.
- Uses the official `com.wireguard.android:tunnel` library and its `GoBackend` / wireguard-go userspace implementation. That backend establishes the Android TUN interface from the config's addresses, DNS, MTU, and peer `AllowedIPs`, and protects its UDP tunnel sockets with `VpnService.protect()`.
- Shows tunnel state, elapsed connection time, configured endpoint, and byte totals returned by WireGuard. These counters are local totals; the app does not read packet contents.
- Watches default-network changes and attempts a reconnect when a new network becomes available.
- Keeps only a short in-memory list of operational status messages. It does not log configuration text, private keys, DNS queries, packet contents, or browsing history.

The dashboard's **Connected** state means the WireGuard backend successfully activated the local tunnel. It does not mean this project has verified public internet egress or that a particular configuration reaches Cloudflare. Verify egress separately on a device using credentials/configuration authorized by the provider.

## Android system controls

Android owns Always-on VPN, boot restart, and lockdown (“Block connections without VPN”). Use **Settings → Network & Internet → VPN → fastspeed** to enable Always-on and, if desired, lockdown. Those controls are deliberately managed by Android rather than simulated by an app preference. Auto-connect in the app is a separate convenience that connects when the app opens and VPN consent was already granted.

## Configuration notes

Import a complete WireGuard client config with an interface address and at least one peer with an endpoint and allowed IPs. A deliberately invalid placeholder file is provided at [`docs/wireguard-config.template.conf`](docs/wireguard-config.template.conf); it cannot connect until every placeholder is replaced with authorized values. The app displays only endpoint, DNS, MTU, and peer count from the parsed configuration. It never places keys in source code or logs. If a config cannot be read, parses incorrectly, has no usable peer, or the endpoint cannot be resolved/reached, the app reports a generic actionable error without printing secret configuration values.

WARP setup requires Cloudflare's registration endpoint to be reachable directly. Some networks may block it; this app does not include the third-party APK's shared bootstrap key. The profile request uses a private, undocumented endpoint and may fail or change without notice. The user must review Cloudflare's terms and confirm before registration.

## Permissions and privacy

The app requests only internet access, foreground-service permissions required for an active VPN, Android notification permission (optional for visible notifications on Android 13+), and Android's built-in VPN consent. No ads, analytics SDK, telemetry, packet capture, DNS collection, or traffic upload are included.

The VPN provider you connect to can still process traffic sent through its endpoint. Review that provider's terms and privacy documentation separately.

## Implementation layout

```text
app/src/main/java/com/example/mywarpvpn/
├── data/config/EncryptedConfigStore.kt
├── data/preferences/PreferencesRepository.kt
├── domain/model/VpnModels.kt
├── domain/repository/VpnStateRepository.kt
├── service/MyVpnService.kt
├── ui/MyWarpVpnApp.kt
├── ui/VpnViewModel.kt
├── vpn/VpnEngine.kt
├── vpn/WireGuardVpnEngine.kt
├── MainActivity.kt
└── MyWarpApplication.kt
```

The supplied fastspeed emblem is stored at `app/src/main/res/drawable-nodpi/fastspeed_logo.png` and is used for the launcher icon and in-app branding.

## Limitations

- WARP enrollment and provider egress have not been tested on a device. The registration endpoint is undocumented and may rate-limit or reject third-party clients.
- A debug APK was successfully assembled on the user's Windows machine. WARP enrollment and provider egress have not been verified on a device.
- The connection log is in-memory and is cleared when the app process ends.
- Android does not let an ordinary app silently toggle lockdown for the user. Configure Always-on and lockdown in Android VPN settings.

## Main dependency

WireGuard's official Android tunnel library: `com.wireguard.android:tunnel:1.0.20260102` (Apache-2.0), published by the [WireGuard Android project](https://github.com/WireGuard/wireguard-android).

