# React Native SDK Parity

Side-by-side mapping between the official Solana Mobile React Native MWA SDK (`@solana-mobile/mobile-wallet-adapter-protocol`) and the Godot MWA SDK. Both wrap the same underlying MWA 2.0 protocol.

---

## Method Mapping

| React Native MWA SDK | Godot MWA SDK | Notes |
|----------------------|---------------|-------|
| `transact(callback)` | `transact()` | RN wraps all operations inside a `transact()` callback. Godot opens the session explicitly and uses signals. |
| `authorize({identity, chain, authToken})` | `authorize(token_handle)` | RN passes identity inline per call. Godot reads identity from node properties set beforehand. Reauth token is `authToken` in RN, `token_handle` in Godot. |
| `deauthorize({authToken})` | `deauthorize(token_handle)` | Identical semantics. |
| `getCapabilities()` | `get_capabilities()` | Identical semantics. Both return max_transactions, max_messages, and a features list. |
| `signAndSendTransactions(payloads, opts)` | `sign_and_send_transactions(payloads)` | RN accepts an options object (minContextSlot, commitment). Godot uses wallet defaults. Payloads are base64 in both. |
| `signMessages(addresses, payloads)` | `sign_messages(addresses, payloads)` | Identical semantics. Both take parallel arrays of base64-encoded addresses and messages. |
| `signTransactions(payloads)` | `sign_transactions(payloads)` | Identical semantics. |
| `cloneAuthorization({authToken})` | Not yet implemented | Planned for a future release. |

---

## Architecture Differences

### Session Lifecycle

**React Native** uses a callback-scoped session:

```javascript
await transact(async (wallet) => {
    const auth = await wallet.authorize({
        identity: { uri, name, icon },
        chain: "solana:devnet",
    });
    const signed = await wallet.signMessages(
        [auth.accounts[0].address],
        [messageBytes]
    );
});
// Session auto-closes when callback returns
```

All operations happen inside the `transact()` callback. The session opens when the callback starts and closes when it returns or throws.

**Godot** uses an explicit session with signal-based flow:

```gdscript
# 1. Open session
mwa.transact()

# 2. session_ready signal fires -> authorize
func _on_session_ready():
    mwa.authorize()

# 3. authorized signal fires -> sign
func _on_authorized(accounts_json, token_handle, wallet_uri_base):
    mwa.sign_messages([address_b64], [message_b64])

# 4. signing_complete signal fires -> done
func _on_signed(signatures_json):
    print("Signed: ", signatures_json)

# 5. Explicitly close when done
mwa.disconnect()
```

The session stays open until `disconnect()` is called or the wallet closes it. Multiple operations can be chained across signals within a single session.

### Key Difference: RN uses `await` inside a callback scope. Godot uses signals fired on the scene tree.

---

### Auth Token Handling

| Aspect | React Native | Godot |
|--------|-------------|-------|
| Token format | Raw auth token string exposed to JS | UUID handle (`token_handle`) -- raw token never leaves Kotlin |
| Storage | Developer manages storage (AsyncStorage, SecureStore, etc.) | Encrypted automatically via Android Keystore + EncryptedSharedPreferences |
| Reauthorization | Pass `authToken` string to `authorize()` | Pass `token_handle` UUID to `authorize()` |
| Persistence | Developer implements save/load | `MWAAuthCache` Resource -- save with `ResourceSaver`, load with `ResourceLoader` |
| Security boundary | Token accessible in JS runtime | Token confined to Kotlin/Android Keystore; GDScript sees only the opaque UUID |

In React Native, the developer receives the raw auth token and is responsible for storing it securely. The Godot SDK wraps this with an indirection layer: the raw token stays in Kotlin's EncryptedSharedPreferences, and GDScript only handles a UUID that maps to it across the JNI boundary.

---

### Error Handling

| React Native | Godot |
|-------------|-------|
| `try/catch` around `await` calls inside `transact()` | Connect to the `error(code, message)` signal |
| Errors are JS exceptions with type-specific classes | Errors are integer codes (see [Error Constants](api_reference.md#error-constants)) + message string |
| Session errors terminate the `transact()` callback | Session errors emit `error` signal; session may still be active depending on error type |

**React Native:**
```javascript
try {
    await transact(async (wallet) => {
        await wallet.authorize({...});
    });
} catch (e) {
    if (e instanceof Error) {
        console.error(e.message);
    }
}
```

**Godot:**
```gdscript
func _on_error(code: int, message: String) -> void:
    match code:
        MobileWalletAdapter.ERROR_NOT_SIGNED:
            print("User declined signing")
        MobileWalletAdapter.ERROR_WALLET_NOT_FOUND:
            print("No wallet installed")
        _:
            print("Error %d: %s" % [code, message])
```

---

### Identity Configuration

| React Native | Godot |
|-------------|-------|
| Passed per-call as an `identity` object to `authorize()` | Set once on the `MobileWalletAdapter` node properties before calling `transact()` |
| `{ uri: "https://...", name: "App", icon: "icon.png" }` | `identity_uri`, `identity_name`, `identity_icon` properties |
| Chain passed per-call to `authorize()` | `chain` property set on the node |

---

### Capabilities Response

| React Native | Godot |
|-------------|-------|
| Returns a JS object: `{ maxTransactions, maxMessages, supportedTransactionVersions, features }` | Emits `capabilities_received(max_transactions, max_messages, features_json)` signal |
| Access features as `capabilities.features` array | Parse `features_json` or use `MWACapabilities.supports_feature()` |

---

## Feature Parity Status

| Feature | React Native | Godot | Status |
|---------|-------------|-------|--------|
| authorize | Yes | Yes | Complete |
| reauthorize (silent) | Yes | Yes | Complete (pass `token_handle` to `authorize()`) |
| deauthorize | Yes | Yes | Complete |
| getCapabilities | Yes | Yes | Complete |
| signAndSendTransactions | Yes | Yes | Complete |
| signMessages | Yes | Yes | Complete |
| signTransactions | Yes | Yes | Complete |
| cloneAuthorization | Yes | No | Planned |
| Sign In With Solana (SIWS) | Yes | No | Planned |
| Custom wallet URI scheme | Yes | Partial | Wallet URI stored but not configurable pre-session |
