package com.solanamwa.godot

import android.util.Log
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Manages LocalAssociationScenario lifecycle. Thread-safe via Mutex. */
class MWASessionManager {

    companion object {
        private const val TAG = "MWASessionManager"
        private const val FUTURE_TIMEOUT_MS = 90_000L
    }

    private val mutex = Mutex()

    @Volatile private var scenario: LocalAssociationScenario? = null
    @Volatile private var client: MobileWalletAdapterClient? = null

    val isActive: Boolean
        get() = scenario != null && client != null

    /**
     * Creates a LocalAssociationScenario, starts it, and awaits the wallet connection.
     * Caller should retrieve [getPort] and [getAssociationPublicKey] afterward for the Intent URI.
     */
    suspend fun startSession(timeoutMs: Int = 90_000): MobileWalletAdapterClient =
        mutex.withLock {
            if (isActive) {
                throw IllegalStateException("A session is already active. Call endSession() first.")
            }

            client = null
            scenario = null

            val newScenario = LocalAssociationScenario(timeoutMs)

            val connectedClient = withContext(Dispatchers.IO) {
                try {
                    newScenario.start().get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    Log.e(TAG, "Wallet connection timed out after ${timeoutMs}ms", e)
                    silentClose(newScenario)
                    throw e
                } catch (e: ExecutionException) {
                    Log.e(TAG, "Wallet connection failed", e.cause ?: e)
                    silentClose(newScenario)
                    throw SessionStartException("Failed to connect to wallet", e.cause ?: e)
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Wallet connection interrupted", e)
                    silentClose(newScenario)
                    Thread.currentThread().interrupt()
                    throw SessionStartException("Wallet connection was interrupted", e)
                }
            }

            scenario = newScenario
            client = connectedClient

            Log.d(TAG, "Session started on port ${newScenario.port}")
            connectedClient
        }

    /** Local WebSocket port for the solana-wallet:// Intent URI. */
    fun getPort(): Int {
        val s = scenario
            ?: throw IllegalStateException("No active scenario. Call startSession() first.")
        return s.port
    }

    /** ECDH association public key for the Intent URI's encrypted channel. */
    fun getAssociationPublicKey(): ByteArray {
        val s = scenario
            ?: throw IllegalStateException("No active scenario. Call startSession() first.")
        return s.associationPublicKey
    }

    /** Gracefully closes the current session. No-op if no session is active. */
    suspend fun endSession(): Unit = mutex.withLock {
        val currentScenario = scenario ?: return@withLock

        withContext(Dispatchers.IO) {
            try {
                currentScenario.close().get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                Log.d(TAG, "Session closed gracefully")
            } catch (e: TimeoutException) {
                Log.w(TAG, "Session close timed out", e)
            } catch (e: ExecutionException) {
                Log.w(TAG, "Session close encountered an error", e.cause ?: e)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Session close was interrupted", e)
                Thread.currentThread().interrupt()
            }
        }

        client = null
        scenario = null
    }

    fun getClient(): MobileWalletAdapterClient? = client

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Best-effort close for error recovery. Failures are logged, not propagated. */
    private fun silentClose(target: LocalAssociationScenario) {
        try {
            target.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Silent close failed", t)
        }
    }

    class SessionStartException(message: String, cause: Throwable) :
        RuntimeException(message, cause)
}
