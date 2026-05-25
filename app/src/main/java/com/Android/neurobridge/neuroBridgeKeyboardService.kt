package com.Android.neurobridge

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A custom input method service that provides a specialized keyboard for NeuroBridge.
 *
 * This keyboard includes buttons to analyze text from a neurodivergent (ND) perspective,
 * rewrite text to be more ND-friendly, and make text more direct and specific.
 */
class NeuroBridgeKeyboardService : InputMethodService() {

    /**
     * Called by the framework when the keyboard view is being created.
     *
     * Initializes the layout and sets up buttons for ND-focused text analysis and manipulation.
     *
     * @return The root view of the keyboard layout.
     */
    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "NeuroBridge Clarity Keyboard"
            textSize = 18f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val analyzeButton = Button(this).apply {
            text = "Analyze"
            setOnClickListener {
                commitText("[Analyze how this may be received by an ND person]")
            }
        }

        val rewriteButton = Button(this).apply {
            text = "ND-Friendly"
            setOnClickListener {
                commitText("[Rewrite this in a clearer ND-friendly way]")
            }
        }

        val directButton = Button(this).apply {
            text = "Direct"
            setOnClickListener {
                commitText("[Make this more direct and specific]")
            }
        }

        val spaceButton = Button(this).apply {
            text = "Space"
            setOnClickListener {
                commitText(" ")
            }
        }

        val deleteButton = Button(this).apply {
            text = "Delete"
            setOnClickListener {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }

        row.addView(analyzeButton)
        row.addView(rewriteButton)
        row.addView(directButton)

        root.addView(title)
        root.addView(row)
        root.addView(spaceButton)
        root.addView(deleteButton)

        return root
    }

    /**
     * Commits the specified text to the current input connection.
     *
     * @param text The text to be committed.
     */
    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}