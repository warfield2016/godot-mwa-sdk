#include "mwa_auth_cache.hpp"

#include <godot_cpp/classes/json.hpp>
#include <godot_cpp/classes/marshalls.hpp>
#include <godot_cpp/classes/time.hpp>
#include <godot_cpp/core/class_db.hpp>
#include <godot_cpp/variant/array.hpp>
#include <godot_cpp/variant/dictionary.hpp>
#include <godot_cpp/variant/utility_functions.hpp>

using namespace godot;

MWAAuthCache::MWAAuthCache() = default;
MWAAuthCache::~MWAAuthCache() = default;

String MWAAuthCache::get_token_handle() const { return token_handle; }
void MWAAuthCache::set_token_handle(const String &p_value) { token_handle = p_value; }

PackedByteArray MWAAuthCache::get_public_key() const { return public_key; }
void MWAAuthCache::set_public_key(const PackedByteArray &p_value) { public_key = p_value; }

String MWAAuthCache::get_wallet_uri_base() const { return wallet_uri_base; }
void MWAAuthCache::set_wallet_uri_base(const String &p_value) { wallet_uri_base = p_value; }

String MWAAuthCache::get_wallet_package() const { return wallet_package; }
void MWAAuthCache::set_wallet_package(const String &p_value) { wallet_package = p_value; }

String MWAAuthCache::get_account_label() const { return account_label; }
void MWAAuthCache::set_account_label(const String &p_value) { account_label = p_value; }

String MWAAuthCache::get_chain() const { return chain; }
void MWAAuthCache::set_chain(const String &p_value) { chain = p_value; }

String MWAAuthCache::get_identity_uri_hash() const { return identity_uri_hash; }
void MWAAuthCache::set_identity_uri_hash(const String &p_value) { identity_uri_hash = p_value; }

int64_t MWAAuthCache::get_cached_at() const { return cached_at; }
void MWAAuthCache::set_cached_at(int64_t p_value) { cached_at = p_value; }

void MWAAuthCache::update_from_auth(
		const String &p_token_handle,
		const String &p_accounts_json,
		const String &p_wallet_uri_base,
		const String &p_chain,
		const String &p_identity_uri_hash) {
	clear();
	token_handle = p_token_handle;
	wallet_uri_base = p_wallet_uri_base;
	chain = p_chain;
	identity_uri_hash = p_identity_uri_hash;
	cached_at = Time::get_singleton()->get_unix_time_from_system();

	// MWA returns a JSON array of account objects.
	// Each account has "address" (base64-encoded Ed25519 pubkey) and "label".
	Variant parsed = JSON::parse_string(p_accounts_json);
	if (parsed.get_type() == Variant::ARRAY) {
		Array accounts = parsed;
		if (accounts.size() > 0) {
			Dictionary first = accounts[0];

			if (first.has("label")) {
				account_label = first["label"];
			}

			// MWA encodes the public key as base64.
			if (first.has("address")) {
				String addr_b64 = first["address"];
				PackedByteArray decoded = Marshalls::get_singleton()->base64_to_raw(addr_b64);
				if (decoded.size() == 32) {
					public_key = decoded;
				} else {
					UtilityFunctions::push_warning(
							"MWAAuthCache: unexpected pubkey length ",
							decoded.size(), " from accounts_json");
				}
			}
		}
	} else {
		UtilityFunctions::push_warning(
				"MWAAuthCache: failed to parse accounts_json");
	}
}

void MWAAuthCache::clear() {
	token_handle = String();
	public_key = PackedByteArray();
	wallet_uri_base = String();
	wallet_package = String();
	account_label = String();
	chain = String();
	identity_uri_hash = String();
	cached_at = 0;
}

bool MWAAuthCache::is_valid() const {
	return !token_handle.is_empty();
}

