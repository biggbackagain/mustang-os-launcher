package com.mustangos.launcher.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.mustangos.launcher.R
import org.xmlpull.v1.XmlPullParser
import kotlin.math.min
import kotlin.math.roundToInt

/** A static app background. The emblem keeps its aspect ratio even in short dialogs. */
class MustangBackgroundDrawable : Drawable() {
    private var blue: Drawable? = null
    private var emblem: Drawable? = null
    private var shade: Drawable? = null
    private var opacity = 255
    private var filter: ColorFilter? = null

    override fun inflate(resources: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(resources, parser, attrs, theme)
        blue = resources.getDrawable(R.drawable.bg_midnight_blue, theme).mutate()
        emblem = resources.getDrawable(R.drawable.bg_mustang_wallpaper, theme).mutate()
        shade = resources.getDrawable(R.drawable.bg_settings_scrim, theme).mutate()
        setAlpha(opacity)
        setColorFilter(filter)
        onBoundsChange(bounds)
    }

    override fun onBoundsChange(bounds: Rect) {
        blue?.bounds = bounds
        shade?.bounds = bounds
        val mark = emblem ?: return
        val scale = min(bounds.width() / 1280f, bounds.height() / 720f)
        val width = (1280 * scale).roundToInt()
        val height = (720 * scale).roundToInt()
        val left = bounds.left + (bounds.width() - width) / 2
        val top = bounds.top + (bounds.height() - height) / 2
        mark.setBounds(left, top, left + width, top + height)
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(bounds)
        blue?.draw(canvas)
        emblem?.draw(canvas)
        shade?.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha.coerceIn(0, 255)
        blue?.alpha = opacity
        emblem?.alpha = opacity
        shade?.alpha = opacity
        invalidateSelf()
    }

    override fun getAlpha() = opacity

    override fun setColorFilter(colorFilter: ColorFilter?) {
        filter = colorFilter
        blue?.colorFilter = colorFilter
        emblem?.colorFilter = colorFilter
        shade?.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
