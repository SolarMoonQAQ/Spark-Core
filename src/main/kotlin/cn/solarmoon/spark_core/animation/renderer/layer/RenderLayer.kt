package cn.solarmoon.spark_core.animation.renderer.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.attachment.IAttachmentHolder
import org.checkerframework.checker.units.qual.t

/**
 * 附加渲染
 */
abstract class RenderLayer<T, A: IAnimatable<T>>() {

    abstract fun getTextureLocation(sth: A): ResourceLocation

    abstract fun getRenderType(sth: A): RenderType

    abstract fun render(sth: A, partialTick: Float, physPartialTick: Float, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int, packedOverlay: Int)

}