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
import com.Android.tonelayer.features.tonelayer.RewriteLevel
import com.Android.tonelayer.features.tonelayer.buildToneLayerSystem
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ToneLayer keyboard — QWERTY layout with L/M/S level buttons and ND→NT rewrite.
 */
private const val PREFS_NAME          = "tonelayer_clarity_prefs"
private const val PREF_CLAUDE_API_KEY = "claude_api_key"
private const val PREF_AI_CONSENT     = "ai_processing_consent"
private const val PREF_SHOW_TEACHING  = "show_teaching_boxes"
private const val PREF_SENDER_LENS    = "sender_lens"
private const val PREF_REWRITE_LEVEL  = "rewrite_level"
private const val PREF_SPIRAL_PAUSE   = "spiral_pause_enabled"

private val kbPurple     = Color.rgb(29,  78,  216)  // royal blue
private val kbGreen      = Color.rgb(5,   150, 105)  // emerald green
private val kbPurpleSoft = Color.rgb(239, 246, 255)  // light blue
private val kbGreenSoft  = Color.rgb(236, 253, 245)  // light green

class ToneLayerKeyboardService : InputMethodService() {

    private var isShifted         = false
    private var latestOriginal    = ""
    private var latestRewrite     = ""
    private var latestGrammar     = ""
    private var latestExplanation = ""
    private var latestDistortions = listOf<String>()
    private var showSpiral        = false
    private var keyboardTypedText = StringBuilder()
    private var latestDeleteCount = 0
    private var latestUsedSelection = false
    private lateinit var root: LinearLayout

    private val currentLevel: RewriteLevel
        get() = runCatching {
            RewriteLevel.valueOf(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(PREF_REWRITE_LEVEL, RewriteLevel.MEDIUM.name) ?: RewriteLevel.MEDIUM.name
            )
        }.getOrDefault(RewriteLevel.MEDIUM)

