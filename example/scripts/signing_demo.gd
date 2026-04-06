extends Control
## Demonstrates message signing and transaction signing via Mobile Wallet Adapter.

const CACHE_PATH := "user://mwa_cache.tres"
const DASHBOARD_SCENE := "res://scenes/wallet_dashboard.tscn"

@onready var mwa: MobileWalletAdapter = %MobileWalletAdapter
@onready var sign_msg_btn: Button = %SignMessageButton
@onready var sign_tx_btn: Button = %SignTransactionButton
@onready var back_btn: Button = %BackButton
@onready var result_label: RichTextLabel = %ResultLabel
@onready var error_label: Label = %ErrorLabel

var cache: MWAAuthCache


func _ready() -> void:
	_load_cache()
	_connect_signals()
	_reset_display()


func _load_cache() -> void:
	if ResourceLoader.exists(CACHE_PATH):
		cache = ResourceLoader.load(CACHE_PATH) as MWAAuthCache
	if cache == null:
		cache = MWAAuthCache.new()
		push_warning("SigningDemo: No cached auth found; signing will likely fail.")


func _connect_signals() -> void:
	sign_msg_btn.pressed.connect(_on_sign_message_pressed)
	sign_tx_btn.pressed.connect(_on_sign_transaction_pressed)
	back_btn.pressed.connect(_on_back_pressed)
	mwa.signing_complete.connect(_on_signing_complete)
	mwa.error.connect(_on_error)


func _reset_display() -> void:
	result_label.text = "Awaiting signature..."
	error_label.text = ""


func _set_buttons_disabled(disabled: bool) -> void:
	sign_msg_btn.disabled = disabled
	sign_tx_btn.disabled = disabled


# -- Message signing --------------------------------------------------------

func _on_sign_message_pressed() -> void:
	error_label.text = ""
	_set_buttons_disabled(true)
	result_label.text = "Requesting message signature..."

	var message_text := "Hello from Godot MWA!"
	var message_bytes := message_text.to_utf8_buffer()
	var message_b64 := Marshalls.raw_to_base64(message_bytes)

	var address_b64 := Marshalls.raw_to_base64(cache.public_key)
	if address_b64.is_empty():
		_on_error(-1, "No wallet address cached. Authorize first.")
		return

	mwa.sign_messages([address_b64], [message_b64])


# -- Transaction signing ----------------------------------------------------

func _on_sign_transaction_pressed() -> void:
	error_label.text = ""
	_set_buttons_disabled(true)
	result_label.text = "Requesting transaction signature..."

	var tx_bytes := _build_dummy_transfer()
	var tx_b64 := Marshalls.raw_to_base64(tx_bytes)

	mwa.sign_and_send_transactions([tx_b64])


func _build_dummy_transfer() -> PackedByteArray:
	# Wire format (v0 legacy message) -- 0-lamport self-transfer with placeholder blockhash.
	# [1] num_sigs, [64] sig slot, [1] required_sigs, [1] ro_signed, [1] ro_unsigned,
	# [1] num_accounts, [32] sender, [32] system_program, [32] blockhash,
	# [1] num_ix, [1] prog_idx, [1] num_acct_idx, [1] sender_idx, [1] recip_idx,
	# [1] data_len, [4] discriminator (2=Transfer LE), [8] lamports (0 LE)

	var buf := PackedByteArray()

	# Signature count + placeholder signature
	buf.append(1)
	buf.resize(buf.size() + 64)  # 64 zero bytes for signature slot

	# Message header
	buf.append(1)  # num_required_signatures
	buf.append(0)  # num_readonly_signed
	buf.append(1)  # num_readonly_unsigned (system program)

	# Account keys
	buf.append(2)  # num_accounts

	# Sender pubkey (decode from cache)
	var sender_bytes := cache.public_key
	if sender_bytes.size() != 32:
		# Pad to 32 bytes
		sender_bytes.resize(32)
	buf.append_array(sender_bytes)

	# System program (all zeroes = 11111111111111111111111111111111)
	var system_program := PackedByteArray()
	system_program.resize(32)
	buf.append_array(system_program)

	# Recent blockhash (placeholder -- 32 zero bytes)
	var blockhash := PackedByteArray()
	blockhash.resize(32)
	buf.append_array(blockhash)

	# Instructions
	buf.append(1)  # num_instructions

	# Transfer instruction
	buf.append(1)  # program_id_index (system program at index 1)
	buf.append(2)  # num_account_indices
	buf.append(0)  # sender (index 0)
	buf.append(0)  # recipient = sender (index 0, transfer to self)

	# Instruction data: Transfer discriminator (2u32 LE) + 0 lamports (u64 LE)
	buf.append(12) # data_len
	var data := PackedByteArray()
	data.resize(12)
	data.encode_u32(0, 2)   # Transfer instruction discriminator
	data.encode_u64(4, 0)   # 0 lamports
	buf.append_array(data)

	return buf


# -- Signal handlers --------------------------------------------------------

func _on_signing_complete(signatures_json: String) -> void:
	_set_buttons_disabled(false)

	var signatures: Variant = JSON.parse_string(signatures_json)
	if signatures == null or not signatures is Array:
		result_label.text = "Signed, but could not parse response:\n%s" % signatures_json
		return

	var display := "[b]Signature(s) received:[/b]\n\n"
	for i: int in signatures.size():
		var sig: String = str(signatures[i])
		# Truncate long signatures for readability
		var short_sig := sig.left(16) + "..." + sig.right(8) if sig.length() > 32 else sig
		display += "[code]%d: %s[/code]\n" % [i + 1, short_sig]

	result_label.text = display


func _on_error(code: int, message: String) -> void:
	_set_buttons_disabled(false)
	error_label.text = "Error %d: %s" % [code, message]
	result_label.text = "Awaiting signature..."


func _on_back_pressed() -> void:
	get_tree().change_scene_to_file(DASHBOARD_SCENE)
