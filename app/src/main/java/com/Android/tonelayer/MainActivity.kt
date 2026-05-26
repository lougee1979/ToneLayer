// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.provider.Settings
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Android.tonelayer.features.clarity.clarityProfilePrompt
import com.Android.tonelayer.features.shared.FeatureColors
import com.Android.tonelayer.features.shared.NeutralGray
import com.Android.tonelayer.features.shared.ToneLayerBlue
import com.Android.tonelayer.features.tonelayer.createToneLayerAnalysis
import com.Android.tonelayer.features.tonelayer.toneLayerProfilePrompt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToneLayerApp()
        }
    }
}

private const val PREFS_NAME = "tonelayer_clarity_prefs"
private const val PREF_CLAUDE_API_KEY = "claude_api_key"
private const val PREF_AI_CONSENT = "ai_processing_consent"
private const val PREF_SENDER_LENS = "sender_lens"
private const val PREF_SHOW_TEACHING = "show_teaching_boxes"


enum class NeuroProfile(val displayName: String) {
    AUTO("Auto"),
    ADHD("ADHD"),
    AUTISM("Autism"),
    PTSD("PTSD/CPTSD"),
    ADHD_PTSD("ADHD + PTSD"),
    AUTISM_PTSD("Autism + PTSD"),
    MIXED("Mixed / Not Sure")
}

enum class RewriteDirection {
    ND_TO_NT,
    NT_TO_ND
}

enum class RewriteStyle(val buttonLabel: String, val resultTitle: String) {
    CLEAR("Clarify", "NT rewrite"),
    SHORTER("Shorter", "Shorter NT rewrite"),
    WARMER("Warmer", "Warmer NT rewrite"),
    DIRECT("Direct", "Direct NT rewrite"),
    SOFTER("Soften", "Softer NT rewrite")
}

fun storedSenderLens(prefs: android.content.SharedPreferences): NeuroProfile {
    val stored = prefs.getString(PREF_SENDER_LENS, NeuroProfile.AUTO.name) ?: NeuroProfile.AUTO.name
    return runCatching { NeuroProfile.valueOf(stored) }.getOrDefault(NeuroProfile.AUTO)
}

