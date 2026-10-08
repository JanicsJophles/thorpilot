package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewTreeObserver

/** Animated inset glass rim; one short transition per focus change, no idle loop. */
class PilotFocusRing(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var opacity = 255
    var reveal: Float = 1f
        set(value) { field = value; invalidateSelf() }

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        rect.inset(5f * density, 5f * density)
    }
    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val drawRect = RectF(rect).apply { inset((1f - reveal) * 4f * density, (1f - reveal) * 2f * density) }
        val radius = minOf(22f * density, drawRect.height() / 2f)
        val save = canvas.saveLayerAlpha(null, (opacity * (.35f + .65f * reveal)).toInt())
        paint.color = 0x50ffb547
        paint.strokeWidth = 6f * density
        canvas.drawRoundRect(drawRect, radius, radius, paint)
        paint.color = 0xffffb547.toInt()
        paint.strokeWidth = 2f * density
        canvas.drawRoundRect(drawRect, radius, radius, paint)
        paint.shader = LinearGradient(drawRect.left, drawRect.top, drawRect.right, drawRect.bottom,
            intArrayOf(0xffffffff.toInt(), 0x70fff0db, 0xffffd699.toInt()), null, Shader.TileMode.CLAMP)
        paint.color = 0xffffe2af.toInt()
        paint.strokeWidth = 1.2f * density
        canvas.drawRoundRect(drawRect, radius, radius, paint)
        paint.shader = null
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Tracks focus for dynamic child controls while preserving their own foregrounds. */
object PilotFocus {
    fun install(root: View) {
        var animator: android.animation.ValueAnimator? = null
        var focused: View? = null
        var original: Drawable? = null
        var decoration: Drawable? = null
        var observer: ViewTreeObserver? = null
        fun clear() {
            animator?.cancel(); animator = null
            focused?.let { if (it.foreground === decoration) it.foreground = original }
            focused = null; original = null; decoration = null
        }
        fun update(view: View?, feedback: Boolean = false) {
            clear()
            if (view == null || !view.isEnabled || !(view.isClickable || view is android.widget.EditText)) return
            focused = view
            original = view.foreground
            val ring = PilotFocusRing(view.resources.displayMetrics.density)
            decoration = original?.let { LayerDrawable(arrayOf(it, ring)) } ?: ring
            view.foreground = decoration
            if (feedback) {
                PilotFeedback.focus(view)
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 180
                        interpolator = android.view.animation.DecelerateInterpolator()
                        addUpdateListener { ring.reveal = it.animatedValue as Float }
                        ring.reveal = 0f
                        start()
                    }
                }
            }
        }
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, next -> update(next, true) }
        fun attach() {
            observer = root.viewTreeObserver.also { it.addOnGlobalFocusChangeListener(listener) }
            update(root.findFocus())
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { attach() }
            override fun onViewDetachedFromWindow(view: View) {
                observer?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(listener)
                observer = null
                clear()
            }
        })
        if (root.isAttachedToWindow) attach()
    }
}
