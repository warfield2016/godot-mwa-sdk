# Installation Guide

## Prerequisites

Before installing the Solana MWA SDK for Godot, ensure you have:

- **Godot 4.3+** with Android export support
- **Android export template** installed (Editor > Manage Export Templates)
- **Android SDK** with Build-Tools 34 and platform API 34
- **Physical Android device** with an MWA-compatible wallet app installed (Phantom, Solflare, or Backpack)
- **USB debugging** enabled on the device

> MWA requires inter-app communication via the `solana-wallet://` URI scheme. This is not available on emulators, iOS, or desktop platforms.

---

## Step 1: Download the Addon

**From GitHub Releases:**

1. Go to the [Releases](https://github.com/user/godot-mwa-sdk/releases) page.
2. Download `SolanaMWA-v*.zip` for the latest version.

**From the Godot Asset Library:**

1. Open Godot Editor.
2. Go to AssetLib (top bar).
3. Search for "Solana MWA".
4. Click Install.

---

## Step 2: Copy to Your Project

Extract (or let the Asset Library install) the addon so the following path exists in your Godot project:

```
your_project/
  addons/
    SolanaMWA/
      plugin.cfg
      scripts/
        solana_mwa_plugin.gd
      bin/
        SolanaMWA.gdextension
        ... (.so / .aar files)
```

If you downloaded manually, copy the entire `addons/SolanaMWA/` directory into your project's `addons/` folder.

---

## Step 3: Enable the Plugin

1. Open your Godot project.
2. Go to **Project > Project Settings > Plugins**.
3. Find **SolanaMWA** in the list.
4. Check the **Enable** checkbox.

You should now see `MobileWalletAdapter` available as a node type when adding nodes to a scene.

---

## Step 4: Configure Network Security

MWA 2.0 communicates over a localhost WebSocket (`ws://127.0.0.1:<port>/solana-wallet`). Since Android 9 (API 28), cleartext HTTP/WS traffic is blocked by default. **You must explicitly allow cleartext for localhost or MWA will silently fail.**

The plugin ships a `network_security_config.xml` template, but the Android manifest merger requires your host app to reference it directly.

### 4a. Add the XML config

If you are using Godot's **Custom Build** export (recommended), create or verify this file exists at:

```
android/build/res/xml/network_security_config.xml
```

with the following content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

This allows cleartext **only** for loopback addresses while keeping it disabled for all other hosts. The MWA session itself uses application-layer encryption (P-256 ECDH + AES-128-GCM), so loopback cleartext is safe.

### 4b. Reference it in your AndroidManifest

In your custom Android build manifest (or Godot's generated manifest override), add the `networkSecurityConfig` attribute to the `<application>` tag:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

If you already have a `network_security_config.xml` for other purposes, merge the `<domain-config>` block for `127.0.0.1` and `localhost` into your existing file.

> **Without this step, MWA will fail with `ERROR_CLEARTEXT_NOT_PERMITTED` (error code 4) on Android 9+. This is the most common installation issue.**

---

## Step 5: Configure the Android Export

1. Go to **Project > Export** and add (or select) an **Android** export preset.
2. Set the following:
   - **Min SDK:** `24` (Android 7.0 -- required by clientlib-ktx)
   - **Target SDK:** `34`
   - **Architectures:** Enable `arm64-v8a`. Optionally enable `armeabi-v7a` for older devices.
   - **Custom Build:** Enable (required for manifest modifications in Step 4).
   - **Gradle Build:** Ensure gradle build is enabled if using custom build.
3. Under **Permissions**, ensure `INTERNET` is enabled (it should be by default).

---

## Troubleshooting

### "MWA not available on this platform"

- `is_available()` returns `false` when the plugin cannot find the `SolanaMWA` Android singleton. This happens when:
  - Running on a non-Android platform (desktop, web, iOS).
  - The GDExtension `.so` / `.aar` files are missing from `addons/SolanaMWA/bin/`.
  - The plugin is not enabled in Project Settings.

### ERROR_CLEARTEXT_NOT_PERMITTED (code 4)

- The `network_security_config.xml` is not referenced in your manifest. See Step 4.
- If using a non-custom build, Godot's generated manifest does not include the config. Switch to Custom Build.

### ERROR_WALLET_NOT_FOUND (code 1)

- No MWA-compatible wallet app is installed on the device.
- Install Phantom, Solflare, or another wallet that supports the `solana-wallet://` URI scheme.
- On Android 11+ (API 30+), the plugin declares a `<queries>` intent filter for `solana-wallet` so wallet apps are visible. If you override the manifest merge, ensure the `<queries>` block is preserved.

### Session times out immediately

- The wallet app may have denied the connection request.
- Ensure the wallet app is unlocked and not in a restricted background state.
- Check that your `identity_uri` is a valid HTTPS URL -- some wallets validate it.

### Build fails with missing classes

- Ensure you have the Android export template installed for your Godot version.
- Verify the `addons/SolanaMWA/bin/` directory contains the `.aar` file for the target architecture.
- Run a clean gradle build: delete `android/build/.gradle/` and re-export.

### Plugin works in debug but not release builds

- ProGuard/R8 may strip classes needed by the Kotlin MWA client library. If using a custom Proguard config, add keep rules for `com.solana.mobilewalletadapter.clientlib.**`.
