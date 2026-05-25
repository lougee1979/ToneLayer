package com.Android.neurobridge

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A custom input method service that provides NeuroBridge rewrite controls.
 */
class NeuroBridgeKeyboardService : InputMethodService() {

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "NeuroBridge Rewrite Keyboard"
            textSize = 18f
        }

        val helperText = TextView(this).apply {
            text = "Tap a rewrite style to insert a prompt into the current app."
            textSize = 13f
        }

        val analysisRow = horizontalRow()
        analysisRow.addView(keyButton("Analyze") {
            commitText("[Analyze this message for possible neurodivergent communication friction]")
        })
        analysisRow.addView(keyButton("Rewrite") {
            commitText("[Rewrite this for clearer neurodivergent-friendly communication]")
        })

        val rewriteRow = horizontalRow()
        rewriteRow.addView(keyButton("Shorter") {
            commitText("[Rewrite this shorter while keeping the same meaning]")
        })
        rewriteRow.addView(keyButton("Warmer") {
            commitText("[Rewrite this warmer and less abrupt while keeping the same meaning]")
        })

        val toneRow = horizontalRow()
        toneRow.addView(keyButton("Direct") {
            commitText("[Rewrite this more direct and specific while staying respectful]")
        })
        toneRow.addView(keyButton("Soften") {
            commitText("[Rewrite this softer and less likely to feel confrontational]")
        })

        val utilityRow = horizontalRow()
        utilityRow.addView(keyButton("Space") {
            commitText(" ")
        })
        utilityRow.addView(keyButton("Delete") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })

        root.addView(title)
        root.addView(helperText)
        root.addView(analysisRow)
        root.addView(rewriteRow)
        root.addView(toneRow)
        root.addView(utilityRow)

        return root
    }

    private fun horizontalRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
    }

    private fun keyButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}
