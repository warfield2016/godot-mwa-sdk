package com.solanamwa.godot

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps MWA protocol results to Godot-friendly formats and provides base58/base64 conversion.
 * MWA 2.0 spec error codes are negative; adapter-level codes are positive.
 */
object MWAResultMapper {

    // MWA 2.0 spec error codes (negative)
    const val ERROR_AUTHORIZATION_FAILED   = -1
    const val ERROR_INVALID_PAYLOADS       = -2
    const val ERROR_NOT_SIGNED             = -3
    const val ERROR_NOT_SUBMITTED          = -4
    const val ERROR_NOT_CLONED             = -5
    const val ERROR_TOO_MANY_PAYLOADS      = -6
    const val ERROR_CHAIN_NOT_SUPPORTED    = -7
    const val ERROR_ATTEST_ORIGIN_ANDROID  = -100

    // Adapter-level error codes (positive)
    const val ERROR_WALLET_NOT_FOUND       = 1
    const val ERROR_SESSION_TIMEOUT        = 2
    const val ERROR_SESSION_CLOSED         = 3
    const val ERROR_CLEARTEXT_NOT_PERMITTED = 4
    const val ERROR_NO_ACTIVE_SESSION      = 5

    // Base58 alphabet (Bitcoin variant)
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE58_INDEXES = IntArray(128) { -1 }.also { table ->
        for (i in BASE58_ALPHABET.indices) {
            table[BASE58_ALPHABET[i].code] = i
        }
    }

    /** Maps a JSON-RPC error code to a normalized (code, description) pair. */
    fun mapJsonRpcError(code: Int, message: String?): Pair<Int, String> {
        val desc = errorDescription(code)
        val suffix = if (!message.isNullOrBlank()) " ($message)" else ""
        return Pair(code, "$desc$suffix")
    }

