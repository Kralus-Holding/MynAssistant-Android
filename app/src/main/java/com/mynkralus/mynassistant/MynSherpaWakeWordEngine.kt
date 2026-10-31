package com.mynkralus.mynassistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.io.IOException

/**
 * Owns the microphone while waiting for “Hey Meyene”.
 *
 * Unlike Turkish Vosk ASR, sherpa-onnx decodes only the configured phonetic keyword.
 * It releases AudioRecord and all KWS native resources before invoking onWakeWordDetected,
 * so the command ASR can acquire the microphone afterwards.
 */
internal class WakeNativeLibraryLoadException(cause: LinkageError) : IOException(
    "Yerel ses/uyandırma kütüphanesi yüklenemedi. APK'nın doğru sürümünü yeniden indirip kurun.",
    cause
)

internal class MynSherpaWakeWordEngine(
    private val modelDirectory: File,
    private val onReady: () -> Unit,
    private val onWakeWordDetected: () -> Unit,
    private val onFailure: (Exception) -> Unit
) {
    @Volatile
    private var stopRequested = false

    @Volatile
    private var userStopRequested = false

    @Volatile
    private var activeRecorder: AudioRecord? = null

    @Volatile
    private var worker: Thread? = null

    val isRunning: Boolean
        get() = worker?.isAlive == true && !stopRequested

    fun start() {
        if (worker?.isAlive == true) return
        stopRequested = false
        userStopRequested = false
        worker = Thread({ runSpotter() }, "Myn-Sherpa-KWS").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        userStopRequested = true
        stopRequested = true
        activeRecorder?.let { recorder ->
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
        }
        worker?.interrupt()
    }

    private fun runSpotter() {
        var spotter: KeywordSpotter? = null
        var stream: OnlineStream? = null
        var recorder: AudioRecord? = null
        var detected = false
        var failure: Exception? = null

        try {
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDirectory, WakeKeywordModelInstaller.ENCODER_FILE).absolutePath,
                        decoder = File(modelDirectory, WakeKeywordModelInstaller.DECODER_FILE).absolutePath,
                        joiner = File(modelDirectory, WakeKeywordModelInstaller.JOINER_FILE).absolutePath
                    ),
                    tokens = File(modelDirectory, WakeKeywordModelInstaller.TOKENS_FILE).absolutePath,
                    numThreads = KWS_THREADS,
                    debug = false,
                    provider = "cpu",
                    // The official zh-en KWS model uses this model family with phone+ppinyin tokens.
                    // Empty modelType selects the model's documented default.
                    modelType = "",
                    modelingUnit = "phone+ppinyin"
                ),
                maxActivePaths = 4,
                keywordsFile = File(modelDirectory, WakeKeywordModelInstaller.KEYWORDS_FILE).absolutePath,
                keywordsScore = KEYWORD_SCORE,
                keywordsThreshold = KEYWORD_THRESHOLD,
                numTrailingBlanks = 2
            )
            spotter = KeywordSpotter(config = config)
            stream = spotter.createStream()

            val minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferBytes <= 0) {
                throw IOException("AudioRecord.getMinBufferSize returned $minBufferBytes.")
            }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferBytes * 2, SAMPLE_RATE * 2 / 2)
            )
            activeRecorder = recorder
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("Düşük güç uyandırma motoru mikrofonu başlatamadı.")
            }
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("Düşük güç uyandırma motoru ses akışına başlayamadı.")
            }

            Log.i(TAG, "KWS ready: threshold=$KEYWORD_THRESHOLD, threads=$KWS_THREADS")
            val pcmBuffer = ShortArray(SAMPLES_PER_FRAME)
            var readyReported = false
            while (!stopRequested) {
                val count = recorder.read(pcmBuffer, 0, pcmBuffer.size, AudioRecord.READ_BLOCKING)
                if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    throw IOException("AudioRecord was released while KWS was waiting.")
                }
                if (count == AudioRecord.ERROR_INVALID_OPERATION || count == AudioRecord.ERROR_BAD_VALUE) {
                    throw IOException("AudioRecord.read returned $count.")
                }
                if (count <= 0) continue
                if (!readyReported) {
                    readyReported = true
                    onReady()
                }

                val samples = FloatArray(count) { index -> pcmBuffer[index] / 32768.0f }
                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (spotter.isReady(stream)) {
                    spotter.decode(stream)
                    val result = spotter.getResult(stream)
                    if (result.keyword.isNotBlank()) {
                        Log.i(TAG, "KWS keyword detected: ${result.keyword}; tokens=${result.tokens.joinToString(" ")}")
                        detected = true
                        stopRequested = true
                        break
                    }
                }
            }
        } catch (error: LinkageError) {
            // Native JNI libraries can be missing or incompatible. Convert this
            // Error into the existing actionable service error path instead of
            // silently killing the worker thread.
            if (!stopRequested) failure = WakeNativeLibraryLoadException(error)
        } catch (error: Exception) {
            if (!stopRequested) failure = error
        } finally {
            recorder?.let { active ->
                runCatching {
                    if (active.recordingState == AudioRecord.RECORDSTATE_RECORDING) active.stop()
                }
                runCatching { active.release() }
            }
            activeRecorder = null
            runCatching { stream?.release() }
            runCatching { spotter?.release() }
        }

        worker = null
        if (detected && !userStopRequested) {
            onWakeWordDetected()
        } else if (failure != null && !userStopRequested) {
            Log.e(TAG, "KWS capture failed", failure)
            onFailure(failure!!)
        }
    }

    companion object {
        private const val TAG = "MynSherpaWakeWord"
        private const val SAMPLE_RATE = 16_000
        private const val SAMPLES_PER_FRAME = 1_280 // 80 ms @ 16 kHz
        private const val KWS_THREADS = 2
        private const val KEYWORD_SCORE = 1.5f
        private const val KEYWORD_THRESHOLD = 0.25f
    }
}
