#ifndef MWA_AUTH_CACHE_HPP
#define MWA_AUTH_CACHE_HPP

#include <godot_cpp/classes/resource.hpp>
#include <godot_cpp/variant/packed_byte_array.hpp>
#include <godot_cpp/variant/string.hpp>

/// Stores non-sensitive auth metadata. The actual auth token lives in
/// Kotlin's EncryptedSharedPreferences -- GDScript only sees a UUID
/// token_handle that looks up the real token in the Android Keystore.
class MWAAuthCache : public godot::Resource {
	GDCLASS(MWAAuthCache, godot::Resource)

public:
	MWAAuthCache();
	~MWAAuthCache();

	godot::String get_token_handle() const;
	void set_token_handle(const godot::String &p_value);

	godot::PackedByteArray get_public_key() const;
	void set_public_key(const godot::PackedByteArray &p_value);

	godot::String get_wallet_uri_base() const;
	void set_wallet_uri_base(const godot::String &p_value);

	godot::String get_wallet_package() const;
	void set_wallet_package(const godot::String &p_value);

	godot::String get_account_label() const;
	void set_account_label(const godot::String &p_value);

	godot::String get_chain() const;
	void set_chain(const godot::String &p_value);

	godot::String get_identity_uri_hash() const;
	void set_identity_uri_hash(const godot::String &p_value);

	int64_t get_cached_at() const;
	void set_cached_at(int64_t p_value);

	/// Populate from an authorize / reauthorize response.
	/// accounts_json is the JSON array string of account objects from MWA.
	void update_from_auth(
			const godot::String &p_token_handle,
			const godot::String &p_accounts_json,
			const godot::String &p_wallet_uri_base,
			const godot::String &p_chain,
			const godot::String &p_identity_uri_hash);

	void clear();
	bool is_valid() const;

	/// Short display form: first 4 chars + "..." + last 4 chars of base58 key.
	godot::String get_display_address() const;

protected:
	static void _bind_methods();

private:
	godot::String token_handle;
	godot::PackedByteArray public_key;
	godot::String wallet_uri_base;
	godot::String wallet_package;
	godot::String account_label;
	godot::String chain;
	godot::String identity_uri_hash;
	int64_t cached_at = 0;
};

#endif // MWA_AUTH_CACHE_HPP
