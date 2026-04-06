# Auth Token Caching

This document describes how the Solana MWA SDK for Godot handles authorization
tokens -- from initial wallet approval through silent reauthorization, cache
invalidation, and secure storage.

## Token Lifecycle Overview

```
 1. transact()           -- open WebSocket session with wallet
 2. authorize()          -- wallet prompts user, returns opaque auth_token
 3. Token stored         -- Kotlin encrypts token, returns UUID handle to GDScript
 4. authorize(handle)    -- silent reauthorization using cached token
 5. Token invalidated    -- wallet rejects stale token, SDK clears cache
 6. deauthorize(handle)  -- explicit revocation, clears both keystore + Resource
```

## Initial Authorization

When `authorize()` is called with no arguments, the wallet app displays its
approval UI. On success the wallet returns:

- An opaque **auth_token** (binary blob, wallet-specific format)
- An array of authorized **accounts** (public key + optional label)
- A **wallet_uri_base** for future deep-linking

The raw auth_token never reaches GDScript. The Kotlin plugin intercepts it
and stores it in EncryptedSharedPreferences (see below), returning only a
random UUID string called the **token handle**.

## Token Storage (Two Layers)

### Layer 1: Kotlin EncryptedSharedPreferences

The raw auth_token is encrypted at rest using Android's `EncryptedSharedPreferences`
backed by the Android Keystore system.

| Detail | Value |
|---|---|
| Key encryption | AES256-SIV |
| Value encryption | AES256-GCM |
| MasterKey scheme | AES256_GCM (StrongBox when available) |
| Prefs filename | `solana_mwa_auth_prefs` |
| Storage keys | `<uuid>.token`, `<uuid>.identity` |

StrongBox (hardware secure element) is preferred on API 28+ devices. If the
device SoC lacks StrongBox (emulators, older chips), the SDK falls back to
TEE-backed Keystore, which is still hardware-backed on virtually all ARM64
devices shipping with Android 7+.

If the prefs file becomes corrupted (e.g., after a factory reset wipes the
Keystore but not app data), the SDK deletes the file and retries. Stored tokens
are lost, but the user simply re-authorizes -- preferable to a crash loop.

### Layer 2: GDScript MWAAuthCache Resource

`MWAAuthCache` is a Godot `Resource` subclass stored at `user://mwa_cache.tres`.
It holds only **non-sensitive metadata**:

| Property | Type | Description |
|---|---|---|
| `token_handle` | `String` | UUID referencing the real token in Keystore |
| `public_key` | `PackedByteArray` | 32-byte Ed25519 public key |
| `wallet_uri_base` | `String` | Wallet deep-link base URI |
| `account_label` | `String` | Human-readable account name |
| `chain` | `String` | e.g., `solana:mainnet` |
| `identity_uri_hash` | `String` | SHA-256 hex of the dApp identity URI |
| `cached_at` | `int` | Unix timestamp of last successful auth |

The cache file is a standard `.tres` resource. It can be inspected in the Godot
editor, saved/loaded with `ResourceSaver`/`ResourceLoader`, and serialized
without any security risk because it contains no secrets.

## Silent Reauthorization

Pass a cached `token_handle` to `authorize()` to skip the wallet approval UI:

```gdscript
mwa.authorize(cache.token_handle)
```

Under the hood:

1. The C++ bridge calls `authorize(handle)` on the Kotlin plugin
2. Kotlin retrieves the real auth_token from EncryptedSharedPreferences
3. The identity URI hash is verified to match the stored binding
4. The token is passed to `client.authorize()` with reauth semantics
5. The wallet validates the token and either approves silently or rejects

If the wallet accepts, a new auth_token is returned and the old handle is
replaced with a fresh one. If rejected, the SDK emits an error and the cache
should be cleared.

## Token Invalidation

When the wallet rejects a cached token (expired, revoked, or wallet reinstalled),
the authorize call fails with `ERROR_AUTHORIZATION_FAILED` (code -1).

Your error handler should detect this and clear the cache:

