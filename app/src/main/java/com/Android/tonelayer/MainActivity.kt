// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.Android.tonelayer.features.shared.DailyTip
import com.Android.tonelayer.features.shared.LogStore
import com.Android.tonelayer.features.shared.RewriteEntry
import com.Android.tonelayer.features.shared.ToneLayerBlue
import com.Android.tonelayer.features.shared.NeutralGray
import com.Android.tonelayer.features.shared.FeatureColors
import com.Android.tonelayer.features.shared.todayTip
import com.Android.tonelayer.features.tonelayer.RewriteLevel
import com.Android.tonelayer.features.tonelayer.buildToneLayerSystem
import com.Android.tonelayer.features.tonelayer.createToneLayerAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ─── Shared prefs keys ────────────────────────────────────────────────────────

internal const val PREFS_NAME          = "tonelayer_clarity_prefs"
internal const val PREF_CLAUDE_API_KEY = "claude_api_key"
internal const val PREF_AI_CONSENT     = "ai_processing_consent"
internal const val PREF_SHOW_TEACHING  = "show_teaching_boxes"
internal const val PREF_SENDER_LENS    = "sender_lens"
internal const val PREF_REWRITE_LEVEL  = "rewrite_level"
internal const val PREF_SPIRAL_PAUSE   = "spiral_pause_enabled"
internal const val PREF_SHOW_EXPL      = "show_explanation"

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class NeuroProfile(val displayName: String, val description: String) {
    AUTO(    "Auto",          "Infer · Detect friction · Choose strategy"),
    ADHD(    "ADHD",          "Tighten · Add structure · Cut tangents"),
    AUTISM(  "Autism",        "Add warmth · Decode · Literalize · Tone-tag"),
    PTSD(    "PTSD/CPTSD",    "De-escalate · Boundary set · Decompress"),
    ADHD_PTSD("ADHD + PTSD", "Blended modes for both profiles"),
    AUTISM_PTSD("Autism + PTSD","Blended modes for both profiles"),
    MIXED(   "Mixed / Not Sure","Broad ND-to-NT lens, no diagnosis required")
}

enum class ToneLayerSection(val label: String) { TONELAYER("ToneLayer"), SETTINGS("Settings") }

fun ToneLayerSection.featureColors(): FeatureColors = when (this) {
    ToneLayerSection.TONELAYER -> ToneLayerBlue
    ToneLayerSection.SETTINGS  -> NeutralGray
}

// ─── Result model ─────────────────────────────────────────────────────────────