@Composable
fun ToneLayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString(PREF_CLAUDE_API_KEY, "") ?: "") }
    var aiConsent by remember { mutableStateOf(prefs.getBoolean(PREF_AI_CONSENT, false)) }
    var showTeachingBoxes by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_TEACHING, true)) }
    var selectedSection by remember { mutableStateOf(ToneLayerSection.TONELAYER) }
    var toneLayerInput by remember {
        mutableStateOf(
            "I know this is a lot but I keep replaying the conversation and I need to explain what I meant because it feels like everything got tangled."
        )
    }
    var toneLayerProfile by remember { mutableStateOf(storedSenderLens(prefs)) }
    var toneLayerRewriteTitle by remember { mutableStateOf("NT rewrite") }
    var toneLayerRewriteText by remember { mutableStateOf("Your NT rewrite will appear here.") }
    var toneLayerTeachingText by remember { mutableStateOf(createToneLayerAnalysis(toneLayerInput, toneLayerProfile)) }
    var isToneLayerRewriting by remember { mutableStateOf(false) }
    var toneLayerStatus by remember { mutableStateOf("") }
    val activeColors = selectedSection.featureColors()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(activeColors.surface, Color.White, activeColors.soft)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ToneLayerHeader(
                aiConsent = aiConsent,
                hasApiKey = apiKey.isNotBlank(),
                featureColors = activeColors
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToneLayerSectionSwitch(
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedSection) {
                ToneLayerSection.TONELAYER -> {
                    MessageWorkspace(
                        title = "ToneLayer",
                        featureColors = ToneLayerBlue,
                        inputText = toneLayerInput,
                        rewriteTitle = toneLayerRewriteTitle,
                        rewriteText = toneLayerRewriteText,
                        teachingTitle = "Translation Notes",
                        teachingText = toneLayerTeachingText,
                        showTeachingBoxes = showTeachingBoxes,
                        status = toneLayerStatus,
                        isRewriting = isToneLayerRewriting,
                        onInputChange = {
                            toneLayerInput = it
                            toneLayerTeachingText = createToneLayerAnalysis(it, toneLayerProfile)
                        },
                        onRewriteSelected = { style ->
                            val input = toneLayerInput.trim()
                            if (input.isBlank()) {
                                toneLayerStatus = "Enter a message first"
                                return@MessageWorkspace
                            }
                            toneLayerRewriteTitle = style.resultTitle
                            isToneLayerRewriting = true
                            toneLayerStatus = "Translating for NT readability..."
                            requestRewrite(
                                prefs = prefs,
                                scope = scope,
                                apiKey = apiKey,
                                aiConsent = aiConsent,
                                input = input,
                                profile = toneLayerProfile,
                                style = style,
                                direction = RewriteDirection.ND_TO_NT,
                                onResult = {
                                    toneLayerRewriteText = it.rewrite
                                    toneLayerTeachingText = it.teaching
                                    isToneLayerRewriting = false
                                    toneLayerStatus = "Ready"
                                }
                            )
                        },
                        onCopyRewrite = {
                            copyToClipboard(context, toneLayerRewriteText)
                            incrementMetric(prefs, "android.tonelayer.rewrite.copied")
                            incrementMetric(prefs, "android.tonelayer.rewrite.accepted")
                            toneLayerStatus = "Copied"
                        },
                        onShareRewrite = {
                            shareText(context, toneLayerRewriteText)
                            incrementMetric(prefs, "android.tonelayer.rewrite.shared")
                            incrementMetric(prefs, "android.tonelayer.rewrite.accepted")
                        }
                    )
                }
                ToneLayerSection.SETTINGS -> {
                    ToneLayerSettings(
                        apiKey = apiKey,
                        aiConsent = aiConsent,
                        showTeachingBoxes = showTeachingBoxes,
                        senderLens = toneLayerProfile,
                        onApiKeyChange = {
                            apiKey = it
                            prefs.edit().putString(PREF_CLAUDE_API_KEY, it.trim()).apply()
                        },
                        onConsentChange = {
                            aiConsent = it
                            prefs.edit().putBoolean(PREF_AI_CONSENT, it).apply()
                        },
                        onTeachingBoxesChange = {
                            showTeachingBoxes = it
                            prefs.edit().putBoolean(PREF_SHOW_TEACHING, it).apply()
                        },
                        onSenderLensSelected = {
                            toneLayerProfile = it
                            toneLayerTeachingText = createToneLayerAnalysis(toneLayerInput, it)
                            prefs.edit().putString(PREF_SENDER_LENS, it.name).apply()
                        },
                        onOpenKeyboardSettings = {
                            openKeyboardSettings(context)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

enum class ToneLayerSection(val label: String) {
    TONELAYER("ToneLayer"),
    SETTINGS("Settings")
}

fun ToneLayerSection.featureColors(): FeatureColors {
    return when (this) {
        ToneLayerSection.TONELAYER -> ToneLayerBlue
        ToneLayerSection.SETTINGS -> NeutralGray
    }
}

@Composable
fun ToneLayerHeader(aiConsent: Boolean, hasApiKey: Boolean, featureColors: FeatureColors) {
    Column {
        Text(
            text = "ToneLayer",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = featureColors.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusPill(
                label = "Mode",
                value = "ND -> NT",
                featureColors = featureColors,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                label = "Live AI",
                value = if (aiConsent && hasApiKey) "On" else "Local",
                featureColors = featureColors,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        ColorDirectionStrip(featureColors)
    }
}

@Composable
fun StatusPill(label: String, value: String, featureColors: FeatureColors, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = featureColors.soft,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, color = Color.Gray)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = featureColors.primary)
        }
    }
}

@Composable
fun ColorDirectionStrip(featureColors: FeatureColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Brush.horizontalGradient(listOf(featureColors.primary, featureColors.secondary)))
    )
}

@Composable
fun ToneLayerSectionSwitch(
    selectedSection: ToneLayerSection,
    onSectionSelected: (ToneLayerSection) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ToneLayerSection.entries.forEach { section ->
            val selected = selectedSection == section
            val colors = section.featureColors()
            if (selected) {
                Button(
                    onClick = { onSectionSelected(section) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) { Text(section.label) }
            } else {
                OutlinedButton(
                    onClick = { onSectionSelected(section) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                ) { Text(section.label) }
            }
            if (section != ToneLayerSection.entries.last()) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun MessageWorkspace(
    title: String,
    featureColors: FeatureColors,
    inputText: String,
    rewriteTitle: String,
    rewriteText: String,
    teachingTitle: String,
    teachingText: String,
    showTeachingBoxes: Boolean,
    status: String,
    isRewriting: Boolean,
    onInputChange: (String) -> Unit,
    onRewriteSelected: (RewriteStyle) -> Unit,
    onCopyRewrite: () -> Unit,
    onShareRewrite: () -> Unit
) {
    Text(
        text = title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = featureColors.primary
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = inputText,
        onValueChange = onInputChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        label = { Text("Message") }
    )

    MessageLengthFlag(inputText)

    Spacer(modifier = Modifier.height(16.dp))

    RewriteTools(
        enabled = !isRewriting,
        onRewriteSelected = onRewriteSelected,
        featureColors = featureColors
    )

    Spacer(modifier = Modifier.height(8.dp))
    if (status.isNotBlank()) {
        Text(status, fontSize = 13.sp, color = Color.Gray)
    }
    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = featureColors.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = rewriteTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = featureColors.primary
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
                    onClick = onCopyRewrite,
                    modifier = Modifier.weight(1f),
                    enabled = rewriteText.isNotBlank() && !rewriteText.startsWith("Your "),
                    colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                ) { Text("Copy") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onShareRewrite,
                    modifier = Modifier.weight(1f),
                    enabled = rewriteText.isNotBlank() && !rewriteText.startsWith("Your "),
                    border = BorderStroke(1.dp, featureColors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = featureColors.primary)
                ) { Text("Share") }
            }
        }
    }

    if (showTeachingBoxes) {
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = featureColors.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = teachingTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = featureColors.primary
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
    }
}

@Composable
fun ToneLayerSettings(
    apiKey: String,
    aiConsent: Boolean,
    showTeachingBoxes: Boolean,
    senderLens: NeuroProfile,
    onApiKeyChange: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onTeachingBoxesChange: (Boolean) -> Unit,
    onSenderLensSelected: (NeuroProfile) -> Unit,
    onOpenKeyboardSettings: () -> Unit
) {
    Text(
        text = "Settings",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(12.dp))
    AndroidSetupCard(
        apiKey = apiKey,
        aiConsent = aiConsent,
        onApiKeyChange = onApiKeyChange,
        onConsentChange = onConsentChange
    )
    Spacer(modifier = Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sender Lens", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Optional private setting for your communication pattern. Leave this on Auto unless a specific lens helps.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileSelector(
                selectedProfile = senderLens,
                onProfileSelected = onSenderLensSelected,
                featureColors = ToneLayerBlue
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Keyboard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enable ToneLayer as a keyboard to rewrite selected text from other apps.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showTeachingBoxes, onCheckedChange = onTeachingBoxesChange)
                Text("Show teaching boxes in the app and keyboard.", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenKeyboardSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Keyboard Settings")
            }
        }
    }
}

