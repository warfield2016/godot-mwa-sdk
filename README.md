# Godot Mobile Wallet Adapter SDK

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Godot 4.3](https://img.shields.io/badge/Godot-4.3-blue)
![Android](https://img.shields.io/badge/Platform-Android-green)

Standalone Godot 4.3 GDExtension Android plugin for the Solana Mobile Wallet Adapter (MWA) 2.0 protocol. API parity with the official React Native SDK.

## Features

- Full MWA 2.0 API: authorize, sign transactions, sign messages, capabilities
- Native Android wallet connection (Phantom, Solflare, Backpack)
- Encrypted auth token cache (Android Keystore-backed)
- Silent reconnection via cached tokens
- Signal-based async API

## API

| MWA Method | GDScript |
|-----------|----------|
| authorize | `authorize(token_handle)` |
| deauthorize | `deauthorize(token_handle)` |
| get_capabilities | `get_capabilities()` |
| sign_and_send_transactions | `sign_and_send_transactions(payloads)` |
| sign_messages | `sign_messages(addresses, payloads)` |
| sign_transactions | `sign_transactions(payloads)` |

## Quick Start

```gdscript
var mwa = MobileWalletAdapter.new()
mwa.identity_name = "My Game"
mwa.chain = "solana:devnet"

mwa.session_ready.connect(func(): mwa.authorize())
mwa.authorized.connect(func(accts, handle, uri): print("Connected: ", accts))
mwa.error.connect(func(code, msg): print("Error: ", code, " ", msg))

mwa.transact()
```

## Building from Source

### Prerequisites

- [Godot 4.3+](https://godotengine.org/)
- [Android SDK](https://developer.android.com/studio) (compileSdk 34, NDK 23.2.8568313)
- JDK 17
- [SCons](https://scons.org/) (`pip install scons`)

### Clone

```bash
git clone --recursive https://github.com/user/godot-mwa-sdk.git
cd godot-mwa-sdk
```

### Build C++ GDExtension

```bash
export ANDROID_NDK_ROOT=$ANDROID_HOME/ndk/23.2.8568313
cd godot-cpp && scons platform=android target=template_debug arch=arm64 -j$(nproc) && cd ..
scons platform=android arch=arm64 target=template_debug -j$(nproc)
```

### Build Android AAR

```bash
cd android && ./gradlew assembleDebug && cd ..
```

### Install in Godot Project

Copy `addons/SolanaMWA/` into your Godot project's `addons/` folder. Enable the plugin in Project Settings > Plugins.

### Network Security Config

Your game's `AndroidManifest.xml` must allow loopback cleartext for MWA's WebSocket:

```xml
<application android:networkSecurityConfig="@xml/network_security_config">
```

The template config is included in the AAR at `res/xml/network_security_config.xml`.

## Platform Support

| Platform | Status |
|----------|--------|
| Android arm64 | Full MWA 2.0 support |
| iOS | Not supported (OS restriction) |
| Desktop/Web | Stubs return error signals |

## Requirements

- Godot 4.3+
- Android minSdk 24, targetSdk 34
- MWA-compatible wallet (Phantom, Solflare, Backpack)

## Documentation

- [Installation](docs/installation.md)
- [Quick Start](docs/quickstart.md)
- [API Reference](docs/api_reference.md)
- [Auth Caching](docs/auth_caching.md)
- [Architecture](docs/architecture.md)
- [React Native Parity](docs/react_native_parity.md)
- [Troubleshooting](docs/troubleshooting.md)

## License

MIT -- see [LICENSE](LICENSE)

## Dependencies

- [mobile-wallet-adapter-clientlib-ktx](https://github.com/solana-mobile/mobile-wallet-adapter) 2.0.8
- [godot-cpp](https://github.com/godotengine/godot-cpp) 4.3-stable
- [androidx.security:security-crypto](https://developer.android.com/jetpack/androidx/releases/security) 1.1.0
