#include "mwa_capabilities.hpp"

#include <godot_cpp/core/class_db.hpp>

using namespace godot;

int64_t MWACapabilities::get_max_transactions() const { return max_transactions; }
void MWACapabilities::set_max_transactions(int64_t p_value) { max_transactions = p_value; }

int64_t MWACapabilities::get_max_messages() const { return max_messages; }
void MWACapabilities::set_max_messages(int64_t p_value) { max_messages = p_value; }

PackedStringArray MWACapabilities::get_features() const { return features; }
void MWACapabilities::set_features(const PackedStringArray &p_value) { features = p_value; }

bool MWACapabilities::supports_feature(const String &p_feature) const {
	return features.has(p_feature);
}

bool MWACapabilities::supports_sign_transactions() const {
	return supports_feature("solana:signTransactions");
}

bool MWACapabilities::supports_clone_authorization() const {
	return supports_feature("solana:cloneAuthorization");
}

void MWACapabilities::_bind_methods() {
	ClassDB::bind_method(D_METHOD("get_max_transactions"), &MWACapabilities::get_max_transactions);
	ClassDB::bind_method(D_METHOD("set_max_transactions", "value"), &MWACapabilities::set_max_transactions);
	ADD_PROPERTY(PropertyInfo(Variant::INT, "max_transactions"), "set_max_transactions", "get_max_transactions");

	ClassDB::bind_method(D_METHOD("get_max_messages"), &MWACapabilities::get_max_messages);
	ClassDB::bind_method(D_METHOD("set_max_messages", "value"), &MWACapabilities::set_max_messages);
	ADD_PROPERTY(PropertyInfo(Variant::INT, "max_messages"), "set_max_messages", "get_max_messages");

	ClassDB::bind_method(D_METHOD("get_features"), &MWACapabilities::get_features);
	ClassDB::bind_method(D_METHOD("set_features", "value"), &MWACapabilities::set_features);
	ADD_PROPERTY(PropertyInfo(Variant::PACKED_STRING_ARRAY, "features"), "set_features", "get_features");

	ClassDB::bind_method(D_METHOD("supports_feature", "feature"), &MWACapabilities::supports_feature);
	ClassDB::bind_method(D_METHOD("supports_sign_transactions"), &MWACapabilities::supports_sign_transactions);
	ClassDB::bind_method(D_METHOD("supports_clone_authorization"), &MWACapabilities::supports_clone_authorization);
}
