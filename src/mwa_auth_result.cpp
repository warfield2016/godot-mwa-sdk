#include "mwa_auth_result.hpp"

#include <godot_cpp/core/class_db.hpp>

using namespace godot;

String MWAAuthResult::get_token_handle() const { return token_handle; }
void MWAAuthResult::set_token_handle(const String &p_value) { token_handle = p_value; }

String MWAAuthResult::get_accounts_json() const { return accounts_json; }
void MWAAuthResult::set_accounts_json(const String &p_value) { accounts_json = p_value; }

String MWAAuthResult::get_wallet_uri_base() const { return wallet_uri_base; }
void MWAAuthResult::set_wallet_uri_base(const String &p_value) { wallet_uri_base = p_value; }

void MWAAuthResult::_bind_methods() {
	ClassDB::bind_method(D_METHOD("get_token_handle"), &MWAAuthResult::get_token_handle);
	ClassDB::bind_method(D_METHOD("set_token_handle", "value"), &MWAAuthResult::set_token_handle);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "token_handle"), "set_token_handle", "get_token_handle");

	ClassDB::bind_method(D_METHOD("get_accounts_json"), &MWAAuthResult::get_accounts_json);
	ClassDB::bind_method(D_METHOD("set_accounts_json", "value"), &MWAAuthResult::set_accounts_json);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "accounts_json"), "set_accounts_json", "get_accounts_json");

	ClassDB::bind_method(D_METHOD("get_wallet_uri_base"), &MWAAuthResult::get_wallet_uri_base);
	ClassDB::bind_method(D_METHOD("set_wallet_uri_base", "value"), &MWAAuthResult::set_wallet_uri_base);
	ADD_PROPERTY(PropertyInfo(Variant::STRING, "wallet_uri_base"), "set_wallet_uri_base", "get_wallet_uri_base");
}
