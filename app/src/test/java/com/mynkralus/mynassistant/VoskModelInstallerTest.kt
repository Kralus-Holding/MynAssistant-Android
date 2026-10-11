package com.mynkralus.mynassistant

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoskModelInstallerTest {
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var sourceArchive: File
    private var downloadCount = 0
    private lateinit var installer: VoskModelInstaller

    @Before
    fun setUp() {
        root = Files.createTempDirectory("myn-voskm-installer-test").toFile()
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
        sourceArchive = File(root, "source-model.zip")
        createModelArchive(sourceArchive)
        downloadCount = 0
        installer = makeInstaller()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun validModelIsReusedWithoutDownloadingAgain() {
        val first = installer.ensureInstalled()
        val second = installer.ensureInstalled()

        assertEquals(first.canonicalPath, second.canonicalPath)
        assertEquals(1, downloadCount)
        assertTrue(File(second, "am/final.mdl").isFile)
    }

    @Test
    fun modelRemainsInstalledAcrossInstallerInstances() {
        val firstInstaller = makeInstaller()
        val installed = firstInstaller.ensureInstalled()
        val secondInstaller = makeInstaller()
        val reused = secondInstaller.ensureInstalled()

        assertEquals(installed.canonicalPath, reused.canonicalPath)
        assertEquals("one download only", 1, downloadCount)
    }

    @Test
    fun lowStorageFailsBeforeAttemptingDownload() {
        val lowStorageInstaller = VoskModelInstaller(
            filesDir = filesDir,
            cacheDir = cacheDir,
            modelDirectoryName = MODEL_NAME,
            modelArchiveUrl = MODEL_URL,
            downloader = archiveDownloader(sourceArchive),
            availableSpaceBytes = { 64L * 1024L * 1024L }
        )

        val error = assertThrows(VoskModelInstallException::class.java) {
            lowStorageInstaller.ensureInstalled()
        }

        assertEquals(VoskModelInstallStage.DOWNLOADING, error.stage)
        assertEquals("no download is started with insufficient space", 0, downloadCount)
        assertFalse(File(cacheDir, ".$MODEL_NAME.zip.part").exists())
    }

    @Test
    fun downloadFailureIsReportedAndLaterAttemptCanRecover() {
        val failingInstaller = VoskModelInstaller(
            filesDir = filesDir,
            cacheDir = cacheDir,
            modelDirectoryName = MODEL_NAME,
            modelArchiveUrl = MODEL_URL,
            downloader = ModelArchiveDownloader { _, _, _, _ ->
                throw java.io.IOException("HTTP 503")
            }
        )

        val error = assertThrows(VoskModelInstallException::class.java) {
            failingInstaller.ensureInstalled()
        }

        assertEquals(VoskModelInstallStage.DOWNLOADING, error.stage)
        assertFalse(File(cacheDir, ".$MODEL_NAME.zip.part").exists())

        val installed = makeInstaller().ensureInstalled()
        assertTrue(File(installed, "graph/HCLG.fst").isFile)
        assertEquals(1, downloadCount)
    }

    @Test
    fun validBackupIsRestoredWithoutRedownloading() {
        val backup = File(filesDir, ".$MODEL_NAME.backup")
        File(backup, "am").mkdirs()
        File(backup, "am/final.mdl").writeText("previous-valid-model")
        File(backup, "conf").mkdirs()
        File(backup, "conf/model.conf").writeText("model-config")
        File(backup, "graph").mkdirs()
        File(backup, "graph/HCLG.fst").writeText("graph")

        val restored = installer.ensureInstalled()

        assertTrue(File(restored, "am/final.mdl").readText().contains("previous-valid-model"))
        assertFalse(backup.exists())
        assertEquals("valid backup is reused", 0, downloadCount)
    }

    @Test
    fun malformedArchiveDoesNotCreateFinalModelAndLeavesRetryPossible() {
        val badArchive = File(root, "bad.zip").apply { writeText("this is not a ZIP archive") }
        val badInstaller = VoskModelInstaller(
            filesDir,
            cacheDir,
            MODEL_NAME,
            MODEL_URL,
            downloader = archiveDownloader(badArchive)
        )

        val error = assertThrows(VoskModelInstallException::class.java) {
            badInstaller.ensureInstalled()
        }

        assertEquals(VoskModelInstallStage.EXTRACTING, error.stage)
        assertFalse(File(filesDir, MODEL_NAME).exists())
        assertFalse(File(cacheDir, ".$MODEL_NAME.zip.part").exists())

        // A later attempt may download a clean archive; failure did not poison the cache.
        val recovered = makeInstaller().ensureInstalled()
        assertTrue(File(recovered, "graph/HCLG.fst").isFile)
        assertEquals(2, downloadCount)
    }

    @Test
    fun replacementIsInstalledWithoutDeletingOldDirectoryBeforeValidation() {
        val existing = File(filesDir, MODEL_NAME)
        File(existing, "am").apply { mkdirs() }
        File(existing, "am/final.mdl").writeText("old-partial-model")

        val installed = installer.ensureInstalled()

        assertTrue(File(installed, "am/final.mdl").readText().contains("test-model"))
        assertTrue(File(installed, "conf/model.conf").isFile)
        assertTrue(File(installed, "graph/HCLG.fst").isFile)
        assertEquals(1, downloadCount)
    }

    @Test
    fun pathTraversalEntryIsRejected() {
        val traversalArchive = File(root, "traversal.zip")
        ZipOutputStream(FileOutputStream(traversalArchive)).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("no".toByteArray())
            zip.closeEntry()
        }
        val traversalInstaller = VoskModelInstaller(
            filesDir,
            cacheDir,
            MODEL_NAME,
            MODEL_URL,
            downloader = archiveDownloader(traversalArchive)
        )

        val error = assertThrows(VoskModelInstallException::class.java) {
            traversalInstaller.ensureInstalled()
        }

        assertEquals(VoskModelInstallStage.EXTRACTING, error.stage)
        assertFalse(File(root, "escape.txt").exists())
    }

    @Test
    fun modelWithoutGraphFstIsRejected() {
        val incompleteArchive = File(root, "incomplete.zip")
        ZipOutputStream(FileOutputStream(incompleteArchive)).use { zip ->
            addEntry(zip, "$MODEL_NAME/am/final.mdl", "test-model")
            addEntry(zip, "$MODEL_NAME/conf/model.conf", "test-conf")
        }
        val incompleteInstaller = VoskModelInstaller(
            filesDir,
            cacheDir,
            MODEL_NAME,
            MODEL_URL,
            downloader = archiveDownloader(incompleteArchive)
        )

        val error = assertThrows(VoskModelInstallException::class.java) {
            incompleteInstaller.ensureInstalled()
        }

        assertEquals(VoskModelInstallStage.VALIDATING, error.stage)
        assertFalse(File(filesDir, MODEL_NAME).exists())
        assertNotNull(error.message)
    }

    private fun makeInstaller(): VoskModelInstaller = VoskModelInstaller(
        filesDir = filesDir,
        cacheDir = cacheDir,
        modelDirectoryName = MODEL_NAME,
        modelArchiveUrl = MODEL_URL,
        downloader = archiveDownloader(sourceArchive)
    )

    private fun archiveDownloader(archive: File) = ModelArchiveDownloader { _, target, isCancelled, onProgress ->
        if (isCancelled()) throw java.io.InterruptedIOException("cancelled")
        downloadCount++
        archive.copyTo(target, overwrite = true)
        onProgress(target.length(), target.length())
    }

    private fun createModelArchive(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            addEntry(zip, "$MODEL_NAME/am/final.mdl", "test-model-content")
            addEntry(zip, "$MODEL_NAME/conf/model.conf", "test-model-config")
            addEntry(zip, "$MODEL_NAME/graph/HCLG.fst", "test-graph-content")
        }
    }

    private fun addEntry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray())
        zip.closeEntry()
    }

    companion object {
        private const val MODEL_NAME = "vosk-model-small-tr-0.3"
        private const val MODEL_URL = "https://example.invalid/model.zip"
    }
}
