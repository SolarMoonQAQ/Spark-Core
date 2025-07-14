package cn.solarmoon.spark_core.animation.renderer.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.renderer.render
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

open class GlowingTextureLayer<T, S: IAnimatable<T>>: RenderLayer<T, S>() {

    override fun getTextureLocation(sth: S): ResourceLocation {
        val id = sth.modelIndex.textureLocation
        val path = id.path

        // 解析路径以使用SparkResourcePathBuilder
        val pathParts = path.split("/")
        return if (pathParts.size >= 3 && pathParts[1] == "textures") {
            val moduleName = pathParts[0]
            val texturePath = pathParts.drop(2).joinToString("/")
            val basePath = texturePath.substringBeforeLast(".")
            val glowTexturePath = "${basePath}_glow.png"
            SparkResourcePathBuilder.buildTexturePath(id.namespace, moduleName, glowTexturePath)
        } else {
            // 回退到原始方法
            val basePath = path.substringBeforeLast(".")
            val newPath = "${basePath}_glow.png"
            ResourceLocation.fromNamespaceAndPath(id.namespace, newPath)
        }
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
        sth.render(poseStack.last().normal(), buffer, packedLight, overlay, -1, partialTick)
        poseStack.popPose()
    }

}