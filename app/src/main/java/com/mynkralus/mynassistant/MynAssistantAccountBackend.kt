package com.mynkralus.mynassistant

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseUser
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Calls the free Render backend for Brevo transactional email and signed Cloudinary uploads.
 * No Brevo API key or Cloudinary API secret is shipped in the Android APK.
 */
internal object MynAssistantAccountBackend {
    private const val BASE_URL = "https://mynassistant-server.onrender.com"
    private const val MAX_PROFILE_IMAGE_BYTES = 5L * 1024L * 1024L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendDiagnosticReport(
        user: FirebaseUser,
        report: JSONObject,
        callback: (Boolean, String) -> Unit
    ) {
        withIdToken(user, callback) { token ->
            postJson(
                path = "/api/v1/diagnostics/report",
                idToken = token,
                body = report
            )
        }
    }

    fun sendEmailVerification(user: FirebaseUser, callback: (Boolean, String) -> Unit) {
        withIdToken(user, callback) { token ->
            postJson(
                path = "/api/v1/account/email/verification",
                idToken = token,
                body = JSONObject()
            )
        }
    }

    fun changeEmailAddress(
        user: FirebaseUser,
        newEmail: String,
        callback: (Boolean, String) -> Unit
    ) {
        withIdToken(user, callback) { token ->
            postJson(
                path = "/api/v1/account/email/change",
                idToken = token,
                body = JSONObject().put("newEmail", newEmail.trim())
            )
        }
    }

    fun uploadProfilePhoto(
        context: Context,
        user: FirebaseUser,
        sourceUri: Uri,
        callback: (String?, String?) -> Unit
    ) {
        user.getIdToken(true).addOnCompleteListener { tokenTask ->
            if (!tokenTask.isSuccessful) {
                callback(null, "Firebase oturumu doğrulanamadı. İnternet bağlantını kontrol edip yeniden giriş yap.")
                return@addOnCompleteListener
            }
            val idToken = tokenTask.result?.token
            if (idToken.isNullOrBlank()) {
                callback(null, "Firebase oturum belirteci alınamadı. Yeniden giriş yap.")
                return@addOnCompleteListener
            }

            Thread({
                var connection: HttpURLConnection? = null
                try {
                    val knownLength = try {
                        context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { it.length }
                            ?: -1L
                    } catch (_: Exception) {
                        -1L
                    }
                    if (knownLength >= MAX_PROFILE_IMAGE_BYTES) {
                        throw IOException("Kırpılmış fotoğraf 5 MB sınırına ulaştı. Daha küçük bir fotoğraf seç.")
                    }

                    val signatureResponse = postJsonBlocking(
                        path = "/api/v1/account/profile-photo/sign",
                        idToken = idToken,
                        body = JSONObject()
                    )
                    val cloudName = signatureResponse.optString("cloudName")
                    val apiKey = signatureResponse.optString("apiKey")
                    val timestamp = signatureResponse.optLong("timestamp", 0L)
                    val publicId = signatureResponse.optString("publicId")
                    val signature = signatureResponse.optString("signature")
                    val overwrite = signatureResponse.optBoolean("overwrite", true)

                    if (cloudName.isBlank() || apiKey.isBlank() || timestamp <= 0L ||
                        publicId.isBlank() || signature.isBlank()) {
                        throw IOException("Cloudinary yükleme imzası eksik. Sunucu ayarlarını kontrol et.")
                    }

                    val boundary = "MynAssistant-${UUID.randomUUID()}"
                    val uploadConnection = (URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 60_000
                        doOutput = true
                        useCaches = false
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    }
                    connection = uploadConnection

                    uploadConnection.outputStream.use { output ->
                        fun writeField(name: String, value: String) {
                            val text = "--$boundary\r\n" +
                                "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                                value + "\r\n"
                            output.write(text.toByteArray(StandardCharsets.UTF_8))
                        }

                        writeField("api_key", apiKey)
                        writeField("timestamp", timestamp.toString())
                        writeField("public_id", publicId)
                        writeField("overwrite", overwrite.toString().lowercase())
                        writeField("signature", signature)
                        output.write(
                            ("--$boundary\r\n" +
                                "Content-Disposition: form-data; name=\"file\"; filename=\"profile.jpg\"\r\n" +
                                "Content-Type: image/jpeg\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
                        )

                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            copyWithLimit(input, output, MAX_PROFILE_IMAGE_BYTES)
                        } ?: throw IOException("Kırpılmış fotoğraf dosyası açılamadı.")
                        output.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
                    }

                    val statusCode = uploadConnection.responseCode
                    val responseBody = readResponse(uploadConnection, statusCode)
                    if (statusCode !in 200..299) {
                        val errorJson = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                        val detail = errorJson.optString("error")
                        throw IOException(
                            when (statusCode) {
                                401, 403 -> "Cloudinary yükleme imzası reddedildi. Render gizli anahtarlarını kontrol et."
                                413 -> "Fotoğraf yükleme sınırını aştı. Daha küçük bir fotoğraf seç."
                                420, 429 -> "Cloudinary ücretsiz kullanım sınırına ulaşıldı. Daha sonra tekrar dene."
                                else -> "Cloudinary fotoğrafı kabul etmedi (HTTP $statusCode).${if (detail.isNotBlank()) " $detail" else ""}"
                            }
                        )
                    }

                    val uploadJson = JSONObject(responseBody)
                    val secureUrl = uploadJson.optString("secure_url")
                    val parsedUrl = try { URL(secureUrl) } catch (_: Exception) { null }
                    if (parsedUrl?.protocol != "https" || parsedUrl.host != "res.cloudinary.com") {
                        throw IOException("Cloudinary güvenli fotoğraf bağlantısı döndürmedi.")
                    }
                    mainHandler.post { callback(secureUrl, null) }
                } catch (error: Exception) {
                    val message = error.message?.take(220).orEmpty()
                        .ifBlank { "Profil fotoğrafı yüklenemedi. İnternetini ve ücretsiz servis ayarlarını kontrol et." }
                    mainHandler.post { callback(null, message) }
                } finally {
                    connection?.disconnect()
                }
            }, "MynAccountMediaUpload").apply { isDaemon = true }.start()
        }
    }

