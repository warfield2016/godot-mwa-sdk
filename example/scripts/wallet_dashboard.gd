extends Control
## Wallet dashboard shown after successful connection.

const CACHE_PATH := "user://mwa_cache.tres"
const MAIN_MENU_SCENE := "res://scenes/main_menu.tscn"
const SIGN_MESSAGE_SCENE := "res://scenes/sign_message.tscn"
const CAPABILITIES_SCENE := "res://scenes/capabilities.tscn"

@onready var mwa: MobileWalletAdapter = %MobileWalletAdapter
@onready var address_label: Label = %AddressLabel
@onready var chain_label: Label = %ChainLabel
@onready var cache_status_label: Label = %CacheStatusLabel
@onready var error_label: Label = %ErrorLabel
@onready var disconnect_btn: Button = %DisconnectButton
@onready var sign_btn: Button = %SignMessageButton
@onready var capabilities_btn: Button = %CapabilitiesButton

var cache: MWAAuthCache


func _ready() -> void:
	_load_cache()
	_connect_signals()
	_update_display()


func _load_cache() -> void:
	if ResourceLoader.exists(CACHE_PATH):
		cache = ResourceLoader.load(CACHE_PATH) as MWAAuthCache
	if cache == null:
		cache = MWAAuthCache.new()


func _save_cache() -> void:
	ResourceSaver.save(cache, CACHE_PATH)


func _connect_signals() -> void:
	disconnect_btn.pressed.connect(_on_disconnect_pressed)
	sign_btn.pressed.connect(_on_sign_message_pressed)
	capabilities_btn.pressed.connect(_on_capabilities_pressed)
	mwa.deauthorized.connect(_on_deauthorized)
	mwa.error.connect(_on_error)


func _update_display() -> void:
	address_label.text = cache.get_display_address()
	chain_label.text = cache.chain if cache.chain else "Unknown"
	cache_status_label.text = "Valid" if cache.is_valid() else "Expired"
	error_label.text = ""


func _on_disconnect_pressed() -> void:
	error_label.text = ""
	disconnect_btn.disabled = true
	mwa.deauthorize(cache.token_handle)


func _on_sign_message_pressed() -> void:
	get_tree().change_scene_to_file(SIGN_MESSAGE_SCENE)


func _on_capabilities_pressed() -> void:
	get_tree().change_scene_to_file(CAPABILITIES_SCENE)


func _on_deauthorized() -> void:
	cache.clear()
	_save_cache()
	get_tree().change_scene_to_file(MAIN_MENU_SCENE)


func _on_error(code: int, message: String) -> void:
	error_label.text = "Error %d: %s" % [code, message]
	disconnect_btn.disabled = false
