package com.mynkralus.mynassistant

import android.animation.ValueAnimator
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
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
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
    private var recoveryStableReset: Runnable? = null
    private var overlayView: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var overlayCommand: EditText? = null
    private var overlayMic: ImageView? = null
    private var overlayMicAnimator: ValueAnimator? = null
    private var overlayDragHandle: View? = null
    @Volatile private var userRequestedStop = false
    @Volatile private var serviceFailed = false
    private var primaryButton: TextView? = null
    private var secondaryButton: TextView? = null
    private var awaitingCommand = false
    private var commandReady = false
    private var lastCommand = ""
    private var lastRecoverableIssueAt = 0L
    @Volatile private var closed = false
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
            fail("MYN-WAKE-8002", "Ekran üstü izni verilmedi.", "Settings.canDrawOverlays=false")
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            fail("MYN-WAKE-8004", "Mikrofon izni verilmedi.", "RECORD_AUDIO permission is not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        userRequestedStop = false
        serviceFailed = false
        closed = false
        clearPersistedWakeError()
        if (speechService != null || modelInitThread?.isAlive == true) return START_STICKY

        try {
            updateServiceStatus(
                "Hey Meyene servisi başlatılıyor…",
                enabled = true,
                phase = MynWakeWordPhase.STARTING
            )
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            fail(
                "MYN-WAKE-8003",
                "Ön plan mikrofon servisi başlatılamadı. Uygulama açıkken tekrar dene.",
                error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
            )
            stopSelf()
            return START_NOT_STICKY
        }

        modelInitThread = Thread({ initializeOfflineSpeech() }, "Myn-Vosk-Init").apply {
            isDaemon = true
            start()
        }
        return START_STICKY
    }

    private fun initializeOfflineSpeech() {
        var phase = MynWakeWordPhase.VALIDATING_MODEL
        try {
            val modelDirectory = ensureOfflineModel()
            if (closed || userRequestedStop) return

            phase = MynWakeWordPhase.LOADING_ENGINE
            updateServiceStatus("Türkçe ses motoru yükleniyor…", enabled = true, phase = phase)
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val loadedModel = Model(modelDirectory.absolutePath)
            speechModel = loadedModel
            val loadedRecognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
            recognizer = loadedRecognizer
            val streamingService = SpeechService(loadedRecognizer, SAMPLE_RATE.toFloat())
            speechService = streamingService
            if (closed || userRequestedStop) {
                releaseSpeechEngine()
                return
            }

            phase = MynWakeWordPhase.STARTING_MICROPHONE
            updateServiceStatus("Mikrofon akışı başlatılıyor…", enabled = true, phase = phase)
            if (!streamingService.startListening(voskListener)) {
                throw IOException("Vosk SpeechService.startListening(false) döndürdü.")
            }
            recoveringSpeechService = false
            recoveryAttempts = 0
            updateServiceStatus(
                "Hey Meyene dinliyor · çevrimdışı",
                enabled = true,
                phase = MynWakeWordPhase.LISTENING
            )
            scheduleRecoveryCounterReset(streamingService)
        } catch (error: VoskModelInstallException) {
            if (closed || userRequestedStop) return
            val (code, message) = when (error.stage) {
                VoskModelInstallStage.DOWNLOADING ->
                    "MYN-WAKE-8020" to "Türkçe model indirilemedi. İnternet bağlantısını ve boş depolama alanını kontrol et."
                VoskModelInstallStage.EXTRACTING ->
                    "MYN-WAKE-8021" to "Türkçe model paketi açılamadı. İndirme bozuk veya eksik olabilir."
                VoskModelInstallStage.VALIDATING ->
                    "MYN-WAKE-8022" to "Türkçe model dosyaları doğrulanamadı."
                VoskModelInstallStage.CACHED, VoskModelInstallStage.READY ->
                    "MYN-WAKE-8022" to "Türkçe model kurulumu tamamlanamadı."
            }
            fail(code, message, "stage=${error.stage}; ${error.javaClass.name}: ${error.message}")
            stopSelf()
        } catch (error: LinkageError) {
            if (closed || userRequestedStop) return
            fail(
                "MYN-WAKE-8025",
                "Cihazın yerel ses kitaplığı yüklenemedi. Uygulama/cihaz mimarisi uyumluluğu incelenmeli.",
                "phase=${phase.persistedValue}; ${error.javaClass.name}: ${error.message}"
            )
            stopSelf()
        } catch (error: Exception) {
            if (closed || userRequestedStop) return
            val code = if (phase == MynWakeWordPhase.STARTING_MICROPHONE) {
                "MYN-WAKE-8024"
            } else {
                "MYN-WAKE-8023"
            }
            val message = if (phase == MynWakeWordPhase.STARTING_MICROPHONE) {
                "Model yüklendi ancak mikrofon akışı başlatılamadı. Mikrofon iznini ve başka uygulamaların mikrofon kullanımını kontrol et."
            } else {
                "Türkçe ses motoru/model yüklenemedi. Model dosyası, cihaz uyumluluğu ve native kitaplıkları kontrol et."
            }
            fail(code, message, "phase=${phase.persistedValue}; ${error.javaClass.name}: ${error.message}")
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

    private fun processTranscript(transcript: String, isFinal: Boolean) {
        if (closed || commandReady) return
        val match = MynWakeWordMatcher.findMatch(transcript)
        val isWakeOnly = match != null && match.trailingText.isBlank()
        if (!awaitingCommand) {
            if (match == null) return
            recordWakeDetection(match.matchedPhrase)
            awaitingCommand = true
            val trailing = match.trailingText.trim()
            if (trailing.isBlank()) showOverlay("Seni dinliyorum. Komutunu söyle.", "", ready = false)
            else if (isFinal) acceptRecognizedCommand(trailing)
            else showOverlay("Komutunu dinliyorum.", trailing, ready = false)
            return
        }
        val candidate = match?.trailingText?.takeIf { it.isNotBlank() } ?: transcript.takeIf { !isWakeOnly }.orEmpty()
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

    private fun recordWakeDetection(phrase: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_WAKE_LAST_AT, System.currentTimeMillis())
            .putInt(KEY_WAKE_COUNT, prefs.getInt(KEY_WAKE_COUNT, 0) + 1)
            .putString(KEY_WAKE_LAST_PHRASE, phrase)
            .apply()
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
                updateServiceStatus("Hey Meyene dinliyor · çevrimdışı", enabled = true)
                scheduleRecoveryCounterReset(nextService)
            } catch (error: Exception) {
                recoveringSpeechService = false
                retrying("MYN-WAKE-8010", "Ses akışı yeniden başlatılamadı: " + error.javaClass.simpleName)
                if (!closed && !userRequestedStop && !serviceFailed) mainHandler.postDelayed({ recoverSpeechEngine(error) }, 1800)
            }
        }, 1200)
    }

    private fun scheduleRecoveryCounterReset(activeService: SpeechService) {
        recoveryStableReset?.let(mainHandler::removeCallbacks)
        val reset = Runnable {
            if (!closed && !userRequestedStop && !serviceFailed &&
                speechService === activeService && !recoveringSpeechService
            ) {
                recoveryAttempts = 0
            }
        }
        recoveryStableReset = reset
        mainHandler.postDelayed(reset, 30_000L)
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
        val installer = VoskModelInstaller(
            filesDir = filesDir,
            cacheDir = cacheDir,
            modelDirectoryName = MODEL_DIRECTORY,
            modelArchiveUrl = MODEL_DOWNLOAD_URL
        )
        var lastPublishedPercent = -5
        var lastPublishedMegabytes = -1L
        return installer.ensureInstalled(
            isCancelled = { closed || userRequestedStop }
        ) { progress ->
            when (progress.stage) {
                VoskModelInstallStage.CACHED -> updateServiceStatus(
                    "Kurulu Türkçe model kullanılıyor…",
                    enabled = true,
                    phase = MynWakeWordPhase.VALIDATING_MODEL
                )
                VoskModelInstallStage.DOWNLOADING -> {
                    val total = progress.totalBytes
                    if (total > 0L) {
                        val percent = ((progress.downloadedBytes * 100L) / total).toInt().coerceIn(0, 100)
                        if (percent >= lastPublishedPercent + 5 || percent == 100) {
                            lastPublishedPercent = percent
                            updateServiceStatus(
                                "Türkçe model indiriliyor · $percent%",
                                enabled = true,
                                phase = MynWakeWordPhase.DOWNLOADING_MODEL
                            )
                        }
                    } else {
                        val megabytes = progress.downloadedBytes / (1024L * 1024L)
                        if (megabytes > lastPublishedMegabytes) {
                            lastPublishedMegabytes = megabytes
                            updateServiceStatus(
                                "Türkçe model indiriliyor · $megabytes MB",
                                enabled = true,
                                phase = MynWakeWordPhase.DOWNLOADING_MODEL
                            )
                        }
                    }
                }
                VoskModelInstallStage.EXTRACTING -> updateServiceStatus(
                    "Türkçe model açılıyor…",
                    enabled = true,
                    phase = MynWakeWordPhase.EXTRACTING_MODEL
                )
                VoskModelInstallStage.VALIDATING -> updateServiceStatus(
                    "Türkçe model dosyaları doğrulanıyor…",
                    enabled = true,
                    phase = MynWakeWordPhase.VALIDATING_MODEL
                )
                VoskModelInstallStage.READY -> updateServiceStatus(
                    "Türkçe model hazır; ses motoru yükleniyor…",
                    enabled = true,
                    phase = MynWakeWordPhase.LOADING_ENGINE
                )
            }
        }
    }

    private fun clearPersistedWakeError() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_ERROR)
            .remove(KEY_ERROR_DETAILS)
            .remove(KEY_ERROR_AT)
            .apply()
    }

    private fun updateServiceStatus(
        status: String,
        enabled: Boolean,
        errorCode: String? = null,
        errorDetails: String? = null,
        phase: MynWakeWordPhase? = null
    ) {
        notificationStatus = status
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_STATUS, status)

        if (phase != null) {
            editor.putString(KEY_PHASE, phase.persistedValue)
            if (phase != MynWakeWordPhase.ERROR) {
                editor.putString(KEY_LAST_PHASE, phase.persistedValue)
            }
        }
        if (!errorCode.isNullOrBlank()) {
            editor.putString(KEY_ERROR, errorCode)
                .putString(KEY_ERROR_DETAILS, errorDetails.orEmpty().take(1200))
                .putLong(KEY_ERROR_AT, System.currentTimeMillis())
        }
        if (phase == MynWakeWordPhase.LISTENING) {
            editor.putLong(KEY_LAST_LISTENING_AT, System.currentTimeMillis())
                .remove(KEY_ERROR)
                .remove(KEY_ERROR_DETAILS)
                .remove(KEY_ERROR_AT)
        }
        editor.apply()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }



    private fun showOverlay(status: String, recognizedCommand: String, ready: Boolean) {
        if (closed || !Settings.canDrawOverlays(this)) return
        if (overlayView == null) createOverlay()
        overlayStatus?.text = status
        updateOverlayMicPulse(commandIsReady = ready)
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
                y = dp(72)
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



    private fun updateOverlayMicPulse(commandIsReady: Boolean) {
        val mic = overlayMic ?: return
        if (commandIsReady) {
            overlayMicAnimator?.cancel()
            overlayMicAnimator = null
            mic.scaleX = 1f
            mic.scaleY = 1f
            mic.background = roundedBackground(Color.rgb(39, 48, 79), dp(18))
            mic.setColorFilter(Color.rgb(139, 156, 255))
            return
        }
        mic.background = roundedBackground(
            Color.rgb(20, 65, 58), dp(18), Color.rgb(70, 214, 151), dp(1)
        )
        mic.setColorFilter(Color.rgb(70, 214, 151))
        if (overlayMicAnimator?.isRunning == true) return
        overlayMicAnimator = ValueAnimator.ofFloat(1f, 1.10f).apply {
            duration = 680L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as? Float ?: 1f
                mic.scaleX = scale
                mic.scaleY = scale
            }
            start()
        }
    }

    private fun installOverlayDrag(handle: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            val view = overlayView ?: return@setOnTouchListener false
            val params = view.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = ((resources.displayMetrics.widthPixels - params.width) / 2)
                        .coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height - dp(16))
                        .coerceAtLeast(dp(16))
                    params.x = (startX + (event.rawX - downRawX).toInt()).coerceIn(-maxX, maxX)
                    params.y = (startY - (event.rawY - downRawY).toInt()).coerceIn(dp(16), maxY)
                    runCatching { windowManager.updateViewLayout(view, params) }.isSuccess
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun createOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(9))
            background = roundedBackground(Color.rgb(10, 13, 21), dp(28), Color.rgb(139, 156, 255), dp(1))
            elevation = dp(20).toFloat()
        }
        val dragHandle = View(this).apply {
            background = roundedBackground(Color.rgb(88, 98, 122), dp(12))
            contentDescription = "Mikrofon panelini taşı"
        }
        overlayDragHandle = dragHandle
        installOverlayDrag(dragHandle)
        root.addView(dragHandle, LinearLayout.LayoutParams(dp(38), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mic = ImageView(this).apply {
            setImageResource(R.drawable.ic_myn_microphone)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBackground(Color.rgb(39, 48, 79), dp(18))
            overlayMic = this
        }
        heading.addView(mic, LinearLayout.LayoutParams(dp(42), dp(42)))
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
            setPadding(dp(10), dp(7), dp(10), dp(7))
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
            textSize = 14f
            maxLines = 2
            minLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextColor(Color.rgb(241, 243, 250))
            setHintTextColor(Color.rgb(137, 145, 164))
            setPadding(dp(12), dp(8), dp(12), dp(8))
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
        actions.addView(primaryButton, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) })
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
        actions.addView(secondaryButton, LinearLayout.LayoutParams(0, dp(38), 1f))
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
        overlayMicAnimator?.cancel()
        overlayMicAnimator = null
        overlayMic?.scaleX = 1f
        overlayMic?.scaleY = 1f
        val view = overlayView ?: return
        try {
            if (view.parent != null) windowManager.removeView(view)
        } catch (_: Exception) {
            // The system may already have removed the overlay during a permission change.
        }
        overlayView = null
        overlayStatus = null
        overlayCommand = null
        overlayMic = null
        overlayDragHandle = null
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
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(KEY_RETRY_COUNT, recoveryAttempts)
            .apply()
        updateServiceStatus(
            "Ses akışı yenileniyor · deneme $recoveryAttempts…",
            enabled = true,
            errorCode = code,
            errorDetails = detail,
            phase = MynWakeWordPhase.RETRYING
        )
        val now = System.currentTimeMillis()
        if (now - lastRecoverableIssueAt >= 60_000L) {
            MynAssistantDiagnostics.record(
                this, code, "Hey Meyene", "", detail
            )
            lastRecoverableIssueAt = now
        }
    }

    private fun fail(code: String, userMessage: String, technicalDetails: String = userMessage) {
        serviceFailed = true
        Log.e(LOG_TAG, "$code: $userMessage; $technicalDetails")
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val previousPhase = prefs.getString(KEY_PHASE, MynWakeWordPhase.STARTING.persistedValue)
            ?: MynWakeWordPhase.STARTING.persistedValue
        prefs.edit()
            .putString(KEY_LAST_PHASE, previousPhase.takeUnless { it == MynWakeWordPhase.ERROR.persistedValue }
                ?: prefs.getString(KEY_LAST_PHASE, "UNKNOWN"))
            .apply()
        updateServiceStatus(
            "Hata $code · $userMessage",
            enabled = false,
            errorCode = code,
            errorDetails = technicalDetails,
            phase = MynWakeWordPhase.ERROR
        )
        MynAssistantDiagnostics.record(
            this, code, "Hey Meyene", "", "USER_MESSAGE=$userMessage; DETAILS=$technicalDetails"
        )
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onDestroy() {
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        recoveryStableReset = null
        overlayMicAnimator?.cancel()
        overlayMicAnimator = null
        modelInitThread?.interrupt()
        hideOverlay()
        releaseSpeechEngine()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        when {
            serviceFailed -> {
                // Keep the real diagnostic status; do not replace it with a generic "Kapalı".
                prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putString(KEY_PHASE, MynWakeWordPhase.ERROR.persistedValue)
                    .apply()
            }
            userRequestedStop || !prefs.getBoolean(KEY_ENABLED, false) -> {
                prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putString(KEY_STATUS, "Kapalı")
                    .putString(KEY_PHASE, MynWakeWordPhase.OFF.persistedValue)
                    .apply()
            }
            else -> {
                val code = "MYN-WAKE-8029"
                val detail = "Wake-word foreground service onDestroy was called while listening was still requested."
                prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putString(KEY_STATUS, "Hata $code · Servis beklenmedik şekilde durdu. Yeniden başlatmayı deneyebilirsin.")
                    .putString(KEY_PHASE, MynWakeWordPhase.ERROR.persistedValue)
                    .putString(KEY_ERROR, code)
                    .putString(KEY_ERROR_DETAILS, detail)
                    .putLong(KEY_ERROR_AT, System.currentTimeMillis())
                    .apply()
                MynAssistantDiagnostics.record(this, code, "Hey Meyene", "", detail)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val LOG_TAG = "MynWakeWordService"
        private const val PREFS_NAME = "mynassistant_prefs"
        private const val KEY_ENABLED = "wake_word_enabled"
        private const val KEY_STATUS = "wake_word_status"
        private const val KEY_ERROR = "wake_word_error_code"
        private const val KEY_ERROR_DETAILS = "wake_word_error_details"
        private const val KEY_ERROR_AT = "wake_word_error_at"
        private const val KEY_PHASE = "wake_word_phase"
        private const val KEY_LAST_PHASE = "wake_word_last_phase"
        private const val KEY_LAST_LISTENING_AT = "wake_word_last_listening_at"
        private const val KEY_RETRY_COUNT = "wake_word_retry_count"
        private const val KEY_PENDING_COMMAND = "wake_pending_command"
        private const val KEY_WAKE_LAST_AT = "wake_word_last_detected_at"
        private const val KEY_WAKE_COUNT = "wake_word_detection_count"
        private const val KEY_WAKE_LAST_PHRASE = "wake_word_last_phrase"
        private const val CHANNEL_ID = "mynassistant_wake_word"
        private const val NOTIFICATION_ID = 4127
        private const val ACTION_STOP = "com.mynkralus.mynassistant.STOP_WAKE_WORD"
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_DIRECTORY = "vosk-model-small-tr-0.3"
        private const val MODEL_ARCHIVE_NAME = "vosk-model-small-tr-0.3.zip"
        private const val MODEL_DOWNLOAD_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
    }
}
