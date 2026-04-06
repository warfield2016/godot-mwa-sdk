# Architecture

System design for the Solana MWA SDK for Godot 4.3.

## Layer Diagram

```
+-------------------------------------------------------------------+
|                     LAYER 1: GDScript (Game Code)                 |
|                                                                   |
|  @onready var mwa: MobileWalletAdapter = %MobileWalletAdapter     |
|  mwa.transact()  /  mwa.authorize()  /  mwa.sign_and_send_...()  |
|                                                                   |
|  Signals: authorized, signing_complete, error, ...                |
|  Resources: MWAAuthCache (token_handle, public_key, chain, ...)   |
+-------------------------------+-----------------------------------+
                                |
                    GDExtension ClassDB calls
                                |
+-------------------------------v-----------------------------------+
|                LAYER 2: C++ GDExtension Bridge                    |
|                                                                   |
|  MobileWalletAdapter : Node                                       |
|    _ready() -> discovers "SolanaMWA" Android singleton            |
|    transact() -> android_plugin->call("startSession", ...)        |
|    authorize() -> android_plugin->call("authorize", ...)          |
|                                                                   |
|  MWAAuthCache : Resource                                          |
|    Stores non-sensitive metadata (UUID handle, pubkey, chain)     |
|    Serialized to user://mwa_cache.tres via ResourceSaver          |
+-------------------------------+-----------------------------------+
                                |
                    JNI bridge (Godot Engine <-> Android)
                                |
+-------------------------------v-----------------------------------+
|               LAYER 3: Kotlin GodotPlugin                         |
|                                                                   |
|  SolanaMWAPlugin : GodotPlugin                                    |
|    @UsedByGodot methods called from C++ via JNI                   |
|    Coroutine scope (SupervisorJob + Dispatchers.Main)             |
|    Emits signals back to C++ via emitSignal() on UI thread        |
|                                                                   |
|  MWASessionManager                                                |
|    Creates LocalAssociationScenario                               |
|    Manages session lifecycle (start, client, close)               |
|    Thread-safe via Mutex + Dispatchers.IO                         |
|                                                                   |
|  MWATokenStore                                                    |
|    EncryptedSharedPreferences (AES256-SIV / AES256-GCM)           |
|    MasterKey backed by Android Keystore (StrongBox if available)  |
|    UUID handle <-> raw auth_token mapping                         |
|                                                                   |
|  MWAResultMapper                                                  |
|    Base58/Base64 encoding, JSON serialization                     |
|    MWA 2.0 error code mapping                                    |
+-------------------------------+-----------------------------------+
                                |
                    solana-wallet:// Intent + WebSocket
                                |
+-------------------------------v-----------------------------------+
|              LAYER 4: MWA Protocol (clientlib-ktx 2.0.8)          |
|                                                                   |
|  LocalAssociationScenario                                         |
|    Binds ws://127.0.0.1:<random_port>/solana-wallet               |
|    Fires Intent to open wallet app                                |
|                                                                   |
|  Session encryption (handled by clientlib):                       |
|    1. P-256 ECDH key agreement (association keys)                 |
|    2. AES-128-GCM symmetric encryption derived from shared secret |
|    3. JSON-RPC 2.0 messages over encrypted WebSocket frames       |
|                                                                   |
|  Methods: authorize, sign_transactions, sign_and_send_transactions|
|           sign_messages, get_capabilities, deauthorize             |
+-------------------------------+-----------------------------------+
                                |
                    Encrypted JSON-RPC over loopback WebSocket
                                |
+-------------------------------v-----------------------------------+
|               LAYER 5: Wallet App                                 |
|                                                                   |
|  Phantom, Solflare, or any MWA 2.0 compatible wallet              |
|    Receives solana-wallet:// Intent                               |
|    Connects to ws://127.0.0.1:<port>                              |
|    Performs ECDH handshake                                         |
|    Processes JSON-RPC requests (authorize, sign, etc.)            |
|    Returns encrypted JSON-RPC responses                           |
+-------------------------------------------------------------------+
```

## Data Flow: Transaction Signing

```
GDScript                 C++ Bridge           Kotlin Plugin         Wallet App
   |                        |                      |                    |
   | sign_and_send_         |                      |                    |
   |   transactions()       |                      |                    |
   |----------------------->|                      |                    |
   |                        | call("signAnd        |                    |
   |                        |   SendTransactions") |                    |
   |                        |--------------------->|                    |
   |                        |                      | withContext(IO) {  |
   |                        |                      |   client.signAnd   |
   |                        |                      |   Send...().get()  |
   |                        |                      | }                  |
   |                        |                      |------------------->|
   |                        |                      |                    | User confirms
   |                        |                      |                    | in wallet UI
   |                        |                      |<-------------------|
   |                        |                      | signaturesToJson() |
   |                        |                      |                    |
   |                        | emitSignal(          |                    |
   |                        |   "signing_complete") |                    |
   |                        |<---------------------|                    |
   | emit_signal(           |                      |                    |
   |   "signing_complete")  |                      |                    |
   |<-----------------------|                      |                    |
   |                        |                      |                    |
```

## Auth Token Flow

