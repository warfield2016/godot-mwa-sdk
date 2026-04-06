# Troubleshooting

Common issues when integrating the Solana MWA SDK for Godot, with symptoms,
root causes, and solutions.

---

## 1. CLEARTEXT communication not permitted

**Symptom:** Error code 4 (`ERROR_CLEARTEXT_NOT_PERMITTED`) immediately after
calling `transact()`. Logcat shows:

```
CLEARTEXT communication to 127.0.0.1 not permitted by network security policy
```

**Cause:** Android 9+ blocks cleartext HTTP by default. The MWA protocol uses
a WebSocket on `ws://127.0.0.1:<port>` for the local association. Without an
explicit opt-in, the OS kills the connection before the wallet can connect.

**Solution:** Your host app's `AndroidManifest.xml` must reference a network
security config that allows cleartext to localhost. The SDK ships a template
at `res/xml/network_security_config.xml`. Either reference it directly or merge
the localhost rules into your existing config.

In your Godot project's `android/build/AndroidManifest.xml`:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

The template config allows cleartext ONLY for `127.0.0.1` and `localhost`,
keeping all other traffic HTTPS-only:

```xml
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

---

## 2. No MWA wallet found

**Symptom:** Error code 1 (`ERROR_WALLET_NOT_FOUND`) after `transact()`. The
wallet chooser never appears.

**Cause:** No installed app on the device handles the `solana-wallet://`
Intent scheme. The SDK calls `resolveActivity()` before launching the Intent
and emits this error when nothing matches.

**Solution:** Install a wallet that supports Mobile Wallet Adapter 2.0:

- **Phantom** (recommended for testing): [Google Play](https://play.google.com/store/apps/details?id=app.phantom)
- **Solflare**: [Google Play](https://play.google.com/store/apps/details?id=com.solflare.mobile)

On emulators, install via `adb install <wallet.apk>`. Some emulators lack
Google Play Services; use a direct APK or a Play Store-enabled system image.

Verify a wallet is installed:

```bash
adb shell pm query-intent-activities --brief -a android.intent.action.VIEW -d "solana-wallet://"
```

---

## 3. Authorization failed silently

**Symptom:** `authorize(cache.token_handle)` returns error code -1
(`ERROR_AUTHORIZATION_FAILED`) without showing the wallet UI.

**Cause:** The cached auth token is stale. Tokens can expire when:

- The wallet app was reinstalled or updated
- The user revoked the dApp's approval in wallet settings
- The wallet has its own token TTL policy
- The device was restored from backup (Keystore keys are hardware-bound)

**Solution:** Clear the cache and prompt a fresh authorization:

```gdscript
func _on_error(code: int, message: String) -> void:
    if code == MobileWalletAdapter.ERROR_AUTHORIZATION_FAILED:
        cache.clear()
        ResourceSaver.save(cache, CACHE_PATH)
        # Retry with fresh auth (wallet will show approval UI)
        mwa.transact()
```

---

## 4. Plugin not found on non-Android platforms

**Symptom:** `is_available()` returns `false`. Calling `transact()` emits error
code 5 (`ERROR_NO_ACTIVE_SESSION`) with message "MWA not available on this
platform".

**Cause:** Mobile Wallet Adapter is an Android-only protocol. The
`MobileWalletAdapter` node looks for the `SolanaMWA` Android plugin singleton
in `_ready()`. On desktop, iOS, or web exports, the singleton does not exist.

**Solution:** Always guard MWA calls with `is_available()`:

```gdscript
func _ready() -> void:
    if not mwa.is_available():
        status_label.text = "MWA requires Android"
        connect_btn.disabled = true
        return
    # Safe to use MWA here
```

For cross-platform games, consider a wallet abstraction layer that routes to
MWA on Android and a different adapter (e.g., WalletConnect or deep-link) on
other platforms.

---

## 5. App freezes after wallet selection

**Symptom:** The game freezes or becomes unresponsive after the user selects a
wallet from the Android chooser.

**Cause:** This was a known bug in the old `godot-solana-sdk` where the
`LocalAssociationScenario.start().get()` Future was resolved on the main
thread, blocking the Godot render loop.

**Solution:** This SDK does not have this bug. All Future resolution happens on
`Dispatchers.IO` via Kotlin coroutines. The `MWASessionManager` handles the
`start() -> Future<Client>` lifecycle off the main thread.

If you still experience freezes, check:

- You are using this SDK (`SolanaMWA` plugin), not the old `godot-solana-sdk`
- The `MobileWalletAdapter` node is in the scene tree (see issue 6)
- Your `_process()` or `_physics_process()` is not doing heavy synchronous work

---

## 6. Signals not received

**Symptom:** `mwa.authorized`, `mwa.error`, or other signals never fire even
though the wallet interaction appears to succeed.

**Cause:** The `MobileWalletAdapter` node connects to the Kotlin plugin's
signals in its `_ready()` callback. If the node is not in the scene tree,
`_ready()` never runs and signals are never wired up.

**Solution:** Ensure the `MobileWalletAdapter` node is added to the scene
tree before calling any methods:

```gdscript
# Option A: Place in scene (recommended)
# Add a MobileWalletAdapter node in the Godot editor and use a unique name
@onready var mwa: MobileWalletAdapter = %MobileWalletAdapter

# Option B: Add programmatically
func _ready() -> void:
    var mwa = MobileWalletAdapter.new()
    add_child(mwa)
    # Now safe to connect signals and call methods
    mwa.session_ready.connect(_on_session_ready)
```

Also verify your signal connections match the GDScript-facing names (not the
Kotlin internal names):

| GDScript signal | Kotlin signal (internal) |
|---|---|
| `session_ready` | `session_ready` |
| `authorized` | `auth_result` |
| `signing_complete` | `signing_complete` |
| `capabilities_received` | `capabilities_result` |
| `deauthorized` | `deauth_complete` |
| `error` | `error` |

---

## 7. Build errors

### SCons: godot-cpp not found

**Symptom:** `scons: *** No SConstruct file found.` or missing godot-cpp headers.

**Solution:** Initialize the godot-cpp submodule:

```bash
git submodule update --init --recursive
```

### SCons: Android NDK not configured

**Symptom:** `ERROR: Android NDK not found` or missing cross-compiler.

**Solution:** Set the `ANDROID_NDK_ROOT` environment variable:

```bash
export ANDROID_NDK_ROOT=$HOME/Android/Sdk/ndk/27.0.12077973
scons platform=android arch=arm64 target=template_debug
```

### Gradle: dependency resolution failure

**Symptom:** `Could not resolve com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.8`.

**Solution:** Ensure Maven Central is in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

### Gradle: R8/ProGuard strips crypto classes

**Symptom:** Runtime crash with `ClassNotFoundException` for Tink or
`ServiceLoader` entries after release build.

**Solution:** The SDK's AAR sets `isMinifyEnabled = false` by design. If your
host app enables R8, ensure the SDK's `consumer-rules.pro` is being applied.
Check your app-level `build.gradle.kts`:

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // consumer-rules.pro from the AAR should be auto-applied
        }
    }
}
```

### Gradle: duplicate class with other Solana libraries

**Symptom:** `Duplicate class com.solana.mobilewalletadapter...` at build time.

**Solution:** If your project also depends on `clientlib-ktx` directly (e.g.,
from another plugin), exclude the SDK's transitive copy:

```kotlin
implementation("com.solanamwa.godot:SolanaMWA:0.1.0") {
    exclude(group = "com.solanamobile", module = "mobile-wallet-adapter-clientlib-ktx")
}
```

---

## 8. Token handle returns null

**Symptom:** `cache.token_handle` is empty or `authorize(handle)` behaves as
a fresh auth instead of silent reauth.

**Cause:** The token handle is a UUID that references an entry in Kotlin's
EncryptedSharedPreferences. It returns null when:

- The token expired or was revoked by the wallet
- The `identity_uri` changed since the token was stored (SHA-256 hash mismatch)
- The EncryptedSharedPreferences file was corrupted or deleted
- The app was restored from backup on a different device (Keystore keys are
  hardware-bound)

**Solution:** Treat a null/empty handle as a signal to re-authorize:

```gdscript
func _on_session_ready() -> void:
    if cache.is_valid():
        mwa.authorize(cache.token_handle)
    else:
        mwa.authorize()

