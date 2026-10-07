package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** Original vector landscape; no launcher wallpaper or third-party art. */
class PilotWallpaper : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xff182b3b.toInt(), 0xff203c47.toInt(), 0xff292640.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        paint.shader = RadialGradient(w*.75f, h*.15f, w*.55f, intArrayOf(0x304fc6c5, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint); paint.shader = null
        paint.color = 0x42c3e6e3
        for (i in 0..38) {
            val x = ((i * 173 + 43) % 997) / 997f * w
            val y = ((i * 71 + 11) % 503) / 503f * h * .7f
            canvas.drawCircle(x, y, if (i % 4 == 0) 1.7f else .8f, paint)
        }
        fun ridge(color: Int, base: Float, amplitude: Float, shift: Float) {
            val path = Path().apply {
                moveTo(0f,h); lineTo(0f,h*base)
                for (i in 0..14) {
                    val x=w*i/14f
                    val y=h*(base-amplitude*(.5f+.5f*kotlin.math.sin(i*1.3f+shift)))
                    lineTo(x,y)
                }
                lineTo(w,h); close()
            }
            paint.color=color; canvas.drawPath(path,paint)
        }
        ridge(0x57376b70, .85f,.22f,0f)
        ridge(0x7834435e, .98f,.26f,1.5f)
        ridge(0xb0152536.toInt(),1.05f,.18f,3f)
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A friendly compass-shaped pilot emblem, drawn for this project. */
class PilotEmblem : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val cx=bounds.exactCenterX(); val cy=bounds.exactCenterY(); val r=minOf(bounds.width(),bounds.height())*.4f
        paint.shader=LinearGradient(cx-r,cy-r,cx+r,cy+r,0xff86e4d8.toInt(),0xff5a9cf0.toInt(),Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,r,paint);paint.shader=null
        paint.color=0x55ffffff;paint.style=Paint.Style.STROKE;paint.strokeWidth=r*.04f
        canvas.drawCircle(cx,cy,r*.88f,paint);paint.style=Paint.Style.FILL
        paint.color=Color.WHITE
        val arrow=Path().apply {moveTo(cx,cy-r*.6f);lineTo(cx+r*.45f,cy+r*.42f);quadTo(cx,cy+r*.12f,cx-r*.45f,cy+r*.42f);close()}
        canvas.drawPath(arrow,paint)
        paint.color=0xff386788.toInt();canvas.drawCircle(cx,cy+r*.06f,r*.1f,paint)
    }
    override fun setAlpha(alpha:Int) {paint.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?) {paint.colorFilter=filter}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