data class AndroidRewriteResult(
    val rewrite: String,
    val grammarOnly: String,
    val explanation: String,
    val distortions: List<String>
) {
    val isSpiraling: Boolean get() = distortions.isNotEmpty()
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ToneLayerApp() }
    }
}

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun ToneLayerApp() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }

    // Settings state
    var apiKey          by remember { mutableStateOf(prefs.getString(PREF_CLAUDE_API_KEY, "") ?: "") }
    var aiConsent       by remember { mutableStateOf(prefs.getBoolean(PREF_AI_CONSENT, false)) }
    var showTeaching    by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_TEACHING, true)) }
    var showExplanation by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_EXPL, true)) }
    var spiralPause     by remember { mutableStateOf(prefs.getBoolean(PREF_SPIRAL_PAUSE, true)) }
    var profile         by remember {
        mutableStateOf(
            runCatching { NeuroProfile.valueOf(prefs.getString(PREF_SENDER_LENS, NeuroProfile.AUTO.name)!!) }
                .getOrDefault(NeuroProfile.AUTO)
        )
    }
    var level by remember {
        mutableStateOf(
            runCatching { RewriteLevel.valueOf(prefs.getString(PREF_REWRITE_LEVEL, RewriteLevel.MEDIUM.name)!!) }
                .getOrDefault(RewriteLevel.MEDIUM)
        )
    }

    // Composer state
    var selectedSection by remember { mutableStateOf(ToneLayerSection.TONELAYER) }
    var inputText       by remember { mutableStateOf("") }
    var isRewriting     by remember { mutableStateOf(false) }
    var status          by remember { mutableStateOf("") }

    // Output state
    var composerOriginal    by remember { mutableStateOf("") }
    var composerGrammar     by remember { mutableStateOf("") }
    var composerNT          by remember { mutableStateOf("") }
    var composerExplanation by remember { mutableStateOf("") }
    var composerDistortions by remember { mutableStateOf(listOf<String>()) }
    var selectedOutput      by remember { mutableStateOf("NT version") }
    var showSpiralCard      by remember { mutableStateOf(false) }

    // Log state
    var logEntries by remember { mutableStateOf(listOf<RewriteEntry>()) }

    // Load log on start
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val entries = LogStore.load(context)
            withContext(Dispatchers.Main) { logEntries = entries }
        }
    }

    val activeColors = selectedSection.featureColors()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(activeColors.surface, Color.White, activeColors.soft))
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            AppHeader(activeColors = activeColors, hasApiKey = apiKey.isNotBlank(), aiConsent = aiConsent)
            Spacer(Modifier.height(12.dp))

            // Section tabs
            SectionSwitch(selected = selectedSection, onSelect = { selectedSection = it })
            Spacer(Modifier.height(16.dp))

            when (selectedSection) {
                ToneLayerSection.TONELAYER -> {
                    // Daily tip
                    DailyTipCard(tip = todayTip(), featureColors = ToneLayerBlue)
                    Spacer(Modifier.height(16.dp))

                    // Composer
                    ComposerCard(
                        inputText       = inputText,
                        level           = level,
                        isRewriting     = isRewriting,
                        status          = status,
                        featureColors   = ToneLayerBlue,
                        onInputChange   = { inputText = it },
                        onLevelChange   = { l ->
                            level = l
                            prefs.edit { putString(PREF_REWRITE_LEVEL, l.name) }
                        },
                        onPaste = {
                            val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.primaryClip?.getItemAt(0)?.text?.toString()?.let { inputText = it }
                        },
                        onClear = {
                            inputText = ""
                            composerOriginal = ""; composerGrammar = ""; composerNT = ""
                            composerExplanation = ""; composerDistortions = emptyList()
                            showSpiralCard = false; status = ""
                        },
                        onRewrite = {
                            val input = inputText.trim()
                            if (input.isBlank()) { status = "Type something first"; return@ComposerCard }
                            isRewriting = true
                            status = "Rewriting ${input.length} chars…"
                            composerOriginal = input
                            composerGrammar = ""; composerNT = ""
                            composerExplanation = ""; composerDistortions = emptyList()
                            showSpiralCard = false
                            selectedOutput = "NT version"

                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (!aiConsent || apiKey.isBlank()) {
                                        AndroidRewriteResult(
                                            rewrite = input,
                                            grammarOnly = input,
                                            explanation = "Add your Claude API key and enable AI processing in Settings to use live rewrites.",
                                            distortions = emptyList()
                                        )
                                    } else {
                                        runCatching { callClaudeForApp(apiKey.trim(), input, profile, level) }
                                            .getOrElse { err ->
                                                AndroidRewriteResult(
                                                    rewrite = input,
                                                    grammarOnly = input,
                                                    explanation = friendlyClaudeFailure(err),
                                                    distortions = emptyList()
                                                )
                                            }
                                    }
                                }
                                isRewriting = false
                                composerGrammar     = result.grammarOnly.ifBlank { input }
                                composerNT          = result.rewrite
                                composerExplanation = result.explanation
                                composerDistortions = result.distortions
                                status = "Ready"

                                if (spiralPause && result.isSpiraling) {
                                    showSpiralCard = true
                                }

                                // Save to log
                                withContext(Dispatchers.IO) {
                                    LogStore.append(context, RewriteEntry(
                                        profile      = profile.displayName,
                                        level        = level.displayName,
                                        originalText = input,
                                        rewrittenText = result.rewrite,
                                        explanation  = result.explanation,
                                        distortions  = result.distortions,
                                        spiraling    = result.isSpiraling
                                    ))
                                    val updated = LogStore.load(context)
                                    withContext(Dispatchers.Main) { logEntries = updated }
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))

                    // Spiral card
                    if (showSpiralCard) {
                        SpiralCard(
                            onAsIs   = { showSpiralCard = false },
                            onGrammar = { selectedOutput = "Grammar only"; showSpiralCard = false },
                            onNT     = { selectedOutput = "NT version"; showSpiralCard = false }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Output card
                    val hasOutput = composerNT.isNotBlank()
                    if (hasOutput) {
                        OutputCard(
                            original        = composerOriginal,
                            grammarOnly     = composerGrammar,
                            ntVersion       = composerNT,
                            selectedOutput  = selectedOutput,
                            featureColors   = ToneLayerBlue,
                            onTabSelect     = { selectedOutput = it },
                            onCopy = {
                                val text = selectedOutputText(selectedOutput, composerOriginal, composerGrammar, composerNT)
                                copyToClipboard(context, text)
                                status = "Copied"
                            },
                            onShare = {
                                val text = selectedOutputText(selectedOutput, composerOriginal, composerGrammar, composerNT)
                                shareText(context, text)
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // Explanation card
                    if (hasOutput && showExplanation && composerExplanation.isNotBlank()) {
                        ExplanationCard(explanation = composerExplanation, featureColors = ToneLayerBlue)
                        Spacer(Modifier.height(16.dp))
                    }

                    // Log card
                    LogCard(entries = logEntries, featureColors = ToneLayerBlue)
                    Spacer(Modifier.height(60.dp))
                }

                ToneLayerSection.SETTINGS -> {
                    SettingsScreen(
                        apiKey          = apiKey,
                        aiConsent       = aiConsent,
                        showTeaching    = showTeaching,
                        showExplanation = showExplanation,
                        spiralPause     = spiralPause,
                        profile         = profile,
                        level           = level,
                        onApiKeyChange  = { apiKey = it; prefs.edit { putString(PREF_CLAUDE_API_KEY, it.trim()) } },
                        onConsentChange = { aiConsent = it; prefs.edit { putBoolean(PREF_AI_CONSENT, it) } },
                        onTeachingChange = { showTeaching = it; prefs.edit { putBoolean(PREF_SHOW_TEACHING, it) } },
                        onExplChange     = { showExplanation = it; prefs.edit { putBoolean(PREF_SHOW_EXPL, it) } },
                        onSpiralChange   = { spiralPause = it; prefs.edit { putBoolean(PREF_SPIRAL_PAUSE, it) } },
                        onProfileChange  = { profile = it; prefs.edit { putString(PREF_SENDER_LENS, it.name) } },
                        onLevelChange    = { level = it; prefs.edit { putString(PREF_REWRITE_LEVEL, it.name) } },
                        onOpenKeyboard   = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    )
                    Spacer(Modifier.height(60.dp))
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
fun AppHeader(activeColors: FeatureColors, hasApiKey: Boolean, aiConsent: Boolean) {
    Column {
        Text("ToneLayer", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = activeColors.primary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            StatusPill("Mode", "ND → NT", activeColors, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatusPill("Live AI", if (aiConsent && hasApiKey) "On" else "Local", activeColors, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Brush.horizontalGradient(listOf(activeColors.primary, activeColors.secondary)))
        )
    }
}

@Composable
fun StatusPill(label: String, value: String, colors: FeatureColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = colors.soft, shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = Color.Gray)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        }
    }
}

// ─── Section switch ───────────────────────────────────────────────────────────

@Composable
fun SectionSwitch(selected: ToneLayerSection, onSelect: (ToneLayerSection) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        ToneLayerSection.entries.forEachIndexed { i, section ->
            val isSelected = selected == section
            val colors     = section.featureColors()
            if (isSelected) {
                Button(onClick = { onSelect(section) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) { Text(section.label) }
            } else {
                OutlinedButton(onClick = { onSelect(section) }, modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                ) { Text(section.label) }
            }
            if (i < ToneLayerSection.entries.size - 1) Spacer(Modifier.width(8.dp))
        }
    }
}

// ─── Daily tip ────────────────────────────────────────────────────────────────

@Composable
fun DailyTipCard(tip: DailyTip, featureColors: FeatureColors) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = featureColors.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("✨  FYI of the day", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = featureColors.primary)
            Spacer(Modifier.height(6.dp))
            Text(tip.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(tip.body, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF374151))
        }
    }
}

// ─── Composer ─────────────────────────────────────────────────────────────────

@Composable
fun ComposerCard(
    inputText: String,
    level: RewriteLevel,
    isRewriting: Boolean,
    status: String,
    featureColors: FeatureColors,
    onInputChange: (String) -> Unit,
    onLevelChange: (RewriteLevel) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onRewrite: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = featureColors.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Composer", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = featureColors.primary, modifier = Modifier.weight(1f))
                if (inputText.isNotEmpty()) {
                    Text("${inputText.length} chars", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Level picker (L / M / S)
            Row(Modifier.fillMaxWidth()) {
                RewriteLevel.entries.forEachIndexed { i, l ->
                    val selected = level == l
                    if (selected) {
                        Button(
                            onClick = { onLevelChange(l) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                        ) { Text(l.shortLabel, fontWeight = FontWeight.Bold) }
                    } else {
                        OutlinedButton(
                            onClick = { onLevelChange(l) },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, featureColors.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = featureColors.primary)
                        ) { Text(l.shortLabel) }
                    }
                    if (i < RewriteLevel.entries.size - 1) Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (level) {
                    RewriteLevel.LIGHT  -> "L — Small adjustments, keep your wording close"
                    RewriteLevel.MEDIUM -> "M — Restructured for NT readers while sounding like you"
                    RewriteLevel.STRONG -> "S — Full ND-to-NT translation"
                },
                fontSize = 12.sp, color = Color.Gray
            )
            Spacer(Modifier.height(12.dp))

            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                placeholder = { Text("Type or paste the brain dump…", color = Color.Gray) }
            )
            Spacer(Modifier.height(8.dp))

            // Paste / Clear
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) { Text("Paste") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), enabled = inputText.isNotEmpty()) { Text("Clear") }
            }
            Spacer(Modifier.height(10.dp))

            // Rewrite button
            Button(
                onClick = onRewrite,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRewriting && inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRewriting) featureColors.primary.copy(alpha = 0.5f) else featureColors.primary
                )
            ) {
                if (isRewriting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Rewriting…", fontWeight = FontWeight.Bold)
                } else {
                    Text("✨  Rewrite", fontWeight = FontWeight.Bold)
                }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// ─── Spiral card ──────────────────────────────────────────────────────────────

@Composable
fun SpiralCard(onAsIs: () -> Unit, onGrammar: () -> Unit, onNT: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))) {
        Column(Modifier.padding(14.dp)) {
            Text("💚  Pause for a sec?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("Your text has some patterns that might land differently than you intend.", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAsIs, modifier = Modifier.weight(1f)) { Text("As-is", fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = onGrammar, modifier = Modifier.weight(1f)) { Text("Grammar", fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onNT, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) { Text("NT", fontSize = 12.sp, color = Color.White) }
            }
        }
    }
}

