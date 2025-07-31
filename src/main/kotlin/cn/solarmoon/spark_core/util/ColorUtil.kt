package cn.solarmoon.spark_core.util

import java.awt.Color

object ColorUtil {

    @JvmStatic
    fun getColorAndSetAlpha(color: Int, alpha: Float): Int {
        val colorObj = Color(color, true)
        val red = colorObj.red.toFloat() / 255
        val green = colorObj.green.toFloat() / 255
        val blue = colorObj.blue.toFloat() / 255
        val color = Color(red, green, blue, alpha).rgb
        return color
    }

}

fun Color.lerp(color2: Color, t: Float): Color {
    val color1 = this
    val r = (color1.red + (color2.red - color1.red) * t).toInt()
    val g = (color1.green + (color2.green - color1.green) * t).toInt()
    val b = (color1.blue + (color2.blue - color1.blue) * t).toInt()
    val a = (color1.alpha + (color2.alpha - color1.alpha) * t).toInt()
    return Color(r, g, b, a)
}

fun Color.setAlpha(alpha: Float): Color = Color(ColorUtil.getColorAndSetAlpha(rgb, alpha), true)