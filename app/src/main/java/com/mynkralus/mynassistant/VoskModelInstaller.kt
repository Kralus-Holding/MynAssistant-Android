package com.mynkralus.mynassistant

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

internal enum class VoskModelInstallStage {
    CACHED,
    DOWNLOADING,
    EXTRACTING,
    VALIDATING,
    READY
}

internal data class VoskModelInstallProgress(
    val stage: VoskModelInstallStage,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L
)

internal class VoskModelInstallException(
    val stage: VoskModelInstallStage,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

internal fun interface ModelArchiveDownloader {
    @Throws(IOException::class)
    fun download(
        url: URL,
        target: File,
        isCancelled: () -> Boolean,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    )
}

internal object HttpModelArchiveDownloader : ModelArchiveDownloader {
    private const val EXTRA_SPACE_BYTES = 8L * 1024L * 1024L
    private const val BUFFER_SIZE = 64 * 1024

    override fun download(
        url: URL,
        target: File,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) {
                throw IOException("Model sunucusu HTTP $response döndürdü.")
            }

            val totalBytes = connection.contentLengthLong
            val parent = target.parentFile
            if (totalBytes > 0L && parent != null &&
                parent.usableSpace > 0L &&
                parent.usableSpace < totalBytes + EXTRA_SPACE_BYTES
            ) {
                throw IOException("Modeli indirmek için yeterli boş depolama alanı yok.")
            }

            target.parentFile?.let { directory ->
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Geçici model klasörü oluşturulamadı.")
                }
            }
            var downloaded = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target).use { fileOutput ->
                    BufferedOutputStream(fileOutput, BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (isCancelled() || Thread.currentThread().isInterrupted) {
                                throw InterruptedIOException("Model indirme işlemi kullanıcı tarafından iptal edildi.")
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalBytes)
                        }
                        output.flush()
                        fileOutput.fd.sync()
                    }
                }
            }
            if (downloaded == 0L) throw IOException("Model sunucusu boş bir dosya döndürdü.")
            if (totalBytes > 0L && downloaded != totalBytes) {
                throw IOException("Model indirmesi eksik: $downloaded / $totalBytes byte.")
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Downloads a model archive into cache, extracts into a staging directory under filesDir,
 * verifies the expected Vosk directory structure, and only then replaces the final model.
 * A valid installed model is always reused and is never deleted before a replacement is ready.
 */
internal class VoskModelInstaller(
    private val filesDir: File,
    private val cacheDir: File,
    private val modelDirectoryName: String,
    private val modelArchiveUrl: String,
    private val downloader: ModelArchiveDownloader = HttpModelArchiveDownloader
) {
    fun ensureInstalled(
        isCancelled: () -> Boolean = { false },
        onProgress: (VoskModelInstallProgress) -> Unit = {}
    ): File {
        val modelDirectory = File(filesDir, modelDirectoryName)
        val backupDirectory = File(filesDir, ".$modelDirectoryName.backup")

        restoreValidBackupIfNeeded(modelDirectory, backupDirectory)
        if (isValidModelDirectory(modelDirectory)) {
            onProgress(VoskModelInstallProgress(VoskModelInstallStage.CACHED))
            return modelDirectory
        }

        if (!filesDir.exists() && !filesDir.mkdirs()) {
            throw VoskModelInstallException(VoskModelInstallStage.VALIDATING, "Uygulama veri klasörü oluşturulamadı.")
        }
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw VoskModelInstallException(VoskModelInstallStage.DOWNLOADING, "Geçici indirme klasörü oluşturulamadı.")
        }

        val stagingRoot = File(filesDir, ".$modelDirectoryName.staging")
        val extractionRoot = File(stagingRoot, "extracted")
        val preparedModel = File(stagingRoot, "prepared-model")
        val partialArchive = File(cacheDir, ".$modelDirectoryName.zip.part")
        var stage = VoskModelInstallStage.DOWNLOADING

        try {
            checkCancellation(isCancelled)
            stagingRoot.deleteRecursively()
            if (!extractionRoot.mkdirs()) {
                throw IOException("Geçici model çıkarma klasörü oluşturulamadı.")
            }
            partialArchive.delete()

            onProgress(VoskModelInstallProgress(VoskModelInstallStage.DOWNLOADING))
            downloader.download(URL(modelArchiveUrl), partialArchive, isCancelled) { downloaded, total ->
                onProgress(VoskModelInstallProgress(VoskModelInstallStage.DOWNLOADING, downloaded, total))
            }
            checkCancellation(isCancelled)
            if (!partialArchive.isFile || partialArchive.length() == 0L) {
                throw IOException("İndirilen model paketi boş veya bulunamadı.")
            }

            stage = VoskModelInstallStage.EXTRACTING
            onProgress(VoskModelInstallProgress(stage))
            extractZipSafely(partialArchive, extractionRoot, isCancelled)

            stage = VoskModelInstallStage.VALIDATING
            onProgress(VoskModelInstallProgress(stage))
            val candidate = findModelDirectory(extractionRoot)
                ?: throw IOException("Arşivde gerekli Vosk model dosyaları bulunamadı.")
            candidate.copyRecursively(preparedModel, overwrite = true)
            if (!isValidModelDirectory(preparedModel)) {
                preparedModel.deleteRecursively()
                throw IOException("Hazırlanan Türkçe modelin dosya yapısı doğrulanamadı.")
            }
            checkCancellation(isCancelled)

            // Recover an interrupted prior swap before touching the existing directory.
            restoreValidBackupIfNeeded(modelDirectory, backupDirectory)
            if (backupDirectory.exists()) {
                if (!backupDirectory.deleteRecursively()) {
                    throw IOException("Eski geçici model yedeği temizlenemedi.")
                }
            }
            if (modelDirectory.exists() && !modelDirectory.renameTo(backupDirectory)) {
                throw IOException("Mevcut model güvenli yedek konumuna taşınamadı.")
            }

            if (!preparedModel.renameTo(modelDirectory)) {
                if (backupDirectory.exists() && !modelDirectory.exists()) {
                    backupDirectory.renameTo(modelDirectory)
                }
                throw IOException("Doğrulanmış model nihai klasöre aktarılamadı.")
            }
            if (!isValidModelDirectory(modelDirectory)) {
                modelDirectory.deleteRecursively()
                if (backupDirectory.exists()) backupDirectory.renameTo(modelDirectory)
                throw IOException("Nihai model dizininin doğrulaması başarısız oldu.")
            }

            backupDirectory.deleteRecursively()
            onProgress(VoskModelInstallProgress(VoskModelInstallStage.READY))
            return modelDirectory
        } catch (error: VoskModelInstallException) {
            throw error
        } catch (error: Exception) {
            if (isCancelled() || Thread.currentThread().isInterrupted) {
                throw VoskModelInstallException(stage, "Model kurulumu iptal edildi.", error)
            }
            throw VoskModelInstallException(
                stage,
                error.message?.take(240) ?: error.javaClass.simpleName,
                error
            )
        } finally {
            partialArchive.delete()
            stagingRoot.deleteRecursively()
        }
    }

    private fun restoreValidBackupIfNeeded(modelDirectory: File, backupDirectory: File) {
        if (isValidModelDirectory(modelDirectory)) {
            if (backupDirectory.exists()) backupDirectory.deleteRecursively()
            return
        }
        if (!isValidModelDirectory(backupDirectory)) return

        if (modelDirectory.exists() && !modelDirectory.deleteRecursively()) {
            throw VoskModelInstallException(
                VoskModelInstallStage.VALIDATING,
                "Geçersiz model klasörü onarılamadı."
            )
        }
        if (!backupDirectory.renameTo(modelDirectory)) {
            throw VoskModelInstallException(
                VoskModelInstallStage.VALIDATING,
                "Önceki doğrulanmış model yedekten geri alınamadı."
            )
        }
    }

    private fun findModelDirectory(root: File): File? =
        root.walkTopDown().firstOrNull { it.isDirectory && isValidModelDirectory(it) }

    private fun isValidModelDirectory(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val acousticModel = File(directory, "am/final.mdl")
        val hasConfig = File(directory, "conf/model.conf").isFile ||
            File(directory, "conf/mfcc.conf").isFile
        val graphDirectory = File(directory, "graph")
        val hasNonEmptyGraph = graphDirectory.isDirectory &&
            graphDirectory.walkTopDown().any {
                it.isFile && it.extension.equals("fst", ignoreCase = true) && it.length() > 0L
            }
        return acousticModel.isFile && acousticModel.length() > 0L &&
            hasConfig && hasNonEmptyGraph
    }

    private fun extractZipSafely(archive: File, destination: File, isCancelled: () -> Boolean) {
        val rootPath = destination.canonicalPath + File.separator
        var expandedBytes = 0L
        val maxExpandedBytes = 512L * 1024L * 1024L
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                checkCancellation(isCancelled)
                val entry: ZipEntry = zip.nextEntry ?: break
                val normalizedName = entry.name.replace('\\', '/')
                val output = File(destination, normalizedName).canonicalFile
                if (!output.path.startsWith(rootPath)) {
                    throw IOException("Model arşivinde geçersiz dosya yolu bulundu.")
                }
                if (entry.isDirectory) {
                    if (!output.exists() && !output.mkdirs()) {
                        throw IOException("Model alt klasörü oluşturulamadı.")
                    }
                } else {
                    val parent = output.parentFile
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw IOException("Model alt klasörü oluşturulamadı.")
                    }
                    BufferedOutputStream(FileOutputStream(output)).use { target ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkCancellation(isCancelled)
                            val read = zip.read(buffer)
                            if (read < 0) break
                            expandedBytes += read
                            if (expandedBytes > maxExpandedBytes) {
                                throw IOException("Model arşivinin açılmış boyutu güvenlik sınırını aşıyor.")
                            }
                            target.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        if (expandedBytes == 0L) throw IOException("Model arşivinden dosya çıkarılamadı.")
    }

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Model kurulumu kullanıcı tarafından iptal edildi.")
        }
    }
}
