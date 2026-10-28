package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeNativeLibraryLoadExceptionTest {
    @Test
    fun missingNativeLibraryIsWrappedWithActionableMessageAndCause() {
        val original = UnsatisfiedLinkError("library not found: libsherpa-onnx-jni.so")

        val error = WakeNativeLibraryLoadException(original)

        assertEquals(original, error.cause)
        assertTrue(error.message.orEmpty().contains("yerel ses/uyandırma kütüphanesi"))
        assertTrue(error.message.orEmpty().contains("yeniden indirip kurun"))
    }
}
