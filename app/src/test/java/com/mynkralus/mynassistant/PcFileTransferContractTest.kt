package com.mynkralus.mynassistant

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcFileTransferContractTest {
    @Test
    fun acceptsPayloadWhenByteCountAndSha256Match() {
        val bytes = "MynAssistant file transfer".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        val result = verifyPcFileDownloadPayload(
            name = "sample.txt",
            path = "C:/Users/test/Documents/sample.txt",
            size = bytes.size.toLong(),
            expectedSha256 = sha256,
            contentBase64 = Base64.getEncoder().encodeToString(bytes)
        )

        assertEquals("sample.txt", result.name)
        assertEquals(bytes.size.toLong(), result.size)
        assertArrayEquals(bytes, result.content)
        assertEquals(sha256, result.sha256)
    }

    @Test
    fun rejectsPayloadWhenChecksumDoesNotMatch() {
        val bytes = "file".toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            verifyPcFileDownloadPayload(
                name = "sample.txt",
                path = "C:/Users/test/Documents/sample.txt",
                size = bytes.size.toLong(),
                expectedSha256 = "0".repeat(64),
                contentBase64 = Base64.getEncoder().encodeToString(bytes)
            )
        }
    }

    @Test
    fun rejectsPayloadWhenDeclaredSizeDoesNotMatchDecodedBytes() {
        val bytes = "file".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        assertThrows(IllegalArgumentException::class.java) {
            verifyPcFileDownloadPayload(
                name = "sample.txt",
                path = "C:/Users/test/Documents/sample.txt",
                size = (bytes.size + 1).toLong(),
                expectedSha256 = sha256,
                contentBase64 = Base64.getEncoder().encodeToString(bytes)
            )
        }
    }

    @Test
    fun rejectsFileAboveCurrentSafeSingleResponseLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            verifyPcFileDownloadPayload(
                name = "large.bin",
                path = "C:/Users/test/Documents/large.bin",
                size = MAX_PC_FILE_DOWNLOAD_BYTES + 1,
                expectedSha256 = "a".repeat(64),
                contentBase64 = ""
            )
        }
    }
}
