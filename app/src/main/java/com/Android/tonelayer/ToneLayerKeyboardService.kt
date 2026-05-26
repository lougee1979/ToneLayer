// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * QWERTY input method with iOS-style keys, rewrite controls, a rewrite box, and a teaching box.
 */
private const val PREFS_NAME = "tonelayer_clarity_prefs"
private const val PREF_CLAUDE_API_KEY = "claude_api_key"
private const val PREF_AI_CONSENT = "ai_processing_consent"

class ToneLayerKeyboardService : InputMethodService() {
    private var isShifted = false
    private var latestOriginal = ""
    private var latestRewrite = ""
    private var latestTeaching = "Select text or type a message, then tap Rewrite."
    private var keyboardTypedText = StringBuilder()
    private var latestDeleteCount = 0
    private var latestUsedSelection = false
    private lateinit var root: LinearLayout

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            setBackgroundColor(Color.rgb(207, 211, 217))
        }

        buildKeyboard()
        return root
    }

    private fun buildKeyboard() {
        root.removeAllViews()
        addClarityToolbar()
        addRewritePanel()
        addLetterRow("qwertyuiop", sideInsetWeight = 0f)
        addLetterRow("asdfghjkl", sideInsetWeight = 0.45f)
        addBottomLetterRow()
        addUtilityRow()
    }

    private fun addClarityToolbar() {
        val row = horizontalRow(heightDp = 36)
        row.addView(toolbarKey("Clarify", weight = 1.2f) { runRewrite(RewriteMode.CLEAR) })
        row.addView(toolbarKey("Shorter") { runRewrite(RewriteMode.SHORTER) })
        row.addView(toolbarKey("Warmer") { runRewrite(RewriteMode.WARMER) })
        row.addView(toolbarKey("Direct") { runRewrite(RewriteMode.DIRECT) })
        root.addView(row)
    }

    private fun addRewritePanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.rgb(232, 226, 236), 12f)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(3), dp(2), dp(5)) }
        }

        panel.addView(TextView(this).apply {
            text = "Rewrite box"
            setTextColor(Color.rgb(28, 28, 30))
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(TextView(this).apply {
            text = latestRewrite.ifBlank { "Tap Rewrite to generate a clearer version." }
            setTextColor(Color.rgb(28, 28, 30))
            setTextSize(14f)
            maxLines = 2
        })

        panel.addView(TextView(this).apply {
            text = "Teaching box"
            setTextColor(Color.rgb(28, 28, 30))
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, 0)
        })
        panel.addView(TextView(this).apply {
            text = latestTeaching
            setTextColor(Color.rgb(60, 60, 67))
            setTextSize(13f)
            maxLines = 2
        })

        val useRow = horizontalRow(heightDp = 34)
        useRow.addView(toolbarKey("Use Rewrite", weight = 1.4f) { useLatestRewrite() })
        useRow.addView(toolbarKey("Clear", weight = 0.8f) {
            latestOriginal = ""
            latestRewrite = ""
            keyboardTypedText.clear()
            latestDeleteCount = 0
            latestUsedSelection = false
            latestTeaching = "Select text or type a message, then tap Rewrite."
            buildKeyboard()
        })
        panel.addView(useRow)

        root.addView(panel)
    }

    private fun runRewrite(mode: RewriteMode) {
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty().trim()
        val typedByKeyboard = keyboardTypedText.toString().trim()
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(2000, 0)?.toString().orEmpty().trim()

        val source = when {
            selected.isNotBlank() -> selected
            typedByKeyboard.isNotBlank() -> typedByKeyboard
            else -> beforeCursor
        }.trim()
        latestUsedSelection = selected.isNotBlank()
        latestDeleteCount = if (latestUsedSelection) selected.length else source.length

        if (source.isBlank()) {
            latestOriginal = ""
            latestRewrite = ""
            latestTeaching = "No text found. Type a message first, paste text into the field, or select text, then tap Rewrite."
            buildKeyboard()
            return
        }

        latestOriginal = source
        latestRewrite = "Fine-tuning for clarity…"
        latestTeaching = longMessageCheck(source)
        buildKeyboard()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val apiKey = prefs.getString(PREF_CLAUDE_API_KEY, "").orEmpty().trim()
        val consent = prefs.getBoolean(PREF_AI_CONSENT, false)
        if (!consent) {
            latestRewrite = createRewriteResult(source, NeuroProfile.AUTO, mode.toRewriteStyle(), RewriteDirection.ND_TO_NT)
            latestTeaching = appendLongMessageCheck(
                fallbackTeaching(
                    source,
                    NeuroProfile.AUTO,
                    mode.toRewriteStyle(),
                    RewriteDirection.ND_TO_NT,
                    "Turn on AI processing consent in the ToneLayer app to use live rewrites."
                ),
                source
            )
            buildKeyboard()
            return
        }
        if (apiKey.isBlank()) {
            latestRewrite = createRewriteResult(source, NeuroProfile.AUTO, mode.toRewriteStyle(), RewriteDirection.ND_TO_NT)
            latestTeaching = appendLongMessageCheck(
                fallbackTeaching(
                    source,
                    NeuroProfile.AUTO,
                    mode.toRewriteStyle(),
                    RewriteDirection.ND_TO_NT,
                    "Add your Claude API key in the ToneLayer app to use live rewrites."
                ),
                source
            )
            buildKeyboard()
            return
        }

        Thread {
            val result = runCatching { callClaude(apiKey, source, mode) }
            Handler(Looper.getMainLooper()).post {
                result.onSuccess {
                    latestRewrite = it.first
                    latestTeaching = appendLongMessageCheck(it.second, source)
                }.onFailure {
                    latestRewrite = createRewriteResult(source, NeuroProfile.AUTO, mode.toRewriteStyle(), RewriteDirection.ND_TO_NT)
                    latestTeaching = appendLongMessageCheck(
                        fallbackTeaching(
                            source,
                            NeuroProfile.AUTO,
                            mode.toRewriteStyle(),
                            RewriteDirection.ND_TO_NT,
                            friendlyClaudeFailure(it)
                        ),
                        source
                    )
                }
                buildKeyboard()
            }
        }.start()
    }

    private fun useLatestRewrite() {
        if (latestRewrite.isBlank()) {
            latestTeaching = "Tap Rewrite first, then Use Rewrite."
            buildKeyboard()
            return
        }

        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        if (selected.isNotBlank() || latestUsedSelection) {
            currentInputConnection?.commitText(latestRewrite, 1)
        } else if (latestDeleteCount > 0) {
            currentInputConnection?.deleteSurroundingText(latestDeleteCount, 0)
            currentInputConnection?.commitText(latestRewrite, 1)
        } else {
            currentInputConnection?.commitText(latestRewrite, 1)
        }
        keyboardTypedText.clear()
        keyboardTypedText.append(latestRewrite)
        latestOriginal = latestRewrite
        latestDeleteCount = latestRewrite.length
        latestUsedSelection = false
    }

    private fun createRewrite(text: String, mode: RewriteMode): String {
        return createRewriteResult(text, NeuroProfile.AUTO, mode.toRewriteStyle(), RewriteDirection.ND_TO_NT)
    }

    private fun createTeaching(text: String, mode: RewriteMode): String {
        return fallbackTeaching(text, NeuroProfile.AUTO, mode.toRewriteStyle(), RewriteDirection.ND_TO_NT)
    }

    private fun longMessageCheck(text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return if (text.length >= 700 || words >= 120) {
            "This is getting long for a text. Are you okay? If this is turning into a novel, try Make Brief before sending."
        } else ""
    }

    private fun callClaude(apiKey: String, text: String, mode: RewriteMode): Pair<String, String> {
        val style = when (mode) {
            RewriteMode.CLEAR -> "Clarify the message while preserving meaning."
            RewriteMode.SHORTER -> "Make the message brief and easier to act on."
            RewriteMode.WARMER -> "Soften the tone while preserving meaning."
            RewriteMode.DIRECT -> "Make the message direct and clear without sounding harsh."
        }
        val system = """
            You are ToneLayer, a communication assistant. The sender is neurodivergent and wants the message to land clearly with a neurotypical reader. Rewrite the user's message so it is clearer, easier to receive, and matched to the requested style. Do not shame the user. Preserve meaning. Return ONLY valid JSON with keys rewrite and teaching. Teaching should explain briefly what was translated for NT readability.
            Style: $style
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 2048)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "Message:\n$text\n\nReply with ONLY valid JSON.")
            }))
        }

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("Content-Type", "application/json")
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw ClaudeHttpException(conn.responseCode, response)
        val content = JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
        val cleaned = extractJson(content)
        val parsed = JSONObject(cleaned)
        return Pair(parsed.optString("rewrite", text), parsed.optString("teaching", "Fine-tuned for clarity."))
    }

    private fun extractJson(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.substringAfter('\n').removeSuffix("```").trim()
        }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    private fun addLetterRow(letters: String, sideInsetWeight: Float) {
        val row = horizontalRow(heightDp = 44)
        if (sideInsetWeight > 0f) row.addView(spacer(sideInsetWeight))
        letters.forEach { letter ->
            row.addView(letterKey(displayLetter(letter.toString())) {
                commitText(displayLetter(letter.toString()))
                if (isShifted) {
                    isShifted = false
                    buildKeyboard()
                }
            })
        }
        if (sideInsetWeight > 0f) row.addView(spacer(sideInsetWeight))
        root.addView(row)
    }

    private fun addBottomLetterRow() {
        val row = horizontalRow(heightDp = 44)
        row.addView(utilityKey("⇧", weight = 1.35f) {
            isShifted = !isShifted
            buildKeyboard()
        })
        row.addView(spacer(0.15f))
        "zxcvbnm".forEach { letter ->
            row.addView(letterKey(displayLetter(letter.toString())) {
                commitText(displayLetter(letter.toString()))
                if (isShifted) {
                    isShifted = false
                    buildKeyboard()
                }
            })
        }
        row.addView(spacer(0.15f))
        row.addView(utilityKey("⌫", weight = 1.35f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            if (keyboardTypedText.isNotEmpty()) {
                keyboardTypedText.deleteCharAt(keyboardTypedText.length - 1)
            }
        })
        root.addView(row)
    }

    private fun addUtilityRow() {
        val row = horizontalRow(heightDp = 44)
        row.addView(utilityKey("123", weight = 1.25f) { commitText("123") })
        row.addView(utilityKey(",", weight = 0.8f) { commitText(",") })
        row.addView(letterKey("space", weight = 4.4f) { commitText(" ") })
        row.addView(utilityKey(".", weight = 0.8f) { commitText(".") })
        row.addView(utilityKey("return", weight = 1.45f) { commitText("\n") })
        root.addView(row)
    }

    private fun displayLetter(letter: String): String {
        return if (isShifted) letter.uppercase() else letter.lowercase()
    }

    private fun horizontalRow(heightDp: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        }
    }

    private fun spacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun letterKey(label: String, weight: Float = 1f, onClick: () -> Unit): TextView {
        return keyView(label, weight, Color.WHITE, Color.rgb(28, 28, 30), if (label == "space") 15f else 20f, 9f, onClick)
    }

    private fun utilityKey(label: String, weight: Float = 1f, onClick: () -> Unit): TextView {
        return keyView(label, weight, Color.rgb(172, 179, 188), Color.rgb(28, 28, 30), 14f, 9f, onClick)
    }

    private fun toolbarKey(label: String, weight: Float = 1f, onClick: () -> Unit): TextView {
        return keyView(label, weight, Color.rgb(245, 245, 247), Color.rgb(54, 54, 57), 13f, 16f, onClick)
    }

    private fun keyView(
        label: String,
        weight: Float,
        backgroundColor: Int,
        textColor: Int,
        textSize: Float,
        radius: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(textSize)
            typeface = Typeface.DEFAULT
            background = roundedBackground(backgroundColor, radius)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        keyboardTypedText.append(text)
    }

    private fun appendLongMessageCheck(teaching: String, source: String): String {
        val lengthNote = longMessageCheck(source)
        return if (lengthNote.isBlank()) teaching else "$teaching $lengthNote"
    }

    private enum class RewriteMode {
        CLEAR,
        SHORTER,
        WARMER,
        DIRECT
    }

    private fun RewriteMode.toRewriteStyle(): RewriteStyle {
        return when (this) {
            RewriteMode.CLEAR -> RewriteStyle.CLEAR
            RewriteMode.SHORTER -> RewriteStyle.SHORTER
            RewriteMode.WARMER -> RewriteStyle.WARMER
            RewriteMode.DIRECT -> RewriteStyle.DIRECT
        }
    }
}
