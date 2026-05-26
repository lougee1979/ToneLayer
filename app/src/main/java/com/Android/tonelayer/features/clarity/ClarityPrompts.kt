// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.clarity

import com.Android.tonelayer.NeuroProfile

fun clarityProfilePrompt(profile: NeuroProfile): String {
    return when (profile) {
        NeuroProfile.AUTO -> """
            Profile: Auto.
            Analyze the message and identify the most likely communication friction without diagnosing the reader.
            Look for buried main point, unclear ask, vague timing, hidden social expectation, threat signal, indirect wording, missing reassurance, unclear next step, and cognitive load.
            Choose the most useful NT-to-ND rewrite strategy based on the message itself. Do not mention a diagnosis unless the user selected one. Explain what friction you detected and how the rewrite reduces it for ND readability.
        """.trimIndent()
        NeuroProfile.ADHD -> "Reader profile: ADHD. Reduce working-memory load, state the point early, give concrete timing, and keep the requested action obvious."
        NeuroProfile.AUTISM -> "Reader profile: Autism. Make implied social meaning explicit, remove vague hints, define expectations, and avoid forcing inference."
        NeuroProfile.PTSD -> "Reader profile: PTSD/CPTSD. Lower threat signals, include reassurance when true, clarify urgency, and avoid open-ended tension."
        NeuroProfile.AUTISM_PTSD -> "Reader profile: Autism + PTSD. Make the topic explicit, define expectations, lower threat, and avoid ambiguous conflict signals."
        NeuroProfile.ADHD_PTSD -> "Reader profile: ADHD + PTSD. Put the point first, lower urgency panic, reduce cognitive load, and include one clear next step."
        NeuroProfile.MIXED -> """
            Profile: Mixed / Not Sure.
            Apply a broad neurodivergent communication lens without choosing a specific diagnosis.
            Account for possible overlap between ADHD, autism, PTSD/CPTSD, anxiety-related threat sensitivity, working-memory load, literal interpretation, and emotional ambiguity.
            Rewrite the message to reduce ambiguity, hidden expectations, threat signals, cognitive load, and unclear next steps. Explain the rewrite in general ND-accessibility terms instead of naming one specific profile.
        """.trimIndent()
    }
}

fun createClarityAnalysis(input: String, profile: NeuroProfile): String {
    val message = input.trim().ifEmpty { "the message" }
    return when (profile) {
        NeuroProfile.ADHD -> """
ADHD Reader Profile
This NT wording may create cognitive load if the point, timing, or expected action is implied.

How it may land:
- unclear urgency
- unclear next step
- too much social subtext
- hard to know what to do first

Clarity should state the topic, urgency, and requested action directly.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.AUTISM -> """
Autism Reader Profile
This NT wording may rely on implied emotional context or social inference.

How it may land:
- unclear purpose
- unclear seriousness
- hidden expectation
- vague timing

Clarity should make the topic, intent, and expectation explicit.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.PTSD -> """
PTSD/CPTSD Reader Profile
This NT wording may create anticipatory stress if safety, urgency, or conflict level is unclear.

How it may land:
- "am I in trouble?"
- undefined conflict level
- open-ended tension
- unclear urgency

Clarity should include true reassurance, concrete timing, and a clear reason for the conversation.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.AUTISM_PTSD -> """
Autism + PTSD Reader Profile
This NT wording may combine inference load with threat uncertainty.

How it may land:
- the reader may not know what the conversation is about
- the reader may assume danger because the tone is vague
- hidden expectations may feel unsafe
- unclear timing may keep the nervous system activated

Clarity should name the topic, state reassurance when true, define timing, and give one clear next step.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.ADHD_PTSD -> """
ADHD + PTSD Reader Profile
This NT wording may create both working-memory load and threat uncertainty.

How it may land:
- urgency may feel bigger than intended
- vague timing may create rumination
- the reader may miss the requested action
- open loops may be hard to put down

Clarity should put the point first, lower alarm, and make the next step concrete.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.MIXED -> """
Mixed / Not Sure Reader Profile
Use this when the reader needs ND-aware clarity but the specific profile is not known.

How it may land:
- emotional ambiguity
- timeline ambiguity
- expectation ambiguity
- urgency ambiguity
- hidden social expectations
- unnecessary cognitive load

Clarity should apply a broad ND-accessibility lens without choosing a diagnosis.

Current message being analyzed:
"$message"
""".trimIndent()
        NeuroProfile.AUTO -> """
Auto Clarity Analysis
The app will infer the most likely communication friction from the message itself without choosing a diagnosis.

It will look for:
- buried main point
- unclear ask
- vague timing
- hidden social expectation
- threat signal
- indirect wording
- missing reassurance
- unclear next step

Current message being analyzed:
"$message"
""".trimIndent()
    }
}
