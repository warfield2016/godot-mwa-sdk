#ifndef MWA_CAPABILITIES_HPP
#define MWA_CAPABILITIES_HPP

#include <godot_cpp/classes/ref_counted.hpp>
#include <godot_cpp/variant/packed_string_array.hpp>
#include <godot_cpp/variant/string.hpp>

/// Describes what the connected wallet endpoint supports.
/// Populated from the MWA get_capabilities response.
class MWACapabilities : public godot::RefCounted {
	GDCLASS(MWACapabilities, godot::RefCounted)

public:
	MWACapabilities() = default;

	int64_t get_max_transactions() const;
	void set_max_transactions(int64_t p_value);

	int64_t get_max_messages() const;
	void set_max_messages(int64_t p_value);

	godot::PackedStringArray get_features() const;
	void set_features(const godot::PackedStringArray &p_value);

	bool supports_feature(const godot::String &p_feature) const;
	bool supports_sign_transactions() const;
	bool supports_clone_authorization() const;

protected:
	static void _bind_methods();

private:
	int64_t max_transactions = 0;
	int64_t max_messages = 0;
	godot::PackedStringArray features;
};

#endif // MWA_CAPABILITIES_HPP
