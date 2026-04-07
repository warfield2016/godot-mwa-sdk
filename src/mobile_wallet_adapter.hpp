#ifndef MOBILE_WALLET_ADAPTER_HPP
#define MOBILE_WALLET_ADAPTER_HPP

#include <godot_cpp/classes/engine.hpp>
#include <godot_cpp/classes/node.hpp>
#include <godot_cpp/classes/object.hpp>
#include <godot_cpp/core/class_db.hpp>
#include <godot_cpp/variant/array.hpp>
#include <godot_cpp/variant/string.hpp>

class MobileWalletAdapter : public godot::Node {
    GDCLASS(MobileWalletAdapter, godot::Node)

public:
    // MWA 2.0 spec errors (negative, from wallet)
    enum {
        ERROR_AUTHORIZATION_FAILED  = -1,
        ERROR_INVALID_PAYLOADS      = -2,
        ERROR_NOT_SIGNED            = -3,
        ERROR_NOT_SUBMITTED         = -4,
        ERROR_NOT_CLONED            = -5,
        ERROR_TOO_MANY_PAYLOADS     = -6,
        ERROR_CHAIN_NOT_SUPPORTED   = -7,
        ERROR_ATTEST_ORIGIN         = -100,
    };

    // Plugin-level errors (positive, from SolanaMWAPlugin.kt)
    enum {
        ERR_CLEARTEXT_BLOCKED       = 1,
        ERR_SESSION_START           = 2,
        ERR_AUTHORIZE               = 3,
        ERR_SIGN_TRANSACTIONS       = 4,
        ERR_SIGN_MESSAGES           = 5,
        ERR_SIGN_AND_SEND           = 6,
        ERR_CAPABILITIES            = 7,
        ERR_DEAUTHORIZE             = 8,
        ERR_NO_SESSION              = 9,
        ERR_WALLET_NOT_FOUND        = 10,
        ERR_TIMEOUT                 = 11,
        ERR_CANCELLED               = 12,
    };

    MobileWalletAdapter();
    ~MobileWalletAdapter();

    void _ready() override;

    void transact();
    void authorize(const godot::String &p_token_handle = godot::String());
    void sign_and_send_transactions(const godot::Array &p_payloads);
    void sign_messages(const godot::Array &p_addresses, const godot::Array &p_payloads);
    void sign_transactions(const godot::Array &p_payloads);
    void get_capabilities();
    void deauthorize(const godot::String &p_token_handle);
    void end_session();
    bool is_available() const;

    void set_identity_uri(const godot::String &p_uri);
    godot::String get_identity_uri() const;

    void set_identity_name(const godot::String &p_name);
    godot::String get_identity_name() const;

    void set_identity_icon(const godot::String &p_icon);
    godot::String get_identity_icon() const;

    void set_chain(const godot::String &p_chain);
    godot::String get_chain() const;

protected:
    static void _bind_methods();

private:
    // Connected to the Kotlin plugin's signals in _ready().
    // Kotlin signal names differ from GDScript-facing names in some cases
    // (e.g. "auth_result" -> "authorized").
    void _on_session_ready();
    void _on_auth_result(const godot::String &p_accounts_json,
                         const godot::String &p_token_handle,
                         const godot::String &p_wallet_uri_base);
    void _on_signing_complete(const godot::String &p_signatures_json);
    void _on_capabilities_result(int64_t p_max_tx, int64_t p_max_msg,
                                 const godot::String &p_features_json);
    void _on_deauth_complete();
    void _on_error(int p_code, const godot::String &p_message);

    /// Convert a GDScript Array of Strings to a JSON array string for
    /// passing across the JNI bridge.
    godot::String _array_to_json(const godot::Array &p_array) const;

    void _emit_not_available();

    godot::Object *android_plugin = nullptr;

    godot::String identity_uri;
    godot::String identity_name;
    godot::String identity_icon;
    godot::String chain;
};

#endif // MOBILE_WALLET_ADAPTER_HPP
