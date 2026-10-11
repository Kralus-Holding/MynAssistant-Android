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
import android.text.InputType
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.EditText
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
    @Volatile private var speechModel: Model? = null
    @Volatile private var recognizer: Recognizer? = null
    @Volatile private var speechService: SpeechService? = null
    @Volatile private var modelInitThread: Thread? = null
    @Volatile private var notificationStatus = "Çevrimdışı Türkçe modeli hazırlanıyor…"
    private var recoveryAttempts = 0
    private var recoveringSpeechService = false
    private var overlayView: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var overlayCommand: EditText? = null
    private var userRequestedStop = false
    private var serviceFailed = false
    private var primaryButton: TextView? = null
    private var secondaryButton: TextView? = null
    private var awaitingCommand = false
    private var commandReady = false
    private var lastCommand = ""
    private var lastRecoverableIssueAt = 0L
    private var closed = false
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userRequestedStop = true
            modelInitThread?.interrupt()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            fail("MYN-WAKE-8002", "Ekran üstü izni verilmedi.")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            notificationStatus = "Çevrimdışı Türkçe modeli hazırlanıyor…"
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            fail("MYN-WAKE-8003", "Mikrofon servisi başlatılamadı: " + error.javaClass.simpleName)
            stopSelf()
            return START_NOT_STICKY
        }
        if (speechService != null || modelInitThread?.isAlive == true) return START_STICKY
        userRequestedStop = false
        serviceFailed = false
        updateServiceStatus("Çevrimdışı Türkçe modeli hazırlanıyor…", enabled = true)
        modelInitThread = Thread({ initializeOfflineSpeech() }, "Myn-Vosk-Init").apply {
            isDaemon = true
            start()
        }
        return START_STICKY
    }
    private fun initializeOfflineSpeech() {
        try {
            val modelDirectory = ensureOfflineModel()
            if (closed || userRequestedStop) return
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val loadedModel = Model(modelDirectory.absolutePath)
            val loadedRecognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
            val streamingService = SpeechService(loadedRecognizer, SAMPLE_RATE.toFloat())
            speechModel = loadedModel
            recognizer = loadedRecognizer
            speechService = streamingService
            if (closed || userRequestedStop) { releaseSpeechEngine(); return }
            updateServiceStatus("Hey Meyene dinliyor · çevrimdışı", enabled = true)
            if (!streamingService.startListening(voskListener)) throw IOException("Vosk ses akışı başlatılamadı.")
        } catch (error: Exception) {
            if (closed || userRequestedStop) return
            fail(
                "MYN-WAKE-8013",
                "Çevrimdışı Türkçe ses motoru başlatılamadı: " +
                    (error.message ?: error.javaClass.simpleName) +
                    ". İlk kurulumda internet bağlantısı gerekebilir."
            )
            stopSelf()
        }
    }

    private val voskListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val transcript = parseVoskText(hypothesis, "partial")
            if (transcript.isNotBlank()) mainHandler.post { processTranscript(transcript, isFinal = false) }
        }
        override fun onResult(hypothesis: String?) {
            val transcript = parseVoskText(hypothesis, "text")
            if (transcript.isNotBlank()) mainHandler.post { processTranscript(transcript, isFinal = true) }
        }
        override fun onFinalResult(hypothesis: String?) {
            val transcript = parseVoskText(hypothesis, "text")
            if (transcript.isNotBlank()) mainHandler.post { processTranscript(transcript, isFinal = true) }
        }
        override fun onError(exception: Exception?) {
            mainHandler.post { recoverSpeechEngine(exception) }
        }
        override fun onTimeout() = Unit
    }

    private fun wakePhrases(): List<String> =
        listOf("hey meyene", "hey mayene", "hey meyene ai", "hey myassistant")

    private fun findWakePhraseAndTrailingText(heard: String): Pair<String, String>? {
        val normalized = normalize(heard)
        val wake = wakePhrases().firstOrNull { normalized.contains(it) } ?: return null
        val start = normalized.indexOf(wake)
        if (start < 0) return null
        val end = (start + wake.length).coerceAtMost(heard.length)
        val trailing = heard.drop(end).trim().trim(',', '.', '!', ':', ';', '-')
        return wake to trailing
    }

    private fun processTranscript(transcript: String, isFinal: Boolean) {
        if (closed || commandReady) return
        val normalized = normalize(transcript).trim(' ', ',', '.', '!', ':', ';', '-')
        val match = findWakePhraseAndTrailingText(transcript)
        val isWakeOnly = wakePhrases().any { normalized == it }
        if (!awaitingCommand) {
            if (match == null) return
            awaitingCommand = true
            val trailing = match.second.trim()
            if (trailing.isBlank()) showOverlay("Seni dinliyorum. Komutunu söyle.", "", ready = false)
            else if (isFinal) acceptRecognizedCommand(trailing)
            else showOverlay("Komutunu dinliyorum.", trailing, ready = false)
            return
        }
        val candidate = match?.second?.takeIf { it.isNotBlank() } ?: transcript.takeIf { !isWakeOnly }.orEmpty()
        if (candidate.isBlank()) {
            if (isFinal) showOverlay("Seni dinliyorum. Komutunu söyle.", "", ready = false)
            return
        }
        if (isFinal) acceptRecognizedCommand(candidate)
        else showOverlay("Komutunu dinliyorum.", candidate, ready = false)
    }

    private fun acceptRecognizedCommand(command: String) {
        val clean = command.trim()
        if (clean.isBlank()) return
        lastCommand = clean
        commandReady = true
        awaitingCommand = false
        speechService?.setPause(true)
        showOverlay("Komutunu algıladım.", clean, ready = true)
    }

    private fun parseVoskText(payload: String?, key: String): String {
        if (payload.isNullOrBlank()) return ""
        return try { JSONObject(payload).optString(key, "").trim() } catch (_: Exception) { "" }
    }

    private fun recoverSpeechEngine(exception: Exception?) {
        if (closed || userRequestedStop || serviceFailed || recoveringSpeechService) return
        recoveringSpeechService = true
        recoveryAttempts += 1
        retrying("MYN-WAKE-8010", "Sürekli ses akışı yeniden kuruluyor: " +
            (exception?.message ?: exception?.javaClass?.simpleName ?: "ses akışı kesildi"))
        if (recoveryAttempts > 10) {
            recoveringSpeechService = false
            fail("MYN-WAKE-8011", "Mikrofon akışı çok kez kesildi. Pil kısıtlamalarını kapatıp Hey Meyene'yi yeniden başlat.")
            stopSelf()
            return
        }
        mainHandler.postDelayed({
            try {
                if (closed || userRequestedStop || serviceFailed) {
                    recoveringSpeechService = false
                    return@postDelayed
                }
                releaseSpeechStreamOnly()
                val activeModel = speechModel ?: throw IOException("Türkçe model bellekte bulunamadı.")
                val nextRecognizer = Recognizer(activeModel, SAMPLE_RATE.toFloat())
                val nextService = SpeechService(nextRecognizer, SAMPLE_RATE.toFloat())
                recognizer = nextRecognizer
                speechService = nextService
                if (!nextService.startListening(voskListener)) throw IOException("Ses akışı yeniden başlatılamadı.")
                recoveringSpeechService = false
                recoveryAttempts = 0
                updateServiceStatus("Hey Meyene dinliyor · çevrimdışı", enabled = true)
            } catch (error: Exception) {
                recoveringSpeechService = false
                retrying("MYN-WAKE-8010", "Ses akışı yeniden başlatılamadı: " + error.javaClass.simpleName)
                if (!closed && !userRequestedStop && !serviceFailed) mainHandler.postDelayed({ recoverSpeechEngine(error) }, 1800)
            }
        }, 1200)
    }

    private fun releaseSpeechStreamOnly() {
        runCatching { speechService?.cancel() }
        runCatching { speechService?.shutdown() }
        speechService = null
        runCatching { recognizer?.close() }
        recognizer = null
    }

    private fun releaseSpeechEngine() {
        releaseSpeechStreamOnly()
        runCatching { speechModel?.close() }
        speechModel = null
    }

    private fun ensureOfflineModel(): File {
        val modelDirectory = File(filesDir, MODEL_DIRECTORY)
        if (isValidModelDirectory(modelDirectory)) return modelDirectory
        if (modelDirectory.exists()) modelDirectory.deleteRecursively()
        val archive = File(cacheDir, MODEL_ARCHIVE_NAME)
        val partialArchive = File(cacheDir, "$MODEL_ARCHIVE_NAME.part")
        val connection = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            updateServiceStatus("Türkçe çevrimdışı model indiriliyor…", enabled = true)
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) throw IOException("Model sunucusu HTTP $response döndürdü.")
            val totalBytes = connection.contentLengthLong
            var downloaded = 0L
            var lastPublishedProgress = -1
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partialArchive)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted || userRequestedStop || closed) throw IOException("Model kurulumu iptal edildi.")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent >= lastPublishedProgress + 5 || percent == 100) {
                                lastPublishedProgress = percent
                                updateServiceStatus("Türkçe model indiriliyor · $percent%", enabled = true)
                            }
                        } else if (downloaded / (1024 * 1024) > lastPublishedProgress) {
                            lastPublishedProgress = (downloaded / (1024 * 1024)).toInt()
                            updateServiceStatus("Türkçe model indiriliyor · $lastPublishedProgress MB", enabled = true)
                        }
                    }
                }
            }
            if (partialArchive.length() < 20L * 1024L * 1024L) throw IOException("İndirilen Türkçe model paketi eksik görünüyor.")
            if (archive.exists()) archive.delete()
            if (!partialArchive.renameTo(archive)) throw IOException("Model paketi kaydedilemedi.")
            updateServiceStatus("Türkçe model açılıyor…", enabled = true)
            val extractionRoot = File(cacheDir, "myn-vosk-extract")
            extractionRoot.deleteRecursively()
            if (!extractionRoot.mkdirs()) throw IOException("Model klasörü oluşturulamadı.")
            unzipModelSafely(archive, extractionRoot)
            val extracted = File(extractionRoot, MODEL_DIRECTORY).takeIf { isValidModelDirectory(it) }
                ?: extractionRoot.listFiles()?.firstOrNull { isValidModelDirectory(it) }
                ?: throw IOException("İndirilen pakette kullanılabilir Türkçe model bulunamadı.")
            if (modelDirectory.exists()) modelDirectory.deleteRecursively()
            if (!extracted.renameTo(modelDirectory)) extracted.copyRecursively(modelDirectory, overwrite = true)
            extractionRoot.deleteRecursively()
            archive.delete()
            if (!isValidModelDirectory(modelDirectory)) {
                modelDirectory.deleteRecursively()
                throw IOException("Türkçe model doğrulaması başarısız.")
            }
            return modelDirectory
        } finally {
            connection.disconnect()
            partialArchive.delete()
        }
    }

    private fun unzipModelSafely(archive: File, destination: File) {
        val rootPath = destination.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                if (Thread.currentThread().isInterrupted || userRequestedStop || closed) throw IOException("Model kurulumu iptal edildi.")
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name.replace('\\', '/')).canonicalFile
                if (!output.path.startsWith(rootPath)) throw IOException("Model paketinde geçersiz dosya yolu var.")
                if (entry.isDirectory) output.mkdirs()
                else {
                    output.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(output)).use { target ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            target.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun isValidModelDirectory(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            (File(directory, "conf/model.conf").isFile || File(directory, "conf/mfcc.conf").isFile)

    private fun updateServiceStatus(status: String, enabled: Boolean, errorCode: String? = null) {
        notificationStatus = status
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_STATUS, status)
        if (errorCode.isNullOrBlank()) editor.remove(KEY_ERROR) else editor.putString(KEY_ERROR, errorCode)
        editor.apply()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
    }



    private fun showOverlay(status: String, recognizedCommand: String, ready: Boolean) {
        if (closed || !Settings.canDrawOverlays(this)) return
        if (overlayView == null) createOverlay()
        overlayStatus?.text = status
        if (recognizedCommand.isNotBlank()) {
            overlayCommand?.setText(recognizedCommand)
            overlayCommand?.setSelection(recognizedCommand.length)
        }
        val hasCommand = !overlayCommand?.text.isNullOrBlank()
        primaryButton?.text = if (ready) "MynAssistant'ta aç" else "Komutu hazırla"
        primaryButton?.isEnabled = hasCommand
        primaryButton?.alpha = if (hasCommand) 1f else .58f
        secondaryButton?.text = if (ready) "Tekrar dinle" else "Gizle"
        if (overlayView?.parent == null) {
            val width = (resources.displayMetrics.widthPixels - dp(40))
                .coerceAtLeast(dp(220))
                .coerceAtMost(dp(320))
            val params = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(88)
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            try {
                windowManager.addView(overlayView ?: return, params)
            } catch (_: Exception) {
                fail("MYN-WAKE-8007", "Mikrofon paneli ekranın üzerine açılamadı.")
                stopSelf()
            }
        }
    }



    private fun createOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(11))
            background = roundedBackground(Color.rgb(10, 13, 21), dp(28), Color.rgb(139, 156, 255), dp(1))
            elevation = dp(20).toFloat()
        }
        root.addView(TextView(this).apply {
            text = "━━━━"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(97, 106, 127))
            setPadding(0, 0, 0, dp(10))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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
            text = "HEY MEYENE  •  MİKROFON ASİSTANI"
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
            setOnClickListener {
                hideOverlay()
                awaitingCommand = false
                commandReady = false
                lastCommand = ""
                speechService?.setPause(false)
            }
        })
        root.addView(heading)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.rgb(17, 23, 36), dp(16), Color.rgb(36, 47, 68), dp(1))
        }
        statusCard.addView(View(this).apply {
            background = roundedBackground(Color.rgb(70, 214, 151), dp(50))
        }, LinearLayout.LayoutParams(dp(8), dp(8)))
        overlayStatus = TextView(this).apply {
            text = "“Hey Meyene” diye seslenebilirsin."
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(9), 0, 0, 0)
        }
        statusCard.addView(overlayStatus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(statusCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        overlayCommand = EditText(this).apply {
            hint = "Komutunu söyle veya yaz…"
            textSize = 15f
            maxLines = 3
            minLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextColor(Color.rgb(241, 243, 250))
            setHintTextColor(Color.rgb(137, 145, 164))
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = roundedBackground(Color.rgb(17, 21, 31), dp(17), Color.rgb(44, 55, 81), dp(1))
            isFocusableInTouchMode = true
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) speechService?.setPause(true)
                else if (!commandReady) speechService?.setPause(false)
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    openRecognizedCommand()
                    true
                } else false
            }
        }
        root.addView(overlayCommand, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        primaryButton = styledButton("MynAssistant'ta aç", primary = true).apply {
            isEnabled = false
            alpha = .58f
            setOnClickListener { openRecognizedCommand() }
        }
        actions.addView(primaryButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        secondaryButton = styledButton("Gizle", primary = false).apply {
            setOnClickListener {
                if (commandReady) {
                    commandReady = false
                    lastCommand = ""
                    awaitingCommand = true
                    overlayCommand?.setText("")
                    showOverlay("Tekrar dinliyorum. Komutunu söyle.", "", ready = false)
                    speechService?.setPause(false)
                } else {
                    hideOverlay()
                    awaitingCommand = false
                    lastCommand = ""
                    speechService?.setPause(false)
                }
            }
        }
        actions.addView(secondaryButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(actions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        overlayCommand?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !text.isNullOrBlank()
                primaryButton?.isEnabled = hasText
                primaryButton?.alpha = if (hasText) 1f else .58f
                primaryButton?.text = if (hasText) "MynAssistant'ta aç" else "Komut bekleniyor"
                if (hasText) lastCommand = text.toString().trim()
            }
            override fun afterTextChanged(editable: android.text.Editable?) = Unit
        })
        overlayView = root
    }

    private fun openRecognizedCommand() {
        val typedCommand = overlayCommand?.text?.toString()?.trim().orEmpty()
        val command = typedCommand.ifBlank { lastCommand }.trim()
        if (command.isBlank()) {
            overlayStatus?.text = "Önce komutunu söyle veya yaz."
            overlayCommand?.requestFocus()
            val input = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.showSoftInput(overlayCommand, InputMethodManager.SHOW_IMPLICIT)
            return
        }
        lastCommand = command
        commandReady = false
        awaitingCommand = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
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
        } catch (_: Exception) {
            fail("MYN-WAKE-8008", "Komut MynAssistant içinde açılamadı.")
            stopSelf()
        }
        speechService?.setPause(false)
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
            .setContentText(notificationStatus)
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

    private fun retrying(code: String, detail: String) {
        updateServiceStatus("Ses akışı yenileniyor…", enabled = true, errorCode = code)
        val now = System.currentTimeMillis()
        if (now - lastRecoverableIssueAt >= 60_000L) {
            MynAssistantDiagnostics.record(
                this, code, "Hey Meyene", "", detail
            )
            lastRecoverableIssueAt = now
        }
    }

    private fun fail(code: String, detail: String) {
        serviceFailed = true
        updateServiceStatus("Kapalı · " + detail, enabled = false, errorCode = code)
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
        modelInitThread?.interrupt()
        hideOverlay()
        releaseSpeechEngine()
        if (userRequestedStop || serviceFailed) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, false)
                .putString(KEY_STATUS, "Kapalı")
                .apply()
        }
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
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_DIRECTORY = "vosk-model-small-tr-0.3"
        private const val MODEL_ARCHIVE_NAME = "vosk-model-small-tr-0.3.zip"
        private const val MODEL_DOWNLOAD_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
    }
}