// ─── Output card ──────────────────────────────────────────────────────────────

@Composable
fun OutputCard(
    original: String,
    grammarOnly: String,
    ntVersion: String,
    selectedOutput: String,
    featureColors: FeatureColors,
    onTabSelect: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val tabs = listOf("Original", "Grammar only", "NT version")
    val displayText = selectedOutputText(selectedOutput, original, grammarOnly, ntVersion)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = featureColors.surface)) {
        Column(Modifier.padding(16.dp)) {
            // Tab row
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, tab ->
                    val selected = selectedOutput == tab
                    if (selected) {
                        Button(onClick = { onTabSelect(tab) }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) { Text(tab, fontSize = 11.sp, maxLines = 1) }
                    } else {
                        OutlinedButton(onClick = { onTabSelect(tab) }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, featureColors.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = featureColors.primary),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) { Text(tab, fontSize = 11.sp, maxLines = 1) }
                    }
                    if (i < tabs.size - 1) Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            SelectionContainer {
                Text(displayText, fontSize = 16.sp, lineHeight = 24.sp)
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                Button(onClick = onCopy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = featureColors.primary)
                ) { Text("Copy") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, featureColors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = featureColors.primary)
                ) { Text("Share") }
            }
        }
    }
}

// ─── Explanation card ─────────────────────────────────────────────────────────

