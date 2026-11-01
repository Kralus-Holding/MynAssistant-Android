package com.mynkralus.mynassistant

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class MynPcRelayDevice(
    val id: String,
    val name: String,
    val platform: String,
    val status: String,
    val connectionMode: String,
    val lastSeenAtMillis: Long?
)

internal object MynAssistantPcRelayClient {
    private const val BASE_URL = "https://mynassistant-server.onrender.com"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val COMMAND_POLL_INTERVAL_MS = 1_000L
    private const val COMMAND_TIMEOUT_MS = 65_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun listWindowsDevices(
        user: FirebaseUser,
        callback: (List<MynPcRelayDevice>?, String?) -> Unit
    ) {
        Thread({
            try {
                val response = request(user, "GET", "/api/v1/pc/devices")
                val items = response.optJSONArray("items") ?: JSONArray()
                val devices = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        val timestamp = item.optString("lastSeenAt")
                        val lastSeen = runCatching {
                            java.time.Instant.parse(timestamp).toEpochMilli()
                        }.getOrNull()
                        add(
                            MynPcRelayDevice(
                                id = id,
                                name = item.optString("name").ifBlank { "Windows bilgisayar" },
                                platform = item.optString("platform").ifBlank { "Windows" },
                                status = item.optString("status").ifBlank { "offline" },
                                connectionMode = item.optString("connectionMode").ifBlank { "firebase-agent" },
                                lastSeenAtMillis = lastSeen
                            )
                        )
                    }
                }
                mainHandler.post { callback(devices, null) }
            } catch (error: Exception) {
                mainHandler.post { callback(null, safeMessage(error)) }
            }
        }, "MynPcRelay-Devices").apply { isDaemon = true }.start()
    }

    fun enqueueCommand(
        user: FirebaseUser,
        deviceId: String,
        command: String,
        payload: Map<String, Any>,
        callback: (String?, String?, String?) -> Unit
    ) {
        Thread({
            try {
                val body = JSONObject()
                    .put("command", command)
                    .put("payload", JSONObject(payload))
                val response = request(
                    user,
                    "POST",
                    "/api/v1/pc/devices/${encodePath(deviceId)}/commands",
                    body
                )
                val id = response.optString("id").takeIf { it.isNotBlank() }
                    ?: throw IOException("Sunucu komut kimliği döndürmedi.")
                val message = response.optString(
                    "message",
                    "Komut MynAssistant Server üzerinden Windows bilgisayarına gönderildi."
                )
                mainHandler.post { callback(message, id, null) }
            } catch (error: Exception) {
                mainHandler.post { callback(null, null, safeMessage(error)) }
            }
        }, "MynPcRelay-Enqueue").apply { isDaemon = true }.start()
    }

    fun observeCommand(
        user: FirebaseUser,
        commandId: String,
        callback: (JSONObject?, String?) -> Unit
    ): ListenerRegistration {
        val cancelled = AtomicBoolean(false)
        val deliveredTerminal = AtomicBoolean(false)
        Thread({
            val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
            var consecutiveFailures = 0
            while (!cancelled.get() && System.currentTimeMillis() < deadline) {
                try {
                    val response = request(
                        user,
                        "GET",
                        "/api/v1/pc/commands/${encodePath(commandId)}"
                    )
                    consecutiveFailures = 0
                    val status = response.optString("status").lowercase()
                    mainHandler.post {
                        if (!cancelled.get()) callback(response, null)
                    }
                    if (status in setOf("completed", "failed", "cancelled")) {
                        deliveredTerminal.set(true)
                        return@Thread
                    }
                } catch (error: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        val message = safeMessage(error)
                        mainHandler.post {
                            if (!cancelled.get()) callback(null, message)
                        }
                        return@Thread
                    }
                }
                try {
                    Thread.sleep(COMMAND_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
            }
            if (!cancelled.get() && !deliveredTerminal.get()) {
                mainHandler.post {
                    if (!cancelled.get()) {
                        callback(null, "Sunucudan 65 saniye içinde son komut durumu alınamadı. Cihaz bağlantısını kontrol et.")
                    }
                }
            }
        }, "MynPcRelay-Observe").apply { isDaemon = true }.start()

        return ListenerRegistration { cancelled.set(true) }
    }

    private fun request(
        user: FirebaseUser,
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject {
        val token = getFreshIdToken(user)
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = try {
                if (status in 200..299) connection.inputStream else connection.errorStream
            } catch (_: Exception) {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
            if (status !in 200..299) {
                val detail = response.optString("detail").ifBlank {
                    response.optString("message")
                }
                throw IOException(
                    when (status) {
                        401 -> "Oturum doğrulanamadı. MynAssistant hesabından çıkış yapıp yeniden giriş yap."
                        403 -> "Bu Windows cihazına erişim iznin yok."
                        404 -> "İstenen Windows cihazı veya komutu sunucuda bulunamadı."
                        409 -> detail.ifBlank { "Windows bağlantı durumu değişti. Cihazı yenileyip tekrar dene." }
                        413 -> "İşlem verisi sunucu boyut sınırını aşıyor."
                        429 -> "Sunucu istek sınırına ulaştı. Biraz bekleyip yeniden dene."
                        in 500..599 -> detail.ifBlank { "MynAssistant Server geçici olarak yanıt veremiyor (HTTP $status)." }
                        else -> detail.ifBlank { "Sunucu isteği reddetti (HTTP $status)." }
                    }
                )
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun getFreshIdToken(user: FirebaseUser): String {
        val latch = CountDownLatch(1)
        var token: String? = null
        var failure: Exception? = null
        user.getIdToken(false)
            .addOnSuccessListener { result ->
                token = result.token
                latch.countDown()
            }
            .addOnFailureListener { error ->
                failure = error
                latch.countDown()
            }
        if (!latch.await(15, TimeUnit.SECONDS))
            throw java.net.SocketTimeoutException("Firebase oturum doğrulaması zaman aşımına uğradı.")
        failure?.let { throw IOException("Firebase oturumu doğrulanamadı.", it) }
        return token?.takeIf { it.isNotBlank() }
            ?: throw IOException("Firebase oturum belirteci alınamadı.")
    }

    private fun safeMessage(error: Exception): String = when (error) {
        is java.net.UnknownHostException -> "MynAssistant Server'a ulaşılamadı. İnternet bağlantını kontrol et."
        is java.net.SocketTimeoutException -> "MynAssistant Server yanıtı zaman aşımına uğradı."
        else -> error.message?.take(240).orEmpty().ifBlank { "MynAssistant Server isteği tamamlanamadı." }
    }

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
