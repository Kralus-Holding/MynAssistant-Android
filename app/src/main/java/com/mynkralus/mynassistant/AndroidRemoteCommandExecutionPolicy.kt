package com.mynkralus.mynassistant

/**
 * Final gate for executing a command received by the Android-device queue.
 * The caller checks identity, foreground lifecycle and expiry again immediately before
 * execution, not only when the Firestore command is first claimed.
 */
internal object AndroidRemoteCommandExecutionPolicy {
    fun rejectionMessage(
        receiverDisposed: Boolean,
        sameAuthenticatedAccount: Boolean,
        notExpiredAtExecution: Boolean,
        foreground: Boolean,
        commandName: String,
        commandAllowed: Boolean
    ): String? = when {
        receiverDisposed || !sameAuthenticatedAccount ->
            "Hesap veya alıcı oturumu değişti; komut Android cihazında çalıştırılmadı."
        !notExpiredAtExecution ->
            "Komut çalıştırılmadan önce süresi doldu."
        !foreground ->
            "Telefon uygulaması artık ön planda değil; komut çalıştırılmadı."
        commandName != "text_command" ->
            "Bu Android cihazı bu komut türünü desteklemiyor."
        !commandAllowed -> AndroidRemoteCommandPolicy.rejectionMessage()
        else -> null
    }

    fun shouldMarkCompleted(
        receiverDisposed: Boolean,
        sameAuthenticatedAccount: Boolean,
        notExpiredAtExecution: Boolean,
        foreground: Boolean,
        commandAllowed: Boolean,
        resultIndicatesFailure: Boolean
    ): Boolean =
        !receiverDisposed &&
            sameAuthenticatedAccount &&
            notExpiredAtExecution &&
            foreground &&
            commandAllowed &&
            !resultIndicatesFailure
}

/**
 * Detects user-facing error results before a queued Android command is marked completed.
 * Keep failure wording centralized so Turkish verb forms such as "çalıştırılamadı"
 * cannot accidentally be reported as successful.
 */
internal fun androidCommandResultIndicatesFailure(result: String): Boolean =
    listOf(
        "henüz desteklenmiyor",
        "desteklenmiyor",
        "bulunamadı",
        "açılamadı",
        "başlatılamadı",
        "okunamadı",
        "gerekiyor",
        "çalıştırılmadı",
        "çalıştırılamadı",
        "süresi doldu",
        "oturumu değişti"
    ).any { result.contains(it, ignoreCase = true) }