@Composable
fun ExplanationCard(explanation: String, featureColors: FeatureColors) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = featureColors.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("💡  Teaching explanation", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = featureColors.primary)
            Spacer(Modifier.height(6.dp))
            Text(explanation, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

// ─── Log card ─────────────────────────────────────────────────────────────────

@Composable
fun LogCard(entries: List<RewriteEntry>, featureColors: FeatureColors) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = featureColors.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rewrite Log", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = featureColors.primary, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    Text("${entries.size} entries", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (entries.isEmpty()) {
                Text("Your rewrite history will appear here after your first rewrite.", fontSize = 13.sp, color = Color.Gray)
            } else {
                entries.takeLast(10).reversed().forEach { entry ->
                    LogRow(entry = entry, featureColors = featureColors)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun LogRow(entry: RewriteEntry, featureColors: FeatureColors) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = featureColors.primary.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                    Text(entry.profile, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("·  ${entry.level}", fontSize = 11.sp, color = Color.Gray)
            }
            if (entry.explanation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.explanation, fontSize = 13.sp)
            }
            Spacer(Modifier.height(3.dp))
            val preview = entry.rewrittenText.take(100) + if (entry.rewrittenText.length > 100) "…" else ""
            Text(preview, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
        }
    }
}

// ─── Settings ─────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    apiKey: String,
    aiConsent: Boolean,
    showTeaching: Boolean,
    showExplanation: Boolean,
    spiralPause: Boolean,
    profile: NeuroProfile,
    level: RewriteLevel,
    onApiKeyChange: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onTeachingChange: (Boolean) -> Unit,
    onExplChange: (Boolean) -> Unit,
    onSpiralChange: (Boolean) -> Unit,
    onProfileChange: (NeuroProfile) -> Unit,
    onLevelChange: (RewriteLevel) -> Unit,
    onOpenKeyboard: () -> Unit
) {
    Column {
        // API + Privacy
        ApiPrivacyCard(apiKey = apiKey, aiConsent = aiConsent,
            onApiKeyChange = onApiKeyChange, onConsentChange = onConsentChange)
        Spacer(Modifier.height(16.dp))

        // Profile
        ProfileCard(selected = profile, featureColors = ToneLayerBlue, onSelect = onProfileChange)
        Spacer(Modifier.height(16.dp))

        // NT Level
        LevelCard(selected = level, featureColors = ToneLayerBlue, onSelect = onLevelChange)
        Spacer(Modifier.height(16.dp))

        // Spiral Pause
        SpiralPauseCard(enabled = spiralPause, onChange = onSpiralChange)
        Spacer(Modifier.height(16.dp))

        // Teaching explanations
        ToggleCard(
            title = "Teaching Explanations",
            description = "Show a short note explaining what changed and why. Turn this off when you only want the rewrite.",
            enabled = showExplanation,
            onChange = onExplChange
        )
        Spacer(Modifier.height(16.dp))

        // Keyboard
        KeyboardCard(showTeaching = showTeaching, onTeachingChange = onTeachingChange, onOpenKeyboard = onOpenKeyboard)
    }
}

