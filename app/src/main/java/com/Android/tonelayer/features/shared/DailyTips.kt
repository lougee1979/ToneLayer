// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.shared

import java.util.Calendar

data class DailyTip(val title: String, val body: String)

val DAILY_TIPS = listOf(
    DailyTip(
        "RSD can make silence feel personal",
        "Rejection sensitivity is common for many people with ADHD. A delayed reply, a blocked call, or a short message can feel like proof that something is wrong, even when the other person is only busy or overwhelmed."
    ),
    DailyTip(
        "Direct does not mean rude",
        "Many neurodivergent people communicate best with clear, specific language. A direct request can reduce guessing, anxiety, and the pressure to decode hidden meaning."
    ),
    DailyTip(
        "Too many options can freeze action",
        "When everything feels equally urgent, the brain may stall instead of choosing. Naming one next step can be more helpful than giving a full list of possible solutions."
    ),
    DailyTip(
        "Tone can get lost in text",
        "Short messages can be read as anger or rejection when the nervous system is already activated. Adding one warm sentence can change how safe the message feels."
    ),
    DailyTip(
        "Body doubling is practical support",
        "Some people start tasks more easily when another person is present or checking in. It is not dependence; it can be a way to borrow structure long enough to begin."
    ),
    DailyTip(
        "Clarity lowers the social load",
        "A message that says what happened, what is needed, and when a reply is expected gives the other person fewer hidden steps to interpret."
    ),
    DailyTip(
        "Overexplaining can be a safety behavior",
        "A long message may be an attempt to prevent misunderstanding, criticism, or rejection. The goal is not to remove the person's voice, but to organize it so the need is clear."
    )
)

fun todayTip(): DailyTip {
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    return DAILY_TIPS[(dayOfYear - 1) % DAILY_TIPS.size]
}
