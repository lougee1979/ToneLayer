// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.neurobridge

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToneLayerClarityApp()
        }
    }
}

private const val PREFS_NAME = "tonelayer_clarity_prefs"
private const val PREF_CLAUDE_API_KEY = "claude_api_key"
private const val PREF_AI_CONSENT = "ai_processing_consent"


enum class ClarityLens {
    ADHD,
    AUTISM,
    PTSD,
    MIXED,
    AUTO
}

enum class RewriteStyle(val buttonLabel: String, val resultTitle: String) {
    CLEAR("Clarify", "Clearer rewrite"),
    SHORTER("Shorter", "Shorter rewrite"),
    WARMER("Warmer", "Warmer rewrite"),
    DIRECT("Direct", "More direct rewrite"),
    SOFTER("Soften", "Softer rewrite")
}

@Composable
fun ToneLayerClarityApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString(PREF_CLAUDE_API_KEY, "") ?: "") }
    var aiConsent by remember { mutableStateOf(prefs.getBoolean(PREF_AI_CONSENT, false)) }
    var inputText by remember {
        mutableStateOf(
            "Hey so I've been thinking about what you said the other night and I think we should probably talk at some point."
        )
    }
    var selectedLens by remember { mutableStateOf(ClarityLens.AUTO) }
    var rewriteTitle by remember { mutableStateOf("Clearer rewrite") }
    var rewriteText by remember { mutableStateOf("Your clearer version will appear here.") }
    var teachingText by remember { mutableStateOf(createClarityResult(inputText, selectedLens)) }
    var isRewriting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F4F4))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "ToneLayer Clarity",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            AndroidSetupCard(
                apiKey = apiKey,
                aiConsent = aiConsent,
                onApiKeyChange = {
                    apiKey = it
                    prefs.edit().putString(PREF_CLAUDE_API_KEY, it).apply()
                },
                onConsentChange = {
                    aiConsent = it
                    prefs.edit().putBoolean(PREF_AI_CONSENT, it).apply()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Lens Mode", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LensSelector(
                selectedLens = selectedLens,
                onLensSelected = {
                    selectedLens = it
                    teachingText = createClarityResult(inputText, it)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    teachingText = createClarityResult(it, selectedLens)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("Message") }
            )

            MessageLengthFlag(inputText)

            Spacer(modifier = Modifier.height(16.dp))

            RewriteTools(
                enabled = !isRewriting,
                onRewriteSelected = { style ->
                    val input = inputText.trim()
                    if (input.isBlank()) {
                        status = "Enter a message first"
                        return@RewriteTools
                    }
                    rewriteTitle = style.resultTitle
                    isRewriting = true
                    status = "Fine-tuning for clarity…"
                    incrementMetric(prefs, "android.app.rewrite.requested")
                    incrementMetric(prefs, "android.app.rewrite.style.${style.name}")
                    if (input.length >= 700 || input.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 120) {
                        incrementMetric(prefs, "android.app.longMessage.flagged")
                    }
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (!aiConsent || apiKey.isBlank()) {
                                AndroidRewriteResult(
                                    rewrite = createRewriteResult(input, selectedLens, style),
                                    teaching = "Add your Claude API key and enable AI processing in Privacy + API to use live structured rewrites."
                                )
                            } else {
                                runCatching { callClaudeForApp(apiKey, input, selectedLens, style) }
                                    .getOrElse {
                                        AndroidRewriteResult(
                                            rewrite = createRewriteResult(input, selectedLens, style),
                                            teaching = "Live rewrite failed, so this is a local fallback. ${it.localizedMessage ?: ""}"
                                        )
                                    }
                            }
                        }
                        rewriteText = result.rewrite
                        teachingText = result.teaching
                        isRewriting = false
                        status = "Ready"
                        incrementMetric(prefs, "android.app.rewrite.success")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            if (status.isNotBlank()) {
                Text(status, fontSize = 13.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = rewriteTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = rewriteText,
                            fontSize = 17.sp,
                            lineHeight = 26.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                copyToClipboard(context, rewriteText)
                                incrementMetric(prefs, "android.app.rewrite.copied")
                                incrementMetric(prefs, "android.app.rewrite.accepted")
                                status = "Copied"
                            },
                            modifier = Modifier.weight(1f),
                            enabled = rewriteText.isNotBlank() && !rewriteText.startsWith("Your clearer")
                        ) { Text("Copy") }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                shareText(context, rewriteText)
                                incrementMetric(prefs, "android.app.rewrite.shared")
                                incrementMetric(prefs, "android.app.rewrite.accepted")
                            },
                            modifier = Modifier.weight(1f),
                            enabled = rewriteText.isNotBlank() && !rewriteText.startsWith("Your clearer")
                        ) { Text("Share") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Teaching Explanation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = teachingText,
                            fontSize = 17.sp,
                            lineHeight = 26.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun AndroidSetupCard(
    apiKey: String,
    aiConsent: Boolean,
    onApiKeyChange: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Privacy + API", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "ToneLayer sends the message text you choose to clarify to the AI provider so it can rewrite it. Do not use private secrets, passwords, or medical record numbers in test messages.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = aiConsent, onCheckedChange = onConsentChange)
                Text("I understand and consent to AI processing for rewrites.", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Claude API key") },
                singleLine = true
            )
        }
    }
}

