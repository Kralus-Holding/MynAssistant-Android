package com.mynkralus.mynassistant

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Displays the Windows pairing code as ABCDE-12345 while keeping the actual
 * text value canonical (ABCDE12345). This avoids cursor jumps when typing.
 */
internal class DesktopPairingCodeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = DesktopPairingCodeFormat.normalize(text.text)
        val formatted = DesktopPairingCodeFormat.format(raw)
        val hasSeparator = raw.length > 5

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, raw.length)
                return if (hasSeparator && safeOffset >= 5) safeOffset + 1 else safeOffset
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, formatted.length)
                return when {
                    !hasSeparator || safeOffset <= 5 -> safeOffset.coerceAtMost(raw.length)
                    safeOffset == 6 -> 5.coerceAtMost(raw.length)
                    else -> (safeOffset - 1).coerceAtMost(raw.length)
                }
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