    private fun withIdToken(
        user: FirebaseUser,
        callback: (Boolean, String) -> Unit,
        operation: (String) -> JSONObject
    ) {
        user.getIdToken(true).addOnCompleteListener { tokenTask ->
            if (!tokenTask.isSuccessful) {
                callback(false, "Firebase oturumu doğrulanamadı. İnternet bağlantını kontrol edip yeniden giriş yap.")
                return@addOnCompleteListener
            }
            val token = tokenTask.result?.token
            if (token.isNullOrBlank()) {
                callback(false, "Firebase oturum belirteci alınamadı. Yeniden giriş yap.")
                return@addOnCompleteListener
            }
            Thread({
                val result = try {
                    operation(token)
                } catch (error: Exception) {
                    null to (error.message?.take(220).orEmpty()
                        .ifBlank { "Sunucu isteği tamamlanamadı. Biraz sonra tekrar dene." })
                }
                mainHandler.post {
                    if (result is JSONObject) callback(true, result.optString("message", "İstek kabul edildi."))
                    else {
                        val message = (result as? Pair<*, *>)?.second as? String
                        callback(false, message ?: "Sunucu isteği tamamlanamadı.")
                    }
                }
            }, "MynAccountEmailRequest").apply { isDaemon = true }.start()
        }
    }

    private fun postJson(
        path: String,
        idToken: String,
        body: JSONObject
    ): JSONObject = postJsonBlocking(path, idToken, body)

    private fun postJsonBlocking(
        path: String,
        idToken: String,
        body: JSONObject
    ): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            val apiConnection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 70_000
                doOutput = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            connection = apiConnection
            apiConnection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val statusCode = apiConnection.responseCode
            val responseBody = readResponse(apiConnection, statusCode)
            val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
            if (statusCode !in 200..299) {
                val title = json.optString("title")
                val detail = json.optString("detail")
                val message = when (statusCode) {
                    401 -> "Oturumun doğrulanamadı. Uygulamadan çıkış yapıp yeniden giriş yap."
                    429 -> "Ücretsiz servisin istek sınırına ulaşıldı. Biraz sonra tekrar dene."
                    502, 503 -> detail.ifBlank { "Ücretsiz e-posta/görsel servisi henüz yapılandırılmadı. Sunucu ayarlarını kontrol et." }
                    else -> detail.ifBlank { title.ifBlank { "Sunucu isteği başarısız oldu (HTTP $statusCode)." } }
                }
                throw IOException(message)
            }
            return json
        } catch (error: IOException) {
            throw error
        } catch (_: Exception) {
            throw IOException("MynAssistant sunucusuna ulaşılamadı. Ücretsiz sunucu uyku modundaysa biraz bekleyip tekrar dene.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection, statusCode: Int): String {
        val stream = try {
            if (statusCode in 200..299) connection.inputStream else connection.errorStream
        } catch (_: Exception) {
            connection.errorStream
        }
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun copyWithLimit(input: InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(16 * 1024)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            if (copied >= limit) {
                throw IOException("Kırpılmış fotoğraf 5 MB sınırını aşıyor. Daha küçük bir fotoğraf seç.")
            }
            output.write(buffer, 0, count)
        }
        if (copied == 0L) throw IOException("Fotoğraf dosyası boş.")
    }
}
