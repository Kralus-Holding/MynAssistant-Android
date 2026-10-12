package com.mynkralus.mynassistant

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WakeKeywordModelInstallerTest {
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var sourceArchive: File
    private var downloadCount = 0

    @Before
    fun setUp() {
        root = Files.createTempDirectory("myn-kws-installer-test").toFile()
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
        sourceArchive = File(root, "kws-model.tar.bz2")
        createArchive(sourceArchive, includeAllRequiredFiles = true)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun extractsOnlyRequiredFilesAndReusesInstalledModel() {
        val installer = makeInstaller(sourceArchive)

        val first = installer.ensureInstalled()
        val second = makeInstaller(sourceArchive).ensureInstalled()

        assertEquals(first.canonicalPath, second.canonicalPath)
        assertEquals("archive downloads once", 1, downloadCount)
        assertTrue(File(first, WakeKeywordModelInstaller.ENCODER_FILE).length() > 32L * 1024L)
        assertTrue(File(first, WakeKeywordModelInstaller.DECODER_FILE).length() > 32L * 1024L)
        assertTrue(File(first, WakeKeywordModelInstaller.JOINER_FILE).length() > 32L * 1024L)
        assertTrue(File(first, WakeKeywordModelInstaller.TOKENS_FILE).length() > 500L)
        assertTrue(File(first, WakeKeywordModelInstaller.KEYWORDS_FILE).readText().contains("@HEY_MEYENE"))
        assertFalse("unused archive content is not extracted", File(first, "unused-test-audio.wav").exists())
    }

    @Test
    fun missingRequiredModelFileFailsValidationAndCanRetry() {
        val incompleteArchive = File(root, "incomplete.tar.bz2")
        createArchive(incompleteArchive, includeAllRequiredFiles = false)

        val error = org.junit.Assert.assertThrows(WakeKeywordModelInstallException::class.java) {
            makeInstaller(incompleteArchive).ensureInstalled()
        }
        assertEquals(WakeKeywordModelInstallStage.EXTRACTING, error.stage)
        assertFalse(File(filesDir, WakeKeywordModelInstaller.MODEL_DIRECTORY).exists())

        val recovered = makeInstaller(sourceArchive).ensureInstalled()
        assertTrue(File(recovered, WakeKeywordModelInstaller.KEYWORDS_FILE).isFile)
    }

    private fun makeInstaller(archiveFile: File) = WakeKeywordModelInstaller(
        filesDir = filesDir,
        cacheDir = cacheDir,
        downloader = ModelArchiveDownloader { _, target, isCancelled, onProgress ->
            if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
            downloadCount++
            archiveFile.copyTo(target, overwrite = true)
            onProgress(target.length(), target.length())
        }
    )

    private fun createArchive(target: File, includeAllRequiredFiles: Boolean) {
        val files = linkedMapOf(
            WakeKeywordModelInstaller.ENCODER_FILE to ByteArray(40_000) { 0x31 },
            WakeKeywordModelInstaller.DECODER_FILE to ByteArray(36_000) { 0x32 },
            WakeKeywordModelInstaller.JOINER_FILE to ByteArray(35_000) { 0x33 },
            WakeKeywordModelInstaller.TOKENS_FILE to "token 0\n".repeat(120).toByteArray(Charsets.UTF_8),
            "unused-test-audio.wav" to ByteArray(64) { 0x34 }
        )
        BZip2CompressorOutputStream(FileOutputStream(target)).use { bzip ->
            TarArchiveOutputStream(bzip).use { tar ->
                files.forEach { (name, bytes) ->
                    if (!includeAllRequiredFiles && name == WakeKeywordModelInstaller.JOINER_FILE) {
                        return@forEach
                    }
                    val entry = TarArchiveEntry("sherpa-test-model/$name")
                    entry.size = bytes.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
    }
}
