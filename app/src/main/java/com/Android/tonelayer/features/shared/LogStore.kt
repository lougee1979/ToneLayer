// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.shared

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RewriteEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val profile: String,
    val level: String,
    val originalText: String,
    val rewrittenText: String,
    val explanation: String,
    val distortions: List<String>,
    val spiraling: Boolean
)

object LogStore {
    private const val PREFS_NAME = "tonelayer_clarity_prefs"
    private const val LOG_KEY = "rewrite_log_v2"
    private const val MAX_ENTRIES = 500

    fun load(context: Context): List<RewriteEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(LOG_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val distortionsArr = obj.optJSONArray("distortions")
                val distortions = if (distortionsArr != null) {
                    (0 until distortionsArr.length()).map { j -> distortionsArr.getString(j) }
                } else emptyList()
                RewriteEntry(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    profile = obj.optString("profile", ""),
                    level = obj.optString("level", ""),
                    originalText = obj.optString("originalText", ""),
                    rewrittenText = obj.optString("rewrittenText", ""),
                    explanation = obj.optString("explanation", ""),
                    distortions = distortions,
                    spiraling = obj.optBoolean("spiraling", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun append(context: Context, entry: RewriteEntry) {
        val entries = load(context).toMutableList()
        entries.add(entry)
        val trimmed = if (entries.size > MAX_ENTRIES) entries.takeLast(MAX_ENTRIES) else entries
        save(context, trimmed)
    }

    private fun save(context: Context, entries: List<RewriteEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("profile", entry.profile)
                put("level", entry.level)
                put("originalText", entry.originalText)
                put("rewrittenText", entry.rewrittenText)
                put("explanation", entry.explanation)
                put("distortions", JSONArray(entry.distortions))
                put("spiraling", entry.spiraling)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LOG_KEY, array.toString())
            .apply()
    }

    fun topPatterns(context: Context, limit: Int = 40): List<Pair<String, Int>> {
        val recent = load(context).takeLast(limit)
        val all = recent.flatMap { it.distortions }.filter { it.isNotBlank() }
        return all.groupBy { it }
            .mapValues { it.value.size }
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { Pair(it.key, it.value) }
    }
}
