package dev.thorpilot

import android.animation.ValueAnimator
import android.content.Context

/** Shared by both displays and the compact copilot; system accessibility settings win. */
object PilotPreferences {
    private fun preferences(context: Context) = context.getSharedPreferences("pilot-experience", Context.MODE_PRIVATE)
    fun motion(context: Context) = preferences(context).getBoolean("motion", true)
    fun haptics(context: Context) = preferences(context).getBoolean("haptics", true)
    fun animate(context: Context) = motion(context) && ValueAnimator.areAnimatorsEnabled()
    fun setMotion(context: Context, enabled: Boolean) { preferences(context).edit().putBoolean("motion", enabled).apply() }
    fun setHaptics(context: Context, enabled: Boolean) { preferences(context).edit().putBoolean("haptics", enabled).apply() }
}