    private val currentProfile: NeuroProfile
        get() = runCatching {
            NeuroProfile.valueOf(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(PREF_SENDER_LENS, NeuroProfile.AUTO.name) ?: NeuroProfile.AUTO.name
            )
        }.getOrDefault(NeuroProfile.AUTO)

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            setBackgroundColor(Color.rgb(207, 211, 217))
        }
        buildKeyboard()
        return root
    }

    // ─── Layout ───────────────────────────────────────────────────────────────

    private fun buildKeyboard() {
        root.removeAllViews()
        addLevelToolbar()
        addRewritePanel()
        if (showSpiral) addSpiralPanel()
        addLetterRow("qwertyuiop", sideInsetWeight = 0f)
        addLetterRow("asdfghjkl",  sideInsetWeight = 0.45f)
        addBottomLetterRow()
        addUtilityRow()
    }

    private fun addLevelToolbar() {
        val row = horizontalRow(heightDp = 38)
        val level = currentLevel
        RewriteLevel.entries.forEach { l ->
            val isSelected = l == level
            row.addView(
                keyView(
                    label = l.shortLabel,
                    weight = 1f,
                    backgroundColor = if (isSelected) kbPurple else Color.rgb(210, 210, 220),
                    textColor = if (isSelected) Color.WHITE else Color.rgb(60, 60, 80),
                    textSize = 14f,
                    radius = 10f
                ) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putString(PREF_REWRITE_LEVEL, l.name).apply()
                    buildKeyboard()
                }
            )
        }
        // Rewrite button
        row.addView(
            keyView(
                label = "Rewrite",
                weight = 2.5f,
                backgroundColor = kbPurple,
                textColor = Color.WHITE,
                textSize = 13f,
                radius = 14f,
                useGradient = true
            ) { runRewrite() }
        )
        root.addView(row)
    }

    private fun addRewritePanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBackground(kbPurpleSoft, kbGreenSoft, 12f)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(3), dp(2), dp(5)) }
        }

        panel.addView(labelText(getString(R.string.rewrite_box_label)))
        panel.addView(bodyText(
            latestRewrite.ifBlank { "Tap Rewrite to generate a clearer version." },
            maxLines = 2
        ))

        if (showTeachingEnabled() && latestExplanation.isNotBlank()) {
            panel.addView(labelText("💡  Why this change", topPad = 4))
            panel.addView(bodyText(latestExplanation, maxLines = 2, color = Color.rgb(60, 60, 67)))
        }

        val useRow = horizontalRow(heightDp = 34)
        useRow.addView(toolbarKey("Use Rewrite", weight = 1.4f) { useLatestRewrite() })
        useRow.addView(toolbarKey("Clear", weight = 0.8f) {
            latestOriginal = ""; latestRewrite = ""; latestGrammar = ""
            latestExplanation = ""; latestDistortions = emptyList()
            showSpiral = false; keyboardTypedText.clear()
            latestDeleteCount = 0; latestUsedSelection = false
            buildKeyboard()
        })
        panel.addView(useRow)
        root.addView(panel)
    }

    private fun addSpiralPanel() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBackground(Color.rgb(220, 252, 231), Color.rgb(236, 253, 245), 12f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), 0, dp(2), dp(5)) }
        }
        panel.addView(labelText("💚  Pause for a sec? Your text may land differently than you intend."))

        val btnRow = horizontalRow(heightDp = 34)
        btnRow.addView(toolbarKey("As-is", weight = 1f) {
            showSpiral = false; buildKeyboard()
        })
        btnRow.addView(toolbarKey("Grammar", weight = 1f) {
            if (latestGrammar.isNotBlank()) {
                latestDeleteCount = latestOriginal.length
                currentInputConnection?.deleteSurroundingText(latestDeleteCount, 0)
                currentInputConnection?.commitText(latestGrammar, 1)
                keyboardTypedText.clear().append(latestGrammar)
            }
            showSpiral = false; buildKeyboard()
        })
        btnRow.addView(toolbarKey("NT", weight = 1f, bg = kbGreen) {
            if (latestRewrite.isNotBlank()) {
                val count = if (latestUsedSelection) latestDeleteCount else latestOriginal.length
                currentInputConnection?.deleteSurroundingText(count, 0)
                currentInputConnection?.commitText(latestRewrite, 1)
                keyboardTypedText.clear().append(latestRewrite)
            }
            showSpiral = false; buildKeyboard()
        })
        panel.addView(btnRow)
        root.addView(panel)
    }

    // ─── Rewrite logic ────────────────────────────────────────────────────────

    private fun runRewrite() {
        val selected   = currentInputConnection?.getSelectedText(0)?.toString().orEmpty().trim()
        val typedText  = keyboardTypedText.toString().trim()
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(2000, 0)?.toString().orEmpty().trim()

        val source = when {
            selected.isNotBlank() -> selected
            typedText.isNotBlank() -> typedText
            else -> beforeCursor
        }.trim()
        latestUsedSelection = selected.isNotBlank()
        latestDeleteCount   = source.length

        if (source.isBlank()) {
            latestRewrite = "No text found. Type or select some text first."
            buildKeyboard(); return
        }

        latestOriginal    = source
        latestRewrite     = "Rewriting…"
        latestGrammar     = ""
        latestExplanation = ""
        latestDistortions = emptyList()
        showSpiral        = false
        buildKeyboard()

        val prefs   = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val apiKey  = prefs.getString(PREF_CLAUDE_API_KEY, "").orEmpty().trim()
        val consent = prefs.getBoolean(PREF_AI_CONSENT, false)
        val profile = currentProfile
        val level   = currentLevel

        if (!consent || apiKey.isBlank()) {
            latestRewrite     = source
            latestExplanation = if (!consent) "Enable AI processing in the ToneLayer app." else "Add your Claude API key in the ToneLayer app."
            buildKeyboard(); return
        }

        Thread {
            val result = runCatching { callClaudeKeyboard(apiKey, source, profile, level) }
            Handler(Looper.getMainLooper()).post {
                result.onSuccess { r ->
                    latestRewrite     = r.rewrite
                    latestGrammar     = r.grammarOnly
                    latestExplanation = r.explanation
                    latestDistortions = r.distortions
                    val spiralEnabled = prefs.getBoolean(PREF_SPIRAL_PAUSE, true)
                    showSpiral = spiralEnabled && r.distortions.isNotEmpty()
                }.onFailure {
                    latestRewrite     = source
                    latestExplanation = friendlyClaudeFailure(it)
                }
                buildKeyboard()
            }
        }.start()
    }

    private fun useLatestRewrite() {
        if (latestRewrite.isBlank() || latestRewrite == "Rewriting…") {
            latestExplanation = "Wait for the rewrite to finish, then tap Use Rewrite."
            buildKeyboard(); return
        }
        if (latestUsedSelection) {
            currentInputConnection?.commitText(latestRewrite, 1)
        } else if (latestDeleteCount > 0) {
            currentInputConnection?.deleteSurroundingText(latestDeleteCount, 0)
            currentInputConnection?.commitText(latestRewrite, 1)
        } else {
            currentInputConnection?.commitText(latestRewrite, 1)
        }
        keyboardTypedText.clear().append(latestRewrite)
        latestOriginal    = latestRewrite
        latestDeleteCount = latestRewrite.length
        latestUsedSelection = false
    }

    // ─── Claude call ──────────────────────────────────────────────────────────

    private data class KeyboardResult(
        val rewrite: String,
        val grammarOnly: String,
        val explanation: String,
        val distortions: List<String>
    )

    private fun callClaudeKeyboard(
        apiKey: String,
        text: String,
        profile: NeuroProfile,
        level: RewriteLevel
    ): KeyboardResult {
        val system = buildToneLayerSystem(profile, level)
        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 4096)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "Text:\n$text\n\nReply with ONLY valid JSON.")
            }))
        }

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000; readTimeout = 90000; doOutput = true
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("Content-Type", "application/json")
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val stream   = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw ClaudeHttpException(conn.responseCode, response)

        val content = JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
        val parsed  = JSONObject(extractJsonObject(content))

        val rewrite = parsed.optJSONArray("paragraphs")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }.joinToString("\n\n")
        }?.ifBlank { null } ?: parsed.optString("rewrite", "").ifBlank { text }

        val distArr = parsed.optJSONArray("distortions")
        val distortions = if (distArr != null) {
            (0 until distArr.length()).map { distArr.getString(it) }.filter { it.isNotBlank() }
        } else emptyList()

        return KeyboardResult(
            rewrite     = rewrite,
            grammarOnly = parsed.optString("grammar_only", "").ifBlank { text },
            explanation = parsed.optString("explanation", ""),
            distortions = distortions
        )
    }

    // ─── Key rows ─────────────────────────────────────────────────────────────

    private fun addLetterRow(letters: String, sideInsetWeight: Float) {
        val row = horizontalRow(heightDp = 44)
        if (sideInsetWeight > 0f) row.addView(spacer(sideInsetWeight))
        letters.forEach { letter ->
            row.addView(letterKey(displayLetter(letter.toString())) {
                commitText(displayLetter(letter.toString()))
                if (isShifted) { isShifted = false; buildKeyboard() }
            })
        }
        if (sideInsetWeight > 0f) row.addView(spacer(sideInsetWeight))
        root.addView(row)
    }

    private fun addBottomLetterRow() {
        val row = horizontalRow(heightDp = 44)
        row.addView(utilityKey("⇧", 1.35f) { isShifted = !isShifted; buildKeyboard() })
        row.addView(spacer(0.15f))
        "zxcvbnm".forEach { l ->
            row.addView(letterKey(displayLetter(l.toString())) {
                commitText(displayLetter(l.toString()))
                if (isShifted) { isShifted = false; buildKeyboard() }
            })
        }
        row.addView(spacer(0.15f))
        row.addView(utilityKey("⌫", 1.35f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            if (keyboardTypedText.isNotEmpty()) keyboardTypedText.deleteCharAt(keyboardTypedText.length - 1)
        })
        root.addView(row)
    }

    private fun addUtilityRow() {
        val row = horizontalRow(heightDp = 44)
        row.addView(utilityKey("123", 1.25f) { commitText("123") })
        row.addView(utilityKey(",",   0.80f) { commitText(",") })
        row.addView(letterKey("space", weight = 4.4f) { commitText(" ") })
        row.addView(utilityKey(".",    0.80f) { commitText(".") })
        row.addView(utilityKey("return", 1.45f) { commitText("\n") })
        root.addView(row)
    }

    // ─── View helpers ─────────────────────────────────────────────────────────

    private fun displayLetter(letter: String) = if (isShifted) letter.uppercase() else letter.lowercase()

    private fun horizontalRow(heightDp: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity     = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)
        )
    }

    private fun spacer(weight: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
    }

    private fun letterKey(label: String, weight: Float = 1f, onClick: () -> Unit) =
        keyView(label, weight, Color.WHITE, Color.rgb(28, 28, 30),
            if (label == "space") 15f else 20f, 9f, onClick = onClick)

    private fun utilityKey(label: String, weight: Float = 1f, onClick: () -> Unit) =
        keyView(label, weight, Color.rgb(172, 179, 188), Color.rgb(28, 28, 30), 14f, 9f, onClick = onClick)

    private fun toolbarKey(label: String, weight: Float = 1f, bg: Int = kbPurple, onClick: () -> Unit) =
        keyView(label, weight, bg, Color.WHITE, 13f, 16f, useGradient = false, onClick = onClick)

    private fun labelText(text: String, topPad: Int = 0) = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(28, 28, 30))
        setTextSize(13f)
        typeface = Typeface.DEFAULT_BOLD
        if (topPad > 0) setPadding(0, dp(topPad), 0, 0)
    }

    private fun bodyText(text: String, maxLines: Int, color: Int = Color.rgb(28, 28, 30)) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(14f)
        this.maxLines = maxLines
    }

    private fun keyView(
        label: String,
        weight: Float,
        backgroundColor: Int,
        textColor: Int,
        textSize: Float,
        radius: Float,
        useGradient: Boolean = false,
        onClick: () -> Unit
    ) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setTextSize(textSize)
        typeface = Typeface.DEFAULT
        background = if (useGradient)
            gradientBackground(backgroundColor, kbGreen, radius)
        else
            roundedBackground(backgroundColor, radius)
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun gradientBackground(start: Int, end: Int, radiusDp: Float) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        keyboardTypedText.append(text)
    }

    private fun showTeachingEnabled() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_SHOW_TEACHING, true)
}
