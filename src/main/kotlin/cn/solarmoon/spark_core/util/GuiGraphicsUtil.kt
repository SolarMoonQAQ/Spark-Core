package cn.solarmoon.spark_core.util

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f


fun GuiGraphics.blitTransparent(location: ResourceLocation, x: Float, y: Float, uMin: Float, uMax: Float, vMin: Float, vMax: Float, width: Float, height: Float, color: Int) {
    RenderSystem.setShaderTexture(0, location)
    RenderSystem.setShader(GameRenderer::getPositionTexColorShader)// 使用支持颜色的着色器
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
    val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
    buffer.addVertex(pose().last(), Vector3f(x, y , 0f))
        .setNormal(pose().last(), 0f, 0f, 1f)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(LightTexture.FULL_BRIGHT)
        .setUv(uMin, vMin)
        .setColor(color)
    buffer.addVertex(pose().last(), Vector3f(x, y + height , 0f))
        .setNormal(pose().last(), 0f, 0f, 1f)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(LightTexture.FULL_BRIGHT)
        .setUv(uMin, vMax)
        .setColor(color)
    buffer.addVertex(pose().last(), Vector3f(x + width, y + height , 0f))
        .setNormal(pose().last(), 0f, 0f, 1f)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(LightTexture.FULL_BRIGHT)
        .setUv(uMax, vMax)
        .setColor(color)
    buffer.addVertex(pose().last(), Vector3f(x + width, y , 0f))
        .setNormal(pose().last(), 0f, 0f, 1f)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(LightTexture.FULL_BRIGHT)
        .setUv(uMax, vMin)
        .setColor(color)
    BufferUploader.drawWithShader(buffer.buildOrThrow())
    RenderSystem.disableBlend()
}

fun GuiGraphics.blitTransparent(location: ResourceLocation, x: Float, y: Float, u: Float, v: Float, width: Float, height: Float, color: Int) {
    blitTransparent(location, x, y, 0f, u, 0f, v, width, height, color)
}