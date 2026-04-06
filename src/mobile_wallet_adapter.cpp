#include "mobile_wallet_adapter.hpp"

#include <godot_cpp/classes/engine.hpp>
#include <godot_cpp/classes/json.hpp>
#include <godot_cpp/core/class_db.hpp>
#include <godot_cpp/variant/callable.hpp>
#include <godot_cpp/variant/utility_functions.hpp>

using namespace godot;

MobileWalletAdapter::MobileWalletAdapter() {
    chain = "solana:mainnet";
}

MobileWalletAdapter::~MobileWalletAdapter() {
    android_plugin = nullptr;
}

void MobileWalletAdapter::_ready() {
    Engine *engine = Engine::get_singleton();
    if (!engine) {
        return;
    }

    if (engine->has_singleton("SolanaMWA")) {
        android_plugin = engine->get_singleton("SolanaMWA");

        auto safe_connect = [&](const String &signal, const String &method) {
            Callable cb(this, method);
            if (!android_plugin->is_connected(signal, cb)) {
                android_plugin->connect(signal, cb);
            }
        };

        safe_connect("session_ready", "_on_session_ready");
        safe_connect("auth_result", "_on_auth_result");
        safe_connect("signing_complete", "_on_signing_complete");
        safe_connect("capabilities_result", "_on_capabilities_result");
        safe_connect("deauth_complete", "_on_deauth_complete");
        safe_connect("error", "_on_error");
    }
}

// Public API

void MobileWalletAdapter::transact() {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    android_plugin->call("startSession", identity_uri, identity_name,
                         identity_icon, chain);
}

void MobileWalletAdapter::authorize(const String &p_token_handle) {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    android_plugin->call("authorize", p_token_handle);
}

void MobileWalletAdapter::sign_and_send_transactions(const Array &p_payloads) {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    String payloads_json = _array_to_json(p_payloads);
    // Options JSON is empty -- the Kotlin side handles the default.
    android_plugin->call("signAndSendTransactions", payloads_json, String());
}

void MobileWalletAdapter::sign_messages(const Array &p_addresses,
                                         const Array &p_payloads) {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    String addresses_json = _array_to_json(p_addresses);
    String payloads_json = _array_to_json(p_payloads);
    android_plugin->call("signMessages", addresses_json, payloads_json);
}

void MobileWalletAdapter::sign_transactions(const Array &p_payloads) {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    String payloads_json = _array_to_json(p_payloads);
    android_plugin->call("signTransactions", payloads_json);
}

void MobileWalletAdapter::get_capabilities() {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    android_plugin->call("getCapabilities");
}

void MobileWalletAdapter::deauthorize(const String &p_token_handle) {
    if (!android_plugin) {
        _emit_not_available();
        return;
    }
    android_plugin->call("deauthorize", p_token_handle);
}

void MobileWalletAdapter::end_session() {
    if (!android_plugin) {
        return;
    }
    android_plugin->call("endSession");
}

bool MobileWalletAdapter::is_available() const {
    return android_plugin != nullptr;
}

// Signal forwarding callbacks

void MobileWalletAdapter::_on_session_ready() {
    emit_signal("session_ready");
}

void MobileWalletAdapter::_on_auth_result(const String &p_accounts_json,
                                           const String &p_token_handle,
                                           const String &p_wallet_uri_base) {
    emit_signal("authorized", p_accounts_json, p_token_handle,
                p_wallet_uri_base);
}

void MobileWalletAdapter::_on_signing_complete(
        const String &p_signatures_json) {
    emit_signal("signing_complete", p_signatures_json);
}

void MobileWalletAdapter::_on_capabilities_result(
        int64_t p_max_tx, int64_t p_max_msg, const String &p_features_json) {
    emit_signal("capabilities_received", p_max_tx, p_max_msg,
                p_features_json);
}

void MobileWalletAdapter::_on_deauth_complete() {
    emit_signal("deauthorized");
}

void MobileWalletAdapter::_on_error(int p_code, const String &p_message) {
    emit_signal("error", p_code, p_message);
}

// Property accessors

void MobileWalletAdapter::set_identity_uri(const String &p_uri) {
    identity_uri = p_uri;
}

String MobileWalletAdapter::get_identity_uri() const {
    return identity_uri;
}

void MobileWalletAdapter::set_identity_name(const String &p_name) {
    identity_name = p_name;
}

String MobileWalletAdapter::get_identity_name() const {
    return identity_name;
}

void MobileWalletAdapter::set_identity_icon(const String &p_icon) {
    identity_icon = p_icon;
}

String MobileWalletAdapter::get_identity_icon() const {
    return identity_icon;
}

void MobileWalletAdapter::set_chain(const String &p_chain) {
    chain = p_chain;
}

String MobileWalletAdapter::get_chain() const {
    return chain;
}

String MobileWalletAdapter::_array_to_json(const Array &p_array) const {
    return JSON::stringify(p_array);
}

void MobileWalletAdapter::_emit_not_available() {
    emit_signal("error", ERROR_NO_ACTIVE_SESSION,
                String("MWA not available on this platform"));
}

