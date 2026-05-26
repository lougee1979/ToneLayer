// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.tonelayer

import com.Android.tonelayer.NeuroProfile

fun toneLayerProfilePrompt(profile: NeuroProfile): String {
    return when (profile) {
        NeuroProfile.AUTO -> """
            Profile: Auto.
            Analyze the message and identify the most likely communication friction without diagnosing the user.
            Look for buried main point, unclear ask, vague timing, emotional intensity, too much context, threat signal, indirect wording, missing reassurance, and unclear next step.
            Choose the most useful ND-to-NT rewrite strategy based on the message itself. Do not mention a diagnosis unless the user selected one. Explain what friction you detected and how the rewrite reduces it for NT readability.
        """.trimIndent()
        NeuroProfile.ADHD -> "Sender profile: ADHD. Keep the main point first, reduce tangents and urgency loops, preserve the useful context, and make the ask concrete."
        NeuroProfile.AUTISM -> "Sender profile: Autism. Preserve precision and boundaries while adding social context, expected response, and enough warmth for NT readers."
        NeuroProfile.PTSD -> "Sender profile: PTSD/CPTSD. Preserve safety needs and boundaries while reducing defensive framing, threat signals, and over-explaining."
        NeuroProfile.AUTISM_PTSD -> "Sender profile: Autism + PTSD. Preserve directness, precision, and safety needs while adding clear context, low-threat wording, and an explicit next step."
        NeuroProfile.ADHD_PTSD -> "Sender profile: ADHD + PTSD. Move the main point up, reduce spirals and defensive urgency, keep valid needs, and add calm sequencing."
        NeuroProfile.MIXED -> """
            Profile: Mixed / Not Sure.
            Apply a broad neurodivergent communication lens without choosing a specific diagnosis.
            Account for possible overlap between ADHD, autism, PTSD/CPTSD, anxiety-related threat sensitivity, working-memory load, literal interpretation, and emotional ambiguity.
            Rewrite the message to make it easier for NT readers by organizing the main point, context, request, timing, and tone. Explain the rewrite in general ND-to-NT translation terms instead of naming one specific profile.
        """.trimIndent()
    }
}

fun createToneLayerAnalysis(input: String, profile: NeuroProfile): String {
    val message = input.trim().ifEmpty { "the message" }
    return when (profile) {
        NeuroProfile.ADHD -> """
ADHD Sender Profile
This message may contain useful context but place the ask later than many NT readers expect.

Possible friction points:
- buried main point
- side quests
- urgency loops
- too many live-processing details

ToneLayer should move the point up, group context, and make the next step concrete.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.AUTISM -> """
Autism Sender Profile
This message may be precise but may not include the social framing NT readers expect.

Possible friction points:
- direct wording may be misread as cold
- precise boundaries may need context
- expected response may be unstated
- emotional intent may be assumed rather than named

ToneLayer should preserve precision while adding social context, warmth, and a clear expected response.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.PTSD -> """
PTSD/CPTSD Sender Profile
This message may carry protective urgency, defensiveness, or over-explanation because safety feels uncertain.

Possible friction points:
- too much pre-defense
- repeated reassurance seeking
- intensity that may hide the core request
- boundaries that need calmer sequencing

ToneLayer should keep the valid need while reducing threat language and making the request steady.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.AUTISM_PTSD -> """
Autism + PTSD Sender Profile
This message may combine precise literal meaning with high threat sensitivity.

Possible friction points:
- directness may be read as harsh
- safety needs may sound like accusation
- important context may be dense
- boundaries may need warmer framing

ToneLayer should preserve precision and safety while adding low-threat wording and one clear next step.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.ADHD_PTSD -> """
ADHD + PTSD Sender Profile
This message may combine urgency, spiraling context, and defensive protection.

Possible friction points:
- main point arrives late
- emotional urgency may overshadow the ask
- too much context at once
- reader may miss what support is needed

ToneLayer should lead with the ask, calm the sequence, and keep only context that helps the NT reader respond.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.MIXED -> """
Mixed / Not Sure Sender Profile
Use this when the sender knows the message needs ND-aware translation but does not know which specific profile fits.

Possible friction points:
- buried ask
- high context load
- emotional intensity
- unclear sequencing
- overlapping working-memory, literal-meaning, and threat-sensitivity needs

ToneLayer should apply a broad ND-to-NT lens without choosing a diagnosis: point, context, request, timing, and close.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.AUTO -> """
Auto ToneLayer Analysis
The app will infer the most likely communication friction from the message itself without choosing a diagnosis.

It will look for:
- buried main point
- unclear ask
- vague timing
- emotional intensity
- too much context
- threat signal
- missing reassurance
- unclear next step

Current message being analyzed:
"$message"
""".trimIndent()
    }
}