@Composable
fun MessageLengthFlag(inputText: String) {
    val words = inputText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    val chars = inputText.length
    val isLong = chars >= 700 || words >= 120
    Text(
        text = "$chars chars • $words words",
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(top = 6.dp)
    )
    if (isLong) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D6))
        ) {
            Text(
                text = "This is getting long for a text. Are you okay? If this is turning into a novel, try Clarify or Make Brief before sending.",
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun RewriteTools(enabled: Boolean = true, onRewriteSelected: (RewriteStyle) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rewrite Tools",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.CLEAR) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                ) { Text(RewriteStyle.CLEAR.buttonLabel) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.SHORTER) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                ) { Text(RewriteStyle.SHORTER.buttonLabel) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.WARMER) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                ) { Text(RewriteStyle.WARMER.buttonLabel) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.DIRECT) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                ) { Text(RewriteStyle.DIRECT.buttonLabel) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onRewriteSelected(RewriteStyle.SOFTER) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) { Text(RewriteStyle.SOFTER.buttonLabel) }
        }
    }
}

@Composable
fun LensSelector(
    selectedLens: ClarityLens,
    onLensSelected: (ClarityLens) -> Unit
) {
    Column {
        ClarityLens.entries.forEach { lens ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedLens == lens,
                    onClick = { onLensSelected(lens) }
                )
                Text(text = lens.name)
            }
        }
    }
}


data class AndroidRewriteResult(
    val rewrite: String,
    val teaching: String
)

fun incrementMetric(prefs: android.content.SharedPreferences, key: String, amount: Int = 1) {
    val fullKey = "metrics.$key"
    prefs.edit()
        .putInt(fullKey, prefs.getInt(fullKey, 0) + amount)
        .putLong("metrics.lastUpdated", System.currentTimeMillis())
        .apply()
}

fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ToneLayer rewrite", text))
}

fun shareText(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Send rewrite"))
}

