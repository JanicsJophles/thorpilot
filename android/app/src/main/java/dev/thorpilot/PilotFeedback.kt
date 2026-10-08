package dev.thorpilot

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.WeakHashMap

/** Local controller feedback. Never intercepts a key or bypasses system haptic settings. */
object PilotFeedback {
    private var lastTick = 0L
    private val presses = WeakHashMap<View, ValueAnimator>()
    fun focus(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 45) return
        lastTick = now
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun key(view: View?, event: KeyEvent) {
        if (view == null || !view.isEnabled || !view.isClickable || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        if (event.keyCode !in setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        presses.remove(view)?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) { view.scaleX = 1f; view.scaleY = 1f; return }
        val animator = ValueAnimator.ofFloat(1f, .985f, 1f).apply {
            duration = 160
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val scale = if (view.isAttachedToWindow) it.animatedValue as Float else 1f
                view.scaleX = scale; view.scaleY = scale
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.scaleX = 1f; view.scaleY = 1f
                    presses.remove(view)
                }
            })
        }
        presses[view] = animator
        animator.start()
    }
}
