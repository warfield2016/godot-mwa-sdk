#ifndef MWA_AUTH_RESULT_HPP
#define MWA_AUTH_RESULT_HPP

#include <godot_cpp/classes/ref_counted.hpp>
#include <godot_cpp/variant/string.hpp>

/// DTO returned from authorize / reauthorize calls.
/// token_handle is a UUID that maps to the real auth token stored in
/// Kotlin's EncryptedSharedPreferences -- the raw token never crosses
/// the JNI boundary into C++/GDScript.
class MWAAuthResult : public godot::RefCounted {
	GDCLASS(MWAAuthResult, godot::RefCounted)

public:
	MWAAuthResult() = default;

	godot::String get_token_handle() const;
	void set_token_handle(const godot::String &p_value);

	godot::String get_accounts_json() const;
	void set_accounts_json(const godot::String &p_value);

	godot::String get_wallet_uri_base() const;
	void set_wallet_uri_base(const godot::String &p_value);

protected:
	static void _bind_methods();

private:
	godot::String token_handle;
	godot::String accounts_json;
	godot::String wallet_uri_base;
};

#endif // MWA_AUTH_RESULT_HPP
