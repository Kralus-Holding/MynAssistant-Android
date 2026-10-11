package com.mynkralus.mynassistant

import java.text.Normalizer
import java.util.Locale

/**
 * Small, deterministic text matcher for the Turkish wake phrase.
 *
 * This does not replace an acoustic wake-word model. It compensates for common
 * near-spellings in Vosk's transcript after speech recognition has produced text.
 */
internal data class MynWakeWordMatch(
    val matchedPhrase: String,
    val trailingText: String
)

internal object MynWakeWordMatcher {
    private val acceptedWakeTokens = setOf(
        "meyene", "mayene", "meyane", "mayane", "miyene", "myene",
        "myassistant", "mynassistant", "myassistent"
    )
    private val heyTokens = setOf("hey", "hay")
    private val assistantWords = setOf("assistant", "assistent")

    fun findMatch(heard: String): MynWakeWordMatch? {
        if (heard.isBlank()) return null

        val words = heard.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2) return null

        for (index in 0 until words.lastIndex) {
            val first = cleanToken(words[index])
            if (first !in heyTokens) continue

            val target = cleanToken(words[index + 1])
            var consumedWords = when {
                target in acceptedWakeTokens || isMeyeneVariant(target) -> index + 2
                target in setOf("my", "myn") &&
                    index + 2 < words.size &&
                    cleanToken(words[index + 2]) in assistantWords -> index + 3
                else -> continue
            }

            // Treat "Hey Meyene AI" as part of the wake phrase, not as the command.
            if (consumedWords < words.size && cleanToken(words[consumedWords]) == "ai") {
                consumedWords++
            }

            val trailing = words.drop(consumedWords)
                .joinToString(" ")
                .trim()
                .trimStart(',', '.', '!', ':', ';', '-')

            return MynWakeWordMatch(
                matchedPhrase = "Hey Meyene",
                trailingText = trailing
            )
        }
        return null
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale("tr", "TR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('ı', 'i')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun cleanToken(token: String): String =
        normalize(token).trim { !it.isLetterOrDigit() }

    private fun isMeyeneVariant(token: String): Boolean {
        if (token in acceptedWakeTokens) return true
        if (token.length !in 4..8) return false
        return editDistance(token, "meyene") <= 1 || editDistance(token, "mayene") <= 1
    }

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}