void MobileWalletAdapter::_bind_methods() {
    ADD_SIGNAL(MethodInfo("session_ready"));

    ADD_SIGNAL(MethodInfo("authorized",
        PropertyInfo(Variant::STRING, "accounts_json"),
        PropertyInfo(Variant::STRING, "token_handle"),
        PropertyInfo(Variant::STRING, "wallet_uri_base")));

    ADD_SIGNAL(MethodInfo("signing_complete",
        PropertyInfo(Variant::STRING, "signatures_json")));

    ADD_SIGNAL(MethodInfo("capabilities_received",
        PropertyInfo(Variant::INT, "max_transactions"),
        PropertyInfo(Variant::INT, "max_messages"),
        PropertyInfo(Variant::STRING, "features_json")));

    ADD_SIGNAL(MethodInfo("deauthorized"));

    ADD_SIGNAL(MethodInfo("error",
        PropertyInfo(Variant::INT, "code"),
        PropertyInfo(Variant::STRING, "message")));

    // Public methods
    ClassDB::bind_method(D_METHOD("transact"), &MobileWalletAdapter::transact);
    ClassDB::bind_method(D_METHOD("authorize", "token_handle"),
        &MobileWalletAdapter::authorize, DEFVAL(String()));
    ClassDB::bind_method(D_METHOD("sign_and_send_transactions", "payloads"),
        &MobileWalletAdapter::sign_and_send_transactions);
    ClassDB::bind_method(D_METHOD("sign_messages", "addresses", "payloads"),
        &MobileWalletAdapter::sign_messages);
    ClassDB::bind_method(D_METHOD("sign_transactions", "payloads"),
        &MobileWalletAdapter::sign_transactions);
    ClassDB::bind_method(D_METHOD("get_capabilities"),
        &MobileWalletAdapter::get_capabilities);
    ClassDB::bind_method(D_METHOD("deauthorize", "token_handle"),
        &MobileWalletAdapter::deauthorize);
    ClassDB::bind_method(D_METHOD("end_session"),
        &MobileWalletAdapter::end_session);
    ClassDB::bind_method(D_METHOD("is_available"),
        &MobileWalletAdapter::is_available);

    // Internal signal-forwarding callbacks
    ClassDB::bind_method(D_METHOD("_on_session_ready"),
        &MobileWalletAdapter::_on_session_ready);
    ClassDB::bind_method(D_METHOD("_on_auth_result",
            "accounts_json", "token_handle", "wallet_uri_base"),
        &MobileWalletAdapter::_on_auth_result);
    ClassDB::bind_method(D_METHOD("_on_signing_complete", "signatures_json"),
        &MobileWalletAdapter::_on_signing_complete);
    ClassDB::bind_method(D_METHOD("_on_capabilities_result",
            "max_tx", "max_msg", "features_json"),
        &MobileWalletAdapter::_on_capabilities_result);
    ClassDB::bind_method(D_METHOD("_on_deauth_complete"),
        &MobileWalletAdapter::_on_deauth_complete);
    ClassDB::bind_method(D_METHOD("_on_error", "code", "message"),
        &MobileWalletAdapter::_on_error);

    // Properties
    ClassDB::bind_method(D_METHOD("set_identity_uri", "uri"),
        &MobileWalletAdapter::set_identity_uri);
    ClassDB::bind_method(D_METHOD("get_identity_uri"),
        &MobileWalletAdapter::get_identity_uri);
    ADD_PROPERTY(PropertyInfo(Variant::STRING, "identity_uri"),
        "set_identity_uri", "get_identity_uri");

    ClassDB::bind_method(D_METHOD("set_identity_name", "name"),
        &MobileWalletAdapter::set_identity_name);
    ClassDB::bind_method(D_METHOD("get_identity_name"),
        &MobileWalletAdapter::get_identity_name);
    ADD_PROPERTY(PropertyInfo(Variant::STRING, "identity_name"),
        "set_identity_name", "get_identity_name");

    ClassDB::bind_method(D_METHOD("set_identity_icon", "icon"),
        &MobileWalletAdapter::set_identity_icon);
    ClassDB::bind_method(D_METHOD("get_identity_icon"),
        &MobileWalletAdapter::get_identity_icon);
    ADD_PROPERTY(PropertyInfo(Variant::STRING, "identity_icon"),
        "set_identity_icon", "get_identity_icon");

    ClassDB::bind_method(D_METHOD("set_chain", "chain"),
        &MobileWalletAdapter::set_chain);
    ClassDB::bind_method(D_METHOD("get_chain"),
        &MobileWalletAdapter::get_chain);
    ADD_PROPERTY(PropertyInfo(Variant::STRING, "chain"),
        "set_chain", "get_chain");

    // Error code constants
    BIND_CONSTANT(ERROR_AUTHORIZATION_FAILED);
    BIND_CONSTANT(ERROR_INVALID_PAYLOADS);
    BIND_CONSTANT(ERROR_NOT_SIGNED);
    BIND_CONSTANT(ERROR_NOT_SUBMITTED);
    BIND_CONSTANT(ERROR_NOT_CLONED);
    BIND_CONSTANT(ERROR_TOO_MANY_PAYLOADS);
    BIND_CONSTANT(ERROR_CHAIN_NOT_SUPPORTED);
    BIND_CONSTANT(ERROR_WALLET_NOT_FOUND);
    BIND_CONSTANT(ERROR_SESSION_TIMEOUT);
    BIND_CONSTANT(ERROR_SESSION_CLOSED);
    BIND_CONSTANT(ERROR_CLEARTEXT_NOT_PERMITTED);
    BIND_CONSTANT(ERROR_NO_ACTIVE_SESSION);
}
