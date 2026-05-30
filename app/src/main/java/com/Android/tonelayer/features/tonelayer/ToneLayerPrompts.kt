// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.tonelayer

import com.Android.tonelayer.NeuroProfile

enum class RewriteLevel(val displayName: String, val shortLabel: String) {
    LIGHT("Light", "L"),
    MEDIUM("Medium", "M"),
    STRONG("Strong", "S")
}

fun levelInstruction(level: RewriteLevel, profile: NeuroProfile): String = when (profile) {
    NeuroProfile.ADHD -> when (level) {
        RewriteLevel.LIGHT -> "Make minimal changes. Fix typos and grammar. If the main point is completely buried, move it to the first sentence. Preserve all content and the user's voice. This is a light polish — do not cut or restructure."
        RewriteLevel.MEDIUM -> "Restructure from ND flow into NT readability. Move the main point to the first sentence. Group related ideas into short paragraphs — each paragraph covers one topic. Cut obvious repetition but keep all distinct ideas and the user's voice intact. The rewrite MUST have multiple paragraphs. NT readers should be able to follow without effort."
        RewriteLevel.STRONG -> "Reorganize and signal this content clearly for NT readers while keeping the user's voice and meaning fully intact. Lead with what the person needs, is asking, or is communicating. Break into clear paragraphs — each covering one idea or thread. Keep the emotional content and the connections between ideas — sequence them so they read as deliberate rather than scattered. Do not strip the person's voice, delete their concerns, or flatten the emotional texture. If the text asks for help or describes a struggle, name it clearly in the first paragraph — then context and detail follow. This is translation, not deletion. Output MUST be multiple paragraphs."
    }
    NeuroProfile.AUTISM -> when (level) {
        RewriteLevel.LIGHT -> "Make a light ND-to-NT rewrite. Fix typos. Add a brief greeting or sign-off only if completely absent. Keep all content and voice intact."
        RewriteLevel.MEDIUM -> "Make a medium ND-to-NT rewrite. Add appropriate social warmth — a genuine greeting, warm transitions, polite closing. Decode any implied meaning and state it directly. Keep all literal content. Use multiple paragraphs to separate distinct topics."
        RewriteLevel.STRONG -> "Make a strong ND-to-NT rewrite using NT social norms. Add natural social flow — appropriate opening, warmth throughout, clear closing. Remove overly blunt phrasing where it would land poorly. Preserve all the user's meaning. Break into multiple paragraphs — each one covering one idea."
    }
    NeuroProfile.PTSD -> when (level) {
        RewriteLevel.LIGHT -> "Make a light ND-to-NT rewrite. Soften the most reactive or escalating phrases only. Keep all content and the user's voice intact."
        RewriteLevel.MEDIUM -> "Make a medium ND-to-NT rewrite. Remove over-justification, excessive apology, and defensive language. Rewrite hedging sentences to be direct. Calm tone throughout. Use multiple paragraphs to organize the content."
        RewriteLevel.STRONG -> "Make a strong ND-to-NT rewrite into calm, grounded communication. Remove all defensive language, over-explanation, and anticipatory apology. Break into multiple paragraphs — each one making a clear, direct point. Write with quiet confidence. No escalating language, no hedging."
    }
    NeuroProfile.AUTISM_PTSD -> when (level) {
        RewriteLevel.LIGHT -> "Make a light ND-to-NT rewrite. Soften the most reactive phrases and add a greeting if absent. Minimal changes otherwise."
        RewriteLevel.MEDIUM -> "Make a medium ND-to-NT rewrite. Remove over-justification and add social warmth. Direct but kind. Use multiple paragraphs to separate distinct topics."
        RewriteLevel.STRONG -> "Make a strong ND-to-NT rewrite: warm, direct, calm, no over-justification. Break into multiple paragraphs — one idea per paragraph."
    }
    NeuroProfile.ADHD_PTSD -> when (level) {
        RewriteLevel.LIGHT -> "Make a light ND-to-NT rewrite. Soften the most reactive phrasing and move the main point closer to the start if buried. Minimal changes otherwise."
        RewriteLevel.MEDIUM -> "Make a medium ND-to-NT rewrite. Lead with the main point. Cut the worst tangents. Remove defensive over-explanation. Use multiple paragraphs. Calmer and more focused."
        RewriteLevel.STRONG -> "Reorganize and signal this content clearly for NT readers while keeping the user's voice and meaning intact. Lead with the main point or need. Break into multiple paragraphs — each one organized around one topic. Keep emotional content — sequence it so it reads as deliberate. Remove defensive language and over-explanation. Output MUST be multiple paragraphs."
    }
    NeuroProfile.MIXED, NeuroProfile.AUTO -> when (level) {
        RewriteLevel.LIGHT -> "Make a light ND-to-NT rewrite. Fix typos and grammar only. Keep all content and voice intact."
        RewriteLevel.MEDIUM -> "Restructure ND communication into NT-readable clarity. Main point first. Cut obvious repetition. Use multiple paragraphs. Keep the user's voice and all distinct substance."
        RewriteLevel.STRONG -> "Fully translate ND communication for NT readers. Clear, direct, organized into multiple paragraphs. Preserve the whole message."
    }
}

