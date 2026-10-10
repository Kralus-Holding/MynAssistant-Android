package com.mynkralus.mynassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import java.text.Normalizer
import java.util.Locale

const val EXTRA_MYN_WAKE_COMMAND = "com.mynkralus.mynassistant.EXTRA_WAKE_COMMAND"

class MynWakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var overlayView: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var overlayCommand: TextView? = null
    private var primaryButton: TextView? = null
    private var secondaryButton: TextView? = null
    private var isListening = false
    private var awaitingCommand = false
    private var commandReady = false
    private var lastCommand = ""
    private var closed = false
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            fail("MYN-WAKE-8002", "Ekran üstü izni verilmedi.")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            fail("MYN-WAKE-8003", "Mikrofon servisi başlatılamadı: " + error.javaClass.simpleName)
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            fail(
                "MYN-WAKE-8001",
                "Bu cihazda yerleşik çevrimdışı konuşma tanıma hizmeti bulunamadı."
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (recognizer == null) {
            recognizer = try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also {
                    it.setRecognitionListener(listener)
                }
            } catch (error: Exception) {
                fail("MYN-WAKE-8004", "Yerleşik ses tanıma başlatılamadı: " + error.javaClass.simpleName)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_STATUS, "Mikrofon açık · Hey Meyene dinleniyor")
            .remove(KEY_ERROR)
            .apply()
        startListeningSoon(150)
        return START_STICKY
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

        override fun onError(error: Int) {
            isListening = false
            if (closed) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                fail("MYN-WAKE-8005", "Mikrofon izni kaybedildi. Yeniden izin verip dinlemeyi başlat.")
                stopSelf()
                return
            }
            // SpeechRecognizer ends an utterance after a pause; restart to keep the wake phrase available.
            startListeningSoon(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 550)
        }

        override fun onResults(results: android.os.Bundle?) {
            isListening = false
            if (closed) return
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (heard.isBlank()) {
                startListeningSoon(350)
                return
            }
            consumeSpeech(heard)
        }
    }

    private fun consumeSpeech(heard: String) {
        val normalized = normalize(heard)
        val wakePhrases = listOf("hey meyene", "hey mayene", "hey meyene ai", "hey myassistant")
        if (!awaitingCommand) {
            val wake = wakePhrases.firstOrNull { normalized.contains(it) } ?: run {
                startListeningSoon(250)
                return
            }
            val rawIndex = normalize(heard).indexOf(wake)
            val trailing = if (rawIndex >= 0) heard.drop((rawIndex + wake.length).coerceAtMost(heard.length))
                .trim().trim(',', '.', '!', ':', ';', '-')
            else ""
            if (trailing.isNotBlank()) {
                lastCommand = trailing
                commandReady = true
                awaitingCommand = false
                showOverlay("Komutunu algıladım.", trailing, ready = true)
            } else {
                awaitingCommand = true
                commandReady = false
                lastCommand = ""
                showOverlay("Seni dinliyorum. Komutunu söyle.", "", ready = false)
                startListeningSoon(350)
            }
        } else {
            lastCommand = heard
            commandReady = true
            awaitingCommand = false
            showOverlay("Komutunu algıladım.", heard, ready = true)
        }
    }

    private fun startListeningSoon(delayMs: Long) {
        if (closed || commandReady || isListening) return
        mainHandler.removeCallbacks(startListeningRunnable)
        mainHandler.postDelayed(startListeningRunnable, delayMs)
    }

    private val startListeningRunnable = Runnable {
        if (closed || commandReady || isListening) return@Runnable
        val activeRecognizer = recognizer ?: return@Runnable
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            isListening = true
            activeRecognizer.startListening(intent)
        } catch (error: Exception) {
            isListening = false
            fail("MYN-WAKE-8006", "Ses dinlemesi devam ettirilemedi: " + error.javaClass.simpleName)
            startListeningSoon(1500)
        }
    }

    private fun showOverlay(status: String, recognizedCommand: String, ready: Boolean) {
        if (closed || !Settings.canDrawOverlays(this)) return
        if (overlayView == null) createOverlay()
        overlayStatus?.text = status
        overlayCommand?.text = if (recognizedCommand.isBlank()) {
            "“Hey Meyene” diyerek istediğin zaman yeniden seslenebilirsin."
        } else {
            "“$recognizedCommand”"
        }
        overlayCommand?.visibility = if (recognizedCommand.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        primaryButton?.visibility = if (ready) android.view.View.VISIBLE else android.view.View.GONE
        primaryButton?.text = if (ready) "Komutu aç" else ""
        secondaryButton?.text = if (ready) "Tekrar dinle" else "Gizle"
        if (overlayView?.parent == null) {
            val width = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(260))
            val params = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(54)
            }
            try {
                windowManager.addView(overlayView ?: return, params)
            } catch (error: Exception) {
                fail("MYN-WAKE-8007", "Mikrofon paneli ekranın üzerine açılamadı.")
            }
        }
    }

    private fun createOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundedBackground(Color.rgb(22, 27, 42), dp(28), Color.rgb(139, 156, 255), dp(1))
            elevation = dp(16).toFloat()
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mic = ImageView(this).apply {
            setImageResource(R.drawable.ic_myn_microphone)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = roundedBackground(Color.rgb(39, 48, 79), dp(18))
        }
        heading.addView(mic, LinearLayout.LayoutParams(dp(44), dp(44)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        labels.addView(TextView(this).apply {
            text = "MynAssistant"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        labels.addView(TextView(this).apply {
            text = "HEY MEYENE  •  ÇEVRİMDIŞI"
            textSize = 10f
            setTextColor(Color.rgb(169, 181, 224))
        })
        heading.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(TextView(this).apply {
            text = "×"
            textSize = 27f
            setTextColor(Color.rgb(202, 207, 226))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { hideOverlay(); awaitingCommand = false; commandReady = false; startListeningSoon(150) }
        })
        root.addView(heading)
        overlayStatus = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(15), 0, dp(6))
        }
        root.addView(overlayStatus)
        overlayCommand = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(197, 205, 236))
            setPadding(0, dp(4), 0, dp(12))
            visibility = android.view.View.GONE
        }
        root.addView(overlayCommand)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        primaryButton = styledButton("Komutu aç", primary = true).apply {
            visibility = android.view.View.GONE
            setOnClickListener { openRecognizedCommand() }
        }
        actions.addView(primaryButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginEnd = dp(8)
        })
        secondaryButton = styledButton("Gizle", primary = false).apply {
            setOnClickListener {
                if (commandReady) {
                    commandReady = false
                    lastCommand = ""
                    awaitingCommand = true
                    showOverlay("Tekrar dinliyorum. Komutunu söyle.", "", ready = false)
                    startListeningSoon(100)
                } else {
                    hideOverlay()
                }
            }
        }
        actions.addView(secondaryButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(actions)
        overlayView = root
    }

    private fun openRecognizedCommand() {
        if (!commandReady || lastCommand.isBlank()) return
        val command = lastCommand
        commandReady = false
        awaitingCommand = false
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_PENDING_COMMAND, command)
            .apply()
        hideOverlay()
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_MYN_WAKE_COMMAND, command)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        } catch (error: Exception) {
            fail("MYN-WAKE-8008", "Komut MynAssistant içinde açılamadı.")
        }
        startListeningSoon(1200)
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        try {
            if (view.parent != null) windowManager.removeView(view)
        } catch (_: Exception) {
            // The system may already have removed the overlay during a permission change.
        }
        overlayView = null
        overlayStatus = null
        overlayCommand = null
        primaryButton = null
        secondaryButton = null
    }

    private fun styledButton(label: String, primary: Boolean): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) Color.rgb(17, 21, 42) else Color.rgb(196, 205, 239))
            background = roundedBackground(
                if (primary) Color.rgb(139, 156, 255) else Color.rgb(39, 46, 68),
                dp(20)
            )
            isClickable = true
            isFocusable = true
        }

    private fun roundedBackground(color: Int, radius: Int, strokeColor: Int? = null, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, MynWakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Meyene etkin")
            .setContentText("Çevrimdışı uyandırma dinlemesi açık")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Hey Meyene", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Çevrimdışı uyandırma dinlemesi durum bildirimi"
                }
            )
        }
    }

    private fun fail(code: String, detail: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "Kapalı · " + detail)
            .putString(KEY_ERROR, code)
            .apply()
        MynAssistantDiagnostics.record(
            this, code, "Hey Meyene", "", detail
        )
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale("tr", "TR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('ı', 'i')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onDestroy() {
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        isListening = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        hideOverlay()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "Kapalı")
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val PREFS_NAME = "mynassistant_prefs"
        private const val KEY_ENABLED = "wake_word_enabled"
        private const val KEY_STATUS = "wake_word_status"
        private const val KEY_ERROR = "wake_word_error_code"
        private const val KEY_PENDING_COMMAND = "wake_pending_command"
        private const val CHANNEL_ID = "mynassistant_wake_word"
        private const val NOTIFICATION_ID = 4127
        private const val ACTION_STOP = "com.mynkralus.mynassistant.STOP_WAKE_WORD"
    }
}
