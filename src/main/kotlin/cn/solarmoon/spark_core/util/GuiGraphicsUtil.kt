package cn.solarmoon.spark_core.util

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation


fun GuiGraphics.blitTransparent(location: ResourceLocation, x: Float, y: Float, uMin: Float, uMax: Float, vMin: Float, vMax: Float, width: Float, height: Float, color: Int, alpha: Float = 1f) {
    val buffer = bufferSource().getBuffer(RenderTypeUtil.transparentRepair(location))
    val color = ColorUtil.getColorAndSetAlpha(color, alpha)
    buffer.addVertex(x, y, 0f, color, uMin, vMin, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 0f, 1f)
    buffer.addVertex(x, y + height, 0f, color, uMin, vMax, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 0f, 1f)
    buffer.addVertex(x + width, y + height, 0f, color, uMax, vMax, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 0f, 1f)
    buffer.addVertex(x + width, y, 0f, color, uMax, vMin, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0f, 0f, 1f)
}

fun GuiGraphics.blitTransparent(location: ResourceLocation, x: Float, y: Float, u: Float, v: Float, width: Float, height: Float, color: Int, alpha: Float = 1f) {
    blitTransparent(location, x, y, 0f, u, 0f, v, width, height, color, alpha)
}