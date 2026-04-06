extends Control
## Displays wallet capabilities reported by the Mobile Wallet Adapter.

const DASHBOARD_SCENE := "res://scenes/wallet_dashboard.tscn"

## Feature URIs we check for, with human-readable labels.
const KNOWN_FEATURES: Dictionary = {
	"solana:signTransactions": "Sign Transactions (without send)",
	"solana:cloneAuthorization": "Clone Authorization",
	"solana:signInWithSolana": "Sign In With Solana (SIWS)",
}

@onready var mwa: MobileWalletAdapter = %MobileWalletAdapter
@onready var max_tx_label: Label = %MaxTransactionsLabel
@onready var max_msg_label: Label = %MaxMessagesLabel
@onready var features_list: RichTextLabel = %FeaturesList
@onready var back_btn: Button = %BackButton
@onready var error_label: Label = %ErrorLabel
@onready var loading_label: Label = %LoadingLabel

var _capabilities_received := false


func _ready() -> void:
	_connect_signals()
	_reset_display()
	_request_capabilities()


func _connect_signals() -> void:
	back_btn.pressed.connect(_on_back_pressed)
	mwa.capabilities_received.connect(_on_capabilities_received)
	mwa.error.connect(_on_error)


func _reset_display() -> void:
	max_tx_label.text = "Max transactions per request: --"
	max_msg_label.text = "Max messages per request: --"
	features_list.text = ""
	error_label.text = ""
	loading_label.text = "Querying wallet capabilities..."
	loading_label.visible = true


func _request_capabilities() -> void:
	mwa.get_capabilities()


func _on_capabilities_received(max_tx: int, max_msg: int, features_json: String) -> void:
	_capabilities_received = true
	loading_label.visible = false

	max_tx_label.text = "Max transactions per request: %d" % max_tx
	max_msg_label.text = "Max messages per request: %d" % max_msg

	# Parse the features array from JSON
	var features: Variant = JSON.parse_string(features_json)
	if features == null or not features is Array:
		features_list.text = "[i]Could not parse features.[/i]"
		return

	var supported_set: Array[String] = []
	for feature: Variant in features:
		supported_set.append(str(feature))

	_build_features_display(supported_set)


func _build_features_display(supported: Array[String]) -> void:
	var display := "[b]Wallet Feature Support:[/b]\n\n"

	# Show known features with check/cross indicators
	for uri: String in KNOWN_FEATURES:
		var label: String = KNOWN_FEATURES[uri]
		var is_supported := uri in supported
		if is_supported:
			display += "[color=green][b]+[/b][/color]  %s\n" % label
		else:
			display += "[color=red][b]x[/b][/color]  %s\n" % label

	# Show any additional features the wallet reports that we do not track
	var extra_features: Array[String] = []
	for uri: String in supported:
		if not KNOWN_FEATURES.has(uri):
			extra_features.append(uri)

	if extra_features.size() > 0:
		display += "\n[b]Additional features:[/b]\n"
		for uri: String in extra_features:
			display += "[color=green][b]+[/b][/color]  [code]%s[/code]\n" % uri

	features_list.text = display


func _on_error(code: int, message: String) -> void:
	loading_label.visible = false
	error_label.text = "Error %d: %s" % [code, message]

	if not _capabilities_received:
		features_list.text = "[i]Failed to retrieve capabilities.[/i]"


func _on_back_pressed() -> void:
	get_tree().change_scene_to_file(DASHBOARD_SCENE)