    /**
     * Converts MWA Account objects to JSON: [{"address": "<base58>", "label": "<optional>"}].
     * Uses reflection for forward-compatibility with clientlib Account class changes.
     */
    fun accountsToJson(accounts: Array<*>): String {
        val arr = JSONArray()
        for (account in accounts) {
            if (account == null) continue
            val obj = JSONObject()
            try {
                // clientlib 2.0.8: Account.publicKey (ByteArray), Account.accountLabel (String?)
                val publicKeyField = account.javaClass.getDeclaredField("publicKey")
                publicKeyField.isAccessible = true
                val addressBytes = publicKeyField.get(account) as? ByteArray
                obj.put("address", if (addressBytes != null) encodeBase58(addressBytes) else "")

                val labelField = try {
                    account.javaClass.getDeclaredField("accountLabel").also { it.isAccessible = true }
                } catch (_: NoSuchFieldException) { null }
                val label = labelField?.get(account) as? String
                if (label != null) {
                    obj.put("label", label)
                }
            } catch (e: Exception) {
                obj.put("address", "")
                obj.put("error", e.message ?: "unknown")
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    /** Converts raw signature byte arrays to a JSON array of base58 strings. */
    fun signaturesToJson(signatures: Array<ByteArray>): String {
        val arr = JSONArray()
        for (sig in signatures) {
            arr.put(encodeBase58(sig))
        }
        return arr.toString()
    }

    /** Parses a JSON array of base64-encoded strings into byte arrays (for transaction payloads). */
    fun payloadsFromJson(json: String): Array<ByteArray> {
        val arr = JSONArray(json)
        return Array(arr.length()) { i ->
            val encoded = arr.getString(i)
            Base64.decode(encoded, Base64.NO_WRAP)
        }
    }

    /** Parses a JSON array of base58-encoded Solana addresses into byte arrays. */
    fun addressesFromJson(json: String): Array<ByteArray> {
        val arr = JSONArray(json)
        return Array(arr.length()) { i ->
            val encoded = arr.getString(i)
            decodeBase58(encoded)
        }
    }

    fun errorDescription(code: Int): String = when (code) {
        ERROR_AUTHORIZATION_FAILED   -> "Authorization failed or was declined by the wallet"
        ERROR_INVALID_PAYLOADS       -> "One or more payloads are invalid"
        ERROR_NOT_SIGNED             -> "Wallet declined to sign the transaction(s)"
        ERROR_NOT_SUBMITTED          -> "Wallet declined to submit the transaction(s)"
        ERROR_NOT_CLONED             -> "Wallet declined the clone authorization request"
        ERROR_TOO_MANY_PAYLOADS      -> "Too many payloads in a single request"
        ERROR_CHAIN_NOT_SUPPORTED    -> "The requested chain/cluster is not supported by this wallet"
        ERROR_ATTEST_ORIGIN_ANDROID  -> "Origin attestation failed (Android app identity)"
        ERROR_WALLET_NOT_FOUND       -> "No compatible MWA wallet found on this device"
        ERROR_SESSION_TIMEOUT        -> "MWA session timed out waiting for wallet response"
        ERROR_SESSION_CLOSED         -> "MWA session was closed unexpectedly"
        ERROR_CLEARTEXT_NOT_PERMITTED -> "Cleartext (HTTP) traffic not permitted by network security policy"
        ERROR_NO_ACTIVE_SESSION      -> "No active MWA session; call authorize() first"
        else -> "Unknown MWA error (code=$code)"
    }

    // -- Base58 codec (Bitcoin alphabet, no external dependency) --

    fun encodeBase58(input: ByteArray): String {
        if (input.isEmpty()) return ""

        // Defensive copy -- divmod mutates the array in place
        val bytes = input.copyOf()

        var leadingZeros = 0
        for (b in bytes) {
            if (b.toInt() == 0) leadingZeros++ else break
        }

        val encoded = CharArray(bytes.size * 2)
        var outputStart = encoded.size
        var inputStart = leadingZeros

        while (inputStart < bytes.size) {
            val remainder = divmod(bytes, inputStart, 256, 58)
            if (bytes[inputStart].toInt() == 0) inputStart++
            encoded[--outputStart] = BASE58_ALPHABET[remainder]
        }

        while (outputStart < encoded.size && encoded[outputStart] == BASE58_ALPHABET[0]) {
            outputStart++
        }
        repeat(leadingZeros) {
            encoded[--outputStart] = BASE58_ALPHABET[0]
        }

        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decodeBase58(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        val input58 = IntArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) BASE58_INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid Base58 character '$c' at index $i" }
            input58[i] = digit
        }

        var leadingZeros = 0
        for (d in input58) {
            if (d == 0) leadingZeros++ else break
        }

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = leadingZeros

        while (inputStart < input58.size) {
            val remainder = divmod58(input58, inputStart, 58, 256)
            if (input58[inputStart] == 0) inputStart++
            decoded[--outputStart] = remainder.toByte()
        }

        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
            outputStart++
        }

        val result = ByteArray(leadingZeros + (decoded.size - outputStart))
        System.arraycopy(decoded, outputStart, result, leadingZeros, decoded.size - outputStart)
        return result
    }

    private fun divmod(bytes: ByteArray, startAt: Int, fromBase: Int, toBase: Int): Int {
        var remainder = 0
        for (i in startAt until bytes.size) {
            val digit = bytes[i].toInt() and 0xFF
            val temp = remainder * fromBase + digit
            bytes[i] = (temp / toBase).toByte()
            remainder = temp % toBase
        }
        return remainder
    }

    private fun divmod58(digits: IntArray, startAt: Int, fromBase: Int, toBase: Int): Int {
        var remainder = 0
        for (i in startAt until digits.size) {
            val digit = digits[i]
            val temp = remainder * fromBase + digit
            digits[i] = temp / toBase
            remainder = temp % toBase
        }
        return remainder
    }
}
