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
        entity: S,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        val buffer = bufferSource.getBuffer(getRenderType(entity))

        poseStack.pushPose()
        val overlay = getOverlay(entity, partialTick)
        entity.render(poseStack, buffer, packedLight, overlay, getColor(entity, partialTick), partialTick)
        poseStack.popPose()

        layers.forEach { it.render(entity, partialTick, poseStack, bufferSource, packedLight, -1) }
    }

    fun getColor(entity: S, partialTick: Float): Int = -1

    fun getOverlay(entity: S, partialTick: Float) = OverlayTexture.NO_OVERLAY

    fun getRenderType(entity: S): RenderType {
        return RenderType.entityTranslucent(getGeoTextureLocation(entity))
    }

    fun getGeoTextureLocation(entity: S): ResourceLocation {
        return entity.modelIndex.textureLocation
    }

}