# API Reference

Complete reference for the Godot MWA SDK classes. Built on Solana Mobile's `clientlib-ktx:2.0.8`.

---

## MobileWalletAdapter

**Inherits:** `Node`

The primary class for interacting with MWA-compatible wallets on Android. Add as a node in your scene tree. All wallet operations are asynchronous -- results are delivered via signals.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `identity_uri` | `String` | `""` | HTTPS URI identifying your app. Displayed to the user in the wallet's authorization prompt. Must be a valid URL. |
| `identity_name` | `String` | `""` | Human-readable app name shown in the wallet prompt. |
| `identity_icon` | `String` | `""` | Relative path (from `identity_uri`) to a favicon or icon image. Optional. |
| `chain` | `String` | `"solana:mainnet"` | Target Solana cluster. Common values: `solana:mainnet`, `solana:devnet`, `solana:testnet`. |

All properties are editable in the Godot inspector and can be set in code before calling `transact()`.

---

### Methods

#### `transact() -> void`

Opens a new MWA session with the wallet app. This triggers Android's intent system to launch the wallet. The wallet establishes an encrypted WebSocket session over localhost.

Emits `session_ready` on success, or `error` on failure.

Call this before any other wallet operation. A session must be active for `authorize()`, `sign_messages()`, etc.

```gdscript
mwa.transact()
```

---

#### `authorize(token_handle: String = "") -> void`