fun buildToneLayerSystem(profile: NeuroProfile, level: RewriteLevel): String {
    val instruction = levelInstruction(level, profile)
    return """
You are ToneLayer, a communication assistant that helps neurodivergent people be understood by neurotypical readers. Your job is to translate the structure and signals of ND communication — not to delete the person's voice, meaning, or emotional content. Direction: ND → NT. Profile: ${profile.displayName}. $instruction

Rewrite the entire text the user provided from ND style into NT style. Do not stop halfway, do not summarize only the beginning, and do not omit later points just because the text is long or messy. Preserve the user's intended message, requests, constraints, and necessary context from the whole original, but translate the structure, order, tone, and phrasing into what an NT reader would naturally expect.

The "paragraphs" array is the primary output. For any text longer than 3 sentences, you MUST return at least 2 paragraphs — never collapse everything into a single string. Brain dumps and multi-topic text must always be organized into multiple paragraphs.

The explanation must teach — don't just say what changed, say WHY that change makes the text land better with NT readers.

Always respond with ONLY valid JSON — no markdown, no code fences, no extra text.

{
  "paragraphs": ["first paragraph as a plain string", "second paragraph as a plain string"],
  "explanation": "REQUIRED: one sentence explaining what ND pattern you addressed and why the change makes it more NT-legible.",
  "distortions": ["any cognitive distortions found — empty array if none"],
  "grammar_only": "grammar-fixed version of the full original that keeps the user's ND structure but fixes grammar, spelling, and punctuation."
}
    """.trimIndent()
}

fun toneLayerProfilePrompt(profile: NeuroProfile): String = when (profile) {
    NeuroProfile.AUTO -> "Profile: Auto. Analyze the message and identify the most likely communication friction without diagnosing the user."
    NeuroProfile.ADHD -> "Sender profile: ADHD. Keep the main point first, reduce tangents and urgency loops, preserve the useful context, and make the ask concrete."
    NeuroProfile.AUTISM -> "Sender profile: Autism. Preserve precision and boundaries while adding social context, expected response, and enough warmth for NT readers."
    NeuroProfile.PTSD -> "Sender profile: PTSD/CPTSD. Preserve safety needs and boundaries while reducing defensive framing, threat signals, and over-explaining."
    NeuroProfile.AUTISM_PTSD -> "Sender profile: Autism + PTSD. Preserve directness, precision, and safety needs while adding clear context, low-threat wording, and an explicit next step."
    NeuroProfile.ADHD_PTSD -> "Sender profile: ADHD + PTSD. Move the main point up, reduce spirals and defensive urgency, keep valid needs, and add calm sequencing."
    NeuroProfile.MIXED -> "Profile: Mixed / Not Sure. Apply a broad neurodivergent communication lens without choosing a specific diagnosis."
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

ToneLayer should apply a broad ND-to-NT lens: point, context, request, timing, and close.

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