```
Wallet                  Kotlin (Layer 3)                   GDScript (Layer 1)
  |                          |                                  |
  | auth_token (raw bytes)   |                                  |
  |------------------------->|                                  |
  |                          | MWATokenStore.storeToken(        |
  |                          |   authToken, identityUriHash)    |
  |                          |     |                            |
  |                          |     | EncryptedSharedPreferences  |
  |                          |     | prefs[uuid.token] = raw    |
  |                          |     | prefs[uuid.identity] = hash|
  |                          |     v                            |
  |                          | handle = UUID.randomUUID()       |
  |                          |                                  |
  |                          | emitSignal("auth_result",        |
  |                          |   accounts, handle, walletUri)   |
  |                          |--------------------------------->|
  |                          |                                  |
  |                          |         (never sees raw token)   |
  |                          |                                  | MWAAuthCache
  |                          |                                  | .token_handle = uuid
  |                          |                                  | .public_key = bytes
  |                          |                                  | ResourceSaver.save()
  |                          |                                  |
```

On reauthorization, the flow reverses -- GDScript sends the UUID handle,
Kotlin looks up the real token from the Keystore, and passes it to the wallet.

## Security Boundaries

| Boundary | Trusts | Validates |
|---|---|---|
| **GDScript -> C++** | ClassDB type safety | Nothing -- all data is non-sensitive metadata |
| **C++ -> Kotlin (JNI)** | Godot Engine singleton registry | Plugin existence via `has_singleton()` |
| **Kotlin -> clientlib** | clientlib handles crypto correctly | Future timeout (90s), exception handling |
| **clientlib -> Wallet** | Nothing -- untrusted peer | P-256 ECDH key agreement, AES-128-GCM payload encryption |
| **Kotlin -> Keystore** | Android Keystore TEE/StrongBox | Identity URI hash binding, corrupted prefs recovery |
| **GDScript -> .tres file** | Godot resource serializer | `is_valid()` check on load; no secrets in file |

Key principle: **raw auth tokens never cross the JNI boundary**. The Kotlin
layer is the trust boundary for credentials. GDScript operates entirely on
UUID handles and public metadata.

## Component Responsibilities

```
src/
  mobile_wallet_adapter.hpp/cpp   -- MobileWalletAdapter Node (GDScript API)
  mwa_auth_cache.hpp/cpp          -- MWAAuthCache Resource (metadata storage)
  mwa_auth_result.hpp/cpp         -- MWAAuthResult (parsed auth response)
  mwa_capabilities.hpp/cpp        -- MWACapabilities (wallet feature query)
  register_types.hpp/cpp          -- GDExtension entry point

android/plugin/src/main/java/com/solanamwa/godot/
  SolanaMWAPlugin.kt              -- GodotPlugin entry, signal emission
  MWASessionManager.kt            -- WebSocket session lifecycle
  MWATokenStore.kt                -- Encrypted auth token storage
  MWAResultMapper.kt              -- Serialization, error codes, Base58
```

## For Contributors

### Prerequisites

- Godot 4.3 export templates (Android arm64)
- Android SDK 34, NDK 27+
- SCons 4.x, Python 3.10+
- JDK 17 (for Gradle/Kotlin compilation)

### Building the C++ GDExtension

```bash
# Initialize godot-cpp (first time only)
git submodule update --init --recursive

# Debug build for Android arm64
scons platform=android arch=arm64 target=template_debug -j$(nproc)

# Release build
scons platform=android arch=arm64 target=template_release -j$(nproc)
```

Output: `addons/SolanaMWA/bin/{debug,release}/arm64-v8a/libSolanaMWA.so`

### Building the Kotlin AAR

```bash
cd android
./gradlew :plugin:assembleRelease
```

Output: `android/plugin/build/outputs/aar/SolanaMWA-release.aar`

### Testing on Device

1. Build both the `.so` and `.aar`
2. Copy the AAR to `addons/SolanaMWA/bin/`
3. Open the `example/` project in Godot 4.3
4. Export to Android (arm64) with the SolanaMWA plugin enabled
5. Install on a device with Phantom or Solflare

### Adding a New MWA Method

To expose a new clientlib method (e.g., a hypothetical `cloneAuthorization`):

1. **Kotlin** (`SolanaMWAPlugin.kt`): Add a `@UsedByGodot` function that
   launches a coroutine, calls `client.cloneAuthorization().get()` on
   `Dispatchers.IO`, and emits a signal with the result.

2. **Kotlin** (`MWAResultMapper.kt`): Add serialization helpers if the method
   has new parameter/return types.

3. **C++** (`mobile_wallet_adapter.hpp/cpp`): Add the public method, the
   signal forwarding callback, and register both in `_bind_methods()`. The
   method should call `android_plugin->call("cloneAuthorization", ...)`.

4. **Signals**: Register the new signal in both Kotlin
   (`getPluginSignals()`) and C++ (`_bind_methods()`). Use a clean
   GDScript-facing name in the C++ bridge.

5. **Error codes**: If the method introduces new failure modes, add constants
   in `MWAResultMapper.kt` and mirror them in `mobile_wallet_adapter.hpp`.

### Threading Model

- **GDScript**: Single-threaded (Godot main thread). All signals arrive on
  the main thread.
- **C++ Bridge**: Runs on the Godot main thread. Calls into Kotlin via JNI
  (non-blocking -- Kotlin methods return immediately).
- **Kotlin**: `pluginScope` uses `Dispatchers.Main` as its base context.
  Blocking MWA operations are dispatched to `Dispatchers.IO`. Signal emission
  uses `runOnUiThread` to return to the Godot render thread.
- **clientlib**: Internally uses its own threads for the WebSocket and ECDH
  handshake. Returns `Future` objects that Kotlin resolves on IO.
