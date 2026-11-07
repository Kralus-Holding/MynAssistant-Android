package com.mynkralus.mynassistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal enum class MynPermissionAction {
    REQUEST_RUNTIME,
    OVERLAY_SETTINGS,
    ACCESSIBILITY_SETTINGS,
    NOTIFICATION_SETTINGS,
    APPLICATION_INFO
}

internal enum class MynPermissionState {
    GRANTED,
    PARTIAL,
    DENIED,
    MANAGED
}

internal data class MynPermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val state: MynPermissionState,
    val statusText: String,
    val action: MynPermissionAction,
    val runtimePermissions: List<String> = emptyList(),
    val actionText: String
)

/**
 * Describes only permissions and special access declared/used by MynAssistant.
 * Every status is read from Android at presentation time; no permission state is guessed.
 */
internal object MynAssistantPermissionCatalog {
    fun read(context: Context): List<MynPermissionItem> {
        fun isGranted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        val notificationsGranted = notificationsEnabled && notificationPermissionGranted

        val mediaPermissions = when {
            Build.VERSION.SDK_INT >= 34 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            Build.VERSION.SDK_INT >= 29 -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        val photosFullAccess = if (Build.VERSION.SDK_INT >= 33) {
            isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                isGranted(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val photosPartialAccess = when {
            Build.VERSION.SDK_INT >= 34 ->
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 ->
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    isGranted(Manifest.permission.READ_MEDIA_VIDEO)
            else -> false
        }

        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val overlayGranted = Settings.canDrawOverlays(context)
        val internetGranted = isGranted(Manifest.permission.INTERNET)
        val foregroundServiceGranted =
            isGranted(Manifest.permission.FOREGROUND_SERVICE) &&
                (Build.VERSION.SDK_INT < 34 ||
                    isGranted(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE))
        val audioSettingsGranted = isGranted(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        val accessibilityComponent = ComponentName(
            context,
            MynAssistantAccessibilityService::class.java
        ).flattenToString()
        val enabledAccessibilityServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accessibilityGranted = enabledAccessibilityServices
            .split(':')
            .any { enabled -> enabled.equals(accessibilityComponent, ignoreCase = true) }

        return listOf(
            MynPermissionItem(
                id = "notifications",
                title = "Bildirimler",
                description = "Arka plan asistanı, görev sonuçları ve önemli uyarılar için kullanılır.",
                state = if (notificationsGranted) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (notificationsGranted) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.NOTIFICATION_SETTINGS,
                runtimePermissions = if (Build.VERSION.SDK_INT >= 33) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else emptyList(),
                actionText = "Ayarları aç"
            ),
            MynPermissionItem(
                id = "photos",
                title = "Fotoğraflar ve videolar",
                description = "Profil fotoğrafı seçme ve kullanıcının seçtiği görsel/video dosyalarına erişim.",
                state = when {
                    photosFullAccess -> MynPermissionState.GRANTED
                    photosPartialAccess -> MynPermissionState.PARTIAL
                    else -> MynPermissionState.DENIED
                },
                statusText = when {
                    photosFullAccess -> "Tam erişim"
                    photosPartialAccess -> "Seçili dosyalara erişim"
                    else -> "İzin verilmedi"
                },
                action = MynPermissionAction.REQUEST_RUNTIME,
                runtimePermissions = mediaPermissions,
                actionText = if (photosPartialAccess) "Erişimi yönet" else "İzin ver"
            ),
            MynPermissionItem(
                id = "camera",
                title = "Kamera",
                description = "Fotoğraf çekme ve QR koduyla telefon-PC eşleştirmesi için kullanılır.",
                state = if (isGranted(Manifest.permission.CAMERA)) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (isGranted(Manifest.permission.CAMERA)) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.REQUEST_RUNTIME,
                runtimePermissions = listOf(Manifest.permission.CAMERA),
                actionText = "İzin ver"
            ),
            MynPermissionItem(
                id = "contacts",
                title = "Kişiler",
                description = "Komut verdiğinde telefon rehberinde kişi ve telefon numarası aramak için kullanılır.",
                state = if (isGranted(Manifest.permission.READ_CONTACTS)) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (isGranted(Manifest.permission.READ_CONTACTS)) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.REQUEST_RUNTIME,
                runtimePermissions = listOf(Manifest.permission.READ_CONTACTS),
                actionText = "İzin ver"
            ),
            MynPermissionItem(
                id = "microphone",
                title = "Mikrofon",
                description = "Sesli komutlar ve isteğe bağlı Hey Meyene çevrimdışı uyandırma için kullanılır.",
                state = if (isGranted(Manifest.permission.RECORD_AUDIO)) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (isGranted(Manifest.permission.RECORD_AUDIO)) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.REQUEST_RUNTIME,
                runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO),
                actionText = "İzin ver"
            ),
            MynPermissionItem(
                id = "audio",
                title = "Müzik ve ses",
                description = "Kullanıcının seçtiği ses dosyalarına erişim için kullanılır.",
                state = if (isGranted(audioPermission)) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (isGranted(audioPermission)) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.REQUEST_RUNTIME,
                runtimePermissions = listOf(audioPermission),
                actionText = "İzin ver"
            ),
            MynPermissionItem(
                id = "phone",
                title = "Telefon",
                description = "Yalnızca açıkça verdiğin arama komutlarında telefon araması başlatmak için kullanılır.",
                state = if (isGranted(Manifest.permission.CALL_PHONE)) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (isGranted(Manifest.permission.CALL_PHONE)) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.REQUEST_RUNTIME,
                runtimePermissions = listOf(Manifest.permission.CALL_PHONE),
                actionText = "İzin ver"
            ),
            MynPermissionItem(
                id = "overlay",
                title = "Diğer uygulamaların üzerinde gösterme",
                description = "Hey Meyene yüzen komut panelini diğer uygulamaların üzerinde göstermek için gerekir.",
                state = if (overlayGranted) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (overlayGranted) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.OVERLAY_SETTINGS,
                actionText = "Ayarları aç"
            ),
            MynPermissionItem(
                id = "accessibility",
                title = "Erişilebilirlik hizmeti",
                description = "Yalnızca sen WhatsApp erişilebilirlik özelliğini etkinleştirirsen, verdiğin komutları arayüzde uygulamak için kullanılır.",
                state = if (accessibilityGranted) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (accessibilityGranted) "Etkin" else "Etkin değil",
                action = MynPermissionAction.ACCESSIBILITY_SETTINGS,
                actionText = "Ayarları aç"
            ),
            MynPermissionItem(
                id = "audio_settings",
                title = "Müzik ve ses ayarları",
                description = "Ses düzeyi ve ses yönlendirme gibi sistem ses ayarlarını yönetmek için kullanılan normal Android izni.",
                state = if (audioSettingsGranted) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (audioSettingsGranted) "İzin verildi" else "Kullanılamıyor",
                action = MynPermissionAction.APPLICATION_INFO,
                actionText = "Uygulama bilgilerini aç"
            ),
            MynPermissionItem(
                id = "background_service",
                title = "Arka plan mikrofon servisi",
                description = "Hey Meyene açıkken Android'in mikrofon ön plan servisini ve kalıcı servis bildirimini çalıştırmak için gereken kurulum izni.",
                state = if (foregroundServiceGranted) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (foregroundServiceGranted) "Sistem izni hazır" else "Sistem izni eksik",
                action = MynPermissionAction.APPLICATION_INFO,
                actionText = "Uygulama bilgilerini aç"
            ),
            MynPermissionItem(
                id = "internet",
                title = "İnternet erişimi",
                description = "MynAssistant hesabı, sunucu eşitlemesi ve telefon-PC bağlantısı için kullanılan kurulum izni.",
                state = if (internetGranted) MynPermissionState.GRANTED else MynPermissionState.DENIED,
                statusText = if (internetGranted) "İzin verildi" else "İzin verilmedi",
                action = MynPermissionAction.APPLICATION_INFO,
                actionText = "Uygulama bilgilerini aç"
            ),
            MynPermissionItem(
                id = "restricted_settings",
                title = "Kısıtlanmış ayarlar",
                description = "Android 13 ve üzeri cihazlarda dışarıdan yüklenen uygulamalar için erişilebilirlik gibi hassas ayarları açmadan önce Uygulama bilgileri → ⋮ → Kısıtlanmış ayarlara izin ver adımı gerekebilir.",
                state = MynPermissionState.MANAGED,
                statusText = "Android tarafından yönetilir",
                action = MynPermissionAction.APPLICATION_INFO,
                actionText = "Uygulama bilgilerini aç"
            )
        )
    }
}
