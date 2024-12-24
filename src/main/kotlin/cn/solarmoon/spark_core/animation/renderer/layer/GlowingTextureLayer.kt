package cn.solarmoon.spark_core.animation.renderer.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.renderer.IGeoRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.attachment.IAttachmentHolder

class GlowingTextureLayer<T: IAttachmentHolder>(renderer: IGeoRenderer<T>): RenderLayer<T>(renderer) {

    override fun getTextureLocation(sth: IAnimatable<T>): ResourceLocation {
        val id = sth.animData.textureLocation
        val path = id.path
        val basePath = path.substringBeforeLast(".")
        val newPath = "${basePath}_glow.png"
        return ResourceLocation.fromNamespaceAndPath(id.namespace, newPath)
    }

    override fun getRenderType(sth: IAnimatable<T>): RenderType {
        return RenderType.eyes(getTextureLocation(sth))
    }

    override fun render(
        sth: IAnimatable<T>,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val buffer = bufferSource.getBuffer(getRenderType(sth))
        val animData = sth.animData
        val model = animData.model
        poseStack.pushPose()
        val overlay = OverlayTexture.NO_OVERLAY
        val matrix = sth.getPositionMatrix(partialTick)
        model.renderBones(animData.playData, matrix, sth.getExtraTransform(partialTick), poseStack.last().normal(), buffer, packedLight, overlay, -1, partialTick)
        poseStack.popPose()
    }

}