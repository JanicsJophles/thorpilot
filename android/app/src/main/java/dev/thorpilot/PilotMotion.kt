package dev.thorpilot

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Short, one-shot page motion; the navigation shell and background stay still. */
object PilotMotion {
    fun reset(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.translationY = 0f
    }

    fun enter(view: View) {
        reset(view)
        if (!view.isAttachedToWindow || !PilotPreferences.animate(view.context)) return

        val detach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = reset(v)
        }
        view.addOnAttachStateChangeListener(detach)
        view.alpha = 0f
        view.translationY = 8f * view.resources.displayMetrics.density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(0)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.removeOnAttachStateChangeListener(detach)
                    view.animate().setListener(null)
                    view.alpha = 1f
                    view.translationY = 0f
                }
            })
            .start()
    }
}
