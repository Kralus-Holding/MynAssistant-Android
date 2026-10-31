package com.mynkralus.mynassistant

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcFileUploadContractTest {
    @Test
    fun buildsBoundedUploadPayloadWithSizeAndSha256() {
        val bytes = "phone to PC".toByteArray()
        val payload = buildPcFileUploadPayload("note.txt", "C:/Users/test/Documents", bytes)
        val fields = payload.toCommandFields()

        assertEquals("note.txt", payload.name)
        assertEquals("C:/Users/test/Documents", payload.destinationPath)
        assertEquals(bytes.size.toLong(), fields["size"])
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
            payload.sha256
        )
        assertArrayEquals(bytes, Base64.getDecoder().decode(payload.contentBase64))
    }

    @Test
    fun rejectsTraversalInvalidCharactersAndReservedNames() {
        for (name in listOf("../note.txt", "bad:name.txt", "CON.txt")) {
            assertThrows(IllegalArgumentException::class.java) {
                buildPcFileUploadPayload(name, "C:/Users/test/Documents", byteArrayOf(1))
            }
        }
    }

    @Test
    fun rejectsBlankDestination() {
        assertThrows(IllegalArgumentException::class.java) {
            buildPcFileUploadPayload("note.txt", " ", byteArrayOf(1))
        }
    }

    @Test
    fun rejectsFileAboveSafeSingleCommandLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            buildPcFileUploadPayload(
                "big.bin",
                "C:/Users/test/Documents",
                ByteArray((MAX_PC_FILE_UPLOAD_BYTES + 1).toInt())
            )
        }
    }
}
