package com.solanamwa.godot

import android.net.Uri
import android.os.Build
import android.security.NetworkSecurityPolicy
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray
import org.json.JSONObject
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Godot 4 plugin bridging the Solana Mobile Wallet Adapter protocol. */
class SolanaMWAPlugin(godot: Godot) : GodotPlugin(godot) {

    companion object {
        private const val TAG = "SolanaMWA"
        private const val CLIENT_TIMEOUT_MS = 90_000L

        const val ERR_CLEARTEXT_BLOCKED = 1
        const val ERR_SESSION_START = 2
        const val ERR_AUTHORIZE = 3
        const val ERR_SIGN_TRANSACTIONS = 4
        const val ERR_SIGN_MESSAGES = 5
        const val ERR_SIGN_AND_SEND = 6
        const val ERR_CAPABILITIES = 7
        const val ERR_DEAUTHORIZE = 8
        const val ERR_NO_SESSION = 9
        const val ERR_WALLET_NOT_FOUND = 10
        const val ERR_TIMEOUT = 11
        const val ERR_CANCELLED = 12
    }

    override fun getPluginName(): String = "SolanaMWA"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
        emitErrorOnUiThread(ERR_SESSION_START, throwable.message ?: "Unknown coroutine error")
    }

    private val pluginScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + exceptionHandler
    )

    private val sessionManager = MWASessionManager()
    private var tokenStore: MWATokenStore? = null

    private var identityUri: Uri? = null
    private var identityName: String? = null
    private var iconUri: Uri? = null
    private var chain: String? = null

    override fun getPluginSignals(): Set<SignalInfo> = setOf(
        SignalInfo("session_ready"),
        SignalInfo("auth_result", String::class.java, String::class.java, String::class.java),
        SignalInfo("signing_complete", String::class.java),
        SignalInfo("capabilities_result", Int::class.java, Int::class.java, String::class.java),
        SignalInfo("deauth_complete"),
        SignalInfo("error", Int::class.java, String::class.java),
    )

    // =========================================================================
    // @UsedByGodot public API
    // =========================================================================

    /**
     * Starts a local association session with the wallet.
     * Launches a solana-wallet:// Intent so the wallet can connect.
     */
    @UsedByGodot
    fun startSession(
        identityUri: String,
        identityName: String,
        identityIcon: String,
        chain: String,
    ) {
        pluginScope.launch {
            try {
                // MWA uses cleartext HTTP to localhost; blocked on Android 6+ without network_security_config
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val allowed = NetworkSecurityPolicy.getInstance()
                        .isCleartextTrafficPermitted("127.0.0.1")
                    if (!allowed) {
                        emitErrorOnUiThread(
                            ERR_CLEARTEXT_BLOCKED,
                            "Cleartext traffic to 127.0.0.1 is blocked. " +
                                "Add a network_security_config allowing localhost."
                        )
                        return@launch
                    }
                }

                this@SolanaMWAPlugin.identityUri = Uri.parse(identityUri)
                this@SolanaMWAPlugin.identityName = identityName
                this@SolanaMWAPlugin.iconUri = Uri.parse(identityIcon)
                this@SolanaMWAPlugin.chain = chain

                val activity = activity
                    ?: throw IllegalStateException("Godot Activity is null")
                if (tokenStore == null) {
                    tokenStore = MWATokenStore(activity.applicationContext)
                }

                sessionManager.endSession()

                // Start the scenario async; the Intent below lets the wallet connect to its WebSocket
                val clientDeferred = kotlinx.coroutines.async(Dispatchers.IO) {
                    sessionManager.startSession()
                }

                kotlinx.coroutines.delay(100)  // Let the scenario bind its WebSocket port

                val port = sessionManager.getPort()
                val associationPublicKey = sessionManager.getAssociationPublicKey()

                val associationIntent =
                    LocalAssociationIntentCreator.createAssociationIntent(
                        null, port, associationPublicKey
                    )

                val resolved = associationIntent.resolveActivity(activity.packageManager)
                if (resolved == null) {
                    sessionManager.endSession()
                    throw IllegalStateException(
                        "No wallet app found that handles solana-wallet:// URIs"
                    )
                }

                activity.startActivity(associationIntent)

                clientDeferred.await()

                emitOnUiThread("session_ready")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "startSession failed", e)
                val code = if (e.message?.contains("No wallet app") == true)
                    ERR_WALLET_NOT_FOUND else ERR_SESSION_START
                emitErrorOnUiThread(code, e.message ?: "Session start failed")
            }
        }
    }

    /**
     * Authorize (or re-authorize) with the connected wallet.
     * Stores the raw auth_token encrypted; only a UUID handle crosses the JNI bridge.
     *
     * @param authTokenHandle UUID handle from a prior authorize(), or empty string for first-time auth.
     */
    @UsedByGodot
    fun authorize(authTokenHandle: String) {
        pluginScope.launch {
            try {
                val client = requireClient() ?: return@launch

                val authToken: String? = if (authTokenHandle.isNotEmpty()) {
                    val identityHash = hashIdentityUri()
                    tokenStore?.retrieveToken(authTokenHandle, identityHash)
                } else {
                    null
                }

                val result = withContext(Dispatchers.IO) {
                    withTimeout(CLIENT_TIMEOUT_MS) {
                        if (authToken != null) {
                            client.authorize(
                                identityUri,
                                iconUri,
                                identityName,
                                chain,
                                authToken,
                                null, // cluster (deprecated)
                                null, // sign_in_payload
                                null  // features
                            ).get()
                        } else {
                            client.authorize(
                                identityUri,
                                iconUri,
                                identityName,
                                chain,
                                null, null, null, null
                            ).get()
                        }
                    }
                }

                val accountsJson = MWAResultMapper.accountsToJson(result.accounts)

                val rawToken = result.authToken ?: ""
                val tokenHandle = if (rawToken.isNotEmpty()) {
                    val store = tokenStore
                    if (store == null) {
                        emitErrorOnUiThread(ERR_AUTHORIZE, "Token store unavailable")
                        return@launch
                    }
                    store.storeToken(rawToken, hashIdentityUri())
                } else {
                    ""
                }

                val walletUriBase = result.walletUriBase?.toString() ?: ""

                emitOnUiThread("auth_result", accountsJson, tokenHandle, walletUriBase)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "authorize failed", e)
                emitErrorOnUiThread(ERR_AUTHORIZE, e.message ?: "Authorization failed")
            }
        }
    }

    /**
     * Sign transactions and submit them to the cluster via the wallet.
     *
     * @param payloadsJson JSON array of base64-encoded serialized transactions.
     * @param optionsJson  JSON object with optional `min_context_slot` (Long).
     */
    @UsedByGodot
    fun signAndSendTransactions(payloadsJson: String, optionsJson: String) {
        pluginScope.launch {
            try {
                val client = requireClient() ?: return@launch

                val payloads = MWAResultMapper.payloadsFromJson(payloadsJson)
                val minContextSlot = parseMinContextSlot(optionsJson)

                val result = withContext(Dispatchers.IO) {
                    withTimeout(CLIENT_TIMEOUT_MS) {
                        client.signAndSendTransactions(payloads, minContextSlot?.toInt()).get()
                    }
                }

                val signaturesJson = MWAResultMapper.signaturesToJson(result.signatures)

                emitOnUiThread("signing_complete", signaturesJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "signAndSendTransactions failed", e)
                emitErrorOnUiThread(ERR_SIGN_AND_SEND, e.message ?: "Sign-and-send failed")
            }
        }
    }

    /**
     * Sign arbitrary messages (off-chain).
     *
     * @param addressesJson JSON array of base64-encoded signer public keys.
     * @param payloadsJson  JSON array of base64-encoded messages to sign.
     */
    @UsedByGodot
    fun signMessages(addressesJson: String, payloadsJson: String) {
        pluginScope.launch {
            try {
                val client = requireClient() ?: return@launch

                val addresses = MWAResultMapper.payloadsFromJson(addressesJson)
                val payloads = MWAResultMapper.payloadsFromJson(payloadsJson)

                val result = withContext(Dispatchers.IO) {
                    withTimeout(CLIENT_TIMEOUT_MS) {
                        client.signMessages(payloads, addresses).get()
                    }
                }

                val signaturesJson = MWAResultMapper.signaturesToJson(result.signedPayloads)

                emitOnUiThread("signing_complete", signaturesJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "signMessages failed", e)
                emitErrorOnUiThread(ERR_SIGN_MESSAGES, e.message ?: "Sign messages failed")
            }
        }
    }

    /**
     * Sign transactions without sending (for offline / partial-sign flows).
     *
     * @param payloadsJson JSON array of base64-encoded serialized transactions.
     */
    @UsedByGodot
    fun signTransactions(payloadsJson: String) {
        pluginScope.launch {
            try {
                val client = requireClient() ?: return@launch

                val payloads = MWAResultMapper.payloadsFromJson(payloadsJson)

                val result = withContext(Dispatchers.IO) {
                    withTimeout(CLIENT_TIMEOUT_MS) {
                        client.signTransactions(payloads).get()
                    }
                }

                val signaturesJson = MWAResultMapper.signaturesToJson(result.signedPayloads)

                emitOnUiThread("signing_complete", signaturesJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "signTransactions failed", e)
                emitErrorOnUiThread(ERR_SIGN_TRANSACTIONS, e.message ?: "Sign transactions failed")
            }
        }
    }

    /** Query wallet capabilities (supported transaction versions, features, etc.). */
    @UsedByGodot
    fun getCapabilities() {
        pluginScope.launch {
            try {
                val client = requireClient() ?: return@launch

                val result = withContext(Dispatchers.IO) {
                    withTimeout(CLIENT_TIMEOUT_MS) {
                        client.getCapabilities().get()
                    }
                }

                val maxTx = result.maxTransactionsPerSigningRequest
                val maxMsg = result.maxMessagesPerSigningRequest
                val featuresJson = JSONArray().apply {
                    result.supportedFeatures?.forEach { put(it) }
                }.toString()

                emitOnUiThread("capabilities_result", maxTx, maxMsg, featuresJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "getCapabilities failed", e)
                emitErrorOnUiThread(ERR_CAPABILITIES, e.message ?: "Get capabilities failed")
            }
        }
    }

    @UsedByGodot
    fun deauthorize(authTokenHandle: String) {
        pluginScope.launch {
            try {
                val client = requireClient() ?: return@launch

                val identityHash = hashIdentityUri()
                val store = tokenStore
                val authToken = if (store != null) {
                    store.retrieveToken(authTokenHandle, identityHash) ?: run {
                        emitErrorOnUiThread(ERR_DEAUTHORIZE, "Token not found for handle")
                        return@launch
                    }
                } else {
                    emitErrorOnUiThread(ERR_DEAUTHORIZE, "Token store unavailable")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    withTimeout(CLIENT_TIMEOUT_MS) {
                        client.deauthorize(authToken).get()
                    }
                }

                store.clearToken(authTokenHandle)

                emitOnUiThread("deauth_complete")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "deauthorize failed", e)
                emitErrorOnUiThread(ERR_DEAUTHORIZE, e.message ?: "Deauthorize failed")
            }
        }
    }

    /** End the MWA session and release resources. Safe to call multiple times. */
    @UsedByGodot
    fun endSession() {
        pluginScope.launch {
            try {
                sessionManager.endSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "endSession cleanup error (non-fatal)", e)
            }
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onMainDestroy() {
        // End session synchronously to ensure cleanup completes before scope dies
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            try {
                sessionManager.endSession()
            } catch (_: Exception) {}
        }
        pluginScope.cancel("Plugin destroyed")
        super.onMainDestroy()
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** Returns the active MWA client, or null. Emits [ERR_NO_SESSION] if no session is active. */
    private fun requireClient(): com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient? {
        val client = sessionManager.getClient()
        if (client == null) {
            Log.w(TAG, "requireClient(): No active MWA session")
            emitErrorOnUiThread(ERR_NO_SESSION, "No active MWA session. Call startSession() first.")
            return null
        }
        return client
    }

    private fun parseMinContextSlot(optionsJson: String): Long? {
        if (optionsJson.isBlank()) return null
        return try {
            val obj = JSONObject(optionsJson)
            if (obj.has("min_context_slot")) obj.getLong("min_context_slot") else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse options JSON, ignoring: $optionsJson", e)
            null
        }
    }

    /** SHA-256 hex digest of the identity URI, used for token binding in MWATokenStore. */
    private fun hashIdentityUri(): String {
        val uriStr = identityUri?.toString() ?: ""
        val digest = MessageDigest.getInstance("SHA-256").digest(uriStr.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Godot's emitSignal() must be called on the UI thread (JNI requirement).

    private fun emitOnUiThread(signalName: String, vararg args: Any) {
        activity?.runOnUiThread {
            emitSignal(signalName, *args)
        } ?: Log.w(TAG, "Cannot emit '$signalName': activity is null")
    }

    private fun emitErrorOnUiThread(code: Int, message: String) {
        Log.e(TAG, "Error [$code]: $message")
        emitOnUiThread("error", code, message)
    }
}
