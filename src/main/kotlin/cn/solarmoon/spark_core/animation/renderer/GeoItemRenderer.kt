package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import cn.solarmoon.spark_core.registry.common.SparkDataComponents
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack


open class GeoItemRenderer: BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher, Minecraft.getInstance().entityModels), IGeoRenderer<ItemAnimatable, ItemAnimatable> {

    override val layers: MutableList<RenderLayer<ItemAnimatable, ItemAnimatable>> = mutableListOf()

    override fun renderByItem(
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val animatable = stack.get(SparkDataComponents.ANIMATABLE) ?: return
        val partialTicks = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(true)
        when(displayContext) {
            ItemDisplayContext.GUI -> {  }
            else -> render(animatable, 0f, partialTicks, poseStack, buffer, packedLight)
        }
    }

}