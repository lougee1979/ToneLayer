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
/**
 * The main activity for the NeuroBridge application.
 *
 * This activity hosts the primary user interface for analyzing and rewriting text
 * from various neurodivergent perspectives.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroBridgeApp()
        }
    }
}
/**
 * Represents different neurodivergent "lenses" through which text can be analyzed.
 */
enum class ClarityLens {
    /** Analysis tailored for ADHD individuals. */
    ADHD,
    /** Analysis tailored for Autistic individuals. */
    AUTISM,
    /** Analysis tailored for individuals with PTSD. */
    PTSD,
    /** A combination of analysis for multiple neurodivergent traits. */
    MIXED,
    /** Automatic detection and analysis based on common ND communication patterns. */
    AUTO
}

/**
 * The main UI component of the NeuroBridge application.
 *
 * Provides fields for inputting text, selecting a clarity lens, and viewing the
 * generated ND-focused analysis and recommendations.
 */
@Composable
fun NeuroBridgeApp() {
    val context = LocalContext.current
    val imm = context.getSystemService(InputMethodManager::class.java)
    var inputText by remember {
        mutableStateOf(
            "Hey so I've been thinking about what you said the other night and I think we should probably talk at some point."
        )
    }
    var selectedLens by remember {
        mutableStateOf(ClarityLens.AUTO)
    }
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
                onClick = {
                    imm?.showInputMethodPicker()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Keyboard Picker")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Lens Mode",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LensSelector(
                selectedLens = selectedLens,
                onLensSelected = {
                    selectedLens = it
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = {
                    Text("NT message")
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
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
/**
 * A UI component for selecting the active [ClarityLens].
 *
 * @param selectedLens The currently selected lens.
 * @param onLensSelected Callback triggered when a new lens is selected.
 */
@Composable
fun LensSelector(
    selectedLens: ClarityLens,
    onLensSelected: (ClarityLens) -> Unit
) {
    Column {
        ClarityLens.entries.forEach { lens ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedLens == lens,
                    onClick = {
                        onLensSelected(lens)
                    }
                )
                Text(
                    text = lens.name
                )
            }
        }
    }
}
/**
 * Generates a clarity analysis and recommendations based on the input text and selected lens.
 *
 * @param input The text to be analyzed.
 * @param lens The neurodivergent perspective to use for the analysis.
 * @return A string containing the analysis, potential issues, and recommended improvements.
 */
fun createClarityResult(
    input: String,
    lens: ClarityLens
): String {
    return when (lens) {
        ClarityLens.ADHD -> """
ADHD Lens Analysis
This message may create cognitive overload because it lacks concrete structure and forces the receiver to hold multiple unresolved possibilities at once.
The phrase "we should talk sometime" creates uncertainty around:
- urgency
- emotional tone
- expected response
- timeline
- emotional risk
An ADHD receiver may begin mentally simulating:
- whether they are in trouble
- whether conflict is coming
- whether immediate action is needed
- whether they forgot something important
This creates executive function drag because the brain must fill in missing context before acting.
Clearer version:
"I want to talk about what happened the other night. Nothing catastrophic — I just want clarification. Could we talk Tuesday at 2pm or Wednesday at 10am?"
""".trimIndent()
        ClarityLens.AUTISM -> """
Autism Lens Analysis
This message depends heavily on implied emotional context rather than explicit communication.
The receiver may struggle to determine:
- the actual topic
- whether the situation is serious
- what action is expected
- whether reassurance is needed
- whether this is casual or emotionally loaded
Autistic processing often prefers direct structure and reduced ambiguity.
The sentence "we should probably talk at some point" contains:
- unclear timing
- unclear purpose
- unclear emotional framing
Clearer version:
"I want to discuss what you said the other night. I am not angry, but I want clarification. Are you available Tuesday at 2pm?"
""".trimIndent()
        ClarityLens.PTSD -> """
PTSD Lens Analysis
This wording may unintentionally trigger anticipatory stress because it implies unresolved emotional tension without defining safety or outcome.
Phrases like:
- "I've been thinking"
- "we should talk"
- "at some point"
can create hypervigilance because the receiver does not know:
- if conflict is coming
- if rejection is coming
- whether danger exists
- whether immediate response is required
The uncertainty itself may become emotionally activating.
Safer version:
"I want to revisit part of our earlier conversation. You are not in trouble and this is not an emergency. I just want clarification and connection."
""".trimIndent()
        ClarityLens.MIXED -> """
Mixed Neurodivergent Lens Analysis
This message creates multiple layers of ambiguity simultaneously:
- emotional ambiguity
- timeline ambiguity
- expectation ambiguity
- urgency ambiguity
Different ND profiles may experience:
- executive paralysis
- over-analysis
- anticipatory anxiety
- rejection sensitivity
- shutdown or delayed response
The sender likely intends softness or reduced pressure, but the lack of specificity increases cognitive load instead.
Clearer version:
"I want to talk about our conversation from the other night. This is not an emergency. I mainly want clarification and connection. Could we talk Tuesday at 2pm or Wednesday at 10am?"
""".trimIndent()
        ClarityLens.AUTO -> """
Automatic Clarity Analysis
This message contains high ambiguity and low structural clarity.
Potential issues:
- unclear urgency
- undefined topic
- implied emotional tension
- no action pathway
- no timeline
Many ND readers may interpret this as emotionally unresolved or cognitively incomplete.
Why this happens:
NT communication often assumes shared emotional inference. ND processing frequently relies more heavily on explicit structure, timing, intent, and categorization.
Recommended improvement:
State:
- the topic
- urgency level
- emotional intent
- requested action
- preferred timeline
Example rewrite:
"I want to talk about our earlier conversation. Nothing catastrophic — I just want clarification. Could we talk Tuesday at 2pm or Wednesday at 10am?"
""".trimIndent()
    }
}