# Quickstart: Connect & Sign a Message

This guide walks you through connecting to a Solana wallet and signing a message in under 5 minutes. It assumes you have already completed the [Installation Guide](installation.md).

---

## Step 1: Add the MobileWalletAdapter Node

1. Open your scene in the Godot editor.
2. Add a new node of type **MobileWalletAdapter** to your scene tree.
3. Name it `MobileWalletAdapter` (or use a unique name and reference it with `%`).

Your scene tree should look something like:

```
MainScene (Control)
  MobileWalletAdapter
  ConnectButton (Button)
  StatusLabel (Label)
```

---

## Step 2: Configure Identity Properties

Select the `MobileWalletAdapter` node in the inspector and set:

| Property | Value | Description |
|----------|-------|-------------|
| `identity_uri` | `https://yourgame.com` | Your app's HTTPS URI. Wallets display this to the user. |
| `identity_name` | `My Godot Game` | Display name shown in the wallet's authorization prompt. |
| `identity_icon` | `favicon.ico` | Relative path to an icon (from identity_uri). Optional. |
| `chain` | `solana:devnet` | Target chain. Use `solana:mainnet` for production. |

You can also set these in code:

```gdscript
mwa.identity_uri = "https://yourgame.com"
mwa.identity_name = "My Godot Game"
mwa.identity_icon = "favicon.ico"
mwa.chain = "solana:devnet"
```

---

## Step 3: Connect Signals

The MWA plugin is signal-driven. The core flow is:

1. `transact()` opens a session -- emits `session_ready`
2. `authorize()` requests wallet approval -- emits `authorized`
3. `sign_messages()` signs data -- emits `signing_complete`
4. Errors at any step emit `error`

---

## Step 4: Complete Example

Attach this script to your main scene node:

```gdscript
extends Control

@onready var mwa: MobileWalletAdapter = $MobileWalletAdapter
@onready var connect_btn: Button = $ConnectButton
@onready var status_label: Label = $StatusLabel

var _token_handle: String


func _ready() -> void:
    connect_btn.pressed.connect(_on_connect)
    mwa.session_ready.connect(_on_session_ready)
    mwa.authorized.connect(_on_authorized)
    mwa.signing_complete.connect(_on_signed)
    mwa.error.connect(_on_error)

    if not mwa.is_available():
        status_label.text = "MWA requires Android"
        connect_btn.disabled = true


func _on_connect() -> void:
    status_label.text = "Connecting..."
    connect_btn.disabled = true
    mwa.transact()


func _on_session_ready() -> void:
    mwa.authorize()


func _on_authorized(accounts_json: String, token_handle: String,
        wallet_uri_base: String) -> void:
    _token_handle = token_handle
    status_label.text = "Authorized! Signing message..."

    # Build a message to sign
    var msg := "Hello from Godot MWA!"
    var msg_b64 := Marshalls.raw_to_base64(msg.to_utf8_buffer())

    # Extract the address from accounts_json
    var accounts: Array = JSON.parse_string(accounts_json)
    var address_b64: String = accounts[0]["address"]

    mwa.sign_messages([address_b64], [msg_b64])


func _on_signed(signatures_json: String) -> void:
    var sigs: Array = JSON.parse_string(signatures_json)
    status_label.text = "Signed! Signature: %s" % str(sigs[0]).left(16)
    connect_btn.disabled = false


func _on_error(code: int, message: String) -> void:
    status_label.text = "Error %d: %s" % [code, message]
    connect_btn.disabled = false
```

---

## What Happens at Runtime

1. The user taps **Connect**.
2. Android switches to the wallet app (Phantom, Solflare, etc.) showing an authorization prompt with your app's identity.
3. The user approves. Control returns to your Godot game.
4. The plugin immediately sends a sign request for the message.
5. The wallet prompts the user again to approve signing.
6. The signed result (base64-encoded Ed25519 signature) is returned via `signing_complete`.

The entire round-trip involves two wallet prompts: one for authorization, one for signing.

---

## Next Steps

- **Persist sessions** using `MWAAuthCache` -- see the [API Reference](api_reference.md#mwaauthcache) and the `example/scripts/main_menu.gd` in the repository for a full caching pattern.
- **Sign transactions** instead of messages using `sign_and_send_transactions()`.
- **Query wallet capabilities** with `get_capabilities()` to check signing limits.
- **Disconnect cleanly** with `deauthorize(token_handle)` followed by `disconnect()`.