func _on_error(code: int, message: String) -> void:
    if code == MobileWalletAdapter.ERROR_AUTHORIZATION_FAILED:
        # Handle was valid but token is stale -- clear and retry
        cache.clear()
        ResourceSaver.save(cache, CACHE_PATH)
        mwa.authorize()  # Fresh auth
```

Ensure `identity_uri` is consistent across builds. If you change it (e.g.,
from `https://dev.example.com` to `https://example.com`), all cached tokens
become invalid because the SHA-256 binding no longer matches.

---

## Error Code Reference

| Code | Constant | Meaning |
|---|---|---|
| -1 | `ERROR_AUTHORIZATION_FAILED` | Wallet declined or token expired |
| -2 | `ERROR_INVALID_PAYLOADS` | Malformed transaction payload |
| -3 | `ERROR_NOT_SIGNED` | Wallet refused to sign |
| -4 | `ERROR_NOT_SUBMITTED` | Wallet refused to submit |
| -5 | `ERROR_NOT_CLONED` | Clone authorization rejected |
| -6 | `ERROR_TOO_MANY_PAYLOADS` | Exceeded wallet batch limit |
| -7 | `ERROR_CHAIN_NOT_SUPPORTED` | Wallet does not support requested chain |
| 1 | `ERROR_WALLET_NOT_FOUND` | No MWA wallet installed |
| 2 | `ERROR_SESSION_TIMEOUT` | Wallet did not connect in time |
| 3 | `ERROR_SESSION_CLOSED` | Session dropped unexpectedly |
| 4 | `ERROR_CLEARTEXT_NOT_PERMITTED` | Missing network_security_config |
| 5 | `ERROR_NO_ACTIVE_SESSION` | Method called before transact() |
