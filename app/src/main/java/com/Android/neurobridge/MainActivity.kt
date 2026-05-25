package com.Android.neurobridge

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroBridgeApp()
        }
    }
}

enum class ClarityLens {
    ADHD,
    AUTISM,
    PTSD,
    MIXED,
    AUTO
}

enum class RewriteStyle(val buttonLabel: String, val resultTitle: String) {
    CLEAR("Rewrite", "Clearer rewrite"),
    SHORTER("Shorter", "Shorter rewrite"),
    WARMER("Warmer", "Warmer rewrite"),
    DIRECT("Direct", "More direct rewrite"),
    SOFTER("Soften", "Softer rewrite")
}

@Composable
fun NeuroBridgeApp() {
    val context = LocalContext.current
    val imm = context.getSystemService(InputMethodManager::class.java)
    var inputText by remember {
        mutableStateOf(
            "Hey so I've been thinking about what you said the other night and I think we should probably talk at some point."
        )
    }
    var selectedLens by remember { mutableStateOf(ClarityLens.AUTO) }
    var rewriteTitle by remember { mutableStateOf("Clearer rewrite") }
    var rewriteText by remember { mutableStateOf(createRewriteResult(inputText, selectedLens, RewriteStyle.CLEAR)) }

    val output = createClarityResult(inputText, selectedLens)

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F4F4))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "NeuroBridge Clarity",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { imm?.showInputMethodPicker() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Keyboard Picker")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Lens Mode", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LensSelector(
                selectedLens = selectedLens,
                onLensSelected = {
                    selectedLens = it
                    rewriteText = createRewriteResult(inputText, it, RewriteStyle.CLEAR)
                    rewriteTitle = RewriteStyle.CLEAR.resultTitle
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    rewriteText = createRewriteResult(it, selectedLens, RewriteStyle.CLEAR)
                    rewriteTitle = RewriteStyle.CLEAR.resultTitle
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("NT message") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            RewriteTools(
                onRewriteSelected = { style ->
                    rewriteTitle = style.resultTitle
                    rewriteText = createRewriteResult(inputText, selectedLens, style)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = rewriteTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = rewriteText,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    )
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
                    Text(
                        text = output,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun RewriteTools(onRewriteSelected: (RewriteStyle) -> Unit) {
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
                    modifier = Modifier.weight(1f)
                ) { Text(RewriteStyle.CLEAR.buttonLabel) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.SHORTER) },
                    modifier = Modifier.weight(1f)
                ) { Text(RewriteStyle.SHORTER.buttonLabel) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.WARMER) },
                    modifier = Modifier.weight(1f)
                ) { Text(RewriteStyle.WARMER.buttonLabel) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onRewriteSelected(RewriteStyle.DIRECT) },
                    modifier = Modifier.weight(1f)
                ) { Text(RewriteStyle.DIRECT.buttonLabel) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onRewriteSelected(RewriteStyle.SOFTER) },
                modifier = Modifier.fillMaxWidth()
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

fun createRewriteResult(input: String, lens: ClarityLens, style: RewriteStyle): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return "Enter a message above, then tap Rewrite."
    }

    val clarityFrame = when (lens) {
        ClarityLens.ADHD -> "with clear next steps, lower cognitive load, and less ambiguity"
        ClarityLens.AUTISM -> "with explicit context, concrete expectations, and reduced implied meaning"
        ClarityLens.PTSD -> "with emotional safety, low threat, and clear reassurance"
        ClarityLens.MIXED -> "with clear timing, intent, emotional framing, and action steps"
        ClarityLens.AUTO -> "with clearer intent, timing, tone, and requested action"
    }

    return when (style) {
        RewriteStyle.CLEAR -> "I want to revisit this in a clearer way. My goal is to communicate $clarityFrame. Here is the message I mean to send: $trimmed"
        RewriteStyle.SHORTER -> "Clear version: $trimmed"
        RewriteStyle.WARMER -> "I wanted to say this in a warmer way: $trimmed. My intent is connection and clarity, not pressure."
        RewriteStyle.DIRECT -> "Direct version: $trimmed. Please let me know what works for you."
        RewriteStyle.SOFTER -> "Softer version: $trimmed. No pressure to respond immediately — I just wanted to make my intent clear."
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

For mixed ND clarity, state what this is about, how urgent it is, and what response is needed.

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
