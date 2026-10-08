package dev.thorpilot

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** The same navigation row on the home screen and companion display. */
class PilotActionRow(context: Context, title: String, detail: String, icon: String, action: () -> Unit) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = "$title. $detail"
        minimumHeight = dp(64)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = RippleDrawable(ColorStateList.valueOf(0x33ffb547), PilotSurface(dp(18).toFloat()), null)
        layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(8) }
        addView(ImageView(context).apply {
            setImageDrawable(PilotIcon(icon))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(14) })
        val copy = LinearLayout(context).apply { orientation = VERTICAL }
        copy.addView(TextView(context).apply {
            text = title; textSize = 15f; setTextColor(0xfff4f2ec.toInt())
            setTypeface(typeface, Typeface.BOLD)
            PilotTypography.body(this)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        copy.addView(TextView(context).apply {
            text = detail; textSize = 12f; setTextColor(0xffa9a69e.toInt())
            setPadding(0, dp(3), 0, 0)
            PilotTypography.body(this)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        addView(copy, LayoutParams(0, -2, 1f))
        setOnClickListener { action() }
    }
}
