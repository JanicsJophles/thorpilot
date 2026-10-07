package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** A reusable dimensional glass surface. Drawn locally; not a sampled blur. */
class PilotGlass(private val radius: Float = 28f, private val selected: Boolean = false) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val surface = RectF()
    override fun onBoundsChange(bounds: Rect) {
        surface.set(bounds); surface.inset(1f, 1f)
        if (surface.isEmpty) return
        fill.shader = LinearGradient(0f, surface.top, 0f, surface.bottom,
            if (selected) 0xf037726e.toInt() else 0xf01a2730.toInt(),
            if (selected) 0xfa0b353c.toInt() else 0xfa070d16.toInt(), Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, surface.top, 0f, surface.bottom,
            0x807edfd8.toInt(), 0x12253b49, Shader.TileMode.CLAMP)
    }
    override fun draw(c: Canvas) {
        if (surface.isEmpty) return
        c.drawRoundRect(surface, radius, radius, fill)
        c.drawRoundRect(surface, radius, radius, rim)
    }
    override fun setAlpha(a: Int) { fill.alpha = a; rim.alpha = a; invalidateSelf() }
    override fun setColorFilter(f: ColorFilter?) { fill.colorFilter = f; rim.colorFilter = f; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Original launcher-like vector icons, with their own sculpted tile and rim. */
class PilotIcon(private val kind:String): Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scene by lazy {
        Picture().apply {
            val recording = beginRecording(100, 100)
            paintScene(recording)
            endRecording()
        }
    }
    override fun draw(c: Canvas) {
        if (bounds.isEmpty) return
        val save = if (layerPaint.alpha == 255 && layerPaint.colorFilter == null) c.save() else
            c.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), layerPaint)
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.scale(bounds.width() / 100f, bounds.height() / 100f)
        c.drawPicture(scene)
        c.restoreToCount(save)
    }
    private fun paintScene(c: Canvas) {
        val save=c.save()
        val colors=when(kind){"chat"->intArrayOf(0xff84ffc3.toInt(),0xff00bfae.toInt());"device"->intArrayOf(0xff75caff.toInt(),0xff3558ed.toInt());"requests"->intArrayOf(0xffd2a1ff.toInt(),0xff8533f0.toInt());else->intArrayOf(0xffffc979.toInt(),0xffff754b.toInt())}
        p.color=0x44000000;c.drawRoundRect(5f,8f,95f,98f,25f,25f,p)
        p.alpha=255;p.shader=LinearGradient(0f,3f,90f,95f,colors,null,Shader.TileMode.CLAMP);c.drawRoundRect(4f,3f,96f,94f,25f,25f,p);p.alpha=255;p.shader=null
        val clip=c.save();c.clipPath(Path().apply{addRoundRect(RectF(4f,3f,96f,94f),25f,25f,Path.Direction.CW)})
        p.color=0x22ffffff
        for(i in 0..2) c.drawRoundRect((i*39-11).toFloat(),-15f,(i*39+20).toFloat(),52f,12f,12f,p)
        p.alpha=255;p.shader=LinearGradient(0f,5f,0f,55f,0x30ffffff,Color.TRANSPARENT,Shader.TileMode.CLAMP);c.drawOval(-10f,-20f,110f,55f,p);p.alpha=255;p.shader=null;c.restoreToCount(clip)
        p.style=Paint.Style.STROKE;p.strokeWidth=1.2f;p.color=0x66ffffff;c.drawRoundRect(4.8f,3.8f,95.2f,93.2f,24f,24f,p)
        p.strokeWidth=5.2f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;p.color=0xfffaffff.toInt()
        when(kind){
            "chat"->{
                val bubble=Path().apply{moveTo(31f,25f);lineTo(70f,25f);quadTo(79f,25f,79f,34f);lineTo(79f,57f);quadTo(79f,66f,70f,66f);lineTo(47f,66f);lineTo(31f,77f);lineTo(33f,66f);quadTo(23f,66f,23f,57f);lineTo(23f,34f);quadTo(23f,25f,31f,25f)};c.drawPath(bubble,p)
                p.style=Paint.Style.FILL;for(x in listOf(38f,51f,64f))c.drawCircle(x,46f,3.3f,p)
            }
            "device"->{
                c.drawRoundRect(25f,20f,75f,48f,6f,6f,p);c.drawRoundRect(25f,54f,75f,79f,6f,6f,p)
                p.strokeWidth=3f;c.drawLine(33f,65f,41f,65f,p);c.drawLine(37f,61f,37f,69f,p)
                p.style=Paint.Style.FILL;c.drawCircle(64f,63f,2f,p);c.drawCircle(59f,68f,2f,p)
            }
            "requests"->{
                c.drawLine(50f,23f,50f,55f,p);c.drawLine(38f,43f,50f,55f,p);c.drawLine(62f,43f,50f,55f,p)
                val tray=Path().apply{moveTo(26f,54f);lineTo(26f,69f);quadTo(26f,76f,33f,76f);lineTo(67f,76f);quadTo(74f,76f,74f,69f);lineTo(74f,54f)};c.drawPath(tray,p)
            }
            else->{
                for(y in listOf(31f,49f,67f))c.drawLine(25f,y,75f,y,p)
                p.style=Paint.Style.FILL;listOf(39f to 31f,62f to 49f,44f to 67f).forEach{(x,y)->p.color=0xfffaffff.toInt();c.drawCircle(x,y,7f,p);p.color=colors[1];c.drawCircle(x,y,3f,p)}
            }
        }
        p.style=Paint.Style.FILL;p.alpha=255;p.shader=null;c.restoreToCount(save)
    }
    override fun setAlpha(a:Int){layerPaint.alpha=a;invalidateSelf()}
    override fun setColorFilter(f:ColorFilter?){layerPaint.colorFilter=f;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

/** Original orbital scene for the workspace, made from scalable paths. */
class PilotJourney : Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scene by lazy {
        Picture().apply {
            val recording = beginRecording(320, 240)
            paintScene(recording)
            endRecording()
        }
    }
    override fun draw(c: Canvas) {
        if (bounds.isEmpty) return
        val save = if (layerPaint.alpha == 255 && layerPaint.colorFilter == null) c.save() else
            c.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), layerPaint)
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.scale(bounds.width() / 320f, bounds.height() / 240f)
        c.drawPicture(scene)
        c.restoreToCount(save)
    }
    private fun paintScene(c: Canvas) {
        val save=c.save()
        p.alpha=255;p.shader=RadialGradient(152f,120f,118f,intArrayOf(0x703ad6b4,Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,320f,240f,p);p.alpha=255;p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=0x665de4d5
        c.save();c.rotate(-20f,155f,127f);c.drawOval(26f,67f,290f,190f,p);c.drawOval(4f,39f,312f,212f,p);c.restore();p.style=Paint.Style.FILL
        p.alpha=255;p.shader=LinearGradient(91f,38f,214f,173f,0xffa1ffdc.toInt(),0xff16b9c0.toInt(),Shader.TileMode.CLAMP);c.drawCircle(164f,108f,61f,p);p.alpha=255;p.shader=null
        p.color=0x22586c8f;c.drawCircle(144f,76f,14f,p);c.drawCircle(188f,116f,22f,p);c.drawCircle(137f,119f,9f,p)
        c.save();c.rotate(-12f,156f,140f)
        p.color=0x44000000;c.drawRoundRect(96f,108f,211f,191f,19f,19f,p)
        p.alpha=255;p.shader=LinearGradient(96f,99f,203f,177f,0xfff4efff.toInt(),0xff9690e5.toInt(),Shader.TileMode.CLAMP);c.drawRoundRect(96f,99f,205f,179f,17f,17f,p);p.alpha=255;p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=0xaaffffff.toInt();c.drawRoundRect(97f,100f,204f,178f,16f,16f,p);p.style=Paint.Style.FILL
        p.color=0xff162238.toInt();c.drawRoundRect(121f,111f,181f,153f,7f,7f,p)
        p.alpha=255;p.shader=LinearGradient(121f,111f,181f,153f,0xff43f2c5.toInt(),0xff6851ed.toInt(),Shader.TileMode.CLAMP);c.drawRoundRect(125f,115f,177f,149f,4f,4f,p);p.alpha=255;p.shader=null
        p.color=0xfff5fcff.toInt();val mountain=Path().apply{moveTo(127f,145f);lineTo(145f,125f);lineTo(156f,137f);lineTo(166f,128f);lineTo(176f,145f);close()};c.drawPath(mountain,p)
        p.color=0xff52617b.toInt();c.drawRoundRect(103f,130f,117f,135f,2f,2f,p);c.drawRoundRect(108f,125f,112f,140f,2f,2f,p);c.drawCircle(190f,128f,3.2f,p);c.drawCircle(196f,137f,3.2f,p);c.drawRoundRect(142f,162f,163f,166f,2f,2f,p);c.restore()
        p.color=0xffb4fff0.toInt();c.drawCircle(49f,157f,5f,p);p.color=0xffec8fff.toInt();c.drawCircle(260f,72f,8f,p)
        p.color=0xffffbd78.toInt();c.drawCircle(88f,195f,3.5f,p)
        for((x,y) in listOf(69f to 61f,245f to 165f,215f to 35f)){p.color=0xffd7fcf6.toInt();val star=Path().apply{moveTo(x,y-5);lineTo(x+2,y-2);lineTo(x+5,y);lineTo(x+2,y+2);lineTo(x,y+5);lineTo(x-2,y+2);lineTo(x-5,y);lineTo(x-2,y-2);close()};c.drawPath(star,p)}
        c.restoreToCount(save)
    }
    override fun setAlpha(a:Int){layerPaint.alpha=a;invalidateSelf()}
    override fun setColorFilter(f:ColorFilter?){layerPaint.colorFilter=f;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