fun callClaudeForApp(
    apiKey: String,
    text: String,
    lens: ClarityLens,
    style: RewriteStyle
): AndroidRewriteResult {
    val lensInstruction = when (lens) {
        ClarityLens.ADHD -> "ADHD: reduce working-memory load, surface the main point first, use clear next steps, remove side quests and buried asks."
        ClarityLens.AUTISM -> "Autism: make implied meaning explicit, use concrete expectations, add social context without changing the user’s meaning."
        ClarityLens.PTSD -> "PTSD/CPTSD: lower threat signals, preserve boundaries, reduce defensiveness, add calm reassurance where appropriate."
        ClarityLens.MIXED -> "Mixed: rewrite for overlapping ADHD, autistic, PTSD/CPTSD, and anxiety-related communication needs. Make the main point obvious first. Reduce working-memory load. Make implied meaning explicit. Remove vague timing or social hints. Lower threat signals and defensive wording. Include reassurance when appropriate. End with one clear next step."
        ClarityLens.AUTO -> "Auto: identify ambiguity, missing context, unclear urgency, tone mismatch, and unclear next steps."
    }
    val styleInstruction = when (style) {
        RewriteStyle.CLEAR -> "Clarify: produce a polished, sendable rewrite with structure."
        RewriteStyle.SHORTER -> "Make Brief: produce a concise rewrite that keeps only the essential point and next step."
        RewriteStyle.WARMER -> "Soften Tone: produce a warmer rewrite that preserves meaning and reduces accidental harshness."
        RewriteStyle.DIRECT -> "Be Direct: produce a direct rewrite with the ask and next step clearly stated, without sounding harsh."
        RewriteStyle.SOFTER -> "Soften: produce a gentle, lower-pressure rewrite."
    }
    val system = """
        You are ToneLayer Clarity, an AI communication assistant.
        Rewrite the user's message into a sendable version.
        Preserve meaning. Do not shame the user. Do not add fake facts.
        Add structure: short paragraphs or bullets only when useful.
        $lensInstruction
        $styleInstruction
        Return ONLY valid JSON with keys:
        {
          "rewrite": "sendable rewritten message",
          "teaching": "brief explanation of what changed and why it improves clarity"
        }
    """.trimIndent()

    val body = JSONObject().apply {
        put("model", "claude-haiku-4-5-20251001")
        put("max_tokens", 4096)
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
    if (conn.responseCode !in 200..299) error("API failed ${conn.responseCode}: ${response.take(180)}")
    val content = JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
    val parsed = JSONObject(extractJsonObject(content))
    return AndroidRewriteResult(
        rewrite = parsed.optString("rewrite", text),
        teaching = parsed.optString("teaching", "Fine-tuned for clarity.")
    )
}

fun extractJsonObject(raw: String): String {
    var s = raw.trim()
    if (s.startsWith("```")) {
        s = s.substringAfter('\n').removeSuffix("```").trim()
    }
    val start = s.indexOf('{')
    val end = s.lastIndexOf('}')
    return if (start >= 0 && end > start) s.substring(start, end + 1) else s
}

fun createRewriteResult(input: String, lens: ClarityLens, style: RewriteStyle): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return "Enter a message above, then tap Clarify."
    }

    val clarityFrame = when (lens) {
        ClarityLens.ADHD -> "clear next steps, lower cognitive load, and less ambiguity"
        ClarityLens.AUTISM -> "explicit context, concrete expectations, and reduced implied meaning"
        ClarityLens.PTSD -> "emotional safety, low threat, and clear reassurance"
        ClarityLens.MIXED -> "main point first, explicit meaning, low ambiguity, low threat, clear timing, and one obvious next step"
        ClarityLens.AUTO -> "clearer intent, timing, tone, and requested action"
    }
    val brief = trimmed.split(Regex("\\s+")).take(28).joinToString(" ")

    return when (style) {
        RewriteStyle.CLEAR -> """
Clear version:

$trimmed

Intent: communicate with $clarityFrame.
""".trimIndent()
        RewriteStyle.SHORTER -> """
Brief version:

$brief
""".trimIndent()
        RewriteStyle.WARMER -> """
Warmer version:

$trimmed

I want this to come across with connection and clarity, not pressure.
""".trimIndent()
        RewriteStyle.DIRECT -> """
Direct version:

$trimmed

Please let me know what works for you.
""".trimIndent()
        RewriteStyle.SOFTER -> """
Softer version:

$trimmed

No pressure to respond immediately — I just wanted to make my intent clear.
""".trimIndent()
    }
}

fun createClarityResult(input: String, lens: ClarityLens): String {
    val message = input.trim().ifEmpty { "the message" }
    return when (lens) {
        ClarityLens.ADHD -> """
ADHD Lens Analysis
This message may create cognitive overload if it lacks concrete structure.

Possible friction points:
- unclear urgency
- unclear next step
- unclear timeline
- too much implied context

For ADHD-friendly clarity, state the topic, urgency, and requested action directly.

Current message being analyzed:
"$message"
""".trimIndent()
        ClarityLens.AUTISM -> """
Autism Lens Analysis
This message may rely on implied emotional context.

Possible friction points:
- unclear purpose
- unclear seriousness
- unclear expected response
- indirect timing

For autistic-friendly clarity, make the topic, intent, and expectation explicit.

Current message being analyzed:
"$message"
""".trimIndent()
        ClarityLens.PTSD -> """
PTSD Lens Analysis
This message may create anticipatory stress if emotional safety is unclear.

Possible friction points:
- undefined conflict level
- unclear urgency
- unclear whether the receiver is in trouble
- open-ended tension

For trauma-aware clarity, include reassurance, safety, and timing.

Current message being analyzed:
"$message"
""".trimIndent()
        ClarityLens.MIXED -> """
Mixed Neurodivergent Lens Analysis
This message may create several kinds of ambiguity at once.

Possible friction points:
- emotional ambiguity
- timeline ambiguity
- expectation ambiguity
- urgency ambiguity

For mixed clarity, assume overlapping ADHD, autistic, PTSD/CPTSD, and anxiety-related needs. Put the main point first, reduce working-memory load, make implied meaning explicit, lower threat signals, define timing, and give one clear next step.

Current message being analyzed:
"$message"
""".trimIndent()
        ClarityLens.AUTO -> """
Automatic Clarity Analysis
This message is checked for ambiguity, emotional uncertainty, missing context, and unclear action steps.

Recommended improvement:
- state the topic
- state urgency level
- state emotional intent
- give a clear next step
- offer a concrete timeline if needed

Current message being analyzed:
"$message"
""".trimIndent()
    }
}
