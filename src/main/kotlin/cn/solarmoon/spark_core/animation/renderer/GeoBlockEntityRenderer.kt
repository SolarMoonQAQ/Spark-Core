package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IBlockEntityAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.entity.BlockEntity

open class GeoBlockEntityRenderer<B>(
    val context: BlockEntityRendererProvider.Context
): BlockEntityRenderer<B>, IGeoRenderer<B, IBlockEntityAnimatable<B>> where B: BlockEntity, B: IBlockEntityAnimatable<B> {

    override val layers: MutableList<RenderLayer<B, IBlockEntityAnimatable<B>>> = mutableListOf()

    override fun render(
        blockEntity: B,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val light = blockEntity.level?.let { LevelRenderer.getLightColor(it, blockEntity.blockPos.above()) } ?: packedLight
        val pos = blockEntity.blockPos
        poseStack.translate(-pos.x.toFloat(), -pos.y.toFloat(), -pos.z.toFloat())
        super.render(blockEntity, partialTick, poseStack, bufferSource, light)
    }

}