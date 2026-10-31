package com.mynkralus.mynassistant

import java.util.Locale

internal const val REMOTE_COMMAND_SUBMISSION_TIMEOUT_MILLIS = 20_000L
internal const val REMOTE_COMMAND_VALIDITY_MILLIS = 60_000L

/**
 * Allows only one outstanding remote command per account screen. The submission
 * flag covers the asynchronous interval before Firestore returns a command ID;
 * the listener flag covers the period while waiting for that command's result.
 */
internal fun isRemoteCommandSubmissionAllowed(
    submissionInProgress: Boolean,
    hasActiveResultListener: Boolean
): Boolean = !submissionInProgress && !hasActiveResultListener

internal fun isRemoteCommandSubmissionTimeoutExpired(
    submissionInProgress: Boolean,
    startedAtMillis: Long,
    nowMillis: Long,
    timeoutMillis: Long = REMOTE_COMMAND_SUBMISSION_TIMEOUT_MILLIS
): Boolean = submissionInProgress &&
    startedAtMillis > 0L &&
    nowMillis >= startedAtMillis &&
    nowMillis - startedAtMillis >= timeoutMillis

/**
 * "pending" means Firestore accepted a queued document, not that Windows received it.
 * Only "running" confirms that the Agent claimed the command.
 */
internal fun remoteCommandStatusMessage(status: String): String =
    when (status.lowercase(Locale.ROOT)) {
        "pending" -> "Komut kuyruğa alındı; Windows Agent tarafından alınması bekleniyor."
        "running" -> "Windows Agent komutu aldı ve uyguluyor..."
        else -> "Windows komut durumu: $status"
    }


/**
 * Uses platform-appropriate wording for the shared Firestore command queue.
 * The Windows route keeps its existing wording.
 */
internal fun remoteCommandStatusMessageForTarget(status: String, isAndroidTarget: Boolean): String =
    when {
        isAndroidTarget && status.equals("pending", ignoreCase = true) ->
            "Komut seçili Android cihazının alması için kuyrukta bekliyor."
        isAndroidTarget && status.equals("running", ignoreCase = true) ->
            "Android cihaz komutu çalıştırıyor..."
        isAndroidTarget -> "Android komut durumu: $status"
        else -> remoteCommandStatusMessage(status)
    }

internal fun remoteCommandTimeoutMessage(
    isAndroidTarget: Boolean,
    submissionTimeout: Boolean
): String = when {
    isAndroidTarget && submissionTimeout ->
        "Android komutunun sunucu tarafından onaylanması 20 saniyeyi geçti. İstek durumu belirsiz; yinelenen komut oluşmaması için yeni istek kilitlendi. İnternet bağlantısını kontrol et; ilk istek daha sonra tamamlanabilir."
    isAndroidTarget ->
        "Android cihaz üzerinden 60 saniye içinde sonuç gelmedi. Bağlantıyı kontrol et; komutun çalışıp çalışmadığını hedef cihazda doğrula."
    submissionTimeout ->
        "Komutun sunucu tarafından onaylanması 20 saniyeyi geçti. İstek durumu belirsiz; yinelenen komut oluşmaması için yeni istek kilitlendi. İnternet bağlantısını kontrol et; ilk istek daha sonra tamamlanabilir."
    else ->
        "Bilgisayardan 60 saniye içinde sonuç gelmedi. Bağlantıyı kontrol et; komutun çalışıp çalışmadığını bilgisayardan doğrula."
}
