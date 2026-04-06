extends Control
## Main menu with wallet connection via Mobile Wallet Adapter.

const CACHE_PATH := "user://mwa_cache.tres"
const DASHBOARD_SCENE := "res://scenes/wallet_dashboard.tscn"

@onready var mwa: MobileWalletAdapter = %MobileWalletAdapter
@onready var connect_btn: Button = %ConnectButton
@onready var status_label: Label = %StatusLabel
@onready var error_label: Label = %ErrorLabel

var cache: MWAAuthCache


func _ready() -> void:
	_load_cache()
	_connect_signals()

	if not mwa.is_available():
		status_label.text = "MWA requires Android"
		connect_btn.disabled = true
		return

	if cache.is_valid():
		status_label.text = "Cached session found. Tap Connect to resume."
	else:
		status_label.text = "Disconnected"


func _load_cache() -> void:
	if ResourceLoader.exists(CACHE_PATH):
		cache = ResourceLoader.load(CACHE_PATH) as MWAAuthCache
	if cache == null:
		cache = MWAAuthCache.new()


func _save_cache() -> void:
	ResourceSaver.save(cache, CACHE_PATH)


func _connect_signals() -> void:
	connect_btn.pressed.connect(_on_connect_pressed)
	mwa.session_ready.connect(_on_session_ready)
	mwa.authorized.connect(_on_authorized)
	mwa.error.connect(_on_error)


func _on_connect_pressed() -> void:
	error_label.text = ""
	status_label.text = "Connecting..."
	connect_btn.disabled = true
	mwa.transact()


func _on_session_ready() -> void:
	if cache.is_valid():
		# Silent reauthorization with cached token
		mwa.authorize(cache.token_handle)
	else:
		# Fresh authorization
		mwa.authorize()


func _on_authorized(accounts_json: String, token_handle: String, wallet_uri_base: String) -> void:
	cache.update_from_auth(token_handle, accounts_json, wallet_uri_base, mwa.chain, "")
	_save_cache()
	status_label.text = "Connected: %s" % cache.get_display_address()
	get_tree().change_scene_to_file(DASHBOARD_SCENE)


func _on_error(code: int, message: String) -> void:
	status_label.text = "Disconnected"
	error_label.text = "Error %d: %s" % [code, message]
	connect_btn.disabled = false
