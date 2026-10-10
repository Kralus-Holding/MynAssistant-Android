package com.mynkralus.mynassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class MynDiagnosticEntry(
    val id: String,
    val createdAt: Long,
    val category: String,
    val command: String,
    val details: String
)

object MynAssistantDiagnostics {
    private const val PREFS_NAME = "mynassistant_prefs"
    private const val HISTORY_KEY = "diagnostic_history_v1"
    private const val MAX_ENTRIES = 80

    fun record(
        context: Context,
        codePrefix: String,
        category: String,
        command: String,
        details: String
    ): String {
        val stamp = (System.currentTimeMillis() % 1_000_000L)
            .toString().padStart(6, '0')
        val id = codePrefix + "-" + stamp
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(HISTORY_KEY, "[]")) }
            .getOrElse { JSONArray() }
        val entry = JSONObject()
            .put("id", id)
            .put("createdAt", System.currentTimeMillis())
            .put("category", category.take(80))
            .put("command", command.take(300))
            .put("details", details.take(700))
        val next = JSONArray()
        val start = (old.length() - MAX_ENTRIES + 1).coerceAtLeast(0)
        for (index in start until old.length()) {
            runCatching { old.getJSONObject(index) }.getOrNull()?.let(next::put)
        }
        next.put(entry)
        prefs.edit().putString(HISTORY_KEY, next.toString()).apply()
        return id
    }

    fun read(context: Context): List<MynDiagnosticEntry> {
        val json = runCatching {
            JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(HISTORY_KEY, "[]"))
        }.getOrElse { JSONArray() }
        return buildList {
            for (index in json.length() - 1 downTo 0) {
                val item = runCatching { json.getJSONObject(index) }.getOrNull() ?: continue
                add(
                    MynDiagnosticEntry(
                        id = item.optString("id", "MYN-UNKNOWN"),
                        createdAt = item.optLong("createdAt", 0L),
                        category = item.optString("category", "Genel"),
                        command = item.optString("command", ""),
                        details = item.optString("details", "")
                    )
                )
            }
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(HISTORY_KEY).apply()
    }
}