Requests authorization from the wallet. If `token_handle` is provided (from a previous session's `MWAAuthCache`), the wallet attempts silent reauthorization without prompting the user.

- **Fresh authorization:** `mwa.authorize()`
- **Reauthorization:** `mwa.authorize(cache.token_handle)`

Emits `authorized` on success with the account details and a new token handle. Emits `error` on failure.

```gdscript
# Fresh auth
mwa.authorize()

# Reauth with cached token
mwa.authorize(cached_token_handle)
```

---

#### `sign_and_send_transactions(payloads: Array) -> void`

Asks the wallet to sign and broadcast one or more serialized Solana transactions. Each element in `payloads` must be a base64-encoded serialized transaction (legacy or v0 format).

The wallet signs the transactions, submits them to the cluster, and returns the resulting transaction signatures.

Emits `signing_complete` with a JSON array of transaction signatures (base58 strings). Emits `error` on failure.

```gdscript
var tx_b64 := Marshalls.raw_to_base64(serialized_transaction)
mwa.sign_and_send_transactions([tx_b64])
```

---

#### `sign_messages(addresses: Array, payloads: Array) -> void`

Asks the wallet to sign arbitrary byte payloads. Both arrays must be the same length. Each element is a base64-encoded byte buffer.

- `addresses` -- base64-encoded Ed25519 public keys that should produce signatures.
- `payloads` -- base64-encoded messages to sign.

Emits `signing_complete` with a JSON array of base64-encoded Ed25519 signatures. Emits `error` on failure.

```gdscript
var msg_b64 := Marshalls.raw_to_base64("Hello".to_utf8_buffer())
var addr_b64 := accounts[0]["address"]  # from authorized signal
mwa.sign_messages([addr_b64], [msg_b64])
```

---

#### `sign_transactions(payloads: Array) -> void`

Asks the wallet to sign (but not broadcast) one or more serialized transactions. Each element in `payloads` must be a base64-encoded serialized transaction.

Use this when you need to sign a transaction offline or submit it yourself via RPC. Requires the wallet to support the `solana:signTransactions` feature (check via `get_capabilities()`).

Emits `signing_complete` with a JSON array of base64-encoded signed transaction bytes. Emits `error` on failure.

```gdscript
mwa.sign_transactions([tx_b64])
```

---

#### `get_capabilities() -> void`

Queries the connected wallet for its supported limits and optional features. Call within an active session.

Emits `capabilities_received` with max transaction count, max message count, and a JSON array of supported feature URIs. Emits `error` on failure.

```gdscript
mwa.get_capabilities()
```

---

#### `deauthorize(token_handle: String) -> void`

Revokes authorization for the given token handle. The wallet invalidates the auth token. After deauthorization, the user must re-approve to connect again.

Emits `deauthorized` on success. Emits `error` on failure.

```gdscript
mwa.deauthorize(cache.token_handle)
```

---

#### `disconnect() -> void`

Ends the current MWA session (closes the WebSocket). This is a transport-level teardown and does not revoke authorization. Call `deauthorize()` first if you want to fully log out.

Does not emit a signal. Calling `disconnect()` when no session is active is a safe no-op.

```gdscript
mwa.disconnect()
```

---

#### `is_available() -> bool`

Returns `true` if the native Android MWA plugin singleton was found. Returns `false` on non-Android platforms or if the plugin binary is missing.

Use this to show platform-appropriate UI (e.g., disable the connect button on desktop).

```gdscript
if not mwa.is_available():
    status_label.text = "MWA requires Android"
```

---

### Signals

#### `session_ready()`

Emitted when the MWA WebSocket session is established and the plugin is ready to accept commands. This fires after a successful `transact()` call.

---

#### `authorized(accounts_json: String, token_handle: String, wallet_uri_base: String)`

Emitted after a successful `authorize()` call.

| Parameter | Type | Description |
|-----------|------|-------------|
| `accounts_json` | `String` | JSON array of account objects. Each has `"address"` (base64-encoded Ed25519 pubkey) and `"label"` (display name). |
| `token_handle` | `String` | UUID referencing the auth token stored in Android Keystore. Pass to `authorize()` for reauthorization or `deauthorize()` to revoke. |
| `wallet_uri_base` | `String` | Base URI for the wallet endpoint. Stored for session affinity. |

Example `accounts_json` structure:

```json
[{"address": "base64EncodedPubkey==", "label": "Wallet 1"}]
```

---

#### `signing_complete(signatures_json: String)`

Emitted after a successful `sign_messages()`, `sign_transactions()`, or `sign_and_send_transactions()` call.

| Parameter | Type | Description |
|-----------|------|-------------|
| `signatures_json` | `String` | JSON array of signature strings. For `sign_and_send_transactions`, these are base58 transaction signatures. For `sign_messages`, these are base64-encoded Ed25519 signatures. |

---

#### `capabilities_received(max_transactions: int, max_messages: int, features_json: String)`

Emitted after a successful `get_capabilities()` call.

| Parameter | Type | Description |
|-----------|------|-------------|
| `max_transactions` | `int` | Maximum number of transactions the wallet can sign in a single request. |
| `max_messages` | `int` | Maximum number of messages the wallet can sign in a single request. |
| `features_json` | `String` | JSON array of feature URI strings. Known features: `solana:signTransactions`, `solana:cloneAuthorization`, `solana:signInWithSolana`. |

---

#### `deauthorized()`

Emitted after a successful `deauthorize()` call. The auth token has been invalidated.

---

#### `error(code: int, message: String)`

Emitted when any operation fails.

| Parameter | Type | Description |
|-----------|------|-------------|
| `code` | `int` | One of the error constants listed below. |
| `message` | `String` | Human-readable error description. |

---

### Error Constants

#### Protocol Errors (Negative Values)

These map to MWA 2.0 spec error codes from the wallet.

| Constant | Value | Description |
|----------|-------|-------------|
| `ERROR_AUTHORIZATION_FAILED` | `-1` | The wallet declined authorization, or the auth token was invalid during reauthorization. |
| `ERROR_INVALID_PAYLOADS` | `-2` | One or more transaction/message payloads were malformed or could not be deserialized. |
| `ERROR_NOT_SIGNED` | `-3` | The user declined the signing prompt in the wallet. |
| `ERROR_NOT_SUBMITTED` | `-4` | Transactions were signed but the wallet failed to submit them to the cluster. |
| `ERROR_NOT_CLONED` | `-5` | The clone authorization request was rejected by the wallet. |
| `ERROR_TOO_MANY_PAYLOADS` | `-6` | The number of payloads exceeded the wallet's `max_transactions` or `max_messages` limit. |
| `ERROR_CHAIN_NOT_SUPPORTED` | `-7` | The wallet does not support the requested `chain` identifier. |

#### Transport/Environment Errors (Positive Values)

These are raised by the plugin before or during communication with the wallet.

| Constant | Value | Description |
|----------|-------|-------------|
| `ERROR_WALLET_NOT_FOUND` | `1` | No MWA-compatible wallet app is installed on the device. |
| `ERROR_SESSION_TIMEOUT` | `2` | The MWA session timed out waiting for the wallet to respond. |
| `ERROR_SESSION_CLOSED` | `3` | The WebSocket session was closed unexpectedly (wallet killed, user swiped away). |
| `ERROR_CLEARTEXT_NOT_PERMITTED` | `4` | Android's network security policy is blocking the localhost WebSocket. See [Installation Guide](installation.md#step-4-configure-network-security). |
| `ERROR_NO_ACTIVE_SESSION` | `5` | A method was called but no MWA session is active, or the plugin is unavailable on this platform. |

---

## MWAAuthCache

**Inherits:** `Resource`

Persistent authorization cache. Stores non-sensitive session metadata (public key, wallet URI, chain). The actual auth token is held in Android's EncryptedSharedPreferences -- GDScript only sees a UUID `token_handle` that references it.

Because `MWAAuthCache` extends `Resource`, it can be saved and loaded with `ResourceSaver`/`ResourceLoader`.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `token_handle` | `String` | `""` | UUID referencing the encrypted auth token in Android Keystore. |
| `public_key` | `PackedByteArray` | `[]` | Raw 32-byte Ed25519 public key of the authorized account. |
| `wallet_uri_base` | `String` | `""` | Base URI of the wallet endpoint for session affinity. |
| `wallet_package` | `String` | `""` | Android package name of the wallet app (if available). |
| `account_label` | `String` | `""` | Human-readable account label from the wallet. |
| `chain` | `String` | `""` | Chain identifier used during authorization. |
| `identity_uri_hash` | `String` | `""` | Hash of the `identity_uri` used during authorization. Used for cache invalidation. |
| `cached_at` | `int` | `0` | Unix timestamp (seconds) when the cache was last updated. |

### Methods

#### `update_from_auth(token_handle: String, accounts_json: String, wallet_uri_base: String, chain: String, identity_uri_hash: String) -> void`

Populates the cache from an `authorized` signal's parameters. Parses `accounts_json` to extract the first account's public key and label.

```gdscript
cache.update_from_auth(token_handle, accounts_json, wallet_uri_base,
    "solana:devnet", identity_uri.sha256_text())
```

#### `clear() -> void`

Resets all fields to empty/zero defaults.

#### `is_valid() -> bool`

Returns `true` if `token_handle` is non-empty.

#### `get_display_address() -> String`

Returns a truncated base58 address string: first 4 characters + `...` + last 4 characters. Returns an empty string if no public key is stored.

```gdscript
# Example output: "7xKX...9mPq"
label.text = cache.get_display_address()
```

### Usage Pattern

```gdscript
const CACHE_PATH := "user://mwa_cache.tres"

# Load
var cache: MWAAuthCache
if ResourceLoader.exists(CACHE_PATH):
    cache = ResourceLoader.load(CACHE_PATH) as MWAAuthCache
if cache == null:
    cache = MWAAuthCache.new()

# Save after authorization
cache.update_from_auth(token_handle, accounts_json, wallet_uri_base,
    chain, identity_uri_hash)
ResourceSaver.save(cache, CACHE_PATH)

# Use for reauthorization
if cache.is_valid():
    mwa.authorize(cache.token_handle)
```

---

## MWACapabilities

**Inherits:** `RefCounted`

Describes the connected wallet's capabilities. Populated from the `capabilities_received` signal data.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `max_transactions` | `int` | `0` | Maximum transactions the wallet can sign per request. |
| `max_messages` | `int` | `0` | Maximum messages the wallet can sign per request. |
| `features` | `PackedStringArray` | `[]` | Array of supported feature URI strings. |

### Methods

#### `supports_feature(feature: String) -> bool`

Returns `true` if the `features` array contains the given feature URI.

```gdscript
if caps.supports_feature("solana:signInWithSolana"):
    show_siws_button()
```

#### `supports_sign_transactions() -> bool`

Shorthand for `supports_feature("solana:signTransactions")`.

#### `supports_clone_authorization() -> bool`

Shorthand for `supports_feature("solana:cloneAuthorization")`.

---

## MWAAuthResult

**Inherits:** `RefCounted`

Lightweight data transfer object returned from authorize/reauthorize calls. The `token_handle` is a UUID that maps to the real auth token stored in Kotlin's EncryptedSharedPreferences -- the raw token never crosses the JNI boundary.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `token_handle` | `String` | `""` | UUID referencing the encrypted auth token. |
| `accounts_json` | `String` | `""` | JSON array of account objects from the wallet. |
| `wallet_uri_base` | `String` | `""` | Base URI of the wallet endpoint. |
