package com.mynkralus.mynassistant

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

internal enum class WakeKeywordModelInstallStage {
    CACHED,
    DOWNLOADING,
    EXTRACTING,
    VALIDATING,
    READY
}

internal data class WakeKeywordModelInstallProgress(
    val stage: WakeKeywordModelInstallStage,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L
)

internal class WakeKeywordModelInstallException(
    val stage: WakeKeywordModelInstallStage,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Installs only the small INT8/FP32 inference files needed by sherpa-onnx KWS.
 * The upstream archive also contains unused FP32 models and test audio, which are not
 * copied into app storage. Paths are matched by allowlisted basename, never extracted
 * as archive paths.
 */
internal class WakeKeywordModelInstaller(
    private val filesDir: File,
    private val cacheDir: File,
    private val downloader: ModelArchiveDownloader = HttpModelArchiveDownloader,
    private val availableSpaceBytes: (File) -> Long = { it.usableSpace }
) {
    fun ensureInstalled(
        isCancelled: () -> Boolean = { false },
        onProgress: (WakeKeywordModelInstallProgress) -> Unit = {}
    ): File {
        val modelDir = File(filesDir, MODEL_DIRECTORY)
        val stagingDir = File(filesDir, ".$MODEL_DIRECTORY.staging")
        val archive = File(cacheDir, ".$MODEL_DIRECTORY.tar.bz2.part")
        var stage = WakeKeywordModelInstallStage.DOWNLOADING

        if (isValidModelDirectory(modelDir)) {
            onProgress(WakeKeywordModelInstallProgress(WakeKeywordModelInstallStage.CACHED))
            return modelDir
        }

        try {
            checkCancellation(isCancelled)
            if (!filesDir.exists() && !filesDir.mkdirs()) {
                throw IOException("Uyandırma modeli için uygulama klasörü oluşturulamadı.")
            }
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw IOException("Uyandırma modeli için geçici klasör oluşturulamadı.")
            }

            val freeSpace = minOf(availableSpaceBytes(filesDir), availableSpaceBytes(cacheDir))
            if (freeSpace > 0L && freeSpace < MIN_FREE_SPACE_BYTES) {
                throw WakeKeywordModelInstallException(
                    WakeKeywordModelInstallStage.DOWNLOADING,
                    "Düşük güç uyandırma motorunu kurmak için en az 128 MB boş alan gerekli."
                )
            }

            stagingDir.deleteRecursively()
            if (!stagingDir.mkdirs()) throw IOException("Uyandırma modeli hazırlık klasörü oluşturulamadı.")
            archive.delete()

            onProgress(WakeKeywordModelInstallProgress(stage))
            downloader.download(URL(MODEL_ARCHIVE_URL), archive, isCancelled) { downloaded, total ->
                onProgress(WakeKeywordModelInstallProgress(stage, downloaded, total))
            }
            checkCancellation(isCancelled)
            if (!archive.isFile || archive.length() == 0L) {
                throw IOException("İndirilen KWS modeli boş veya bulunamadı.")
            }

            stage = WakeKeywordModelInstallStage.EXTRACTING
            onProgress(WakeKeywordModelInstallProgress(stage))
            extractRequiredFiles(archive, stagingDir, isCancelled)

            stage = WakeKeywordModelInstallStage.VALIDATING
            onProgress(WakeKeywordModelInstallProgress(stage))
            writeKeywordsFile(stagingDir)
            if (!isValidModelDirectory(stagingDir)) {
                throw IOException("Düşük güç uyandırma modelinin gerekli dosyaları doğrulanamadı.")
            }
            checkCancellation(isCancelled)

            // Promote only after the staged model has passed all structural checks.
            if (modelDir.exists() && !modelDir.deleteRecursively()) {
                throw IOException("Eski geçersiz uyandırma modeli temizlenemedi.")
            }
            if (!stagingDir.renameTo(modelDir)) {
                stagingDir.copyRecursively(modelDir, overwrite = true)
                stagingDir.deleteRecursively()
            }
            if (!isValidModelDirectory(modelDir)) {
                modelDir.deleteRecursively()
                throw IOException("Kurulan düşük güç uyandırma modeli doğrulama aşamasını geçemedi.")
            }

            stage = WakeKeywordModelInstallStage.READY
            onProgress(WakeKeywordModelInstallProgress(stage))
            return modelDir
        } catch (error: WakeKeywordModelInstallException) {
            throw error
        } catch (error: Exception) {
            if (isCancelled() || Thread.currentThread().isInterrupted) {
                throw WakeKeywordModelInstallException(stage, "Uyandırma modeli kurulumu iptal edildi.", error)
            }
            throw WakeKeywordModelInstallException(
                stage,
                error.message?.take(240) ?: error.javaClass.simpleName,
                error
            )
        } finally {
            archive.delete()
            stagingDir.deleteRecursively()
        }
    }

    private fun extractRequiredFiles(archive: File, destination: File, isCancelled: () -> Boolean) {
        var extractedTotal = 0L
        val seen = mutableSetOf<String>()
        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)), true).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    checkCancellation(isCancelled)
                    if (!entry.isDirectory) {
                        val normalized = entry.name.replace('\\', '/')
                        val baseName = normalized.substringAfterLast('/')
                        if (baseName in REQUIRED_MODEL_FILES) {
                            if (baseName in seen) throw IOException("KWS arşivinde yinelenen model dosyası bulundu.")
                            if (entry.size <= 0L || entry.size > MAX_SINGLE_FILE_BYTES) {
                                throw IOException("KWS model dosyasının boyutu geçersiz: $baseName")
                            }
                            val output = File(destination, baseName).canonicalFile
                            if (output.parentFile?.canonicalPath != destination.canonicalPath) {
                                throw IOException("KWS arşivinde geçersiz dosya yolu bulundu.")
                            }
                            BufferedOutputStream(FileOutputStream(output)).use { out ->
                                val buffer = ByteArray(64 * 1024)
                                var copied = 0L
                                while (true) {
                                    checkCancellation(isCancelled)
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    extractedTotal += count
                                    if (copied > MAX_SINGLE_FILE_BYTES || extractedTotal > MAX_EXTRACTED_BYTES) {
                                        throw IOException("KWS modelinin açılmış boyutu güvenlik sınırını aşıyor.")
                                    }
                                    out.write(buffer, 0, count)
                                }
                            }
                            if (output.length() != entry.size) {
                                throw IOException("KWS model dosyası eksik çıkarıldı: $baseName")
                            }
                            seen += baseName
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
        val missing = REQUIRED_MODEL_FILES - seen
        if (missing.isNotEmpty()) {
            throw IOException("KWS arşivinde gerekli dosyalar eksik: " + missing.joinToString(", "))
        }
    }

    private fun writeKeywordsFile(directory: File) {
        // CMU-style phones approximate the Turkish pronunciation “hey me-ye-ne”.
        // sherpa-onnx constrains decoding to this phrase instead of transcribing all speech.
        val keywords = File(directory, KEYWORDS_FILE)
        keywords.writeText("HH EY1 M EH0 Y EH0 N EH0 :1.5 #0.25 @HEY_MEYENE\\n", Charsets.UTF_8)
    }

    private fun isValidModelDirectory(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val encoder = File(directory, ENCODER_FILE)
        val decoder = File(directory, DECODER_FILE)
        val joiner = File(directory, JOINER_FILE)
        val tokens = File(directory, TOKENS_FILE)
        val keywords = File(directory, KEYWORDS_FILE)
        return encoder.isFile && encoder.length() > MIN_ONNX_FILE_BYTES &&
            decoder.isFile && decoder.length() > MIN_ONNX_FILE_BYTES &&
            joiner.isFile && joiner.length() > MIN_ONNX_FILE_BYTES &&
            tokens.isFile && tokens.length() > 500L &&
            keywords.isFile && keywords.readText(Charsets.UTF_8).contains("@HEY_MEYENE")
    }

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Uyandırma modeli kurulumu iptal edildi.")
        }
    }

    companion object {
        internal const val MODEL_DIRECTORY = "myn-sherpa-kws-zh-en"
        internal const val ENCODER_FILE = "encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx"
        internal const val DECODER_FILE = "decoder-epoch-13-avg-2-chunk-16-left-64.onnx"
        internal const val JOINER_FILE = "joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx"
        internal const val TOKENS_FILE = "tokens.txt"
        internal const val KEYWORDS_FILE = "keywords.txt"

        private const val MODEL_ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/" +
                "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2"
        private const val MIN_FREE_SPACE_BYTES = 128L * 1024L * 1024L
        private const val MAX_SINGLE_FILE_BYTES = 20L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 16L * 1024L * 1024L
        private const val MIN_ONNX_FILE_BYTES = 32L * 1024L
        private val REQUIRED_MODEL_FILES = setOf(
            ENCODER_FILE, DECODER_FILE, JOINER_FILE, TOKENS_FILE
        )
    }
}
