package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MynWakeWordMatcherTest {
    @Test
    fun exactWakePhraseExtractsTrailingCommand() {
        val match = MynWakeWordMatcher.findMatch("Hey Meyene hava nasıl?")
        assertNotNull(match)
        assertEquals("Hey Meyene", match?.matchedPhrase)
        assertEquals("hava nasıl?", match?.trailingText)
    }

    @Test
    fun commonRecognitionVariantIsAccepted() {
        val match = MynWakeWordMatcher.findMatch("Hey mayene, pil durumum ne?")
        assertNotNull(match)
        assertEquals("pil durumum ne?", match?.trailingText)
    }

    @Test
    fun nearSpellingFromSpeechRecognitionIsAccepted() {
        val match = MynWakeWordMatcher.findMatch("Hey meyane")
        assertNotNull(match)
        assertEquals("", match?.trailingText)
    }

    @Test
    fun assistantAliasIsAccepted() {
        val match = MynWakeWordMatcher.findMatch("Hey my assistant haritaları aç")
        assertNotNull(match)
        assertEquals("haritaları aç", match?.trailingText)
    }

    @Test
    fun observedHeyNeAsrVariantIsAcceptedOnlyWhenFallbackIsEnabled() {
        val match = MynWakeWordMatcher.findMatch("hey ne")
        assertNotNull(match)
        assertEquals("", match?.trailingText)

        assertNull(
            MynWakeWordMatcher.findMatch(
                "hey ne",
                allowObservedAsrFallback = false
            )
        )
    }

    @Test
    fun observedTegmenYeniAsrVariantExtractsTrailingCommand() {
        val match = MynWakeWordMatcher.findMatch("teğmen yeni, hava nasıl?")
        assertNotNull(match)
        assertEquals("Hey Meyene", match?.matchedPhrase)
        assertEquals("hava nasıl?", match?.trailingText)
    }

    @Test
    fun observedAsrAliasesOnlyMatchAtTheBeginning() {
        assertNull(
            MynWakeWordMatcher.findMatch(
                "bugün teğmen yeni geldi",
                allowObservedAsrFallback = true
            )
        )
        assertNull(
            MynWakeWordMatcher.findMatch(
                "bugün hey ne oldu",
                allowObservedAsrFallback = true
            )
        )
    }

    @Test
    fun unrelatedSpeechDoesNotWakeAssistant() {
        assertNull(MynWakeWordMatcher.findMatch("Meyene'nin havası nasıl?"))
        assertNull(MynWakeWordMatcher.findMatch("hey meh"))
        assertNull(MynWakeWordMatcher.findMatch("hava durumunu göster"))
    }

    @Test
    fun punctuationAndTurkishCharactersAreHandled() {
        val match = MynWakeWordMatcher.findMatch("HEY, MAYENE! İstanbul hava durumu")
        assertNotNull(match)
        assertEquals("İstanbul hava durumu", match?.trailingText)
    }
}