fun openKeyboardSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
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
fun RewriteTools(
    enabled: Boolean = true,
    featureColors: FeatureColors,
    onRewriteSelected: (RewriteStyle) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = featureColors.soft)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rewrite Tools",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = featureColors.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.CLEAR) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                ) { Text(RewriteStyle.CLEAR.buttonLabel) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.SHORTER) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                ) { Text(RewriteStyle.SHORTER.buttonLabel) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.WARMER) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                ) { Text(RewriteStyle.WARMER.buttonLabel) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.DIRECT) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                ) { Text(RewriteStyle.DIRECT.buttonLabel) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onRewriteSelected(RewriteStyle.SOFTER) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                border = BorderStroke(1.dp, featureColors.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = featureColors.primary)
            ) { Text(RewriteStyle.SOFTER.buttonLabel) }
        }
    }
}

@Composable
fun ProfileSelector(
    selectedProfile: NeuroProfile,
    onProfileSelected: (NeuroProfile) -> Unit,
    featureColors: FeatureColors
) {
    Column {
        NeuroProfile.entries.forEach { profile ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedProfile == profile,
                    onClick = { onProfileSelected(profile) },
                    colors = RadioButtonDefaults.colors(selectedColor = featureColors.primary)
                )
                Text(text = profile.displayName)
            }
        }
    }
}


data class AndroidRewriteResult(
    val rewrite: String,
    val teaching: String
)