@Composable
fun ApiPrivacyCard(apiKey: String, aiConsent: Boolean, onApiKeyChange: (String) -> Unit, onConsentChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Claude API Key", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Your key is stored locally. Get yours at console.anthropic.com.", fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = apiKey, onValueChange = onApiKeyChange, modifier = Modifier.fillMaxWidth(),
                label = { Text("sk-ant-…") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = aiConsent, onCheckedChange = onConsentChange)
                Text("I understand and consent to AI processing for rewrites.", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ProfileCard(selected: NeuroProfile, featureColors: FeatureColors, onSelect: (NeuroProfile) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Pick the profile that matches how you communicate.", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            NeuroProfile.entries.forEach { p ->
                val isSelected = selected == p
                OutlinedButton(
                    onClick = { onSelect(p) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) featureColors.primary else featureColors.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) featureColors.soft else Color.Transparent,
                        contentColor   = featureColors.primary
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.Start) {
                        Text(p.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        Text(p.description, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun LevelCard(selected: RewriteLevel, featureColors: FeatureColors, onSelect: (RewriteLevel) -> Unit) {
    val descriptions = mapOf(
        RewriteLevel.LIGHT  to "Small ND-to-NT adjustments: fixes clarity, grammar, and tone while keeping your wording close.",
        RewriteLevel.MEDIUM to "Balanced ND-to-NT rewrite: restructures the message for NT readers while still sounding like you.",
        RewriteLevel.STRONG to "Full ND-to-NT translation: concise, direct, emotionally neutral, and easy for NT readers to act on."
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("NT Level", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Choose how strongly ToneLayer translates ND speech into NT speech.", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            RewriteLevel.entries.forEach { l ->
                val isSelected = selected == l
                OutlinedButton(
                    onClick = { onSelect(l) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) featureColors.primary else featureColors.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) featureColors.soft else Color.Transparent,
                        contentColor   = featureColors.primary
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.Start) {
                        Text(l.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        Text(descriptions[l] ?: "", fontSize = 11.sp, color = Color.Gray, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpiralPauseCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spiral Pause", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Before rewriting, ToneLayer checks if your text shows cognitive distortions (catastrophizing, all-or-nothing, mind-reading, etc.). If it does, it pauses and offers a calmer draft.",
                fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ToggleCard(title: String, description: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            Spacer(Modifier.height(6.dp))
            Text(description, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
        }
    }
}

@Composable
fun KeyboardCard(showTeaching: Boolean, onTeachingChange: (Boolean) -> Unit, onOpenKeyboard: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Keyboard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Enable ToneLayer as a keyboard to rewrite text from any app.", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showTeaching, onCheckedChange = onTeachingChange)
                Text("Show teaching boxes in keyboard", fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpenKeyboard, modifier = Modifier.fillMaxWidth()) {
                Text("Open Keyboard Settings")
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun selectedOutputText(tab: String, original: String, grammar: String, nt: String): String = when (tab) {
    "Original"     -> original
    "Grammar only" -> grammar.ifBlank { original }
    else           -> nt.ifBlank { grammar.ifBlank { original } }
}

fun copyToClipboard(context: android.content.Context, text: String) {
    val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("ToneLayer rewrite", text))
}

fun shareText(context: android.content.Context, text: String) {
    context.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "Send rewrite")
    )
}

fun incrementMetric(prefs: android.content.SharedPreferences, key: String, amount: Int = 1) {
    val fullKey = "metrics.$key"
    prefs.edit { putInt(fullKey, prefs.getInt(fullKey, 0) + amount) }
}

// ─── Claude API ───────────────────────────────────────────────────────────────

fun callClaudeForApp(
    apiKey: String,
    text: String,
    profile: NeuroProfile,
    level: RewriteLevel
): AndroidRewriteResult {
    val system = buildToneLayerSystem(profile, level)
    val body = JSONObject().apply {
        put("model", "claude-haiku-4-5-20251001")
        put("max_tokens", 8192)
        put("system", system)
        put("messages", JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", "Text:\n$text\n\nReply with ONLY valid JSON.")
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
    val parsed  = JSONObject(extractJsonObject(content))

    // Build rewrite from paragraphs array or rewrite string
    val rewrite = parsed.optJSONArray("paragraphs")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }.joinToString("\n\n")
    }?.ifBlank { null } ?: parsed.optString("rewrite", "").ifBlank { text }

    val distortionsArr = parsed.optJSONArray("distortions")
    val distortions = if (distortionsArr != null) {
        (0 until distortionsArr.length()).map { distortionsArr.getString(it) }.filter { it.isNotBlank() }
    } else emptyList()

    return AndroidRewriteResult(
        rewrite     = rewrite,
        grammarOnly = parsed.optString("grammar_only", "").ifBlank { text },
        explanation = parsed.optString("explanation", ""),
        distortions = distortions
    )
}

class ClaudeHttpException(val statusCode: Int, private val responseBody: String) :
    Exception("Claude API request failed with HTTP $statusCode") {
    fun bodyLowercase(): String = responseBody.lowercase()
}

fun friendlyClaudeFailure(error: Throwable): String {
    val message = error.localizedMessage.orEmpty().lowercase()
    val body    = (error as? ClaudeHttpException)?.bodyLowercase().orEmpty()
    return when {
        (error is ClaudeHttpException && error.statusCode == 401) ||
            "authentication" in message || "x-api-key" in body ->
            "The saved Claude API key is invalid. Open Settings, paste a valid Anthropic key, then try again. Showing original text for now."
        error is ClaudeHttpException && error.statusCode == 429 ->
            "Claude is rate-limiting requests right now. Showing original text for now."
        error is ClaudeHttpException && error.statusCode in 500..599 ->
            "Claude is having a server issue right now. Showing original text for now."
        else -> "Live rewrite is unavailable right now. Showing original text for now."
    }
}

fun extractJsonObject(raw: String): String {
    var s = raw.trim()
    if (s.startsWith("```")) s = s.substringAfter('\n').removeSuffix("```").trim()
    val start = s.indexOf('{'); val end = s.lastIndexOf('}')
    return if (start >= 0 && end > start) s.substring(start, end + 1) else s
}