```gdscript
func _on_error(code: int, message: String) -> void:
    if code == MobileWalletAdapter.ERROR_AUTHORIZATION_FAILED:
        cache.clear()
        ResourceSaver.save(cache, CACHE_PATH)
        # Prompt user to re-connect
```

## Explicit Deauthorization

To revoke wallet approval (e.g., a "Disconnect Wallet" button), call
`deauthorize()` with the token handle. This notifies the wallet AND clears
the encrypted storage:

```gdscript
func _on_disconnect_pressed() -> void:
    mwa.deauthorize(cache.token_handle)

func _on_deauthorized() -> void:
    cache.clear()
    ResourceSaver.save(cache, CACHE_PATH)
```

The Kotlin plugin:

1. Retrieves the real token from EncryptedSharedPreferences
2. Sends `client.deauthorize(token)` to the wallet
3. Removes the token entry from the encrypted prefs
4. Emits `deauth_complete` -> GDScript receives `deauthorized` signal

## Cross-dApp Isolation

Tokens are scoped by identity URI. When the Kotlin plugin stores a token, it
also stores a SHA-256 hash of the `identity_uri` passed to `startSession()`.
On retrieval, the hash is compared:

```
store:    prefs[handle + ".identity"] = SHA256(identity_uri)
retrieve: if stored_hash != SHA256(identity_uri) -> return null
```

This prevents token replay if a device runs multiple Godot games using
the same SDK. Each game's `identity_uri` (e.g., `https://mygame.example.com`)
produces a different hash, so tokens from Game A cannot be used by Game B
even if they share the same Android user profile.

## Backup Exclusion

Auth tokens are excluded from Android Auto Backup. The SDK ships
`backup_rules.xml` which excludes `solana_mwa_auth_prefs.xml`:

```xml
<full-backup-content>
    <exclude domain="sharedpref" path="solana_mwa_auth_prefs.xml" />
</full-backup-content>
```

This is necessary because Keystore master keys are hardware-bound and do NOT
transfer between devices. If the prefs file were restored to a new device,
every decryption attempt would throw `GeneralSecurityException`.

The host app must reference this file (or merge the rules) in its
`AndroidManifest.xml`:

```xml
android:fullBackupContent="@xml/backup_rules"
```

## Complete Example: Connect, Cache, Reconnect

```gdscript
extends Control

const CACHE_PATH := "user://mwa_cache.tres"

@onready var mwa: MobileWalletAdapter = %MobileWalletAdapter

var cache: MWAAuthCache


func _ready() -> void:
    # Load existing cache (if any)
    if ResourceLoader.exists(CACHE_PATH):
        cache = ResourceLoader.load(CACHE_PATH) as MWAAuthCache
    if cache == null:
        cache = MWAAuthCache.new()

    # Wire up signals
    mwa.session_ready.connect(_on_session_ready)
    mwa.authorized.connect(_on_authorized)
    mwa.error.connect(_on_error)

    # Guard: MWA is Android-only
    if not mwa.is_available():
        print("MWA not available on this platform")
        return

    # Start session -- this opens the wallet chooser on Android
    mwa.transact()


func _on_session_ready() -> void:
    if cache.is_valid():
        # Silent reauthorization with cached token
        mwa.authorize(cache.token_handle)
    else:
        # First-time authorization -- wallet shows approval UI
        mwa.authorize()


func _on_authorized(accounts_json: String, token_handle: String,
        wallet_uri_base: String) -> void:
    # Update cache with new credentials
    cache.update_from_auth(token_handle, accounts_json, wallet_uri_base,
            mwa.chain, "")  # identity_uri_hash populated internally
    ResourceSaver.save(cache, CACHE_PATH)
    print("Connected: ", cache.get_display_address())


func _on_error(code: int, message: String) -> void:
    if code == MobileWalletAdapter.ERROR_AUTHORIZATION_FAILED:
        # Token expired or revoked -- clear cache and retry fresh
        cache.clear()
        ResourceSaver.save(cache, CACHE_PATH)
        print("Auth token expired. Reconnecting...")
        mwa.transact()
    else:
        print("MWA error %d: %s" % [code, message])
```