fun requestRewrite(
    prefs: android.content.SharedPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    apiKey: String,
    aiConsent: Boolean,
    input: String,
    profile: NeuroProfile,
    style: RewriteStyle,
    direction: RewriteDirection,
    onResult: (AndroidRewriteResult) -> Unit
) {
    incrementMetric(prefs, "android.${direction.name.lowercase()}.rewrite.requested")
    incrementMetric(prefs, "android.${direction.name.lowercase()}.rewrite.style.${style.name}")
    if (input.length >= 700 || input.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 120) {
        incrementMetric(prefs, "android.${direction.name.lowercase()}.longMessage.flagged")
    }
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            if (!aiConsent || apiKey.isBlank()) {
                AndroidRewriteResult(
                    rewrite = createRewriteResult(input, profile, style, direction),
                    teaching = fallbackTeaching(
                        input,
                        profile,
                        style,
                        direction,
                        "Add your Claude API key and enable AI processing in Settings to use live structured rewrites."
                    )
                )
            } else {
                runCatching { callClaudeForApp(apiKey.trim(), input, profile, style, direction) }
                    .getOrElse {
                        AndroidRewriteResult(
                            rewrite = createRewriteResult(input, profile, style, direction),
                            teaching = fallbackTeaching(
                                input,
                                profile,
                                style,
                                direction,
                                friendlyClaudeFailure(it)
                            )
                        )
                    }
            }
        }
        onResult(result)
        incrementMetric(prefs, "android.${direction.name.lowercase()}.rewrite.success")
    }
}

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
    profile: NeuroProfile,
    style: RewriteStyle,
    direction: RewriteDirection
): AndroidRewriteResult {
    val profileInstruction = when (direction) {
        RewriteDirection.ND_TO_NT -> toneLayerProfilePrompt(profile)
        RewriteDirection.NT_TO_ND -> clarityProfilePrompt(profile)
    }
    val styleInstruction = when (style) {
        RewriteStyle.CLEAR -> "Clarify: produce a polished, sendable rewrite with structure."
        RewriteStyle.SHORTER -> "Make Brief: produce a concise rewrite that keeps only the essential point and next step."
        RewriteStyle.WARMER -> "Soften Tone: produce a warmer rewrite that preserves meaning and reduces accidental harshness."
        RewriteStyle.DIRECT -> "Be Direct: produce a direct rewrite with the ask and next step clearly stated, without sounding harsh."
        RewriteStyle.SOFTER -> "Soften: produce a gentle, lower-pressure rewrite."
    }
    val directionInstruction = when (direction) {
        RewriteDirection.ND_TO_NT -> """
            Direction: ToneLayer mode. The sender is neurodivergent and wants the message to land clearly with a neurotypical reader.
            Translate ND communication patterns into NT-readable structure without erasing meaning, boundaries, or voice.
            Teaching must explain what was translated for NT expectations, such as main point placement, brevity, emotional regulation, sequencing, or explicit asks.
        """.trimIndent()
        RewriteDirection.NT_TO_ND -> """
            Direction: Clarity mode. The sender is neurotypical and wants the message to land clearly with a neurodivergent reader.
            Rewrite NT speech so it is less indirect, less socially coded, less threatening, and easier for ND readers to parse.
            Teaching must explain how the original NT wording could land for ND readers and why the rewrite is safer or clearer.
        """.trimIndent()
    }
    val system = """
        You are ToneLayer, an AI communication assistant.
        Rewrite the user's message into a sendable version.
        Preserve meaning. Do not shame the user. Do not add fake facts.
        Add structure: short paragraphs or bullets only when useful.
        $directionInstruction
        $profileInstruction
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
    if (conn.responseCode !in 200..299) throw ClaudeHttpException(conn.responseCode, response)
    val content = JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
    val parsed = JSONObject(extractJsonObject(content))
    return AndroidRewriteResult(
        rewrite = parsed.optString("rewrite", text),
        teaching = parsed.optString("teaching", "Fine-tuned for clarity.")
    )
}

class ClaudeHttpException(
    val statusCode: Int,
    private val responseBody: String
) : Exception("Claude API request failed with HTTP $statusCode") {
    fun bodyLowercase(): String = responseBody.lowercase()
}

fun friendlyClaudeFailure(error: Throwable): String {
    val message = error.localizedMessage.orEmpty().lowercase()
    val body = (error as? ClaudeHttpException)?.bodyLowercase().orEmpty()
    return when {
        error is ClaudeHttpException && error.statusCode == 401 ||
            "authentication" in message ||
            "authentication" in body ||
            "x-api-key" in message ||
            "x-api-key" in body -> {
            "The saved Claude API key is invalid. Open Privacy + API, paste a valid Anthropic Claude key, then try again. Showing a local rewrite for now."
        }
        error is ClaudeHttpException && error.statusCode == 429 -> {
            "Claude is rate-limiting requests right now. Showing a local rewrite for now."
        }
        error is ClaudeHttpException && error.statusCode in 500..599 -> {
            "Claude is having a server issue right now. Showing a local rewrite for now."
        }
        else -> {
            "Live rewrite is unavailable right now. Showing a local rewrite for now."
        }
    }
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

fun createRewriteResult(input: String, profile: NeuroProfile, style: RewriteStyle, direction: RewriteDirection): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return "Enter a message above, then tap Clarify."
    }

    val lower = trimmed.lowercase()
    if (direction == RewriteDirection.NT_TO_ND && (lower == "we need to talk" || lower == "we need to talk.")) {
        return when (style) {
            RewriteStyle.CLEAR -> "Can we set aside a few minutes to talk about something specific? I do not want to leave this vague or stressful. I would like to explain what is on my mind and agree on next steps."
            RewriteStyle.SHORTER -> "Can we set a time to talk about something specific? I want to be clear and avoid making this feel vague."
            RewriteStyle.WARMER -> "Can we talk when you have a little time? I want to explain something clearly, and I do not want the message to sound alarming or vague."
            RewriteStyle.DIRECT -> "Can we set a time to talk about [topic]? I want to discuss what happened and decide what to do next."
            RewriteStyle.SOFTER -> "When you have the space, could we talk about something specific? Nothing needs to be solved this second; I just want to make sure we understand each other."
        }
    }

    val clarityFrame = when (profile) {
        NeuroProfile.ADHD -> "clear next steps, lower cognitive load, and less ambiguity"
        NeuroProfile.AUTISM -> "explicit context, concrete expectations, and reduced implied meaning"
        NeuroProfile.PTSD -> "emotional safety, low threat, and clear reassurance"
        NeuroProfile.AUTISM_PTSD -> "explicit expectations, emotional safety, reduced threat, and concrete next steps"
        NeuroProfile.ADHD_PTSD -> "main point first, lower urgency, reduced cognitive load, and one concrete next step"
        NeuroProfile.MIXED -> "main point first, explicit meaning, low ambiguity, low threat, clear timing, and one obvious next step"
        NeuroProfile.AUTO -> "clearer intent, timing, tone, and requested action"
    }
    val brief = trimmed.split(Regex("\\s+")).take(28).joinToString(" ")
    val directionLabel = when (direction) {
        RewriteDirection.ND_TO_NT -> "NT-readable version"
        RewriteDirection.NT_TO_ND -> "ND-clear version"
    }

    return when (style) {
        RewriteStyle.CLEAR -> """
$directionLabel:

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

fun fallbackTeaching(
    input: String,
    profile: NeuroProfile,
    style: RewriteStyle,
    direction: RewriteDirection,
    setupOrFailureMessage: String? = null
): String {
    val specificNote = if (direction == RewriteDirection.NT_TO_ND && input.trim().lowercase().removeSuffix(".") == "we need to talk") {
        "The original phrase can create anxiety because it does not say the topic, urgency, emotional intent, or requested action. The rewrite names that a conversation is needed, lowers the threat level, and asks for a concrete time."
    } else {
        val styleNote = when (direction) {
            RewriteDirection.ND_TO_NT -> when (style) {
                RewriteStyle.CLEAR -> "The rewrite keeps the sender's meaning but organizes it for NT expectations."
                RewriteStyle.SHORTER -> "The rewrite reduces processing friction by keeping the main point, need, and next step."
                RewriteStyle.WARMER -> "The rewrite adds social warmth so the message is less likely to be read as blunt or tense."
                RewriteStyle.DIRECT -> "The rewrite makes the ask easier for an NT reader to act on."
                RewriteStyle.SOFTER -> "The rewrite lowers pressure while preserving the original need."
            }
            RewriteDirection.NT_TO_ND -> when (style) {
                RewriteStyle.CLEAR -> "The rewrite makes NT subtext explicit so the ND reader does not have to infer the real point."
                RewriteStyle.SHORTER -> "The rewrite reduces working-memory load and keeps the requested action easy to find."
                RewriteStyle.WARMER -> "The rewrite adds reassurance and context so warmth is stated instead of implied."
                RewriteStyle.DIRECT -> "The rewrite states the topic, request, and timing more concretely."
                RewriteStyle.SOFTER -> "The rewrite lowers threat signals and avoids open-ended tension."
            }
        }
        val profileNote = when (profile) {
            NeuroProfile.ADHD -> "It also puts the useful action closer to the front."
            NeuroProfile.AUTISM -> "It also makes implied meaning more explicit."
            NeuroProfile.PTSD -> "It also reduces threat signals and uncertainty."
            NeuroProfile.AUTISM_PTSD -> "It also combines explicit expectations with lower-threat wording."
            NeuroProfile.ADHD_PTSD -> "It also reduces urgency, sequencing load, and emotional ambiguity."
            NeuroProfile.MIXED -> "It also reduces ambiguity, threat signals, and working-memory load."
            NeuroProfile.AUTO -> "It also clarifies intent, timing, tone, and requested action."
        }
        "$styleNote $profileNote"
    }
    return listOfNotNull(setupOrFailureMessage, specificNote).joinToString("\n\n")
}
