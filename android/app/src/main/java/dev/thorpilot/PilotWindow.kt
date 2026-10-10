package dev.thorpilot

import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Fullscreen only in Thorpilot-owned windows. System navigation remains available by swiping. */
object PilotWindow {
    fun apply(window: Window) {
        val controller = window.insetsController ?: return
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (PilotPreferences.fullscreen(window.context)) controller.hide(WindowInsets.Type.systemBars())
        else controller.show(WindowInsets.Type.systemBars())
    }
}
