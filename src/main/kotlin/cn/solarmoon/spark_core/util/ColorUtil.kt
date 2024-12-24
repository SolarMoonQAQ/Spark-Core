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