String MWAAuthCache::get_display_address() const {
	if (public_key.size() != 32) {
		return String();
	}

	// Base58 encode the raw 32-byte key for display.
	// Godot does not ship base58, so we use a minimal inline encoder.
	static const char *ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

	// Work on a mutable copy -- base58 encodes by repeated divmod.
	Vector<uint8_t> bytes;
	bytes.resize(public_key.size());
	memcpy(bytes.ptrw(), public_key.ptr(), public_key.size());

	// Leading zero bytes become '1' characters in base58.
	int leading_zeros = 0;
	for (int i = 0; i < bytes.size(); i++) {
		if (bytes[i] == 0) {
			leading_zeros++;
		} else {
			break;
		}
	}

	// Max base58 length for 32-byte input: ceil(32 * log(256)/log(58)) = 44, plus margin.
	Vector<uint8_t> b58;
	b58.resize(48);
	int b58_len = 0;

	for (int i = 0; i < bytes.size(); i++) {
		int carry = bytes[i];
		for (int j = 0; j < b58_len; j++) {
			carry += 256 * b58[j];
			b58.write[j] = (uint8_t)(carry % 58);
			carry /= 58;
		}
		while (carry > 0) {
			ERR_FAIL_COND_V(b58_len >= b58.size(), String());
			b58.write[b58_len++] = (uint8_t)(carry % 58);
			carry /= 58;
		}
	}

	String result;
	for (int i = 0; i < leading_zeros; i++) {
		result += "1";
	}
	for (int i = b58_len - 1; i >= 0; i--) {
		result += String::chr(ALPHABET[(uint8_t)b58[i]]);
	}

	if (result.length() <= 8) {
		return result;
	}
	return result.left(4) + "..." + result.right(4);
}

void MWAAuthCache::_bind_methods() {
	ClassDB::bind_method(D_METHOD("get_token_handle"), &MWAAuthCache::get_token_handle);
	ClassDB::bind_method(D_METHOD("set_token_handle", "value"), &MWAAuthCache::set_token_handle);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "token_handle"), "set_token_handle", "get_token_handle");

	ClassDB::bind_method(D_METHOD("get_public_key"), &MWAAuthCache::get_public_key);
	ClassDB::bind_method(D_METHOD("set_public_key", "value"), &MWAAuthCache::set_public_key);
	ADD_PROPERTY(PropertyInfo(Variant::PACKED_BYTE_ARRAY, "public_key"), "set_public_key", "get_public_key");

	ClassDB::bind_method(D_METHOD("get_wallet_uri_base"), &MWAAuthCache::get_wallet_uri_base);
	ClassDB::bind_method(D_METHOD("set_wallet_uri_base", "value"), &MWAAuthCache::set_wallet_uri_base);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "wallet_uri_base"), "set_wallet_uri_base", "get_wallet_uri_base");

	ClassDB::bind_method(D_METHOD("get_wallet_package"), &MWAAuthCache::get_wallet_package);
	ClassDB::bind_method(D_METHOD("set_wallet_package", "value"), &MWAAuthCache::set_wallet_package);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "wallet_package"), "set_wallet_package", "get_wallet_package");

	ClassDB::bind_method(D_METHOD("get_account_label"), &MWAAuthCache::get_account_label);
	ClassDB::bind_method(D_METHOD("set_account_label", "value"), &MWAAuthCache::set_account_label);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "account_label"), "set_account_label", "get_account_label");

	ClassDB::bind_method(D_METHOD("get_chain"), &MWAAuthCache::get_chain);
	ClassDB::bind_method(D_METHOD("set_chain", "value"), &MWAAuthCache::set_chain);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "chain"), "set_chain", "get_chain");

	ClassDB::bind_method(D_METHOD("get_identity_uri_hash"), &MWAAuthCache::get_identity_uri_hash);
	ClassDB::bind_method(D_METHOD("set_identity_uri_hash", "value"), &MWAAuthCache::set_identity_uri_hash);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "identity_uri_hash"), "set_identity_uri_hash", "get_identity_uri_hash");

	ClassDB::bind_method(D_METHOD("get_cached_at"), &MWAAuthCache::get_cached_at);
	ClassDB::bind_method(D_METHOD("set_cached_at", "value"), &MWAAuthCache::set_cached_at);
	ADD_PROPERTY(PropertyInfo(Variant::INT, "cached_at"), "set_cached_at", "get_cached_at");

	ClassDB::bind_method(D_METHOD("update_from_auth", "token_handle", "accounts_json", "wallet_uri_base", "chain", "identity_uri_hash"), &MWAAuthCache::update_from_auth);
	ClassDB::bind_method(D_METHOD("clear"), &MWAAuthCache::clear);
	ClassDB::bind_method(D_METHOD("is_valid"), &MWAAuthCache::is_valid);
	ClassDB::bind_method(D_METHOD("get_display_address"), &MWAAuthCache::get_display_address);
}
