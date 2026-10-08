package dev.thorpilot

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.WeakHashMap

/** Bundled, offline typography from the Claude Design reference. */
object PilotTypography {
    private enum class Role { BODY, HEADING, MONO }
    private val roles = WeakHashMap<TextView, Role>()
    private val faces = mutableMapOf<Pair<Role, Int>, Typeface>()

    /** Apply Instrument Sans without changing an existing bold or italic treatment. */
    fun body(view: TextView): TextView = apply(view, Role.BODY)

    /** Opt a heading into Bricolage Grotesque; its current weight is preserved. */
    fun heading(view: TextView): TextView = apply(view, Role.HEADING)

    /** Opt labels, build IDs, and technical values into JetBrains Mono. */
    fun mono(view: TextView): TextView = apply(view, Role.MONO)

    /**
     * Apply the body family to a newly built hierarchy, including buttons and inputs.
     * Explicit heading/mono roles survive subsequent passes. No size, spacing, or
     * weight is guessed from the content. Call on the UI thread after building views.
     */
    fun applyTo(root: View) {
        if (root is TextView) apply(root, roles[root] ?: Role.BODY)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) applyTo(root.getChildAt(index))
        }
    }

    private fun apply(view: TextView, role: Role): TextView {
        val style = view.typeface?.style ?: Typeface.NORMAL
        view.typeface = face(view.context, role, style)
        roles[view] = role
        return view
    }

    private fun face(context: Context, role: Role, style: Int): Typeface =
        faces.getOrPut(role to style) {
            val resource = when (role) {
                Role.BODY -> R.font.instrument_sans
                Role.HEADING -> R.font.bricolage_grotesque
                Role.MONO -> R.font.jetbrains_mono
            }
            Typeface.create(context.resources.getFont(resource), style)
        }
}
