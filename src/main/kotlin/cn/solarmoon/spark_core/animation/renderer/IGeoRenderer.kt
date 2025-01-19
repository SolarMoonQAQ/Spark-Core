package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

interface IGeoRenderer<T, S: IAnimatable<T>> {

    val layers: MutableList<RenderLayer<T, S>>

    fun render(
        animatable: S,
        yaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        val buffer = bufferSource.getBuffer(getRenderType(animatable))

        val overlay = getOverlay(animatable, partialTick)
        animatable.render(poseStack.last().normal(), buffer, packedLight, overlay, getColor(animatable, partialTick), partialTick)

        layers.forEach { it.render(animatable, partialTick, poseStack, bufferSource, packedLight, -1) }
    }

    fun getColor(animatable: S, partialTick: Float): Int = -1

    fun getOverlay(animatable: S, partialTick: Float) = OverlayTexture.NO_OVERLAY

    fun getRenderType(animatable: S): RenderType {
        return RenderType.entityTranslucent(getGeoTextureLocation(animatable))
    }

    fun getGeoTextureLocation(animatable: S): ResourceLocation {
        return animatable.modelIndex.textureLocation
    }

}