package cn.solarmoon.spark_core.animation.renderer.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.renderer.render
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

open class GlowingTextureLayer<T, S: IAnimatable<T>>: RenderLayer<T, S>() {

    override fun getTextureLocation(sth: S): ResourceLocation {
        return sth.modelController.textureLocation
    }

    override fun getRenderType(sth: S): RenderType {
        return RenderType.eyes(getTextureLocation(sth))
    }

    override fun render(
        sth: S,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val buffer = bufferSource.getBuffer(getRenderType(sth))
        poseStack.pushPose()
        val overlay = OverlayTexture.NO_OVERLAY
        sth.render(poseStack, buffer, packedLight, overlay, -1, partialTick)
        poseStack.popPose()
    }

}